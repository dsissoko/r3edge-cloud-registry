package com.r3edge.cloudregistry;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import com.hazelcast.core.HazelcastInstance;

import lombok.extern.slf4j.Slf4j;

/**
 * ✅ Objectif de ce test d'intégration :
 * 
 * Vérifier que :
 * 1. Un cluster Hazelcast est bien initialisé automatiquement à partir d’un fichier `hazelcast.yaml` présent dans le classpath.
 * 2. Aucun service de registre personnalisé (ServiceRegistry) n’est instancié si aucune stratégie n’est définie.
 *
 * Ce test valide le comportement par défaut de la bibliothèque :
 * → Aucun bean explicite requis, aucune configuration supplémentaire nécessaire côté client.
 * → La lib est activée uniquement si les conditions sont remplies (profil + config classpath).
 *
 * Cf. README pour la description complète du comportement attendu.
 */
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test-hazelcast-autoconfig")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Slf4j
class HazelcastRegistryAutoConfigIntegrationTest {

	@Autowired
	private ApplicationContext context;
	
	@Autowired
	private HazelcastInstance hazelcastInstance;
	
	@BeforeAll
	static void cleanupHazelcast() {
	    com.hazelcast.core.Hazelcast.shutdownAll();
	}

    @Test
    void should_initialize_hazelcast_instance_automatically_from_classpath_config() {
        assertThat(context.getBeansOfType(HazelcastInstance.class))
            .hasSize(1)
            .containsValue(context.getBean(HazelcastInstance.class));
    }
    
    @Test
    void should_not_initialize_any_custom_service_registry_when_no_strategy_specified() {
        assertThat(context.getBeansOfType(ServiceRegistry.class)).isEmpty();
    }
    
    
    @Test
    void hazelcast_should_apply_custom_yaml_config() {
        com.hazelcast.config.Config cfg = hazelcastInstance.getConfig();
        // cluster-name défini dans hazelcast.yaml
        assertThat(cfg.getClusterName()).isEqualTo("r3edge-cluster-auto-conf");
        // multicast activé dans hazelcast.yaml
        assertThat(cfg.getNetworkConfig()
                      .getJoin()
                      .getMulticastConfig()
                      .isEnabled())
            .isFalse();
    }
}
