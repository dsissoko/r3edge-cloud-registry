package com.r3edge.cloudregistry;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Contient les **informations runtime** de l’instance courante du microservice.
 *
 * Ces données (IP, port, SSL, etc.) sont valorisées dynamiquement au démarrage
 * et permettent de générer un `ServiceDescriptor` pour l’enregistrement dans la registry.
 */
@Data
@Component
@ConfigurationProperties(prefix = "r3edge.registry.instance")
public class ServiceInstance {

    /** Nom logique du microservice (ex: datacollect, sessionmanager, etc.) */
    private String serviceName;

    /** IP locale utilisée pour l'écoute (interne dans Docker, réelle hors container) */
    private String internalIp;

    /** Port d'écoute réel (interne dans Docker, exposé hors container) */
    private int serverPort;

    /** Indique si HTTPS est activé */
    private boolean sslEnabled;

    /** URL publique à utiliser si le service est exposé (reverse proxy, NAT, etc.) */
    private String externalBaseUrl;

    /** Identifiant unique de l'instance */
    private String instanceId;

    /** IP publique ou IP à annoncer au cluster (peut être injectée en dur si nécessaire) */
    private String announcedIp;

    /** Indique si l'instance tourne dans un environnement conteneurisé (Docker, K8s...) */
    private boolean containerEnvironment;

    public ServiceDescriptor toServiceDescriptor(List<String> activeFeatures) {
        return ServiceDescriptor.builder()
            .serviceName(this.serviceName)
            .instanceId(this.instanceId)
            .baseUrl(this.externalBaseUrl)
            .features(activeFeatures)
            .build();
    }
}
