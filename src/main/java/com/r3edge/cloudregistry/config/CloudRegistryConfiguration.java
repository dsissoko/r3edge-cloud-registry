package com.r3edge.cloudregistry.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.r3edge.cloudregistry.ServiceRegistryProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Configuration principale du module Cloud Registry.
 */
@Configuration
@EnableConfigurationProperties(ServiceRegistryProperties.class)
@RequiredArgsConstructor
@Slf4j
public class CloudRegistryConfiguration {

}
