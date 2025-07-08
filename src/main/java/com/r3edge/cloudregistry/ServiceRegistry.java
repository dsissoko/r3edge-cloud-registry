package com.r3edge.cloudregistry;

import java.util.List;
import java.util.Map;

/**
 * Interface pour l'annuaire de services distribués.
 *
 * Fournit les opérations d'enregistrement, de découverte et de résolution
 * d'instances, basées sur les `ServiceDescriptor` exposés par chaque microservice.
 */
public interface ServiceRegistry {

    /**
     * Complète l'initialisation du registre avec l'instance locale.
     *
     * Utile lorsque l'instance locale (`ServiceInstance`) n'est entièrement connue
     * qu'après le démarrage du serveur (IP, port, etc).
     *
     * @param selfInstance instance locale du service courant
     */
    void completeInit(ServiceInstance selfInstance);

    /**
     * Enregistre un `ServiceDescriptor` dans le registre.
     *
     * @param descriptor descripteur de l'instance à enregistrer
     */
    void register(ServiceDescriptor descriptor);

    /**
     * Supprime toutes les instances associées à un nom de service.
     *
     * @param serviceName nom logique du service
     */
    void unregister(String serviceName);

    /**
     * Supprime une instance spécifique par son ID.
     *
     * @param instanceId identifiant unique de l'instance
     */
    void unregisterInstance(String instanceId);

    /**
     * Supprime une feature exposée par une instance donnée.
     *
     * @param instanceId identifiant unique de l'instance
     * @param feature nom de la feature à retirer
     */
    void unregisterFeature(String instanceId, String feature);

    /**
     * Résout une URL d’instance pour un service donné.
     *
     * @param serviceName nom du service
     * @return URL d'une instance disponible, ou {@code null}
     */
    String resolveServiceUrl(String serviceName);

    /**
     * Résout une URL d’instance exposant une feature donnée.
     *
     * @param feature nom de la feature
     * @return URL d'une instance, ou {@code null}
     */
    String resolveFeatureUrl(String feature);

    /**
     * Retourne toutes les instances enregistrées, regroupées par service.
     *
     * @return map serviceName → liste de `ServiceDescriptor`
     */
    Map<String, List<ServiceDescriptor>> getRegisteredServices();

    /**
     * Retourne toutes les features disponibles dans le registre.
     *
     * @return map feature → liste de `ServiceDescriptor`
     */
    Map<String, List<ServiceDescriptor>> getRegisteredFeatures();

    /**
     * Nettoyage et libération des ressources du registre distribué.
     */
    void shutdown();
    
    
    ServiceDescriptor getSelfDescriptor();
}
