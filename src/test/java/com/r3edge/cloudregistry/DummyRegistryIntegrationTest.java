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

import lombok.extern.slf4j.Slf4j;

/**
 * Test d'intégration du DummyServiceRegistry via les endpoints REST exposés.
 */
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ImportAutoConfiguration(exclude = {
    org.springframework.boot.autoconfigure.hazelcast.HazelcastAutoConfiguration.class
})
@ActiveProfiles("test-dummy")
@Slf4j
public class DummyRegistryIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Test
    void descriptor_should_expose_expected_fields() {
        ServiceDescriptor descriptor = rest.getForObject("http://localhost:" + port + "/registry/descriptor", ServiceDescriptor.class);
        assertNotNull(descriptor);
        log.info("➡️ Descriptor: {}", descriptor);

        assertEquals("registry-api", descriptor.getServiceName());
        assertNotNull(descriptor.getInstanceId());
        assertNotNull(descriptor.getInternalBaseUrl());
        assertNotNull(descriptor.getExternalBaseUrl());

        assertThat(descriptor.getFeatures()).isNotNull();
        assertThat(descriptor.getMetadata()).isNotNull();
    }

    @Test
    void registry_should_return_features_mapping() {
        @SuppressWarnings("unchecked")
		Map<String, List<ServiceDescriptor>> features = rest.getForObject("http://localhost:" + port + "/registry/features", Map.class);
        assertNotNull(features);
        assertTrue(features.size() >= 0); // Peut être vide si aucune feature activée
    }

    @Test
    void registry_should_return_services_mapping() {
        @SuppressWarnings("unchecked")
		Map<String, List<ServiceDescriptor>> services = rest.getForObject("http://localhost:" + port + "/registry/instances", Map.class);
        assertNotNull(services);
        assertTrue(services.containsKey("registry-api"));
        assertThat(services.get("registry-api")).isNotEmpty();
    }
}
