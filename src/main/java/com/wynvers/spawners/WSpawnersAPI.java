package com.wynvers.spawners;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public API for WSpawners.
 *
 * <p>Other plugins can obtain the singleton via {@link #getInstance()} after
 * WSpawners has been enabled, and use the methods below to interact with
 * custom spawners at runtime.</p>
 *
 * <pre>{@code
 * // Example usage from another plugin
 * WSpawnersAPI api = WSpawnersAPI.getInstance();
 * api.giveSpawner(player, "zombie_spawner");
 * }</pre>
 */
public final class WSpawnersAPI {

    private static WSpawnersAPI instance;

    private final WSpawners plugin;

    private WSpawnersAPI(WSpawners plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Internal lifecycle (called only by WSpawners)
    // -------------------------------------------------------------------------

    static void init(WSpawners plugin) {
        instance = new WSpawnersAPI(plugin);
    }

    static void shutdown() {
        instance = null;
    }

    // -------------------------------------------------------------------------
    // Access point
    // -------------------------------------------------------------------------

    /**
     * Returns the singleton API instance.
     *
     * @return the {@link WSpawnersAPI} instance
     * @throws IllegalStateException if WSpawners is not currently enabled
     */
    public static WSpawnersAPI getInstance() {
        if (instance == null) {
            throw new IllegalStateException("WSpawnersAPI is not available: WSpawners is not enabled.");
        }
        return instance;
    }

    /**
     * Returns {@code true} if WSpawners is loaded and the API is ready.
     */
    public static boolean isAvailable() {
        return instance != null;
    }

    // -------------------------------------------------------------------------
    // Configuration / spawner registry
    // -------------------------------------------------------------------------

    /**
     * Returns the {@link SpawnerData} for the given spawner ID, or {@code null}
     * if no spawner with that ID is configured.
     *
     * @param id the spawner ID (case-insensitive)
     * @return the spawner data, or {@code null}
     */
    public SpawnerData getSpawnerData(String id) {
        return plugin.getSpawnerConfig().getSpawner(id);
    }

    /**
     * Returns an unmodifiable view of all configured spawners, keyed by their ID.
     *
     * @return map of spawner ID → {@link SpawnerData}
     */
    public Map<String, SpawnerData> getAllSpawners() {
        return plugin.getSpawnerConfig().getAllSpawners();
    }

    /**
     * Returns {@code true} if a spawner with the given ID exists in the
     * configuration.
     *
     * @param id the spawner ID (case-insensitive)
     * @return {@code true} if the spawner is configured
     */
    public boolean hasSpawner(String id) {
        return plugin.getSpawnerConfig().getSpawner(id) != null;
    }

    /**
     * Returns the number of spawner types currently configured.
     *
     * @return configured spawner count
     */
    public int getConfiguredSpawnerCount() {
        return plugin.getSpawnerConfig().getAllSpawners().size();
    }

    // -------------------------------------------------------------------------
    // Item helpers
    // -------------------------------------------------------------------------

    /**
     * Creates a spawner {@link ItemStack} for the given spawner ID.
     *
     * @param spawnerId the spawner ID (case-insensitive)
     * @return the spawner item, or {@code null} if the ID is not configured
     */
    public ItemStack createSpawnerItem(String spawnerId) {
        SpawnerData data = plugin.getSpawnerConfig().getSpawner(spawnerId);
        if (data == null) return null;
        return plugin.createSpawnerItem(data);
    }

    /**
     * Returns {@code true} if the given {@link ItemStack} is a WSpawners custom
     * spawner item.
     *
     * @param item the item to test (may be {@code null})
     * @return {@code true} if it is a managed spawner item
     */
    public boolean isCustomSpawner(ItemStack item) {
        return getSpawnerId(item) != null;
    }

    /**
     * Returns the spawner ID stored in the given {@link ItemStack}, or
     * {@code null} if the item is not a managed spawner.
     *
     * @param item the item to inspect (may be {@code null})
     * @return the spawner ID, or {@code null}
     */
    public String getSpawnerId(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER) return null;
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof BlockStateMeta)) return null;
        BlockStateMeta blockMeta = (BlockStateMeta) meta;
        BlockState state = blockMeta.getBlockState();
        if (!(state instanceof CreatureSpawner)) return null;
        return ((CreatureSpawner) state).getPersistentDataContainer()
                .get(plugin.getSpawnerIdKey(), PersistentDataType.STRING);
    }

    // -------------------------------------------------------------------------
    // Block helpers
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the given {@link Block} is a WSpawners custom
     * spawner block.
     *
     * @param block the block to test (may be {@code null})
     * @return {@code true} if it is a managed spawner block
     */
    public boolean isCustomSpawner(Block block) {
        return getSpawnerId(block) != null;
    }

    /**
     * Returns the spawner ID stored in the given {@link Block}, or {@code null}
     * if the block is not a managed spawner.
     *
     * @param block the block to inspect (may be {@code null})
     * @return the spawner ID, or {@code null}
     */
    public String getSpawnerId(Block block) {
        if (block == null || block.getType() != Material.SPAWNER) return null;
        BlockState state = block.getState();
        if (!(state instanceof CreatureSpawner)) return null;
        return ((CreatureSpawner) state).getPersistentDataContainer()
                .get(plugin.getSpawnerIdKey(), PersistentDataType.STRING);
    }

    /**
     * Returns {@code true} if the given block is a MythicMobs custom spawner.
     *
     * @param block the block to inspect (may be {@code null})
     * @return {@code true} if it is a MythicMobs spawner
     */
    public boolean isMythicMobSpawner(Block block) {
        if (block == null || block.getType() != Material.SPAWNER) return false;
        BlockState state = block.getState();
        if (!(state instanceof CreatureSpawner)) return false;
        String mmType = ((CreatureSpawner) state).getPersistentDataContainer()
                .get(plugin.getMythicMobTypeKey(), PersistentDataType.STRING);
        return mmType != null && !mmType.isEmpty();
    }

    /**
     * Returns the MythicMobs mob type stored in the given block, or {@code null}
     * if the block is not a MythicMobs spawner.
     *
     * @param block the block to inspect (may be {@code null})
     * @return the MythicMobs mob type, or {@code null}
     */
    public String getMythicMobType(Block block) {
        if (block == null || block.getType() != Material.SPAWNER) return null;
        BlockState state = block.getState();
        if (!(state instanceof CreatureSpawner)) return null;
        return ((CreatureSpawner) state).getPersistentDataContainer()
                .get(plugin.getMythicMobTypeKey(), PersistentDataType.STRING);
    }

    // -------------------------------------------------------------------------
    // Player helpers
    // -------------------------------------------------------------------------

    /**
     * Gives the specified spawner item to a player.
     * If the player's inventory is full, overflow items are dropped individually
     * at the player's feet as world entities (one item drop per excess stack).
     *
     * @param player    the target player
     * @param spawnerId the spawner ID (case-insensitive)
     * @return {@code true} if the spawner was successfully given,
     *         {@code false} if the spawner ID is unknown
     */
    public boolean giveSpawner(Player player, String spawnerId) {
        SpawnerData data = plugin.getSpawnerConfig().getSpawner(spawnerId);
        if (data == null) return false;
        ItemStack item = plugin.createSpawnerItem(data);
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        overflow.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
        return true;
    }

    /**
     * Returns all spawner records placed by the given player.
     *
     * @param playerUuid the player's UUID
     * @return list of spawner records (never {@code null})
     */
    public List<SpawnerDatabase.SpawnerRecord> getPlayerSpawners(UUID playerUuid) {
        return plugin.getDatabase().getSpawners(playerUuid);
    }

    /**
     * Returns all spawner records placed by the given player name.
     *
     * @param playerName the player's name (case-insensitive)
     * @return list of spawner records (never {@code null})
     */
    public List<SpawnerDatabase.SpawnerRecord> getPlayerSpawnersByName(String playerName) {
        return plugin.getDatabase().getSpawnersByName(playerName);
    }

    /**
     * Returns the UUID of the player who placed the spawner at the given
     * location, or {@code null} if the location is not tracked.
     *
     * @param location the spawner's block location
     * @return the owner's UUID, or {@code null}
     */
    public UUID getSpawnerOwner(Location location) {
        return plugin.getDatabase().getOwner(location);
    }

    // -------------------------------------------------------------------------
    // Tick-manager helpers
    // -------------------------------------------------------------------------

    /**
     * Registers a block location with the spawner tick system using the given
     * delay. This allows external code to activate spawner timers.
     *
     * @param location   the block location to register
     * @param delayTicks the spawn countdown in ticks (must be > 0)
     */
    public void registerSpawner(Location location, int delayTicks) {
        plugin.getTickManager().register(location, delayTicks);
    }

    /**
     * Unregisters (deactivates) a spawner location from the tick system.
     *
     * @param location the block location to deactivate
     */
    public void unregisterSpawner(Location location) {
        plugin.getTickManager().unregister(location);
    }

    /**
     * Returns {@code true} if the given location is currently active in the
     * spawner tick system.
     *
     * @param location the block location to check
     * @return {@code true} if active
     */
    public boolean isSpawnerActive(Location location) {
        return plugin.getTickManager().isRegistered(location);
    }

    // -------------------------------------------------------------------------
    // Plugin state
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if MythicMobs is present and enabled on this server.
     *
     * @return {@code true} if MythicMobs integration is active
     */
    public boolean isMythicMobsEnabled() {
        return plugin.isMythicMobsEnabled();
    }

    /**
     * Reloads the plugin configuration (config.yml and messages.yml) and
     * refreshes all in-memory spawner definitions.
     */
    public void reload() {
        plugin.reloadPlugin();
    }

    /**
     * Returns the underlying {@link WSpawners} plugin instance.
     * Use only when a specific internal method is not exposed by the API.
     *
     * @return the plugin instance
     */
    public WSpawners getPlugin() {
        return plugin;
    }
}
