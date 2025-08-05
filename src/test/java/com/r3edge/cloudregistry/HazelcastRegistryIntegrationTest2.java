package com.r3edge.cloudregistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import com.hazelcast.core.Hazelcast;

import lombok.extern.slf4j.Slf4j;

/**
 * Test d'intégration de la stratégie Hazelcast avec configuration TCP/IP.
 * 
 * Ce test vérifie que le service courant est correctement enregistré dans le registre,
 * que ses features sont accessibles, et que les résolutions d'URL internes/externes fonctionnent.
 * 
 * Configuration : application-test-tcpip-hazelcast.yml
 */
@SpringBootTest(
    classes = TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test-tcpip-hazelcast")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Slf4j
public class HazelcastRegistryIntegrationTest2 {

    // <<<--- STOPPE TOUTE INSTANCE EXISTANTE AVANT QUE SPRING BOOT NE CHARGE LE CONTEXTE
    static {
        Hazelcast.shutdownAll();
    }

    @LocalServerPort
    private int port;
    @Autowired
    private ServiceRegistry registry;
    
    @BeforeAll
    static void cleanupHazelcast() {
        com.hazelcast.core.Hazelcast.shutdownAll();
    }

    @Test
    void test_self_registration_and_resolution() {
        log.info("🔍 Vérification de l'enregistrement local...");
        assertNotNull(registry);

        // Récupération du descripteur local
        ServiceDescriptor self = registry.getSelfDescriptor();
        assertNotNull(self, "Le descripteur local ne doit pas être nul");

        // Vérifications des champs de base
        assertEquals("registry-api", self.getServiceName());
        assertEquals("https://mon-app.io", self.getExternalBaseUrl());

        // Résolution d'URL
        assertEquals(self.getExternalBaseUrl(), registry.resolveExternalServiceUrl("registry-api"));
        assertEquals(self.getInternalBaseUrl(), registry.resolveInternalServiceUrl("registry-api"));

        // Résolution d'URL par feature désactivée
        assertNull(registry.resolveInternalFeatureUrl("featureB"));
        assertNull(registry.resolveExternalFeatureUrl("featureB"));

        // Liste des features connues (dynamiques)
        Map<String, List<ServiceDescriptor>> features = registry.getRegisteredFeatures();
        assertTrue(features.containsKey("featureA"));
        assertFalse(features.get("featureA").isEmpty());

        // Liste des services enregistrés
        Map<String, List<ServiceDescriptor>> services = registry.getRegisteredServices();
        assertTrue(services.containsKey("registry-api"));
        assertEquals(1, services.get("registry-api").size());
    }
}


