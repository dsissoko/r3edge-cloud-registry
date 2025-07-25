package com.r3edge.cloudregistry;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Propriétés de configuration pour le registre de services.
 */
@ConfigurationProperties(prefix = "r3edge.registry")
@Data
public class ServiceRegistryProperties {
    private String strategy;
    private String hazelcastConfig;
    private InstanceProperties instance = new InstanceProperties();
    /**
     * Préfixe des endpoints REST exposés par RegistryController.
     */
    private String basePath = "/registry";
    
    /**
     * Propriétés de l'instance du registre.
     */    
    @Data
    public static class InstanceProperties {
        private String serviceName;
        private String externalBaseUrl;
        private String announcedIp;
    }
}

