package com.wynvers.spawners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class SpawnerTickManager {

    private static final int TICK_INTERVAL = 20;

    private final WynversSpawners plugin;
    private final Random random = new Random();

    private final Map<String, Integer>  countdowns = new HashMap<>();
    private final Map<String, Location> locations  = new HashMap<>();

    private BukkitTask task;

    public SpawnerTickManager(WynversSpawners plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK_INTERVAL, TICK_INTERVAL);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
        countdowns.clear();
        locations.clear();
    }

    public void register(Location loc, int delayTicks) {
        String key = locKey(loc);
        locations.put(key, loc.clone());
        countdowns.put(key, delayTicks > 0 ? delayTicks : 200);
    }

    public void unregister(Location loc) {
        String key = locKey(loc);
        locations.remove(key);
        countdowns.remove(key);
    }

    public boolean isRegistered(Location loc) {
        return countdowns.containsKey(locKey(loc));
    }

    private void tick() {
        Set<String> toRemove = new HashSet<>();

        for (Map.Entry<String, Integer> entry : countdowns.entrySet()) {
            String key = entry.getKey();
            Location loc = locations.get(key);
            if (loc == null) { toRemove.add(key); continue; }

            World world = loc.getWorld();
            if (world == null) continue;

            Block block = world.getBlockAt(loc);
            if (block.getType() != org.bukkit.Material.SPAWNER) { toRemove.add(key); continue; }

            BlockState state = block.getState();
            if (!(state instanceof CreatureSpawner)) { toRemove.add(key); continue; }
            CreatureSpawner cs = (CreatureSpawner) state;

            String spawnerId = cs.getPersistentDataContainer()
                    .get(plugin.getSpawnerIdKey(), PersistentDataType.STRING);
            SpawnerData data = spawnerId != null ? plugin.getSpawnerConfig().getSpawner(spawnerId) : null;

            int playerRange = data != null ? data.getRequiredPlayerRange() : cs.getRequiredPlayerRange();
            if (playerRange > 0) {
                boolean playerNearby = world.getPlayers().stream()
                        .anyMatch(p -> p.getLocation().distanceSquared(loc) <= (double) playerRange * playerRange);
                if (!playerNearby) continue;
            }

            int remaining = entry.getValue() - TICK_INTERVAL;
            if (remaining > 0) { entry.setValue(remaining); continue; }

            spawnMobs(cs, loc, data);

            int delay = data != null ? data.getDelay() : cs.getDelay();
            if (delay <= 0) delay = 200;
            entry.setValue(delay);
        }

        toRemove.forEach(k -> { countdowns.remove(k); locations.remove(k); });
    }

    private void spawnMobs(CreatureSpawner cs, Location loc, SpawnerData data) {
        String mmType = cs.getPersistentDataContainer()
                .get(plugin.getMythicMobTypeKey(), PersistentDataType.STRING);

        Integer pdcMinRadius = cs.getPersistentDataContainer().get(plugin.getMinRadiusKey(), PersistentDataType.INTEGER);
        Integer pdcMaxRadius = cs.getPersistentDataContainer().get(plugin.getMaxRadiusKey(), PersistentDataType.INTEGER);
        Integer pdcMinAmount = cs.getPersistentDataContainer().get(plugin.getMinAmountKey(), PersistentDataType.INTEGER);
        Integer pdcMaxAmount = cs.getPersistentDataContainer().get(plugin.getMaxAmountKey(), PersistentDataType.INTEGER);

        int minRadius = pdcMinRadius != null ? pdcMinRadius : (data != null ? data.getMinRadius() : 0);
        int maxRadius = pdcMaxRadius != null ? pdcMaxRadius : (data != null ? data.getMaxRadius() : 0);
        int minAmount = pdcMinAmount != null ? pdcMinAmount : (data != null ? data.getMinAmount() : 1);
        int maxAmount = pdcMaxAmount != null ? pdcMaxAmount : (data != null ? data.getMaxAmount() : 1);

        if (minAmount < 1) minAmount = 1;
        if (maxAmount < minAmount) maxAmount = minAmount;

        int spawnCount = (maxAmount > minAmount)
                ? minAmount + random.nextInt(maxAmount - minAmount + 1)
                : minAmount;

        for (int i = 0; i < spawnCount; i++) {
            Location spawnLoc = getSpawnLocation(loc, minRadius, maxRadius);

            if (mmType != null && !mmType.isEmpty() && plugin.isMythicMobsEnabled()) {
                try {
                    io.lumine.mythic.bukkit.MythicBukkit.inst().getMobManager()
                            .spawnMob(mmType, spawnLoc);
                    plugin.trackMythicSpawn();
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to spawn MythicMob '" + mmType + "': " + e.getMessage());
                }
            } else if (data != null && !data.isMythicMob()) {
                try {
                    loc.getWorld().spawnEntity(spawnLoc, data.getEntityType());
                    plugin.trackVanillaSpawn();
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to spawn vanilla mob: " + e.getMessage());
                }
            }
        }
    }

    private Location getSpawnLocation(Location center, int minRadius, int maxRadius) {
        if (maxRadius <= 0) return center.clone().add(0.5, 0, 0.5);
        int radius = (maxRadius > minRadius)
                ? minRadius + random.nextInt(maxRadius - minRadius + 1)
                : minRadius;
        double angle    = random.nextDouble() * 2 * Math.PI;
        double distance = Math.sqrt(random.nextDouble()) * radius;
        return center.clone().add(Math.cos(angle) * distance + 0.5, 0, Math.sin(angle) * distance + 0.5);
    }

    private static String locKey(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }
}
