package com.wynvers.spawners;

import org.bukkit.entity.EntityType;
import org.bukkit.Material;
import java.util.List;

public class SpawnerData {

    private final String id;
    private final Material material;
    private final String displayName;
    private final List<String> lore;
    private final EntityType entityType;
    private final String mythicMobType;
    private final int delay;
    private final int minSpawnDelay;
    private final int maxSpawnDelay;
    private final int spawnCount;
    private final int spawnRange;
    private final int requiredPlayerRange;
    private final int minRadius;
    private final int maxRadius;
    private final int minAmount;
    private final int maxAmount;

    public SpawnerData(String id, Material material, String displayName, List<String> lore,
                       EntityType entityType, String mythicMobType, int delay, int minSpawnDelay,
                       int maxSpawnDelay, int spawnCount, int spawnRange, int requiredPlayerRange,
                       int minRadius, int maxRadius, int minAmount, int maxAmount) {
        this.id = id;
        this.material = material;
        this.displayName = displayName;
        this.lore = lore;
        this.entityType = entityType;
        this.mythicMobType = mythicMobType;
        this.delay = delay;
        this.minSpawnDelay = minSpawnDelay;
        this.maxSpawnDelay = maxSpawnDelay;
        this.spawnCount = spawnCount;
        this.spawnRange = spawnRange;
        this.requiredPlayerRange = requiredPlayerRange;
        this.minRadius = minRadius;
        this.maxRadius = maxRadius;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
    }

    public String getId() {
        return id;
    }

    public Material getMaterial() {
        return material;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getLore() {
        return lore;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public boolean isMythicMob() {
        return mythicMobType != null;
    }

    public String getMythicMobType() {
        return mythicMobType;
    }

    public int getDelay() {
        return delay;
    }

    public int getMinSpawnDelay() {
        return minSpawnDelay;
    }

    public int getMaxSpawnDelay() {
        return maxSpawnDelay;
    }

    public int getSpawnCount() {
        return spawnCount;
    }

    public int getSpawnRange() {
        return spawnRange;
    }

    public int getRequiredPlayerRange() {
        return requiredPlayerRange;
    }

    public int getMinRadius() {
        return minRadius;
    }

    public int getMaxRadius() {
        return maxRadius;
    }

    public int getMinAmount() {
        return minAmount;
    }

    public int getMaxAmount() {
        return maxAmount;
    }
}
