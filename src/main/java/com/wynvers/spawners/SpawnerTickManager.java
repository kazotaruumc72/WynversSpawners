package com.wynvers.spawners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

public class SpawnerTickManager {

    private static final int TICK_INTERVAL = 20;
    private static final int DEFAULT_SPAWN_DELAY_TICKS = 200;
    /** Maximum number of pending spawn tasks. Excess spawns are dropped to prevent queue buildup. */
    private static final int MAX_PENDING_SPAWNS = 64;

    private final WSpawners plugin;
    private final Random random = new Random();

    private final Map<String, Integer>  countdowns   = new HashMap<>();
    private final Map<String, Location> locations    = new HashMap<>();
    /** PDC-derived data cached per spawner key – populated once on first tick, eliminating
     *  repeated {@code block.getState()} and PDC reads in the hot loop. */
    private final Map<String, SpawnerCacheEntry> spawnerCache = new HashMap<>();
    private final Queue<Runnable> spawnQueue = new ArrayDeque<>();

    private BukkitTask task;
    private boolean sparkEnabled = true;
    private int maxSpawnsPerTick = 4;

    /** Immutable snapshot of PDC-stored spawn parameters for one spawner block.
     *  Created once on first encounter, reused every subsequent tick. */
    private static final class SpawnerCacheEntry {
        final String spawnerId;
        final String mmType;
        final int    minRadius, maxRadius, minAmount, maxAmount;
        final double minScale, maxScale;
        /** Vanilla spawner's own requiredPlayerRange – used as fallback when SpawnerData is unavailable. */
        final int    fallbackPlayerRange;

        SpawnerCacheEntry(CreatureSpawner cs, WSpawners plugin) {
            this.spawnerId           = cs.getPersistentDataContainer().get(plugin.getSpawnerIdKey(),      PersistentDataType.STRING);
            this.mmType              = cs.getPersistentDataContainer().get(plugin.getMythicMobTypeKey(),  PersistentDataType.STRING);
            this.fallbackPlayerRange = cs.getRequiredPlayerRange();
            Integer pMinR  = cs.getPersistentDataContainer().get(plugin.getMinRadiusKey(),      PersistentDataType.INTEGER);
            Integer pMaxR  = cs.getPersistentDataContainer().get(plugin.getMaxRadiusKey(),      PersistentDataType.INTEGER);
            Integer pMinA  = cs.getPersistentDataContainer().get(plugin.getMinAmountKey(),      PersistentDataType.INTEGER);
            Integer pMaxA  = cs.getPersistentDataContainer().get(plugin.getMaxAmountKey(),      PersistentDataType.INTEGER);
            Double  pMinS  = cs.getPersistentDataContainer().get(plugin.getMinScaleKey(),       PersistentDataType.DOUBLE);
            Double  pMaxS  = cs.getPersistentDataContainer().get(plugin.getMaxScaleKey(),       PersistentDataType.DOUBLE);
            // Fall back to SpawnerData config values when PDC key is absent
            SpawnerData data = this.spawnerId != null ? plugin.getSpawnerConfig().getSpawner(this.spawnerId) : null;
            this.minRadius = pMinR != null ? pMinR : (data != null ? data.getMinRadius() : 0);
            this.maxRadius = pMaxR != null ? pMaxR : (data != null ? data.getMaxRadius() : 0);
            this.minAmount = pMinA != null ? pMinA : (data != null ? data.getMinAmount() : 1);
            this.maxAmount = pMaxA != null ? pMaxA : (data != null ? data.getMaxAmount() : 1);
            this.minScale  = pMinS != null ? pMinS : (data != null ? data.getMinScale()  : 1.0);
            this.maxScale  = pMaxS != null ? pMaxS : (data != null ? data.getMaxScale()  : 1.0);
        }
    }

