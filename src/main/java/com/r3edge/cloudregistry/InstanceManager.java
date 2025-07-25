package com.r3edge.cloudregistry;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Initialise dynamiquement les données d'instance (IP, port, protocole, etc.)
 * une fois le serveur web démarré.
 */
@RequiredArgsConstructor
@Component
@ConditionalOnBean(ServiceRegistry.class)
@Slf4j
public class InstanceManager implements ApplicationListener<WebServerInitializedEvent> {

	private final Environment environment;
	private final ServiceRegistry serviceRegistry;
	private final ServiceRegistryProperties properties;
	private final ServiceInstance serviceInstance;

	@Override
	public void onApplicationEvent(WebServerInitializedEvent event) {
		log.debug("🚀 Web server démarré sur le port {}", event.getWebServer().getPort());

		int port = event.getWebServer().getPort();
		String internalIp = resolveInternalIp();
		boolean isContainer = isRunningInContainer();

		var instanceProps = properties.getInstance();

		String serviceName = Optional.ofNullable(environment.getProperty("spring.application.name")).orElse("unknown-service");
		boolean sslEnabled = instanceProps.getExternalBaseUrl() != null
				&& instanceProps.getExternalBaseUrl().startsWith("https");
		String instanceId = buildInstanceId(serviceName, internalIp, port, instanceProps.getExternalBaseUrl());

		serviceInstance.setServiceName(serviceName);
		serviceInstance.setInternalIp(internalIp);
		serviceInstance.setServerPort(port);
		serviceInstance.setSslEnabled(sslEnabled);
		serviceInstance.setInstanceId(instanceId);
		serviceInstance.setContainerEnvironment(isContainer);

		log.info("✅ ServiceInstance initialized: {}", serviceInstance);

		serviceRegistry.completeInit(serviceInstance);
	}

	private String resolveInternalIp() {
		try {
			return InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) {
			log.warn("⚠️ Impossible de résoudre l'IP locale", e);
			return "UNKNOWN_IP";
		}
	}

	private boolean isRunningInContainer() {
		return System.getenv().containsKey("KUBERNETES_SERVICE_HOST") || System.getenv().containsKey("DOCKER_ENV");
	}

	private String buildInstanceId(String serviceName, String ip, int port, String externalBaseUrl) {
		if (externalBaseUrl != null && !externalBaseUrl.isBlank()) {
			return String.format("%s@%s:%d@%s", serviceName, ip, port, externalBaseUrl);
		} else {
			return String.format("%s@%s:%d", serviceName, ip, port);
		}
	}

	/**
	 * Méthode appelée après la construction de l’instance pour initialisation.
	 */
	@PostConstruct
	public void postConstruct() {
		log.info("📌 InstanceManager présent dans le contexte Spring.");
		log.info("🔧 registry.base-path = {}", properties.getBasePath());
	}
}
