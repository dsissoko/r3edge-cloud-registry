package com.r3edge.cloudregistry;

import java.util.List;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Représente une **description fonctionnelle** exposée par une instance de service.
 *
 * Utilisée pour l’enregistrement dans la registry et la découverte entre microservices.
 * Elle regroupe les éléments essentiels pour identifier une capacité active dans le cluster.
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceDescriptor {

    /** Nom du service (ex: datacollect, orders, ...) */
    private String serviceName;

    /** ID unique de l’instance (ex: datacollect@172.17.0.2:8080) */
    private String instanceId;

    /** Base URL d’appel de l’instance (ex: http://172.17.0.2:8080) */
    private String baseUrl;

    /** Liste des fonctionnalités exposées (features métier) */
    private List<String> features;
}
