package com.r3edge.cloudregistry;

import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Contrôleur REST exposant les endpoints du registre de services.
 */
@RestController
@RequiredArgsConstructor
@ConditionalOnBean(ServiceRegistry.class)
@Slf4j
public class RegistryController {

    private final ServiceRegistry serviceRegistry;

    /**
     * Retourne la liste des services enregistrés.
     *
     * @return map service → liste de descripteurs
     */
    @GetMapping("/registry/instances")
    public Map<String, List<ServiceDescriptor>> listServices() {
        return serviceRegistry.getRegisteredServices();
    }

    /**
     * Retourne la liste des services groupés par feature.
     *
     * @return map feature → liste de descripteurs
     */
    @GetMapping("/registry/features")
    public Map<String, List<ServiceDescriptor>> listFeatures() {
        return serviceRegistry.getRegisteredFeatures();
    }
    
    /**
     * Retourne le descripteur de l’instance locale.
     *
     * @return descripteur de l’instance courante
     */
    @GetMapping("/registry/descriptor")
    public ServiceDescriptor getSelfDescriptor() {
        return serviceRegistry.getSelfDescriptor();
    }
    
    /**
     * Initialisation post-construction.
     */
    @PostConstruct
    public void postConstruct() {
        log.debug("📡 [RegistryController] Actif – Bean ServiceRegistry utilisé : {}", serviceRegistry.getClass().getSimpleName());
    }
}
