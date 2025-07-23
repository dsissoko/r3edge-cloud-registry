package com.r3edge.cloudregistry;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.r3edge.springflip.FlipConfiguration;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implémentation fictive du registre de services, utilisée pour les tests.
 * <p>
 * Cette version ne conserve aucun état persistant et sert uniquement à simuler
 * le comportement d’un {@link ServiceRegistry}. Elle est activée quand la propriété
 * {@code r3edge.registry.strategy=dummy} est définie.
 * </p>
 */
@Component
@ConditionalOnProperty(prefix = "r3edge.registry", name = "strategy", havingValue = "dummy")
@RequiredArgsConstructor
@Slf4j
public class DummyServiceRegistry implements ServiceRegistry {

    private final ServiceRegistryProperties properties;
    private final Optional<FlipConfiguration> flipConfiguration;
    private ServiceInstance selfInstance;

    /**
     * Méthode d'initialisation appelée après construction du composant.
     * Elle vérifie la disponibilité de Spring Flip pour activer les features dynamiques.
     */
    @PostConstruct
    public void init() {
        if (flipConfiguration.isEmpty()) {
            log.warn("⚠️ Aucune configuration Spring Flip détectée. Les features seront vides.");
        } else {
            log.info("🔄 Spring Flip détecté. Les features seront dynamiques.");
        }
    }

    @Override
    public void completeInit(ServiceInstance selfInstance) {
        this.selfInstance = selfInstance;
        log.info("✅ [Dummy] ServiceInstance initialisé : {}", selfInstance);
    }

    /**
     * Retourne la liste des features actuellement activées via Spring Flip.
     * 
     * @return liste des clés activées
     */
    private List<String> getEnabledFeatures() {
        return flipConfiguration
            .map(FlipConfiguration::getFlip)
            .map(map -> map.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .toList()
            )
            .orElse(Collections.emptyList());
    }

    /**
     * Retourne la liste complète des features connues, quelle que soit leur activation.
     * 
     * @return liste des features définies
     */
    private List<String> getDynamicFeatures() {
        return flipConfiguration
            .map(FlipConfiguration::getFlip)
            .map(map -> map.keySet().stream().toList())
            .orElse(Collections.emptyList());
    }

    @Override
    public void register(ServiceDescriptor descriptor) {
        log.info("📝 [Dummy] register appelé avec : {}", descriptor);
    }

    @Override
    public void unregister(String serviceName) {
        log.info("🗑️ [Dummy] unregister pour : {}", serviceName);
    }

    @Override
    public void unregisterInstance(String instanceId) {
        log.info("🗑️ [Dummy] unregisterInstance pour ID : {}", instanceId);
    }

    @Override
    public void unregisterFeature(String instanceId, String feature) {
        log.info("🗑️ [Dummy] unregisterFeature pour instance {} / feature {}", instanceId, feature);
    }

    @Override
    public Map<String, List<ServiceDescriptor>> getRegisteredServices() {
        if (selfInstance == null) return Collections.emptyMap();
        return Map.of(selfInstance.getServiceName(), List.of(getSelfDescriptor()));
    }

    @Override
    public Map<String, List<ServiceDescriptor>> getRegisteredFeatures() {
        if (selfInstance == null) return Collections.emptyMap();
        List<String> features = getSelfDescriptor().getFeatures();
        if (features.isEmpty()) return Collections.emptyMap();
        return features.stream()
            .collect(Collectors.toMap(
                feature -> feature,
                feature -> List.of(getSelfDescriptor())
            ));
    }

    @Override
    public void shutdown() {
        log.info("🛑 [Dummy] shutdown appelé");
    }

    @Override
    public ServiceDescriptor getSelfDescriptor() {
        if (selfInstance == null) return null;
        return new ServiceDescriptor(
            selfInstance.getServiceName(),
            selfInstance.getInstanceId(),
            selfInstance.getInternalBaseUrl(),
            selfInstance.getExternalBaseUrl(),
            getDynamicFeatures(),
            Map.of()
        );
    }

    @Override
    public String resolveInternalServiceUrl(String serviceName) {
        log.info("🔍 [Dummy] resolveInternalServiceUrl pour : {}", serviceName);
        if (selfInstance != null && selfInstance.getServiceName().equals(serviceName)) {
            return selfInstance.getInternalBaseUrl();
        }
        return null;
    }

    @Override
    public String resolveExternalServiceUrl(String serviceName) {
        log.info("🔍 [Dummy] resolveExternalServiceUrl pour : {}", serviceName);
        if (selfInstance != null && selfInstance.getServiceName().equals(serviceName)) {
            return selfInstance.getExternalBaseUrl();
        }
        return null;
    }

    @Override
    public String resolveInternalFeatureUrl(String feature) {
        log.info("🔍 [Dummy] resolveInternalFeatureUrl pour : {}", feature);
        if (selfInstance != null && getEnabledFeatures().contains(feature)) {
            return selfInstance.getInternalBaseUrl();
        }
        return null;
    }

    @Override
    public String resolveExternalFeatureUrl(String feature) {
        log.info("🔍 [Dummy] resolveExternalFeatureUrl pour : {}", feature);
        if (selfInstance != null && getEnabledFeatures().contains(feature)) {
            return selfInstance.getExternalBaseUrl();
        }
        return null;
    }
}
