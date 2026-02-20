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
    private int delay;
    private int requiredPlayerRange;
    private int minRadius;
    private int maxRadius;
    private int minAmount;
    private int maxAmount;

    public SpawnerData(String id, Material material, String displayName, List<String> lore,
                       EntityType entityType, String mythicMobType, int delay,
                       int requiredPlayerRange,
                       int minRadius, int maxRadius, int minAmount, int maxAmount) {
        this.id = id;
        this.material = material;
        this.displayName = displayName;
        this.lore = lore;
        this.entityType = entityType;
        this.mythicMobType = mythicMobType;
        this.delay = delay;
        this.requiredPlayerRange = requiredPlayerRange;
        this.minRadius = minRadius;
        this.maxRadius = maxRadius;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
    }

    public String getId() { return id; }
    public Material getMaterial() { return material; }
    public String getDisplayName() { return displayName; }
    public List<String> getLore() { return lore; }
    public EntityType getEntityType() { return entityType; }
    public boolean isMythicMob() { return mythicMobType != null; }
    public String getMythicMobType() { return mythicMobType; }

    public int getDelay() { return delay; }
    public void setDelay(int delay) { this.delay = delay; }

    public int getRequiredPlayerRange() { return requiredPlayerRange; }
    public void setRequiredPlayerRange(int requiredPlayerRange) { this.requiredPlayerRange = requiredPlayerRange; }

    public int getMinRadius() { return minRadius; }
    public void setMinRadius(int minRadius) { this.minRadius = minRadius; }

    public int getMaxRadius() { return maxRadius; }
    public void setMaxRadius(int maxRadius) { this.maxRadius = maxRadius; }

    public int getMinAmount() { return minAmount; }
    public void setMinAmount(int minAmount) { this.minAmount = minAmount; }

    public int getMaxAmount() { return maxAmount; }
    public void setMaxAmount(int maxAmount) { this.maxAmount = maxAmount; }
}
