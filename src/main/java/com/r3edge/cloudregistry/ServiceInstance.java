package com.r3edge.cloudregistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.r3edge.springflip.FlipConfiguration;

import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;

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

    /** Métadonnées additionnelles (facultatif) */
    private Map<String, String> metadata;

    /** Configuration des features SpringFlip */
    @Autowired
    private FlipConfiguration flipConfig;

    /**
     * Retourne la liste des features activées dans SpringFlip.
     */
    public List<String> getEnabledFeatures() {
        return Optional.ofNullable(flipConfig.getFlip())
                .map(map -> map.entrySet().stream()
                        .filter(Map.Entry::getValue)
                        .map(Map.Entry::getKey)
                        .toList())
                .orElse(List.of());
    }

    /**
     * Construit un {@link ServiceDescriptor} enrichi à partir des données de l'instance courante,
     * en y injectant les fonctionnalités activées et les éventuelles métadonnées personnalisées.
     *
     * @return un descripteur prêt à être enregistré dans la registry
     */
    public ServiceDescriptor toServiceDescriptor() {
        return ServiceDescriptor.builder()
            .serviceName(this.serviceName)
            .instanceId(this.instanceId)
            .internalBaseUrl(getInternalBaseUrl())
            .externalBaseUrl(this.externalBaseUrl)
            .features(getEnabledFeatures())
            .metadata(new HashMap<>(Optional.ofNullable(this.metadata).orElse(Map.of())))
            .build();
    }

    /**
     * Construit dynamiquement l'URL interne à utiliser pour les appels intra-cluster.
     *
     * @return URL interne construite à partir du contexte réseau local
     */
    public String getInternalBaseUrl() {
        String scheme = sslEnabled ? "https" : "http";
        String host = containerEnvironment ? serviceName : internalIp;
        return String.format("%s://%s:%d", scheme, host, serverPort);
    }

    public void setMetadata(Map<String, String> metadata) {
        System.out.println("📥 Injection Spring : " + metadata);
        this.metadata = metadata;
    }
}
