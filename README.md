# 🌀 WSpawners

**WSpawners** is a Spigot/Paper plugin for Minecraft 1.20+ that lets you define fully customizable spawners via `config.yml`, with optional [MythicMobs](https://www.mythicmobs.net/) integration, a built-in admin editor GUI, a SQLite-backed ownership database, and a clean developer API.

---

## ✨ Features

- **Configurable spawner types** – define any number of spawner types with custom display names, lore, entity type, delay, spawn radius, spawn amount, and entity scale.
- **Entity scale variation** – set `min-scale` and `max-scale` to randomly vary the size of spawned entities (requires Minecraft 1.20.5+).
- **MythicMobs support** – set `entity-type: "mm:<MobName>"` to spawn MythicMobs creatures.
- **Ownership tracking** – every placed spawner is recorded in a SQLite database. Players can only break spawners they placed themselves (unless they have admin permission).
- **In-game editor GUI** – admins can right-click a managed spawner to open a GUI and edit its properties live.
- **Spark particle effects** – optional visual particles while a spawner is active.
- **bStats metrics** – anonymous usage statistics (plugin ID `29665`).
- **Developer API** – a stable, documented API (`WSpawnersAPI`) for third-party plugins.

---

## 📋 Requirements

| Requirement | Version |
|---|---|
| Spigot / Paper | 1.20.4+ |
| Java | 17+ |
| MythicMobs *(optional)* | 5.6+ |

---

## ⚙️ Installation

1. Download `WSpawners-1.0.jar` and drop it into your `plugins/` folder.
2. Restart the server.
3. Edit `plugins/WSpawners/config.yml` to define your spawner types (see [Configuration](#%EF%B8%8F-configuration)).
4. Run `/spawner reload` to apply changes without restarting.

---

## ⚙️ Configuration

`plugins/WSpawners/config.yml`

```yaml
# Enable/disable spark particle effects on active spawners
spark-particles: true

# Maximum number of mob spawns processed per tick
max-spawns-per-tick: 4

spawners:
  zombie_spawner:
    material: SPAWNER
    display-name: "<red>Zombie Spawner"
    lore:
      - "<gray>Place this spawner to"
      - "<gray>spawn Zombies"
    entity-type: ZOMBIE       # Bukkit EntityType or "mm:<MythicMobName>"
    delay: 200                # Ticks between spawns
    required-player-range: 16 # Activation range (blocks)
    min-radius: 0             # Minimum spawn radius from the spawner
    max-radius: 0             # Maximum spawn radius from the spawner
    min-amount: 1             # Minimum mobs spawned per cycle
    max-amount: 1             # Maximum mobs spawned per cycle
    min-scale: 0.5            # Minimum entity scale (1.0 = normal size)
    max-scale: 2.0            # Maximum entity scale (1.0 = normal size)

  # MythicMobs example
  elephant_spawner:
    material: SPAWNER
    display-name: "<gold>Elephant Spawner"
    lore:
      - "<gray>Place this spawner to"
      - "<gray>spawn Elephants"
    entity-type: "mm:elephant"  # MythicMobs mob ID
    delay: 200
    required-player-range: 16
    min-radius: 1
    max-radius: 5
    min-amount: 1
    max-amount: 3
    min-scale: 0.8
    max-scale: 1.5
```

Display names and lore support [MiniMessage](https://docs.advntr.dev/minimessage/format.html) formatting (e.g. `<red>`, `<bold>`, `<gradient:...>`).

---

## 💬 Commands

All commands require the `wspawners.command` permission (default: `op`).

| Command | Description |
|---|---|
| `/spawner give <player> <id>` | Give a spawner item to a player. |
| `/spawner list` | List all configured spawner IDs and their types. |
| `/spawner reload` | Reload `config.yml` and `messages.yml` without restarting. |
| `/spawner myspawners` | Show all spawners placed by the executing player. |
| `/spawner info <player>` | Show all spawners placed by a given player (admin). |

Tab-completion is supported for all sub-commands, player names, and spawner IDs.

---

## 🔐 Permissions

| Permission | Description | Default |
|---|---|---|
| `wspawner.admin` | Full admin access – editor GUI, break any spawner, use `/spawner info`. | `op` |
| `wspawners.command` | Allows use of the `/spawner` command. | `op` |
| `wspawners.use` | Allows players to place and recover their **own** spawners. | `true` |

---

## 📨 Messages

All plugin messages are stored in `plugins/WSpawners/messages.yml` and support [MiniMessage](https://docs.advntr.dev/minimessage/format.html) formatting. Edit the file and run `/spawner reload` to apply changes.

---

## 🔌 Developer API

Add WSpawners as a soft-dependency in your `plugin.yml`:

```yaml
softdepend: [WSpawners]
```

### Getting the API instance

```java
if (WSpawnersAPI.isAvailable()) {
    WSpawnersAPI api = WSpawnersAPI.getInstance();
}
```

### Configuration / spawner registry

| Method | Return type | Description |
|---|---|---|
| `getSpawnerData(String id)` | `SpawnerData \| null` | Returns the `SpawnerData` for the given spawner ID, or `null` if not found. |
| `getAllSpawners()` | `Map<String, SpawnerData>` | Returns an unmodifiable map of all configured spawners. |
| `hasSpawner(String id)` | `boolean` | Returns `true` if the given spawner ID is configured. |
| `getConfiguredSpawnerCount()` | `int` | Returns the number of configured spawner types. |

### Item helpers

| Method | Return type | Description |
|---|---|---|
| `createSpawnerItem(String spawnerId)` | `ItemStack \| null` | Creates a spawner `ItemStack` for the given ID, or `null` if unknown. |
| `isCustomSpawner(ItemStack item)` | `boolean` | Returns `true` if the item is a WSpawners managed spawner. |
| `getSpawnerId(ItemStack item)` | `String \| null` | Returns the spawner ID stored in the item, or `null`. |

### Block helpers

| Method | Return type | Description |
|---|---|---|
| `isCustomSpawner(Block block)` | `boolean` | Returns `true` if the block is a WSpawners managed spawner. |
| `getSpawnerId(Block block)` | `String \| null` | Returns the spawner ID stored in the block, or `null`. |
| `isMythicMobSpawner(Block block)` | `boolean` | Returns `true` if the block is a MythicMobs spawner. |
| `getMythicMobType(Block block)` | `String \| null` | Returns the MythicMobs mob type stored in the block, or `null`. |

### Player helpers

| Method | Return type | Description |
|---|---|---|
| `giveSpawner(Player player, String spawnerId)` | `boolean` | Gives the spawner item to a player (overflow drops at feet). Returns `false` if the ID is unknown. |
| `getPlayerSpawners(UUID playerUuid)` | `List<SpawnerRecord>` | Returns all spawner records placed by the given player UUID. |
| `getPlayerSpawnersByName(String playerName)` | `List<SpawnerRecord>` | Returns all spawner records placed by the given player name. |
| `getSpawnerOwner(Location location)` | `UUID \| null` | Returns the UUID of the player who placed the spawner at the given location, or `null`. |

### Tick-manager helpers

| Method | Return type | Description |
|---|---|---|
| `registerSpawner(Location location, int delayTicks)` | `void` | Registers a block location with the spawner tick system. |
| `unregisterSpawner(Location location)` | `void` | Unregisters (deactivates) a spawner location from the tick system. |
| `isSpawnerActive(Location location)` | `boolean` | Returns `true` if the location is currently active in the tick system. |

### Plugin state

| Method | Return type | Description |
|---|---|---|
| `isMythicMobsEnabled()` | `boolean` | Returns `true` if MythicMobs is present and enabled. |
| `reload()` | `void` | Reloads `config.yml` and `messages.yml` and refreshes all in-memory spawner definitions. |
| `getPlugin()` | `WSpawners` | Returns the underlying plugin instance for advanced use. |

### Full usage example

```java
import com.wynvers.spawners.WSpawnersAPI;
import com.wynvers.spawners.SpawnerData;
import com.wynvers.spawners.SpawnerDatabase.SpawnerRecord;

public class MyPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        if (!WSpawnersAPI.isAvailable()) {
            getLogger().warning("WSpawners not found – spawner features disabled.");
            return;
        }

        WSpawnersAPI api = WSpawnersAPI.getInstance();

        // Check if a spawner type exists
        if (api.hasSpawner("zombie_spawner")) {
            SpawnerData data = api.getSpawnerData("zombie_spawner");
            getLogger().info("Delay: " + data.getDelay() + " ticks");
        }

        // Give a spawner to a player
        Player player = ...; // obtain player somehow
        boolean success = api.giveSpawner(player, "zombie_spawner");

        // Check a block at spawn
        Block block = ...;
        if (api.isCustomSpawner(block)) {
            String id = api.getSpawnerId(block);
            UUID owner = api.getSpawnerOwner(block.getLocation());
        }

        // List all spawners placed by a player
        List<SpawnerRecord> records = api.getPlayerSpawners(player.getUniqueId());
        for (SpawnerRecord r : records) {
            getLogger().info(r.getSpawnerId() + " at " + r.getWorld()
                + " (" + r.getX() + ", " + r.getY() + ", " + r.getZ() + ")");
        }
    }
}
```

---

## 📊 bStats

WSpawners collects anonymous usage statistics via [bStats](https://bstats.org/) (plugin ID **29665**). You can disable bStats globally in `plugins/bStats/config.yml`.

---

## 📄 License

This project is licensed under the terms of the [LICENSE](LICENSE) file included in this repository.