    public SpawnerTickManager(WSpawners plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK_INTERVAL, TICK_INTERVAL);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
        countdowns.clear();
        locations.clear();
        spawnerCache.clear();
        spawnQueue.clear();
    }

    public void register(Location loc, int delayTicks) {
        String key = locKey(loc);
        locations.put(key, loc.clone());
        countdowns.put(key, delayTicks > 0 ? delayTicks : DEFAULT_SPAWN_DELAY_TICKS);
        // Cache is populated lazily on the first tick to avoid reading the block state here
    }

    public void unregister(Location loc) {
        String key = locKey(loc);
        locations.remove(key);
        countdowns.remove(key);
        spawnerCache.remove(key);
    }

    public boolean isRegistered(Location loc) {
        return countdowns.containsKey(locKey(loc));
    }

    public void setSparkEnabled(boolean sparkEnabled) {
        this.sparkEnabled = sparkEnabled;
    }

    public void setMaxSpawnsPerTick(int maxSpawnsPerTick) {
        this.maxSpawnsPerTick = Math.max(1, maxSpawnsPerTick);
    }

    private void tick() {
        // Dispatch pending spawn tasks one per server-tick to avoid bundling multiple
        // expensive spawnEntity/spawnMob calls into a single tick (burst lag spike).
        int tickOffset = 1;
        while (!spawnQueue.isEmpty() && tickOffset <= maxSpawnsPerTick) {
            Bukkit.getScheduler().runTaskLater(plugin, spawnQueue.poll(), tickOffset);
            tickOffset++;
        }

        Set<String> toRemove = new HashSet<>();

        for (Map.Entry<String, Integer> entry : countdowns.entrySet()) {
            String key = entry.getKey();
            Location loc = locations.get(key);
            if (loc == null) { toRemove.add(key); continue; }

            World world = loc.getWorld();
            if (world == null) continue;

            // Cheap block-type check – avoids full state deserialization every tick
            Block block = world.getBlockAt(loc);
            if (block.getType() != org.bukkit.Material.SPAWNER) { toRemove.add(key); continue; }

            // Populate cache on first encounter (reads PDC once, reused every subsequent tick)
            SpawnerCacheEntry cached = spawnerCache.get(key);
            if (cached == null) {
                BlockState state = block.getState();
                if (!(state instanceof CreatureSpawner)) { toRemove.add(key); continue; }
                cached = new SpawnerCacheEntry((CreatureSpawner) state, plugin);
                spawnerCache.put(key, cached);
            }

            // SpawnerData is looked up fresh each tick so editor changes take effect immediately
            SpawnerData data = cached.spawnerId != null
                    ? plugin.getSpawnerConfig().getSpawner(cached.spawnerId) : null;

            int playerRange = data != null ? data.getRequiredPlayerRange() : cached.fallbackPlayerRange;
            if (playerRange > 0) {
                boolean playerNearby = world.getPlayers().stream()
                        .anyMatch(p -> p.getLocation().distanceSquared(loc) <= (double) playerRange * playerRange);
                if (!playerNearby) continue;
            }

            if (sparkEnabled) {
                world.spawnParticle(Particle.ELECTRIC_SPARK,
                        loc.clone().add(0.5, 0.5, 0.5), 5, 0.3, 0.3, 0.3, 0);
            }

            int remaining = entry.getValue() - TICK_INTERVAL;
            if (remaining > 0) { entry.setValue(remaining); continue; }

            spawnMobs(cached, loc, data);

            int spawnDelay = data != null ? data.getDelay() : DEFAULT_SPAWN_DELAY_TICKS;
            if (spawnDelay <= 0) spawnDelay = DEFAULT_SPAWN_DELAY_TICKS;
            entry.setValue(spawnDelay);
        }

        toRemove.forEach(k -> { countdowns.remove(k); locations.remove(k); spawnerCache.remove(k); });
    }

    private void spawnMobs(SpawnerCacheEntry cached, Location loc, SpawnerData data) {
        String mmType   = cached.mmType;
        int minRadius   = cached.minRadius;
        int maxRadius   = cached.maxRadius;
        int minAmount   = cached.minAmount;
        int maxAmount   = cached.maxAmount;
        double minScale = cached.minScale;
        double maxScale = cached.maxScale;

        if (minAmount < 1) minAmount = 1;
        if (maxAmount < minAmount) maxAmount = minAmount;

        int spawnCount = (maxAmount > minAmount)
                ? minAmount + random.nextInt(maxAmount - minAmount + 1)
                : minAmount;

        for (int i = 0; i < spawnCount; i++) {
            final Location spawnLoc = getSpawnLocation(loc, minRadius, maxRadius);
            final double finalMinScale = minScale;
            final double finalMaxScale = maxScale;

            if (spawnQueue.size() >= MAX_PENDING_SPAWNS) continue;
            spawnQueue.add(() -> {
                World spawnWorld = spawnLoc.getWorld();
                if (spawnWorld == null || !spawnLoc.getChunk().isLoaded()) return;
                if (mmType != null && !mmType.isEmpty() && plugin.isMythicMobsEnabled()) {
                    try {
                        Entity entity = io.lumine.mythic.bukkit.MythicBukkit.inst().getMobManager()
                                .spawnMob(mmType, spawnLoc).getEntity().getBukkitEntity();
                        applyScale(entity, finalMinScale, finalMaxScale);
                        plugin.trackMythicSpawn();
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to spawn MythicMob '" + mmType + "': " + e.getMessage());
                    }
                } else if (data != null && !data.isMythicMob()) {
                    try {
                        Entity entity = spawnLoc.getWorld().spawnEntity(spawnLoc, data.getEntityType());
                        applyScale(entity, finalMinScale, finalMaxScale);
                        plugin.trackVanillaSpawn();
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to spawn vanilla mob: " + e.getMessage());
                    }
                }
            });
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

    private void applyScale(Entity entity, double minScale, double maxScale) {
        if (Math.abs(minScale - 1.0) < 1e-9 && Math.abs(maxScale - 1.0) < 1e-9) return;
        if (!(entity instanceof LivingEntity)) return;
        double scale = (maxScale > minScale)
                ? minScale + random.nextDouble() * (maxScale - minScale)
                : minScale;
        try {
            org.bukkit.attribute.Attributable attributable = (org.bukkit.attribute.Attributable) entity;
            org.bukkit.attribute.Attribute scaleAttr = org.bukkit.attribute.Attribute.valueOf("GENERIC_SCALE");
            org.bukkit.attribute.AttributeInstance attr = attributable.getAttribute(scaleAttr);
            if (attr != null) {
                attr.setBaseValue(scale);
            }
        } catch (Exception e) {
            // GENERIC_SCALE not available on this server version – silently ignore
        }
    }

    private static String locKey(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }
}
