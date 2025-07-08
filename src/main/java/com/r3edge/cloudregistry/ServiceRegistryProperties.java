package com.r3edge.cloudregistry;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@ConfigurationProperties(prefix = "r3edge.registry")
@Data
public class ServiceRegistryProperties {
    private String strategy;
    private String hazelcastConfig;
    private InstanceProperties instance = new InstanceProperties();
    
    @Data
    public static class InstanceProperties {
        private String serviceName;
        private String externalBaseUrl;
        private String announcedIp;
    }
}

