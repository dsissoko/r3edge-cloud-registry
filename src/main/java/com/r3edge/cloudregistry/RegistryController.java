package com.r3edge.cloudregistry;

import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequiredArgsConstructor
@ConditionalOnBean(ServiceRegistry.class)
@Slf4j
public class RegistryController {

    private final ServiceRegistry serviceRegistry;

    @GetMapping("/registry/instances")
    public Map<String, List<ServiceDescriptor>> listServices() {
        return serviceRegistry.getRegisteredServices();
    }

    @GetMapping("/registry/features")
    public Map<String, List<ServiceDescriptor>> listFeatures() {
        return serviceRegistry.getRegisteredFeatures();
    }
    
    @GetMapping("/registry/descriptor")
    public ServiceDescriptor getSelfDescriptor() {
        return serviceRegistry.getSelfDescriptor();
    }
    
    @PostConstruct
    public void postConstruct() {
        log.debug("📡 [RegistryController] Actif – Bean ServiceRegistry utilisé : {}", serviceRegistry.getClass().getSimpleName());
    }
}
