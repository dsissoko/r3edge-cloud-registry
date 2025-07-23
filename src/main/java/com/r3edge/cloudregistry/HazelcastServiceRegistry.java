package com.r3edge.cloudregistry;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import com.hazelcast.config.Config;
import com.hazelcast.config.YamlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.r3edge.springflip.FlipConfiguration;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implémentation de {@link ServiceRegistry} utilisant Hazelcast comme backend de stockage.
 * <p>
 * Elle gère une map Hazelcast partagée, dans laquelle chaque instance de microservice
 * publie un {@link ServiceDescriptor} décrivant ses capacités exposées.
 * </p>
 * <p>
 * Si Spring Flip est actif, les features sont recalculées dynamiquement pour l’instance locale
 * à chaque consultation via {@link #getEnabledFeatures(ServiceDescriptor)}.
 * </p>
 * <p>
 * L’instance locale est republiée automatiquement lors des événements {@link RefreshScopeRefreshedEvent}.
 * </p>
 */
@Component
@ConditionalOnProperty(prefix = "r3edge.registry", name = "strategy", havingValue = "hazelcast")
@RequiredArgsConstructor
@Slf4j
public class HazelcastServiceRegistry implements ServiceRegistry {

    private final ServiceRegistryProperties properties;
    private final Optional<FlipConfiguration> flipConfiguration;

    @Getter
    private HazelcastInstance hazelcast;

    private ServiceInstance selfInstance;

    /** Nom de la map Hazelcast contenant les {@link ServiceDescriptor} */
    private static final String REGISTRY_MAP_NAME = "r3edge-service-registry";

    /**
     * Retourne la map Hazelcast contenant les {@link ServiceDescriptor}.
     *
     * @return map partagée dans le cluster
     */
    private IMap<String, ServiceDescriptor> getRegistryMap() {
        return hazelcast.getMap(REGISTRY_MAP_NAME);
    }

    /**
     * Initialise Hazelcast à partir de la configuration YAML fournie.
     * Si une instance Hazelcast du même nom existe déjà, elle est arrêtée proprement.
     */
    @PostConstruct
    public void init() {
        try {
            String yaml = properties.getHazelcastConfig();
            Yaml snake = new Yaml();
            Map<String, Object> root = snake.load(yaml);
            Object hazelcastNode = root;
            if (hazelcastNode == null) {
                throw new IllegalArgumentException("Bloc racine 'hazelcast' manquant");
            }
            String cleanYaml = snake.dump(hazelcastNode);
            Config config = new YamlConfigBuilder(
                    new ByteArrayInputStream(cleanYaml.getBytes(StandardCharsets.UTF_8))
            ).build();

            String instanceName = config.getInstanceName();
            HazelcastInstance existing = Hazelcast.getHazelcastInstanceByName(instanceName);
            if (existing != null) {
                log.info("🛑 Instance Hazelcast '{}' déjà existante. Fermeture...", instanceName);
                existing.shutdown();
            }

            this.hazelcast = Hazelcast.newHazelcastInstance(config);
            log.info("✅ Hazelcast initialisé : {}", instanceName);

        } catch (Exception e) {
            log.error("❌ Échec de l'initialisation Hazelcast", e);
            throw new IllegalStateException("Failed to initialize Hazelcast", e);
        }

        if (flipConfiguration.isEmpty()) {
            log.warn("⚠️ Spring Flip non détecté, les features ne seront pas dynamiques");
        } else {
            log.info("🔄 Spring Flip détecté, features dynamiques activées");
        }
    }

    /**
     * Arrête proprement l’instance Hazelcast.
     */
    @PreDestroy
    public void destroy() {
        if (hazelcast != null) {
            log.info("🛑 Arrêt Hazelcast instance '{}'", hazelcast.getName());
            hazelcast.shutdown();
        }
    }

    /**
     * Initialise l’instance locale et l’enregistre dans Hazelcast.
     *
     * @param selfInstance instance locale à enregistrer
     */
    @Override
    public void completeInit(ServiceInstance selfInstance) {
        this.selfInstance = selfInstance;
        log.info("✅ SelfInstance initialisé : {}", selfInstance);
        registerSelf();
    }

    /**
     * Méthode non utilisée dans cette implémentation.
     * L'enregistrement doit se faire via {@link #registerSelf()}.
     */
    @Override
    public void register(ServiceDescriptor descriptor) {
        log.warn("⚠️ register(ServiceDescriptor) ignoré – utiliser registerSelf()");
    }

    /**
     * Supprime toutes les instances d’un service donné de la registry.
     *
     * @param serviceName nom du service
     */
    @Override
    public void unregister(String serviceName) {
        log.info("🗑️ Unregister tous les services '{}'", serviceName);
        getRegistryMap().values().removeIf(d -> d.getServiceName().equals(serviceName));
    }

    /**
     * Supprime une instance précise de la registry.
     *
     * @param instanceId identifiant de l’instance
     */
    @Override
    public void unregisterInstance(String instanceId) {
        log.info("🗑️ Unregister instance '{}'", instanceId);
        getRegistryMap().remove(instanceId);
    }

    /**
     * Supprime une feature d’une instance spécifique.
     *
     * @param instanceId identifiant de l’instance
     * @param feature nom de la feature à retirer
     */
    @Override
    public void unregisterFeature(String instanceId, String feature) {
        log.info("🗑️ Unregister feature '{}' from instance '{}'", feature, instanceId);
        ServiceDescriptor descriptor = getRegistryMap().get(instanceId);
        if (descriptor != null && descriptor.getFeatures() != null) {
            List<String> updated = descriptor.getFeatures().stream()
                    .filter(f -> !f.equals(feature))
                    .toList();
            descriptor.setFeatures(updated);
            getRegistryMap().put(instanceId, descriptor);
        }
    }

    /**
     * Retourne la liste des services enregistrés, regroupés par nom logique.
     * Les features sont recalculées dynamiquement si Spring Flip est actif.
     */
    @Override
    public Map<String, List<ServiceDescriptor>> getRegisteredServices() {
        return getRegistryMap().values().stream()
            .map(this::cloneWithDynamicFeatures)
            .collect(Collectors.groupingBy(
                ServiceDescriptor::getServiceName
            ));
    }

    /**
     * Retourne la liste des instances par feature exposée.
     * Les features sont recalculées dynamiquement si Spring Flip est actif.
     */
    @Override
    public Map<String, List<ServiceDescriptor>> getRegisteredFeatures() {
        return getRegistryMap().values().stream()
            .flatMap(d -> {
                List<String> enabled = selfInstance.getEnabledFeatures();
                return enabled.stream()
                    .map(f -> Map.entry(f, cloneWithDynamicFeatures(d)));
            })
            .collect(Collectors.groupingBy(
                Map.Entry::getKey,
                Collectors.mapping(Map.Entry::getValue, Collectors.toList())
            ));
    }

    /**
     * Clone un {@link ServiceDescriptor} en recalculant dynamiquement ses features.
     *
     * @param d descripteur d’origine
     * @return descripteur cloné avec les features à jour
     */
    private ServiceDescriptor cloneWithDynamicFeatures(ServiceDescriptor d) {
        return ServiceDescriptor.builder()
                .serviceName(d.getServiceName())
                .instanceId(d.getInstanceId())
                .internalBaseUrl(d.getInternalBaseUrl())
                .externalBaseUrl(d.getExternalBaseUrl())
                .features(selfInstance.getEnabledFeatures())
                .metadata(d.getMetadata() != null ? d.getMetadata() : Map.of())
                .build();
    }

    /**
     * Retourne le {@link ServiceDescriptor} de l’instance locale avec les features dynamiques.
     */
    @Override
    public ServiceDescriptor getSelfDescriptor() {
        if (selfInstance == null) return null;
        return selfInstance.toServiceDescriptor();
    }

    /**
     * Arrête Hazelcast manuellement.
     */
    @Override
    public void shutdown() {
        log.warn("🔻 shutdown() appelé manuellement");
        destroy();
    }

    /**
     * Résout l’URL interne (cluster) d’un service.
     *
     * @param serviceName nom logique
     * @return URL interne ou null si non trouvé
     */
    @Override
    public String resolveInternalServiceUrl(String serviceName) {
        return getRegistryMap().values().stream()
            .filter(d -> d.getServiceName().equals(serviceName))
            .map(ServiceDescriptor::getInternalBaseUrl)
            .findFirst()
            .orElse(null);
    }

    /**
     * Résout l’URL externe (reverse proxy) d’un service.
     *
     * @param serviceName nom logique
     * @return URL externe ou null si non trouvé
     */
    @Override
    public String resolveExternalServiceUrl(String serviceName) {
        return getRegistryMap().values().stream()
            .filter(d -> d.getServiceName().equals(serviceName))
            .map(ServiceDescriptor::getExternalBaseUrl)
            .findFirst()
            .orElse(null);
    }

    /**
     * Résout l’URL interne d’une feature donnée.
     *
     * @param feature nom de la feature
     * @return URL interne ou null si aucune instance ne l’expose
     */
    @Override
    public String resolveInternalFeatureUrl(String feature) {
        return getRegistryMap().values().stream()
            .filter(d -> selfInstance.getEnabledFeatures().contains(feature))
            .map(ServiceDescriptor::getInternalBaseUrl)
            .findFirst()
            .orElse(null);
    }

    /**
     * Résout l’URL externe d’une feature donnée.
     *
     * @param feature nom de la feature
     * @return URL externe ou null si aucune instance ne l’expose
     */
    @Override
    public String resolveExternalFeatureUrl(String feature) {
        return getRegistryMap().values().stream()
            .filter(d -> selfInstance.getEnabledFeatures().contains(feature))
            .map(ServiceDescriptor::getExternalBaseUrl)
            .findFirst()
            .orElse(null);
    }

    /**
     * Met à jour la publication de l’instance locale suite à un refresh Spring Cloud.
     */
    @EventListener(RefreshScopeRefreshedEvent.class)
    public void onRefresh() {
        log.info("🔁 RefreshScope détecté – re-publication de selfInstance");
        registerSelf();
    }

    /**
     * Publie l’instance locale dans la registry Hazelcast.
     */
    public void registerSelf() {
        if (selfInstance == null) return;
        var descriptor = selfInstance.toServiceDescriptor();
        getRegistryMap().put(selfInstance.getInstanceId(), descriptor);
        log.info("📥 Publication selfInstance : {}", descriptor);
    }
}
