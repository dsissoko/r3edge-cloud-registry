package com.r3edge.cloudregistry;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.r3edge.springflip.FlipBean;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Contrôleur REST exposant les endpoints du registre de services.
 */
@RestController
@RequiredArgsConstructor
@ConditionalOnBean(ServiceRegistry.class)
@FlipBean("cloudregistry.registryController")
@Slf4j
public class RegistryController {

	private final ServiceRegistry serviceRegistry;
	@Value("${r3edge.registry.base-path:/registry}")
	private String basePath;

	/**
	 * Retourne la liste des services enregistrés.
	 *
	 * @return map service → liste de descripteurs
	 */
	@GetMapping("${r3edge.registry.base-path:/registry}/instances")
	public Map<String, List<ServiceDescriptor>> listServices() {
		log.debug("[GET] /instances — Appel listServices()");
		Map<String, List<ServiceDescriptor>> result = serviceRegistry.getRegisteredServices();
		log.debug("[GET] /instances — Réponse avec {} services", result.size());
		return result;
	}

	/**
	 * Retourne la liste des services groupés par feature.
	 *
	 * @return map feature → liste de descripteurs
	 */
	@GetMapping("${r3edge.registry.base-path:/registry}/features")
	public Map<String, List<ServiceDescriptor>> listFeatures() {
        log.debug("[GET] /features — Appel listFeatures()");
        Map<String, List<ServiceDescriptor>> result = serviceRegistry.getRegisteredFeatures();
        log.debug("[GET] /features — Réponse avec {} features", result.size());
        return result;
	}

	/**
	 * Retourne le descripteur de l’instance locale.
	 *
	 * @return descripteur de l’instance courante
	 */
	@GetMapping("${r3edge.registry.base-path:/registry}/descriptor")
	public ServiceDescriptor getSelfDescriptor() {
        log.debug("[GET] /descriptor — Appel getSelfDescriptor()");
        ServiceDescriptor descriptor = serviceRegistry.getSelfDescriptor();
        log.debug("[GET] /descriptor — Réponse : {}", descriptor);
        return descriptor;
	}

	/**
	 * Initialisation post-construction.
	 */
    @PostConstruct
    public void postConstruct() {
        log.debug("[RegistryController] Actif – Bean ServiceRegistry utilisé : {}", serviceRegistry.getClass().getSimpleName());
        log.debug("Mappings REST initiaux : basePath='{}'", basePath);
    }
}
