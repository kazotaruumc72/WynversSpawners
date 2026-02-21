package com.wynvers.spawners;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

public class SpawnerConfig {

    private static final String MYTHIC_MOBS_PREFIX = "mm:";

    private final Map<String, SpawnerData> spawners = new HashMap<>();
    private final Logger logger;

    public SpawnerConfig(Logger logger) {
        this.logger = logger;
    }

    public void loadSpawners(FileConfiguration config) {
        spawners.clear();

        ConfigurationSection spawnersSection = config.getConfigurationSection("spawners");
        if (spawnersSection == null) {
            logger.warning("No 'spawners' section found in config.yml");
            return;
        }

        for (String id : spawnersSection.getKeys(false)) {
            ConfigurationSection section = spawnersSection.getConfigurationSection(id);
            if (section == null) {
                logger.warning("Invalid spawner entry: " + id);
                continue;
            }
            try {
                SpawnerData data = parseSpawner(id, section);
                spawners.put(id.toLowerCase(Locale.ROOT), data);
                logger.info("Loaded spawner: " + id);
            } catch (IllegalArgumentException e) {
                logger.warning("Failed to load spawner '" + id + "': " + e.getMessage());
            }
        }
        logger.info("Loaded " + spawners.size() + " spawner(s) from config.");
    }

    private SpawnerData parseSpawner(String id, ConfigurationSection section) {
        String materialName = section.getString("material", "SPAWNER");
        Material material = Material.matchMaterial(materialName);
        if (material == null) throw new IllegalArgumentException("Invalid material: " + materialName);

        String displayName = section.getString("display-name", id);

        List<String> lore = section.getStringList("lore");

        String entityTypeName = section.getString("entity-type", "PIG");
        String mythicMobType = null;
        EntityType entityType;
        if (entityTypeName.toLowerCase().startsWith(MYTHIC_MOBS_PREFIX)) {
            mythicMobType = entityTypeName.substring(MYTHIC_MOBS_PREFIX.length());
            entityType = EntityType.PIG;
        } else {
            try {
                entityType = EntityType.valueOf(entityTypeName.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid entity-type: " + entityTypeName);
            }
        }

        int delay               = section.getInt("delay", 200);
        int requiredPlayerRange = section.getInt("required-player-range", 16);
        int minRadius           = section.getInt("min-radius", 0);
        int maxRadius           = section.getInt("max-radius", 0);
        int minAmount           = section.getInt("min-amount", 0);
        int maxAmount           = section.getInt("max-amount", 0);

        return new SpawnerData(id, material, displayName, lore, entityType, mythicMobType,
                delay, requiredPlayerRange,
                minRadius, maxRadius, minAmount, maxAmount);
    }

    public void saveField(FileConfiguration config, String spawnerId, String field, int value) {
        config.set("spawners." + spawnerId + "." + field, value);
    }

    public SpawnerData getSpawner(String id) { return id != null ? spawners.get(id.toLowerCase(Locale.ROOT)) : null; }

    public Map<String, SpawnerData> getAllSpawners() {
        return Collections.unmodifiableMap(spawners);
    }
}
