package com.wynvers.spawners;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class SpawnerConfig {

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
                spawners.put(id, data);
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
        if (material == null) {
            throw new IllegalArgumentException("Invalid material: " + materialName);
        }

        String rawDisplayName = section.getString("display-name", id);
        String displayName = ChatColor.translateAlternateColorCodes('&', rawDisplayName);

        List<String> rawLore = section.getStringList("lore");
        List<String> lore = rawLore.stream()
                .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                .collect(Collectors.toList());

        String entityTypeName = section.getString("entity-type", "PIG");
        EntityType entityType;
        try {
            entityType = EntityType.valueOf(entityTypeName.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid entity-type: " + entityTypeName);
        }

        int delay = section.getInt("delay", 200);
        int minSpawnDelay = section.getInt("min-spawn-delay", 100);
        int maxSpawnDelay = section.getInt("max-spawn-delay", 400);
        int spawnCount = section.getInt("spawn-count", 1);
        int spawnRange = section.getInt("spawn-range", 4);
        int requiredPlayerRange = section.getInt("required-player-range", 16);

        return new SpawnerData(id, material, displayName, lore, entityType,
                delay, minSpawnDelay, maxSpawnDelay, spawnCount, spawnRange, requiredPlayerRange);
    }

    public SpawnerData getSpawner(String id) {
        return spawners.get(id);
    }

    public Map<String, SpawnerData> getAllSpawners() {
        return Collections.unmodifiableMap(spawners);
    }
}
