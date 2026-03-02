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

    private final WSpawners plugin;
    private final Random random = new Random();

    private final Map<String, Integer>     countdowns  = new HashMap<>();
    private final Map<String, Location>    locations   = new HashMap<>();
    private final Map<String, SpawnParams> paramsCache = new HashMap<>();
    private final Queue<Runnable> spawnQueue = new ArrayDeque<>();

    private BukkitTask task;
    private boolean sparkEnabled    = true;
    private int maxSpawnsPerTick    = 4;
    private int maxNearbyEntities   = 0; // 0 = disabled

    /**
     * PDC values read once from the block state and cached for the lifetime of
     * the registration.  Re-populated whenever the spawner is re-registered.
     */
    private static final class SpawnParams {
        final String  spawnerId;
        final String  mmType;          // nullable
        final Integer pdcMinRadius;    // nullable → fall back to SpawnerData
        final Integer pdcMaxRadius;
        final Integer pdcMinAmount;
        final Integer pdcMaxAmount;
        final Double  pdcMinScale;
        final Double  pdcMaxScale;
        final int     blockPlayerRange; // cs.getRequiredPlayerRange() fallback

        SpawnParams(String spawnerId, String mmType,
                    Integer pdcMinRadius, Integer pdcMaxRadius,
                    Integer pdcMinAmount, Integer pdcMaxAmount,
                    Double pdcMinScale,   Double pdcMaxScale,
                    int blockPlayerRange) {
            this.spawnerId        = spawnerId;
            this.mmType           = mmType;
            this.pdcMinRadius     = pdcMinRadius;
            this.pdcMaxRadius     = pdcMaxRadius;
            this.pdcMinAmount     = pdcMinAmount;
            this.pdcMaxAmount     = pdcMaxAmount;
            this.pdcMinScale      = pdcMinScale;
            this.pdcMaxScale      = pdcMaxScale;
            this.blockPlayerRange = blockPlayerRange;
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
        paramsCache.clear();
        spawnQueue.clear();
    }

    public void register(Location loc, int delayTicks) {
        String key = locKey(loc);
        locations.put(key, loc.clone());
        countdowns.put(key, delayTicks > 0 ? delayTicks : 200);
        paramsCache.remove(key); // invalidate so params are re-read on next tick
    }

    public void unregister(Location loc) {
        String key = locKey(loc);
        locations.remove(key);
        countdowns.remove(key);
        paramsCache.remove(key);
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

    public void setMaxNearbyEntities(int maxNearbyEntities) {
        this.maxNearbyEntities = Math.max(0, maxNearbyEntities);
    }

    private void tick() {
        // Process pending spawns from queue (rate-limited)
        int spawnsProcessed = 0;
        while (!spawnQueue.isEmpty() && spawnsProcessed < maxSpawnsPerTick) {
            spawnQueue.poll().run();
            spawnsProcessed++;
        }

        Set<String> toRemove = new HashSet<>();

        for (Map.Entry<String, Integer> entry : countdowns.entrySet()) {
            String key = entry.getKey();
            Location loc = locations.get(key);
            if (loc == null) { toRemove.add(key); continue; }

            World world = loc.getWorld();
            if (world == null) continue;

            Block block = world.getBlockAt(loc);
            if (block.getType() != org.bukkit.Material.SPAWNER) { toRemove.add(key); continue; }

            // Use cached PDC params; populate via block.getState() only on first access.
            SpawnParams params = paramsCache.get(key);
            if (params == null) {
                params = readAndCacheParams(key, block);
                if (params == null) { toRemove.add(key); continue; }
            }

            SpawnerData data = plugin.getSpawnerConfig().getSpawner(params.spawnerId);

            int playerRange = data != null ? data.getRequiredPlayerRange() : params.blockPlayerRange;
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

            spawnMobs(loc, params, data);

            int delay = data != null ? data.getDelay() : 200;
            if (delay <= 0) delay = 200;
            entry.setValue(delay);
        }

        toRemove.forEach(k -> { countdowns.remove(k); locations.remove(k); paramsCache.remove(k); });
    }

    /**
     * Reads all PDC values from the block state once and stores them in
     * {@link #paramsCache}.  Returns {@code null} if the block is not a managed
     * spawner (no {@code spawner_id} PDC key).
     */
    private SpawnParams readAndCacheParams(String key, Block block) {
        BlockState state = block.getState();
        if (!(state instanceof CreatureSpawner)) return null;
        CreatureSpawner cs = (CreatureSpawner) state;

        String spawnerId = cs.getPersistentDataContainer()
                .get(plugin.getSpawnerIdKey(), PersistentDataType.STRING);
        if (spawnerId == null) return null;

        String  mmType    = cs.getPersistentDataContainer().get(plugin.getMythicMobTypeKey(), PersistentDataType.STRING);
        Integer minRadius = cs.getPersistentDataContainer().get(plugin.getMinRadiusKey(),      PersistentDataType.INTEGER);
        Integer maxRadius = cs.getPersistentDataContainer().get(plugin.getMaxRadiusKey(),      PersistentDataType.INTEGER);
        Integer minAmount = cs.getPersistentDataContainer().get(plugin.getMinAmountKey(),      PersistentDataType.INTEGER);
        Integer maxAmount = cs.getPersistentDataContainer().get(plugin.getMaxAmountKey(),      PersistentDataType.INTEGER);
        Double  minScale  = cs.getPersistentDataContainer().get(plugin.getMinScaleKey(),       PersistentDataType.DOUBLE);
        Double  maxScale  = cs.getPersistentDataContainer().get(plugin.getMaxScaleKey(),       PersistentDataType.DOUBLE);

        SpawnParams params = new SpawnParams(
                spawnerId, mmType,
                minRadius, maxRadius,
                minAmount, maxAmount,
                minScale,  maxScale,
                cs.getRequiredPlayerRange()
        );
        paramsCache.put(key, params);
        return params;
    }

    private void spawnMobs(Location loc, SpawnParams params, SpawnerData data) {
        String mmType = params.mmType;

        int minRadius = params.pdcMinRadius != null ? params.pdcMinRadius : (data != null ? data.getMinRadius() : 0);
        int maxRadius = params.pdcMaxRadius != null ? params.pdcMaxRadius : (data != null ? data.getMaxRadius() : 0);
        int minAmount = params.pdcMinAmount != null ? params.pdcMinAmount : (data != null ? data.getMinAmount() : 1);
        int maxAmount = params.pdcMaxAmount != null ? params.pdcMaxAmount : (data != null ? data.getMaxAmount() : 1);
        double minScale = params.pdcMinScale != null ? params.pdcMinScale : (data != null ? data.getMinScale() : 1.0);
        double maxScale = params.pdcMaxScale != null ? params.pdcMaxScale : (data != null ? data.getMaxScale() : 1.0);

        if (minAmount < 1) minAmount = 1;
        if (maxAmount < minAmount) maxAmount = minAmount;

        // Anti-lag: skip spawn when too many entities already crowd the area
        if (maxNearbyEntities > 0) {
            double checkRadius = Math.max(maxRadius, 8);
            int nearby = loc.getWorld().getNearbyEntities(loc, checkRadius, checkRadius, checkRadius,
                    e -> e instanceof LivingEntity).size();
            if (nearby >= maxNearbyEntities) return;
        }

        int spawnCount = (maxAmount > minAmount)
                ? minAmount + random.nextInt(maxAmount - minAmount + 1)
                : minAmount;

        for (int i = 0; i < spawnCount; i++) {
            final Location spawnLoc = getSpawnLocation(loc, minRadius, maxRadius);
            final double finalMinScale = minScale;
            final double finalMaxScale = maxScale;

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
                        Entity entity = spawnWorld.spawnEntity(spawnLoc, data.getEntityType());
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
