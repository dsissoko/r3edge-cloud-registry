# r3edge-cloud-registry

## 🎯 Objectif

Une librairie de **registry cloud-agnostique distribuée** pour les microservices, qui permet :
- L’enregistrement dynamique des instances (nom, URL, features).
- La résolution d’URL à partir du nom de service ou d’une feature.
- La synchronisation des instances dans un cluster distribué (Docker, K8s, local).

La lib repose sur **Hazelcast 5.5** (testée uniquement en mode embedded) et s’intègre dans une application Spring Boot.

---

## ✅ Cas d’usage principal

- Chaque microservice s’enregistre automatiquement dans le ServiceRegistry au démarrage.
- Les autres services peuvent résoudre dynamiquement l’URL d’un service cible ou d’une feature.
- L’état est mis à jour dynamiquement si l’application utilise @RefreshScope.

---

## 🧩 Fonctionnalités proposées

1. Enregistrement automatique avec serviceName, instanceId, baseUrl, features.
2. Résolution d’URL à partir d’un nom de service ou d’une feature.
3. Désenregistrement automatique lors d’un shutdown ou crash de membre Hazelcast.
4. API REST optionnelle :
   - GET /registry/instances → services et URLs enregistrés
   - GET /registry/features → features ↔ services
   - GET /registry/descriptor → description de l'instance courante
5. Stratégie configurable :
   
yaml
   r3edge.registry.strategy: hazelcast | dummy

6. Intégration directe avec [Spring Flip](https://github.com/dsissoko/r3edge-spring-flip) pour la gestion des features dynamiques.

---

## ⚙️ Concepts

La librairie repose sur les concepts suivants :

- **Registry**  
  Composant distribué embarqué dans chaque microservice. Il s’appuie sur Hazelcast pour permettre l’enregistrement, la découverte et la coordination des services au sein du cluster.

- **ServiceInfo**  
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
    implementation "com.r3edge:r3edge-cloud-registry:0.0.3"
}
```

Pour Hazelcast, insérez votre config dans `application.yml` :


```yaml
  registry:
    instance:
      external-base-url: https://mon-app.io
      announced-ip: 1.2.3.4    
    strategy: hazelcast
    hazelcast-config: |
      hazelcast:
        instance-name: r3edge-registry
        cluster-name: r3edge-cluster
        network:
          port:
            port: 5701
            auto-increment: true
          interfaces:
            enabled: true
            interfaces:
              - 127.0.0.1
          join:
            tcp-ip:
              enabled: true
              member-list:
                - 127.0.0.1
                - 127.0.0.2
```

---

Effectuez vos appels inter services simplement

```java

```

[![CI – Build & Publish](https://github.com/dsissoko/r3edge-cloud-registry/actions/workflows/cicd_code.yml/badge.svg)](https://github.com/dsissoko/r3edge-cloud-registry/actions/workflows/cicd_code.yml)
