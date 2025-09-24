# Hazelcast — Cluster mixte : Guide pratique

> **But** : former un cluster Hazelcast sans ports explicites dans `member-list`.  
> **Cibles** : (1) Local *Windows hors‑Docker* + *WSL dans Docker* ; (2) *Windows ↔ VPS* via tunnel WireGuard.

---

## Chapitre 1 — Local : Windows (hors‑Docker) + WSL (Docker), *sans ports* dans `member-list`

### 1) Fichier de configuration de base (Spring Boot)

```yaml
hazelcast:
  instance-name: r3edge-registry
  cluster-name: r3edge-cluster
  properties:
    hazelcast.tcp.join.port.try.count: 10        # scan distant 5701→5710
  network:
    port:
      port: ${HZ_PORT:5701}                      # port local de départ
      auto-increment: true                       # autorise 5702.. etc.
      port-count: 10                             # fenêtre locale (10 ports)
    interfaces:
      enabled: false
    public-address: "${HZ_PUBLIC_ADDRESS:172.24.208.1}"  # IP annoncée (sans port)
    join:
      auto-detection:
        enabled: false
      multicast:
        enabled: false
      tcp-ip:
        enabled: true
        member-list: ${HZ_MEMBERS:[127.0.0.1,10.0.0.1,10.0.0.2]}  # liste d'IP (sans ports)
```

#### Placeholders : sens & résolution (Spring Boot)
- **`HZ_PORT`** : port d’écoute **local** du membre (point de départ). Avec `auto-increment:true` + `port-count`, Hazelcast peut monter au port suivant si le précédent est pris.
- **`HZ_PUBLIC_ADDRESS`** : **IP** annoncée aux pairs (en mode “scan”, **sans port**). Chaque nœud annonce **son** IP joignable.
- **`HZ_MEMBERS`** : liste des **IP** des pairs (ex. `[172.27.80.1,172.27.87.98]`). *Sans ports* → Hazelcast scanne **5701→5710** sur chaque IP, guidé par `hazelcast.tcp.join.port.try.count`.
- **Résolution** : Spring Boot remplace `${...}` par les **variables d’environnement** (ou les valeurs par défaut) **avant** que Hazelcast lise la config.

> Variante possible : écrire une **vraie liste YAML** plutôt qu’une variable unique :  
> ```yaml
> member-list:
>   - 172.27.80.1
>   - 172.27.87.98
> ```

---

### 2) Variables d’environnement (valeurs éprouvées en local)

**Windows (pour chaque JVM)**
```
HZ_MEMBERS=[172.27.80.1,172.27.87.98]
HZ_PUBLIC_ADDRESS=172.27.80.1
# Choisis un port par JVM dans la plage 5701–5710 :
HZ_PORT=5706     # JVM n°1
# HZ_PORT=5710   # JVM n°2 (même IP Windows, autre port)
```

**WSL (membre dans Docker)**
```
HZ_MEMBERS=[172.27.80.1,172.27.87.98]
HZ_PUBLIC_ADDRESS=172.27.87.98
HZ_PORT=5707
```

> Pourquoi ça marche : la **liste d’IP** suffit ; Hazelcast **scanne 5701→5710** côté pair.  
> Deux JVM Windows peuvent partager **la même IP** (172.27.80.1) parce qu’elles écoutent sur **des ports différents** de la plage.

---

### 3) Réseau — publier / ouvrir la **plage 5701–5710** (indispensable)

**Docker (membre WSL)** — dans `docker-compose.yml` du service Hazelcast :
```yaml
ports:
  - "5701-5710:5701-5710"
```

**Windows Defender** (interface **“vEthernet (WSL)”**) — PowerShell (Admin) :
```powershell
New-NetFirewallRule -DisplayName "Hazelcast TCP 5701-5710 (vEthernet WSL)" `
  -Direction Inbound -Action Allow -Protocol TCP -LocalPort 5701-5710 `
  -InterfaceAlias "vEthernet (WSL)" -Profile Private
```

---

### 4) “Formules” (commandes) pour récupérer les IP

