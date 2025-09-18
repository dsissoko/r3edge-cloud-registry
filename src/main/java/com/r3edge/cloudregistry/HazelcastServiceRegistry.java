package com.r3edge.cloudregistry;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.cluster.MembershipEvent;
import com.hazelcast.cluster.MembershipListener;
import com.hazelcast.config.Config;
import com.hazelcast.config.YamlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceNotActiveException;
import com.hazelcast.map.IMap;
import com.hazelcast.spring.context.SpringManagedContext;
import com.r3edge.springflip.FlipConfiguration;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implémentation de {@link ServiceRegistry} utilisant Hazelcast comme backend
 * de stockage.
 * <p>
 * Elle gère une map Hazelcast partagée, dans laquelle chaque instance de
 * microservice publie un {@link ServiceDescriptor} décrivant ses capacités
 * exposées.
 * </p>
 * <p>
 * Si Spring Flip est actif, les features sont recalculées dynamiquement pour
 * l’instance locale à chaque consultation via
 * {@link ServiceInstance#getEnabledFeatures()}.
 * </p>
 * <p>
 * L’instance locale est republiée automatiquement lors des événements
 * {@link RefreshScopeRefreshedEvent}.
 * </p>
 */
@Component("hazelcastServiceRegistry")
@ConditionalOnProperty(prefix = "r3edge.registry", name = "strategy", havingValue = "hazelcast")
@RequiredArgsConstructor
@Slf4j
public class HazelcastServiceRegistry implements ServiceRegistry {

	private final ApplicationContext springContext;
	private final ServiceRegistryProperties properties;
	private final Optional<FlipConfiguration> flipConfiguration;
	private final Environment environment;
	@Getter
	private boolean clientMode = false;
	@Getter
	private ClientConfig clientConfig;

	@Getter
	private HazelcastInstance hazelcast;

	private ServiceInstance selfInstance;

	/** Nom de la map Hazelcast contenant les {@link ServiceDescriptor} */
	private static final String REGISTRY_MAP_NAME = "r3edge-service-registry";
	private static final String INTERNAL_KEY_HAZELCAST_UUID = "__internal__hazelcast_uuid";

	/**
	 * Retourne la map Hazelcast contenant les {@link ServiceDescriptor}.
	 *
	 * @return map partagée dans le cluster
	 */
	private IMap<String, ServiceDescriptor> getRegistryMap() {
		return hazelcast.getMap(REGISTRY_MAP_NAME);
	}

	/**
	 * Initialise Hazelcast à partir de la configuration YAML fournie. Si une
	 * instance Hazelcast du même nom existe déjà, elle est arrêtée proprement.
	 */
	@PostConstruct
	public void init() {
		try {
			String yaml = properties.getHazelcastConfig();
            if (yaml == null || yaml.isBlank()) {
                throw new IllegalArgumentException("r3edge.registry.hazelcast-config manquant");
            }

            // >>> Résoudre les ${...} ici (y compris les valeurs par défaut)
            yaml = environment.resolvePlaceholders(yaml);			
			Yaml snake = new Yaml();
			Map<String, Object> root = snake.load(yaml);
			Object hazelcastNode = root;
			if (hazelcastNode == null) {
				throw new IllegalArgumentException("Bloc racine 'hazelcast' manquant");
			}

			this.clientMode = root.containsKey("hazelcast-client");

			String cleanYaml = snake.dump(hazelcastNode);

			if (!clientMode) {
				Config config = new YamlConfigBuilder(
						new ByteArrayInputStream(cleanYaml.getBytes(StandardCharsets.UTF_8))).build();

			    SpringManagedContext managedContext = new SpringManagedContext();
			    managedContext.setApplicationContext(springContext);
			    config.setManagedContext(managedContext);
			    log.info("✅ SpringManagedContext injecté – les tâches Hazelcast distribuées et annotées @SpringAware peuvent accéder aux beans Spring");

				String instanceName = config.getInstanceName();
				HazelcastInstance existing = Hazelcast.getHazelcastInstanceByName(instanceName);			
				if (existing != null) {
					log.warn("⚠️ Instance Hazelcast '{}' déjà existante. Fermeture...", instanceName);
					existing.shutdown();
				}

				this.hazelcast = Hazelcast.newHazelcastInstance(config);
				log.info("✅ Hazelcast initialisé : {}", instanceName);
			} else if (clientMode) {
				clientConfig = new com.hazelcast.client.config.YamlClientConfigBuilder(
						new ByteArrayInputStream(cleanYaml.getBytes(StandardCharsets.UTF_8))).build();

				this.hazelcast = com.hazelcast.client.HazelcastClient.newHazelcastClient(clientConfig);
				log.info("✅ Hazelcast client initialisé (cluster: {})", clientConfig.getClusterName());
			}

		} catch (Exception e) {
			log.error("❌ Échec de l'initialisation Hazelcast", e);
			throw new IllegalStateException("Failed to initialize Hazelcast", e);
		}

		if (flipConfiguration.isEmpty()) {
			log.warn("⚠️ Spring Flip non détecté, les features ne seront pas dynamiques");
		} else {
			log.info("✅ Spring Flip détecté, features dynamiques activées");
		}

		HazelcastClusterListener listener = new HazelcastClusterListener();
		hazelcast.getCluster().addMembershipListener(listener);
		hazelcast.getLifecycleService().addLifecycleListener(listener);
	}

	/**
	 * Arrête proprement l’instance Hazelcast.
	 */
	@PreDestroy
	public void destroy() {
	    if (hazelcast == null || !hazelcast.getLifecycleService().isRunning()) {
	        log.warn("⚠️ Hazelcast déjà arrêté. Skip destruction logic.");
	        return;
	    }

	    if (selfInstance != null) {
	        try {
	            log.info("✅ Nettoyage selfInstance avant arrêt : {}", selfInstance.getInstanceId());
	            unregisterInstance(selfInstance.getInstanceId());
	        } catch (HazelcastInstanceNotActiveException e) {
	            log.warn("⚠️ Hazelcast instance inactive pendant unregisterInstance – skip", e);
	        }
	    }

	    log.info("✅ Arrêt Hazelcast instance '{}'", hazelcast.getName());
	    hazelcast.shutdown();
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
	 * Méthode non utilisée dans cette implémentation. L'enregistrement doit se
	 * faire via {@link #registerSelf()}.
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
		log.info("ℹ️ Unregister tous les services '{}'", serviceName);
		getRegistryMap().values().removeIf(d -> d.getServiceName().equals(serviceName));
	}

	/**
	 * Supprime une instance précise de la registry.
	 *
	 * @param instanceId identifiant de l’instance
	 */
	@Override
	public void unregisterInstance(String instanceId) {
		log.info("ℹ️ Unregister instance '{}'", instanceId);
		getRegistryMap().remove(instanceId);
	}

	/**
	 * Supprime une feature d’une instance spécifique.
	 *
	 * @param instanceId identifiant de l’instance
	 * @param feature    nom de la feature à retirer
	 */
	@Override
	public void unregisterFeature(String instanceId, String feature) {
		log.info("ℹ️ Unregister feature '{}' from instance '{}'", feature, instanceId);
		ServiceDescriptor descriptor = getRegistryMap().get(instanceId);
		if (descriptor != null && descriptor.getFeatures() != null) {
			List<String> updated = descriptor.getFeatures().stream().filter(f -> !f.equals(feature)).toList();
			descriptor.setFeatures(updated);
			getRegistryMap().put(instanceId, descriptor);
		}
	}

	/**
	 * Retourne la liste des services enregistrés, regroupés par nom logique. Les
	 * features sont recalculées dynamiquement si Spring Flip est actif.
	 */
	@Override
	public Map<String, List<ServiceDescriptor>> getRegisteredServices() {
		return getRegistryMap().values().stream().map(this::cloneWithDynamicFeatures)
				.collect(Collectors.groupingBy(ServiceDescriptor::getServiceName));
	}

	/**
	 * Retourne la liste des instances par feature exposée. Les features sont
	 * recalculées dynamiquement si Spring Flip est actif.
	 */
	@Override
	public Map<String, List<ServiceDescriptor>> getRegisteredFeatures() {
		return getRegistryMap().values().stream().flatMap(d -> {
			List<String> enabled = selfInstance.getEnabledFeatures();
			return enabled.stream().map(f -> Map.entry(f, cloneWithDynamicFeatures(d)));
		}).collect(
				Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
	}

	/**
	 * Clone un {@link ServiceDescriptor} en recalculant dynamiquement ses features.
	 *
	 * @param d descripteur d’origine
	 * @return descripteur cloné avec les features à jour
	 */
	private ServiceDescriptor cloneWithDynamicFeatures(ServiceDescriptor d) {
		return ServiceDescriptor.builder().serviceName(d.getServiceName()).instanceId(d.getInstanceId())
				.internalBaseUrl(d.getInternalBaseUrl()).externalBaseUrl(d.getExternalBaseUrl())
				.features(selfInstance.getEnabledFeatures())
				.metadata(d.getMetadata() != null ? d.getMetadata() : Map.of()).build();
	}

	/**
	 * Retourne le {@link ServiceDescriptor} de l’instance locale avec les features
	 * dynamiques.
	 */
	@Override
	public ServiceDescriptor getSelfDescriptor() {
		if (selfInstance == null)
			return null;
		return selfInstance.toServiceDescriptor();
	}

	/**
	 * Arrête Hazelcast manuellement.
	 */
	@Override
	public void shutdown() {
		log.warn("ℹ️ shutdown() appelé manuellement");
		destroy();
	}

	/**
	 * Sélectionne aléatoirement une URL parmi celles extraites d'un flux de {@link ServiceDescriptor}.
	 *
	 * @param stream le flux de descripteurs de service
	 * @param extractor fonction permettant d’extraire l’URL depuis un descripteur
	 * @return une URL choisie au hasard parmi celles extraites, ou null s'il n'y en a aucune
	 */
	protected String pickRandomUrl(Stream<ServiceDescriptor> stream, Function<ServiceDescriptor, String> extractor) {
		List<String> urls = stream.map(extractor).filter(Objects::nonNull).toList();

		if (urls.isEmpty())
			return null;
		return urls.get(ThreadLocalRandom.current().nextInt(urls.size()));
	}

	/**
	 * Résout l’URL interne (cluster) d’un service.
	 *
	 * @param serviceName nom logique
	 * @return URL interne ou null si non trouvé
	 */
	@Override
	public String resolveInternalServiceUrl(String serviceName) {
		return pickRandomUrl(getRegistryMap().values().stream().filter(d -> d.getServiceName().equals(serviceName)),
				ServiceDescriptor::getInternalBaseUrl);
	}

	/**
	 * Résout l’URL externe (reverse proxy) d’un service.
	 *
	 * @param serviceName nom logique
	 * @return URL externe ou null si non trouvé
	 */
	@Override
	public String resolveExternalServiceUrl(String serviceName) {
		return pickRandomUrl(getRegistryMap().values().stream().filter(d -> serviceName.equals(d.getServiceName())),
				ServiceDescriptor::getExternalBaseUrl);
	}

	/**
	 * Résout l’URL interne d’une feature donnée.
	 *
	 * @param feature nom de la feature
	 * @return URL interne ou null si aucune instance ne l’expose
	 */
	@Override
	public String resolveInternalFeatureUrl(String feature) {
		return pickRandomUrl(
				getRegistryMap().values().stream().filter(d -> selfInstance.getEnabledFeatures().contains(feature)),
				ServiceDescriptor::getInternalBaseUrl);
	}

	/**
	 * Résout l’URL externe d’une feature donnée.
	 *
	 * @param feature nom de la feature
	 * @return URL externe ou null si aucune instance ne l’expose
	 */
	@Override
	public String resolveExternalFeatureUrl(String feature) {
		return pickRandomUrl(
				getRegistryMap().values().stream().filter(d -> selfInstance.getEnabledFeatures().contains(feature)),
				ServiceDescriptor::getExternalBaseUrl);
	}

	/**
	 * Met à jour la publication de l’instance locale suite à un refresh Spring
	 * Cloud.
	 */
	@EventListener(RefreshScopeRefreshedEvent.class)
	public void onRefresh() {
		log.info("✅ RefreshScope détecté – re-publication de selfInstance");
		registerSelf();
	}

	/**
	 * Publie l’instance locale dans la registry Hazelcast.
	 */
	public void registerSelf() {
		if (selfInstance == null)
			return;

		var descriptor = selfInstance.toServiceDescriptor();

		String hazelcastUuid = hazelcast.getCluster().getLocalMember().getUuid().toString();
		Map<String, String> enrichedMetadata = descriptor.getMetadata() != null
				? new HashMap<>(descriptor.getMetadata())
				: new HashMap<>();
		enrichedMetadata.put(INTERNAL_KEY_HAZELCAST_UUID, hazelcastUuid);
		descriptor.setMetadata(enrichedMetadata);

		getRegistryMap().put(selfInstance.getInstanceId(), descriptor);
		log.info("✅ Publication selfInstance avec UUID Hazelcast : {} → {}", hazelcastUuid,
				descriptor.getInstanceId());
	}

	private class HazelcastClusterListener implements MembershipListener, com.hazelcast.core.LifecycleListener {

		@Override
		public void memberRemoved(MembershipEvent event) {
			String removedUuid = event.getMember().getUuid().toString();
			log.warn("⚠️ Membre Hazelcast supprimé : {}", removedUuid);

			int count = 0;
			for (Map.Entry<String, ServiceDescriptor> entry : getRegistryMap().entrySet()) {
				ServiceDescriptor desc = entry.getValue();
				String uuidInMetadata = Optional.ofNullable(desc.getMetadata())
						.map(m -> m.get(INTERNAL_KEY_HAZELCAST_UUID)).orElse(null);

				if (removedUuid.equals(uuidInMetadata)) {
					getRegistryMap().remove(entry.getKey());
					log.info("✅ Instance orpheline supprimée : {}", entry.getKey());
					count++;
				}
			}

			if (count == 0) {
				log.info("✅ Aucun ServiceDescriptor à nettoyer pour {}", removedUuid);
			} else {
				log.info("✅ {} instance(s) nettoyée(s) suite au départ du membre {}", count, removedUuid);
			}
		}

		@Override
		public void memberAdded(MembershipEvent event) {
			log.info("✅ Nouveau membre Hazelcast détecté : {}", event.getMember().getUuid());
		}

		@Override
		public void stateChanged(com.hazelcast.core.LifecycleEvent event) {
			switch (event.getState()) {
			case MERGED:
				log.info("✅ Hazelcast MERGED – Réenregistrement dans la registry");
				registerSelf();
				break;
			case STARTED:
				if (selfInstance != null) {
					log.info("✅ Hazelcast STARTED – Re-publication post-redémarrage");
					registerSelf();
				} else {
					log.debug("ℹ️ Hazelcast STARTED ignoré – selfInstance encore null");
				}
				break;
			default:
				log.debug("ℹ️ Changement d’état Hazelcast ignoré : {}", event.getState());
			}
		}
	}

}
