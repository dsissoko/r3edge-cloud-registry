package com.r3edge.cloudregistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;

@SpringBootTest(
	    classes = TestApplication.class,
	    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	    properties = {
	        "r3edge.registry.base-path=/test-endpoint",
	        "spring.flip.cloudregistry.registryController=true"
	    }
	)
@ImportAutoConfiguration(exclude = {
    org.springframework.boot.autoconfigure.hazelcast.HazelcastAutoConfiguration.class
})
@ActiveProfiles("test-hazelcast")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Slf4j
public class RegistryBasePathOverrideTest {

    @LocalServerPort
    private int port;

    private final RestTemplate restTemplate = new RestTemplate();

    @Test
    void descriptor_shouldRespondOnOverriddenPath() {
        String baseUrl = "http://localhost:" + port;
        var response = restTemplate.getForEntity(baseUrl + "/test-endpoint/descriptor", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void descriptor_shouldNotRespondOnDefaultPath() {
        String baseUrl = "http://localhost:" + port;
        try {
            restTemplate.getForEntity(baseUrl + "/registry/descriptor", String.class);
            fail("Expected 404 NOT_FOUND but request succeeded");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode().value()).isEqualTo(404);
        }
    }
}
