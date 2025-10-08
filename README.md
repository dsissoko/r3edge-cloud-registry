# r3edge-cloud-registry | ![Logo](logo_ds.png)

R3edge Cloud Registry est une librairie Java de **service discovery** basée sur `Hazelcast`, pour l’enregistrement et la découverte dynamiques de services dans une architecture distribuée. Cela vous évite de mettre en oeuvre un serveur supplémentaire comme Eureka ou Consul.
La lib repose sur **Hazelcast 5.5** (testée uniquement en mode embedded) et s’intègre dans une application Spring Boot.

> 🚀 Pourquoi adopter `r3edge-cloud-registry` ?
>
> ✅ Remplace **Eureka** (service discovery)  
> ✅ Remplace **Ribbon** (load balancing côté client)  
> ✅ **Zéro serveur externe** à déployer  
> ✅ 100 % compatible **Spring Boot**  
> ✅ **et en bonus : accès à toute la puissance de Hazelcast** *(tâches distribuées, haute dispo, cache partagé, etc.)*  
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
- ✅ Toutes les fonctionnalités d’un cluster Hazelcast : [voir la documentation officielle](https://docs.hazelcast.com/hazelcast/5.5)
- ✅ Une abstration pour gérer un cache distribué (voir CacheGateway)


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
    // Dépendance principale
    implementation "com.r3edge:r3edge-cloud-registry:0.2.1"

    // Obligatoire : support du cluster Hazelcast
    implementation 'com.hazelcast:hazelcast-spring:5.5.0'

    // Recommandé : pour activer Spring Boot et la configuration automatique
    implementation 'org.springframework.boot:spring-boot-starter'
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
              port:
                port: ${HZ_PORT:5701}
                auto-increment: true
                port-count: 10
              interfaces:
                enabled: false
              public-address: "${HZ_PUBLIC_ADDRESS:172.24.208.1}:${HZ_PORT:5701}"
              join:
                auto-detection:
                  enabled: false
                multicast:
                  enabled: false
                tcp-ip:
                  enabled: true
                  member-list: ${HZ_MEMBERS:[]}
```

> ℹ️ Au démarrage, vos microservices vont constituer un cluster Hazelcast   
> ℹ️ La configuration Hazelcast est native et lue à partir du champ hazelcast-config.  
> ℹ️ Toutes les options sont donc disponibles en théorie : Spring boot peux résoudre tous les placeholders de votre choix. Dans l'exemple ci-dessus, HZ_PORT, HZ_PUBLIC_ADDRESS, HZ_MEMBERS doivent être fournis en variable d'environnement, si non fournis, les valeurs par défaut seront utilisées. La configuration d'Hazelcast permet de nombreuses possibilités, vous pouvez consulter le complément suivant: [Configuration d'Hazelcast: Tips & Tricks](HZ_CONFIG.md)  
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
