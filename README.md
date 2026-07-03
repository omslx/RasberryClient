# 🪐 `rasberryClient` 

> A premium, highly optimized Hypixel-style Survival Multiplayer (SMP) management system designed for high-performance Paper/Spigot Lobby networks. Built with 💜 using idiomatic Kotlin.

---

## ✨ Features
* 🌌 **Multi-Version GUI Core:** Dynamic inventory system built using Adventure API & MiniMessage, fully compatible with older clients (1.19+) via ViaVersion.
* ⚡ **Reactive & Non-Blocking:** High-performance database operations wrapped cleanly inside Kotlin Coroutines (`Dispatchers.IO`).
* 📡 **Instant Synchronization:** Inter-server communication powered by Redis Pub/Sub payloads, serialized using `kotlinx.serialization`.
* 🪙 **Vault Economy Driven:** Built-in verification loops checking funds before initializing a server deployment.
* 🗜️ **Production Ready Shading:** Pre-configured Gradle Kotlin DSL to bundle and relocate HikariCP, Jedis, and Kotlin runtimes cleanly into a standalone jar.

---

## 🛠️ Architecture & Managers
The plugin follows a robust, clean manager pattern separating data layers from game loops:
* `DatabaseManager`: Handles pool-based MySQL queries safely out of the primary server tick thread.
* `RedisManager`: Listens and broadcasts real-time cluster data between the Lobby client and backend microservices.
* `SMPManager`: Coordinates active memory caches using concurrent data structures.
* `GUIManager`: Handles asynchronous layout rendering and player interface interaction states.

---

## 🚀 Getting Started

### 📋 Prerequisites
* Minecraft Paper Server (1.20.4+ Recommended)
* Java 17 or higher
* MySQL & Redis Server Instances
* Vault (and an active Economy provider plugin like EssentialsX)

### 📦 Installation & Build
To build a fully shadowed, relocated, production-ready Jar, execute the following Gradle task:

```bash
./gradlew shadowJar
```
