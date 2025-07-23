package com.r3edge.cloudregistry;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Représente une description publique d'une instance enregistrée dans la registry.
 * <p>
 * Cet objet est partagé entre microservices via Hazelcast. Il permet de résoudre dynamiquement
 * les URLs internes/externes, d'accéder aux features exposées, et de fournir des métadonnées utiles
 * à la découverte, au monitoring ou à la documentation.
 * </p>
 */
@SuppressWarnings("serial")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceDescriptor implements Serializable {

    /** Nom logique du service (ex: sessionmanager, datacollect, etc.) */
    private String serviceName;

    /** Identifiant unique de l'instance (ex: service@host:port) */
    private String instanceId;

    /** URL interne pour les appels intra-cluster (ex: http://service:8080) */
    private String internalBaseUrl;

    /** URL externe pour les appels via reverse proxy (ex: https://api.domain.com/service) */
    private String externalBaseUrl;

    /** Liste des features activées sur cette instance */
    private List<String> features;

    /** Métadonnées additionnelles (facultatif) */
    private Map<String, String> metadata;
}
