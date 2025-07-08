package com.r3edge.cloudregistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.core.HazelcastInstance;

import lombok.extern.slf4j.Slf4j;

@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ImportAutoConfiguration(exclude = {
	    org.springframework.boot.autoconfigure.hazelcast.HazelcastAutoConfiguration.class
	})
@ActiveProfiles("test-hazelcast")
@Slf4j
public class HazelcastRegistryIntegrationTest {

	@LocalServerPort
	private int port;
	@Autowired
	private TestRestTemplate restTemplate;
	@Autowired
	private ServiceRegistry registry;	

	@Test
	void shouldLoadFeaturesFromYaml() {
		Map<String,List<ServiceDescriptor>> features = registry.getRegisteredFeatures();
		assertThat(features).isNotNull();
        assertThat(features)
        .as("La map des features doit contenir 'greeting'")
        .containsKey("greeting");
	}

	@Test
	void shouldInitializeServiceInstance() {
		var descriptor = registry.getSelfDescriptor();
		log.info("📦 Descriptor: {}", descriptor);
		assertThat(descriptor).isNotNull();
	}

	@Test
	void descriptor_endpoint_should_return_self_descriptor() {
	    String url = "http://localhost:" + port + "/registry/descriptor";

	    ServiceDescriptor descriptor = restTemplate.getForObject(url, ServiceDescriptor.class);

	    assertThat(descriptor).isNotNull();
	    assertThat(descriptor.getInstanceId()).contains("@");
	    assertThat(descriptor.getServiceName()).isEqualTo("registry-api");
	}
	
	@Test
	void descriptor_endpoint_should_expose_full_service_descriptor() {
	    ServiceDescriptor descriptor = restTemplate.getForObject("/registry/descriptor", ServiceDescriptor.class);

	    assertNotNull(descriptor);
	    assertEquals("registry-api", descriptor.getServiceName());
	    assertTrue(descriptor.getInstanceId().contains("registry-api@"));
	    assertEquals("https://mon-app.io", descriptor.getBaseUrl());
	    assertTrue(descriptor.getFeatures().contains("greeting"));
	}
	
    @Test
    void hazelcastClusterConfig_shouldMatchTestYaml() {
        // 1) C'est bien l'implémentation Hazelcast
        assertThat(registry)
            .as("Doit être une HazelcastServiceRegistry")
            .isInstanceOf(HazelcastServiceRegistry.class);

        HazelcastServiceRegistry hzRegistry = (HazelcastServiceRegistry) registry;

        // 2) Récupération de l'instance Hazelcast interne
        HazelcastInstance hz = hzRegistry.getHazelcast();
        assertThat(hz)
            .as("HazelcastInstance doit être initialisé")
            .isNotNull();

        // 3) Vérification du Config généré
        Config cfg = hz.getConfig();

        assertThat(cfg.getInstanceName())
            .as("Le nom d'instance doit être 'registry-hz-test'")
            .isEqualTo("registry-hz-test");

        JoinConfig join = cfg.getNetworkConfig().getJoin();

        assertThat(join.getMulticastConfig().isEnabled())
            .as("Multicast doit être activé dans la configuration de test")
            .isTrue();

        assertThat(join.getTcpIpConfig().isEnabled())
            .as("TCP-IP doit être désactivé dans la configuration de test")
            .isFalse();
    }
}
