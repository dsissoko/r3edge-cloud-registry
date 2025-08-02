# r3edge-cloud-registry | ![Logo](logo_ds.png)

R3edge Cloud Registry est une librairie Java de **service discovery** basée sur `Hazelcast`, pour l’enregistrement et la découverte dynamiques de services dans une architecture distribuée. Cela vous évite de mettre en oeuvre un serveur supplémentaire comme Eureka ou Consul.
La lib repose sur **Hazelcast 5.5** (testée uniquement en mode embedded) et s’intègre dans une application Spring Boot.

> 🚀 Pourquoi adopter `r3edge-cloud-registry` ?
>
> ✅ Remplace **Eureka** (service discovery)  
> ✅ Remplace **Ribbon** (load balancing côté client)  
> ✅ **Zéro serveur externe** à déployer  
> ✅ 100 % compatible **Spring Boot**  
> ✅ Basé sur **Hazelcast** → haute disponibilité, résilience, distribution native  
> ✅ Intégration ultra simple : **juste une dépendance à ajouter**  
> ✅ **Hot Reload** des données de registre (@RefreshScope + config server + bus refresh)

This project is documented in French 🇫🇷 by default.  
An auto-translated English version is available here:

[👉 English (auto-translated by Google)](https://translate.google.com/translate?sl=auto&tl=en&u=https://github.com/dsissoko/r3edge-cloud-registry)

---

## 📋 Fonctionnalités clés


- ✅ Enregistrement automatique avec serviceName, instanceId, baseUrl, features.
- ✅ Résolution d’URL à partir d’un nom de service ou d’une feature avec load balancing client (random)
- ✅ Désenregistrement automatique lors d’un shutdown ou crash de membre du cluster
- ✅ API REST optionnelle (flippable en positonnant "r3edge.cloudregistry.registryController: false" dans la conf applicative):
    - GET `{base-path}/instances` → services et URLs enregistrés
    - GET `{base-path}/features` → features ↔ services
    - GET `{base-path}/descriptor` → description de l'instance courante  
    - ℹ️ `{base-path}` est configurable via `r3edge.registry.base-path` (par défaut : `/registry`) 
   
- ✅ Intégration complète avec [Spring Flip](https://github.com/dsissoko/r3edge-spring-flip) pour la gestion des features dynamiques.

### ⚙️ Concepts

La librairie repose sur les concepts suivants :

- **Registry**  
  Composant distribué embarqué dans chaque microservice. Il s’appuie sur Hazelcast pour permettre l’enregistrement, la découverte et la coordination des services au sein du cluster.

- **ServiceDescriptor**  
  Représentation logique d’un service. Contient un nom unique et une liste de features. Il ne reflète pas un processus actif, mais une capacité fonctionnelle offerte dans le système.

- **Feature**  
  Capacité fonctionnelle exposée par un service, identifiée par un texte libre (ex. : type d’API). Permet de rechercher un service selon ses fonctions, indépendamment de son nom.

- **ServiceInstance**  
  Représente un processus concret (instance d’un service) actif dans le cluster. Contient des données runtime (ID, URL, etc.). Une ou plusieurs `ServiceInstance` peuvent être associées à un même `ServiceDescriptor`.

---

## ⚙️ Intégration rapide

### Ajouter les dépendances nécessaires:

```groovy
repositories {
    mavenCentral()
    // Dépôt GitHub Packages de r3edge-cloud-registry
    maven {
        url = uri("https://maven.pkg.github.com/dsissoko/r3edge-cloud-registry")
        credentials {
            username = ghUser
            password = ghKey
        }
    }
    mavenLocal()
}

dependencies {
    ...
    implementation "com.r3edge:r3edge-cloud-registry:0.1.5"
    ...
}
```

> ⚠️ Cette librairie est publiée sur **GitHub Packages**: Même en open source, **GitHub impose une authentification** pour accéder aux dépendances.  
> Il faudra donc valoriser ghUser et ghKey dans votre gradle.properties:

```properties
#pour réccupérer des packages github 
ghUser=your_github_user
ghKey=github_token_with_read_package_scope
```

### Configurez votre service dans votre `application.yml`:

```yaml
r3edge:
  registry:
    base-path: /test-endpoint
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

> ℹ️ Au démarrage, vos microservices vont constituer un cluster Hazelcast   
> ℹ️ La configuration Hazelcast est native et lue à partir du champ hazelcast-config. Toutes les options sont donc disponibles en théorie  
> ℹ️ L'état du registre est rafraîchi grâce à un double mécanisme: celui d'Hazelcast (heartbeat des membres du cluster) et celui de spring cloud bus avec spring cloud server ce qui permet un hot reload très fiable des features des services ! 

### Localisez et effectuez vos appels inter-service:

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

> ℹ️ La résolution des services peux se faire **par nom ou par feature**  
> ℹ️ un **loadbalancing basé sur un algo random** renvoie le service qui répond au critère  

---

## 📦 Stack de référence


✅ Cette librairie a été conçue et testée avec :

- Java 17+
- Spring Boot 3.x
- Hazelcast 5.x
- Spring Cloud Config Server et Spring Cloud Bus *(pour le support du rafraîchissement dynamique, optionnel)*

---

## 🗺️ Roadmap

### 🔧 À venir
- RAS

### 🧠 En réflexion
- Load balancing intelligent basé sur les infos actuator

---

📫 Maintenu par [@dsissoko](https://github.com/dsissoko) – contributions bienvenues.

[![CI – Build & Publish](https://github.com/dsissoko/r3edge-cloud-registry/actions/workflows/cicd_code.yml/badge.svg)](https://github.com/dsissoko/r3edge-cloud-registry/actions/workflows/cicd_code.yml)
