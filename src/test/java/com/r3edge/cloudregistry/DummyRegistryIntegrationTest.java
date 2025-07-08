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
}
