package com.r3edge.cloudregistry;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.hazelcast.config.Config;
import com.hazelcast.config.YamlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.r3edge.springflip.FlipConfiguration;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implémentation de {@link ServiceRegistry} utilisant Hazelcast comme backend de stockage.
 * <p>
 * Cette classe initialise un {@link HazelcastInstance} à partir d'une configuration YAML
 * fournie via {@link ServiceRegistryProperties}. Elle gère la réutilisation d'une instance
 * existante si elle porte le même nom, et ferme proprement l'instance à la destruction du contexte Spring.
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

    /**
     * Initialise le {@link HazelcastInstance} à partir de la configuration YAML.
     * <ul>
     *   <li>Charge le YAML via {@link YamlConfigBuilder}.</li>
     *   <li>Tente de récupérer une instance existante portant le même nom.</li>
     *   <li>Réutilise l'instance existante ou en crée une nouvelle.</li>
     * </ul>
     *
     * @throws IllegalStateException si l'initialisation échoue
     */
    @PostConstruct
    public void init() {
        try {
            // 1. Chargement du YAML de config
            String yaml = properties.getHazelcastConfig();
            Config config = new YamlConfigBuilder(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))
            ).build();

            // 2. Vérification d’une instance existante et shutdown si présente
            String instanceName = config.getInstanceName();
            HazelcastInstance existing = Hazelcast.getHazelcastInstanceByName(instanceName);
            if (existing != null) {
                log.info("🛑 Instance Hazelcast existante '{}' détectée, arrêt avant création", instanceName);
                existing.shutdown();  // on ferme proprement l’ancien membre
            }

            // 3. Création d’une nouvelle instance avec la config chargée
            log.info("🔄 Création d'une nouvelle instance Hazelcast '{}'", instanceName);
            this.hazelcast = Hazelcast.newHazelcastInstance(config);
            log.info("🔄 Hazelcast initialisé localement avec instanceName={}", instanceName);

        } catch (Exception e) {
            log.error("⚠️ Impossible d'initialiser Hazelcast depuis la configuration fournie", e);
            throw new IllegalStateException("Failed to initialize Hazelcast", e);
        }

        // 4. Log Spring Flip
        if (flipConfiguration.isEmpty()) {
            log.warn("⚠️ Aucune configuration Spring Flip détectée. Les features seront vides.");
        } else {
            log.info("🔄 Spring Flip détecté. Les features seront dynamiques.");
        }
    }

    /**
     * Ferme proprement l'instance Hazelcast pour éviter les fuites de ressources
     * lors de l'arrêt du contexte Spring (tests, redéploiement...).
     */
    @PreDestroy
    public void destroy() {
        String name = hazelcast != null ? hazelcast.getName() : "<unknown>";
        log.info("🛑 [Hazelcast] Arrêt de l'instance '{}'", name);
        if (hazelcast != null) {
            hazelcast.shutdown();
        }
    }

    /**
     * Complète l'initialisation en fixant l'instance de service locale.
     *
     * @param selfInstance informations de l'instance courante du service
     */
    @Override
    public void completeInit(ServiceInstance selfInstance) {
        this.selfInstance = selfInstance;
        log.info("✅ [Hazelcast] ServiceInstance initialisé : {}", selfInstance);
        // TODO: enregistrer selfInstance dans la map Hazelcast
    }

    private List<String> getDynamicFeatures() {
        return flipConfiguration.map(FlipConfiguration::getFlip)
                .map(map -> map.keySet().stream().toList())
                .orElse(Collections.emptyList());
    }

    /**
     * Enregistre un {@link ServiceDescriptor} dans Hazelcast.
     *
     * @param descriptor description du service à enregistrer
     */
    @Override
    public void register(ServiceDescriptor descriptor) {
        log.info("📝 [Hazelcast] register appelé avec : {}", descriptor);
        // TODO: stocker descriptor dans la map Hazelcast
    }

    /**
     * Désenregistre tous les services portant ce nom.
     *
     * @param serviceName nom du service à désenregistrer
     */
    @Override
    public void unregister(String serviceName) {
        log.info("🗑️ [Hazelcast] unregister pour : {}", serviceName);
        // TODO: supprimer de la map Hazelcast
    }

    /**
     * Désenregistre une instance de service donnée.
     *
     * @param instanceId identifiant de l'instance à désenregistrer
     */
    @Override
    public void unregisterInstance(String instanceId) {
        log.info("🗑️ [Hazelcast] unregisterInstance pour ID : {}", instanceId);
        // TODO: supprimer instance de la map
    }

    /**
     * Désenregistre la feature d'une instance.
     *
     * @param instanceId identifiant de l'instance
     * @param feature nom de la feature à désenregistrer
     */
    @Override
    public void unregisterFeature(String instanceId, String feature) {
        log.info("🗑️ [Hazelcast] unregisterFeature pour instance {} / feature {}", instanceId, feature);
        // TODO: désenregistrer la feature de l'instance
    }

    /**
     * Résout l'URL d'un service par son nom.
     *
     * @param serviceName nom du service
     * @return URL de base du service, ou null si non trouvé
     */
    @Override
    public String resolveServiceUrl(String serviceName) {
        log.info("🔍 [Hazelcast] resolveServiceUrl pour : {}", serviceName);
        // TODO: lookup dans Hazelcast
        return null;
    }

    /**
     * Résout l'URL associée à une feature.
     *
     * @param feature nom de la feature
     * @return URL du service exposant la feature, ou null si non trouvé
     */
    @Override
    public String resolveFeatureUrl(String feature) {
        log.info("🔍 [Hazelcast] resolveFeatureUrl pour : {}", feature);
        // TODO: lookup feature -> service
        return null;
    }

    /**
     * Retourne la liste des services enregistrés pour chaque nom de service.
     * Pour l'instant, ne contient que l'instance locale si initialisée.
     */
    @Override
    public Map<String, List<ServiceDescriptor>> getRegisteredServices() {
        log.info("📋 [Hazelcast] getRegisteredServices appelé");
        if (selfInstance == null) {
            return Collections.emptyMap();
        }
        return Map.of(selfInstance.getServiceName(), List.of(getSelfDescriptor()));
    }

    /**
     * Retourne la map des features enregistrées vers leurs descriptors.
     * Pour l'instant, ne contient que les features de l'instance locale.
     */
    @Override
    public Map<String, List<ServiceDescriptor>> getRegisteredFeatures() {
        log.info("📋 [Hazelcast] getRegisteredFeatures appelé");
        if (selfInstance == null) {
            return Collections.emptyMap();
        }
        List<String> features = getSelfDescriptor().getFeatures();
        if (features.isEmpty()) {
            return Collections.emptyMap();
        }
        return features.stream()
                .collect(Collectors.toMap(feature -> feature, feature -> List.of(getSelfDescriptor())));
    }

    /**
     * Construit et retourne le {@link ServiceDescriptor} de l'instance locale.
     */
    @Override
    public ServiceDescriptor getSelfDescriptor() {
        if (selfInstance == null) {
            return null;
        }
        return new ServiceDescriptor(
            selfInstance.getServiceName(),
            selfInstance.getInstanceId(),
            selfInstance.getExternalBaseUrl(),
            getDynamicFeatures()
        );
    }

    /**
     * Méthode de compatibilité : ferme l'instance Hazelcast.
     * Utiliser de préférence {@link #destroy()}.
     */
    @Override
    public void shutdown() {
        log.warn("🛑 [Hazelcast] shutdown() appelé mais préférez @PreDestroy destroy()");
        if (hazelcast != null) {
            hazelcast.shutdown();
        }
    }
}
