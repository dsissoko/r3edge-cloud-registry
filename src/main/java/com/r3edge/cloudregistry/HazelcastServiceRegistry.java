package com.r3edge.cloudregistry;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.hazelcast.config.Config;
import com.hazelcast.config.YamlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.r3edge.springflip.FlipConfiguration;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implémentation de ServiceRegistry utilisant Hazelcast.
 * HazelcastInstance est construit en interne à partir de la config YAML.
 */
@Component
@ConditionalOnProperty(prefix = "r3edge.registry", name = "strategy", havingValue = "hazelcast")
@RequiredArgsConstructor
@Slf4j
public class HazelcastServiceRegistry implements ServiceRegistry {

    private final ServiceRegistryProperties properties;
    private final Optional<FlipConfiguration> flipConfiguration;
    @Getter
    private HazelcastInstance hazelcast;
    private ServiceInstance selfInstance;

    @PostConstruct
    public void init() {
        try {
            // Construire HazelcastInstance depuis la config YAML
            String yaml = properties.getHazelcastConfig();
            Config config = new YamlConfigBuilder(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))
            ).build();
            this.hazelcast = Hazelcast.newHazelcastInstance(config);
            log.info("🔄 Hazelcast initialisé localement avec instanceName={}", config.getInstanceName());
        } catch (Exception e) {
            log.error("⚠️ Impossible d'initialiser Hazelcast depuis la config fournie", e);
            throw new IllegalStateException("Failed to initialize Hazelcast", e);
        }

        if (flipConfiguration.isEmpty()) {
            log.warn("⚠️ Aucune configuration Spring Flip détectée. Les features seront vides.");
        } else {
            log.info("🔄 Spring Flip détecté. Les features seront dynamiques.");
        }
    }

    @Override
    public void completeInit(ServiceInstance selfInstance) {
        this.selfInstance = selfInstance;
        log.info("✅ [Hazelcast] ServiceInstance initialisé : {}", selfInstance);
        // TODO: enregistrer selfInstance dans la map Hazelcast
    }

    private List<String> getDynamicFeatures() {
        return flipConfiguration.map(FlipConfiguration::getFlip)
                .map(map -> map.keySet().stream().toList())
                .orElse(Collections.emptyList());
    }

    @Override
    public void register(ServiceDescriptor descriptor) {
        log.info("📝 [Hazelcast] register appelé avec : {}", descriptor);
        // TODO: store descriptor dans Hazelcast map
    }

    @Override
    public void unregister(String serviceName) {
        log.info("🗑️ [Hazelcast] unregister pour : {}", serviceName);
        // TODO: remove de la map Hazelcast
    }

    @Override
    public void unregisterInstance(String instanceId) {
        log.info("🗑️ [Hazelcast] unregisterInstance pour ID : {}", instanceId);
        // TODO: remove instance de la map
    }

    @Override
    public void unregisterFeature(String instanceId, String feature) {
        log.info("🗑️ [Hazelcast] unregisterFeature pour instance {} / feature {}", instanceId, feature);
        // TODO: unregister feature de l'instance
    }

    @Override
    public String resolveServiceUrl(String serviceName) {
        log.info("🔍 [Hazelcast] resolveServiceUrl pour : {}", serviceName);
        // TODO: lookup dans Hazelcast
        return null;
    }

    @Override
    public String resolveFeatureUrl(String feature) {
        log.info("🔍 [Hazelcast] resolveFeatureUrl pour : {}", feature);
        // TODO: lookup feature -> service
        return null;
    }

    @Override
    public Map<String, List<ServiceDescriptor>> getRegisteredServices() {
        log.info("📋 [Hazelcast] getRegisteredServices appelé");
        if (selfInstance == null) {
            return Collections.emptyMap();
        }
        return Map.of(selfInstance.getServiceName(), List.of(getSelfDescriptor()));
    }

    @Override
    public Map<String, List<ServiceDescriptor>> getRegisteredFeatures() {
        log.info("📋 [Hazelcast] getRegisteredFeatures appelé");
        if (selfInstance == null) {
            return Collections.emptyMap();
        }
        List<String> features = getSelfDescriptor().getFeatures();
        if (features.isEmpty()) {
            return Collections.emptyMap();
        }
        return features.stream()
                .collect(Collectors.toMap(feature -> feature, feature -> List.of(getSelfDescriptor())));
    }

    @Override
    public ServiceDescriptor getSelfDescriptor() {
        if (selfInstance == null) {
            return null;
        }
        return new ServiceDescriptor(
            selfInstance.getServiceName(),
            selfInstance.getInstanceId(),
            selfInstance.getExternalBaseUrl(),
            getDynamicFeatures()
        );
    }

    @Override
    public void shutdown() {
        log.info("🛑 [Hazelcast] shutdown appelé");
        if (hazelcast != null) {
            hazelcast.shutdown();
        }
    }
}