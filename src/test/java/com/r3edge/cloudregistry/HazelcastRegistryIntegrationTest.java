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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import com.r3edge.springflip.FlipConfiguration;

import lombok.extern.slf4j.Slf4j;

/**
 * Test d'intégration de la registry Hazelcast.
 * Ces tests valident que les endpoints exposent les bonnes données à partir
 * du fichier de configuration YAML injecté via le classpath (profil test-hazelcast).
 */
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ImportAutoConfiguration(exclude = {
    org.springframework.boot.autoconfigure.hazelcast.HazelcastAutoConfiguration.class
})
@ActiveProfiles("test-hazelcast")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Slf4j
public class HazelcastRegistryIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ServiceRegistry registry;
    
    @Autowired
    FlipConfiguration flipconfig;

    @Test
    void shouldLoadFeaturesFromYaml() {
        Map<String, List<ServiceDescriptor>> features = registry.getRegisteredFeatures();
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
        assertThat(descriptor.getFeatures())
        .containsExactlyInAnyOrder("greeting", "featureB");
        assertThat(descriptor.getExternalBaseUrl()).isEqualTo("https://mon-app.io");
        assertThat(descriptor.getMetadata()).containsEntry("announced-ip", "1.2.3.4");
    }
}
