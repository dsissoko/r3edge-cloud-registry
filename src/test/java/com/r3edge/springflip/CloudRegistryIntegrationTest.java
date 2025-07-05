package com.r3edge.springflip;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import lombok.extern.slf4j.Slf4j;

@SpringBootTest(classes = TestApplication.class)
@Slf4j
public class CloudRegistryIntegrationTest {

	@Autowired
	private FlipConfiguration flipConfiguration;

	
	@Test
	void shouldLoadFeaturesFromYaml() {
		Map<String, Boolean> flips = flipConfiguration.getFlip();
		assertThat(flips).isNotNull();
		assertThat(flips).containsEntry("greeting", false);
	}
}
