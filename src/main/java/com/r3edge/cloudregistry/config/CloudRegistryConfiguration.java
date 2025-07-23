package com.r3edge.cloudregistry.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.r3edge.cloudregistry.ServiceRegistryProperties;

/**
 * Configuration principale du module Cloud Registry.
 */
@Configuration
@EnableConfigurationProperties(ServiceRegistryProperties.class)
public class CloudRegistryConfiguration {
}
