package com.r3edge.cloudregistry;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hazelcast.core.HazelcastInstance;

import lombok.RequiredArgsConstructor;

/**
 * Configuration centralisée pour exposer les beans Hazelcast utiles aux applications clientes.
 *
 * - HazelcastInstance : toujours exposé.
 * - Config : exposé uniquement si le registry est en mode membre.
 * - ClientConfig : exposé uniquement si le registry est en mode client.
 *
 * Les beans conditionnels s'appuient sur la méthode isClientMode() du HazelcastServiceRegistry.
 */
@Configuration
@ConditionalOnBean(HazelcastServiceRegistry.class)
@RequiredArgsConstructor
public class HazelcastBridgeConfiguration {

    private final HazelcastServiceRegistry registry;

    /**
     * Fournit l'instance Hazelcast initialisée par le registry.
     * 
     * @return l'instance Hazelcast partagée dans le cluster
     */
    @Bean
    public HazelcastInstance hazelcastInstance() {
        return registry.getHazelcast();
    }

    /**
     * Expose la configuration Hazelcast en fonction du mode.
     * 
     * @return un {@link Config} si le registry est en mode membre,
     *         ou un {@link ClientConfig} si le registry est en mode client
     */
//    @Bean
//    public Object hazelcastModeSpecificConfig() {
//        if (registry.isClientMode()) {
//            return registry.getClientConfig(); // Spring saura injecter ClientConfig
//        } else {
//            return registry.getHazelcast().getConfig(); // Spring saura injecter Config
//        }
//    }
}

