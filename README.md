# r3edge-cloud-registry | ![Logo](logo_ds.png)

> 🚀 Pourquoi adopter `r3edge-cloud-registry` ?
>
> ✅ Remplace **Eureka** (service discovery)  
> ✅ Remplace **Ribbon** (load balancing côté client)  
> ✅ **Zéro serveur externe** à déployer  
> ✅ 100 % compatible **Spring Boot**  
> ✅ Basé sur **Hazelcast** → haute disponibilité, résilience, distribution native  
> ✅ Intégration ultra simple : **juste une dépendance à ajouter**


## 🎯 Objectif

Bénéficier d'un mécanisme de registre de service distribué sans ajouter de serveur comme Eureka ou Consul avec une librairie java à simplement ajouter dans les dépendances de vos micro services spring boot.
La lib repose sur **Hazelcast 5.5** (testée uniquement en mode embedded) et s’intègre dans une application Spring Boot.

---

## ✅ Cas d’usage principal

- Chaque microservice s’enregistre automatiquement dans le ServiceRegistry au démarrage.
- Les autres services peuvent résoudre dynamiquement l’URL d’un service cible ou d’une feature 
- L’état est mis à jour dynamiquement si l’application utilise @RefreshScope.

---

## 🧩 Fonctionnalités proposées

1. Enregistrement automatique avec serviceName, instanceId, baseUrl, features.
2. Résolution d’URL à partir d’un nom de service ou d’une feature avec load balancing client (random)
3. Désenregistrement automatique lors d’un shutdown ou crash de membre du cluster
4. API REST optionnelle :
   - GET /registry/instances → services et URLs enregistrés
   - GET /registry/features → features ↔ services
   - GET /registry/descriptor → description de l'instance courante
5. Intégration directe avec [Spring Flip](https://github.com/dsissoko/r3edge-spring-flip) pour la gestion des features dynamiques.

---

## ⚙️ Concepts

La librairie repose sur les concepts suivants :

- **Registry**  
  Composant distribué embarqué dans chaque microservice. Il s’appuie sur Hazelcast pour permettre l’enregistrement, la découverte et la coordination des services au sein du cluster.

- **ServiceDescriptor**  
  Représentation logique d’un service. Contient un nom unique et une liste de features. Il ne reflète pas un processus actif, mais une capacité fonctionnelle offerte dans le système.

- **Feature**  
  Capacité fonctionnelle exposée par un service, identifiée par un texte libre (ex. : type d’API). Permet de rechercher un service selon ses fonctions, indépendamment de son nom.

- **ServiceInstance**  
  Représente un processus concret (instance d’un service) actif dans le cluster. Contient des données runtime (ID, URL, etc.). Une ou plusieurs `ServiceInstance` peuvent être associées à un même `ServiceInfo`.

---

## 🔧 Intégration dans un projet Spring Boot

Déclarer le dépôt:

```groovy
repositories {
    mavenCentral()
    mavenLocal()
    gradlePluginPortal()
    // GitHub Packages de dsissoko
    // Dépôt GitHub Packages de r3edge-cloud-registry
    maven {
        url = uri("https://maven.pkg.github.com/dsissoko/r3edge-cloud-registry")
        credentials {
            username = ghUser
            password = ghKey
        }
    }
}
```

Déclarez vos credentials dans votre gradle.properties ou équivalent

```
gpr.user=dsissoko
gpr.key=XXXXXXXXXXXXXXXXXX
```

Ajoutez la dépendance :

```groovy
dependencies {
    implementation "com.r3edge:r3edge-cloud-registry:0.1.2"
}
```

Pour Hazelcast, insérez votre config dans `application.yml` :


```yaml
r3edge:
  registry:
    instance:
      external-base-url: http://10.0.0.1
      announced-ip: 10.0.0.1
    strategy: hazelcast
    hazelcast-config: |
      hazelcast:
        instance-name: r3edge-registry
        cluster-name: r3edge-cluster
        network:
          public-address: 10.0.0.1        
          port:
            port: 5701
            auto-increment: true
            port-count: 10
          interfaces:
            enabled: false
          join:
            auto-detection:
              enabled: false         
            multicast:
              enabled: false          
            tcp-ip:
              enabled: true
              member-list:
                - 10.0.0.1
                - 10.0.0.2
```

---

Effectuez vos appels inter services simplement

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestTemplate;

@Autowired
private ServiceRegistry serviceRegistry;

@Autowired
private RestTemplate restTemplate;

public void callSharedExchangeApi() {
    String baseUrl = serviceRegistry.resolveExternalServiceUrl("shared-api");
    if (baseUrl == null) {
        throw new IllegalStateException("Service shared-api indisponible");
    }

    String fullUrl = baseUrl + "/api/backend/shared/exchanges";
    restTemplate.getForObject(fullUrl, Void.class);
}
```

[![CI – Build & Publish](https://github.com/dsissoko/r3edge-cloud-registry/actions/workflows/cicd_code.yml/badge.svg)](https://github.com/dsissoko/r3edge-cloud-registry/actions/workflows/cicd_code.yml)
