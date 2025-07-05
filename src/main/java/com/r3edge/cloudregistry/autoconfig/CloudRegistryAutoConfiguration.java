package com.r3edge.cloudregistry.autoconfig;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

import com.r3edge.cloudregistry.config.CloudRegistryConfiguration;


/**
 * Configuration automatique du dispatcher de tâches.
 * Active la configuration des propriétés et scanne les composants nécessaires.
 */
@AutoConfiguration
@EnableConfigurationProperties(CloudRegistryConfiguration.class)
@ComponentScan(basePackages = "com.r3edge.cloudregistry")
public class CloudRegistryAutoConfiguration {}