**Depuis WSL**
```bash
# IP WSL (eth0) – à mettre dans HZ_PUBLIC_ADDRESS côté WSL/Docker
WSL_IP=$(ip -4 addr show dev eth0 | awk '/inet /{print $2}' | cut -d/ -f1)

# IP Windows vue par WSL (gateway par défaut) – IP à annoncer côté Windows
WIN_IP_FROM_WSL=$(ip route | awk '/default/{print $3}')
```

**Depuis Windows**
```powershell
# IP vEthernet (WSL) – à mettre dans HZ_PUBLIC_ADDRESS côté Windows
Get-NetIPAddress -InterfaceAlias "vEthernet (WSL)" -AddressFamily IPv4 |
  Select-Object -ExpandProperty IPAddress
```

---

### 5) Vérifications rapides

- **Windows → WSL** :  
  `Test-NetConnection 172.27.87.98 -Port 5701` (…5707…5710) ⇒ `TcpTestSucceeded : True`
- **WSL (dans le conteneur) → Windows** :  
  `nc -zv 172.27.80.1 5701` (…5706…5710) ⇒ `Connected`

> Bonnes pratiques :  
> • Garde tes JVM **dans 5701–5710** (ou augmente `hazelcast.tcp.join.port.try.count` si tu élargis).  
> • Évite que `auto-increment` fasse sortir de la plage en empilant trop d’instances.

---

### 6) Dépannage express & durcissement

- **Avertissements de migration** (ex. `PartitionStateVersionMismatchException`) à l’arrivée d’un nœud : normal pendant le **rebalancing** ; ça se **stabilise** vite.  
  Si ça spamme > 1–2 min : redémarre le nœud bruyant et, si besoin, limite la concurrence :
  ```yaml
  hazelcast:
    properties:
      hazelcast.partition.max.parallel.migrations: 1
  ```
- **Perfs / JDK 9+** : ajoute ces flags JVM sur **tous** les membres :
  ```
  --add-modules java.se --add-exports java.base/jdk.internal.ref=ALL-UNNAMED
  --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED
  --add-opens java.management/sun.management=ALL-UNNAMED
  --add-opens jdk.management/com.sun.management.internal=ALL-UNNAMED
  ```

---

## Chapitre 2 — Windows ↔ VPS via **WireGuard** (très court)

**Architecture** : Windows et un serveur **VPS** sont reliés par un **tunnel WireGuard** (ex. interface `wg0`). Tout le trafic Hazelcast passe par ce tunnel privé, sans exposition publique des ports Hazelcast sur Internet.

**Changement principal** : seules les **IP** changent dans `HZ_PUBLIC_ADDRESS` et `HZ_MEMBERS` pour utiliser les **IP du tunnel** (ex. `/24` WireGuard) :

- **Windows**  
  ```
  HZ_PUBLIC_ADDRESS=10.0.0.2
  HZ_MEMBERS=[10.0.0.1,10.0.0.2]
  HZ_PORT=5706   # ou 5701…5710
  ```

- **VPS**  
  ```
  HZ_PUBLIC_ADDRESS=10.0.0.1
  HZ_MEMBERS=[10.0.0.1,10.0.0.2]
  HZ_PORT=5707   # ou 5701…5710
  ```

> **Note** : ouvre la **plage 5701–5710** dans les **pare-feux des hôtes** en la liant à l’interface WireGuard si possible. Les *Security Groups* cloud doivent autoriser le port **WireGuard** (ex. UDP 51820) entre Windows ↔ VPS ; pas besoin d’exposer les ports Hazelcast sur Internet.

---

### Check-list finale

- [ ] `HZ_MEMBERS` contient **les 2 IP** du contexte (Windows & WSL, ou Windows & VPS), **sans ports**.  
- [ ] `HZ_PUBLIC_ADDRESS` = **IP propre** à chaque nœud (Windows ≠ WSL ≠ VPS).  
- [ ] Plage **5701–5710** **ouverte** (Windows Defender / cloud SG) et **publiée** côté Docker (si WSL).  
- [ ] Probes TCP OK (`Test-NetConnection` / `nc`).  
- [ ] Logs Hazelcast : `Members {size:…}` puis migrations stables.
