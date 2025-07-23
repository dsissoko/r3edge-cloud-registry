package com.r3edge.cloudregistry.autoconfig;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;


/**
 * Configuration automatique cloud registry.
 * Active la configuration des propriétés et scanne les composants nécessaires.
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.r3edge.cloudregistry")
public class CloudRegistryAutoConfiguration {}