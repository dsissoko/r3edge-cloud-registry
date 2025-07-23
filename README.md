# r3edge-cloud-registry

## 🎯 Objectif

Une librairie de **registry cloud-agnostique distribuée** pour les microservices, qui permet :
- L’enregistrement dynamique des instances (nom, URL, features).
- La résolution d’URL à partir du nom de service ou d’une feature.
- La coordination des instances dans un cluster distribué (Docker, K8s, local).
- Un fallback en mode "dummy" pour les tests ou environnements dégradés.

La lib repose sur **Hazelcast 5.5** (tstée uniquement en mode embedded) et s’intègre dans une application Spring Boot.

---

## ✅ Cas d’usage principal

- Dans un système de microservices, chaque service s’enregistre au démarrage dans le `ServiceRegistry`.
- Un autre service peut résoudre dynamiquement une URL d’un service cible (ou d’une feature) sans connaître sa localisation exacte.
- En option, l’état est mis à jour dynamiquement lors d’un `@RefreshScope`.

---

## 🧩 Fonctionnalités proposées

1. **Enregistrement d’instance** avec `serviceName`, `instanceId`, `baseUrl`, `features`.
2. **Résolution** d’URL par `serviceName` ou par `feature`.
3. **Unregister automatique** lors du shutdown ou crash du membre Hazelcast.
4. **Exposition d’une API REST** (optionnelle) :
   - `GET /registry/instances` → liste des services et URLs
   - `GET /registry/features` → mapping features ↔ instances
5. **Conditionnel sur la stratégie** via :
   ```yaml
   r3edge.registry.strategy=hazelcast | dummy
   ```
6. **Toggle de feature via les fichiers de configuration spring boot**: voir [Spring Flip](https://github.com/dsissoko/r3edge-spring-flip)

---

## ⚙️ Définitions

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
