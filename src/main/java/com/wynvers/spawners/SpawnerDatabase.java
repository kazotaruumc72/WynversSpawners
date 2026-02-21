package com.wynvers.spawners;

import org.bukkit.Location;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite database manager for tracking player-owned spawners.
 * Stores coordinates and all internal settings for each placed spawner.
 */
public class SpawnerDatabase {

    private final Logger logger;
    private Connection connection;

    public SpawnerDatabase(File dataFolder, Logger logger) {
        this.logger = logger;
        try {
            if (!dataFolder.exists()) dataFolder.mkdirs();
            File dbFile = new File(dataFolder, "spawners.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            createTable();
            logger.info("SpawnerDatabase initialized (SQLite).");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to initialize SpawnerDatabase", e);
        }
    }

    private void createTable() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS player_spawners ("
                + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + " player_uuid TEXT NOT NULL,"
                + " player_name TEXT NOT NULL,"
                + " spawner_id TEXT NOT NULL,"
                + " world TEXT NOT NULL,"
                + " x INTEGER NOT NULL,"
                + " y INTEGER NOT NULL,"
                + " z INTEGER NOT NULL,"
                + " entity_type TEXT,"
                + " mythic_mob_type TEXT,"
                + " delay INTEGER,"
                + " required_player_range INTEGER,"
                + " min_radius INTEGER,"
                + " max_radius INTEGER,"
                + " min_amount INTEGER,"
                + " max_amount INTEGER,"
                + " placed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                + ")"
            );
        }
    }

    /**
     * Record a spawner placement for a player.
     */
    public void addSpawner(UUID playerUuid, String playerName, String spawnerId,
                           Location loc, SpawnerData data) {
        String sql = "INSERT INTO player_spawners "
                + "(player_uuid, player_name, spawner_id, world, x, y, z, "
                + "entity_type, mythic_mob_type, delay, required_player_range, "
                + "min_radius, max_radius, min_amount, max_amount) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, playerName);
            ps.setString(3, spawnerId);
            ps.setString(4, loc.getWorld().getName());
            ps.setInt(5, loc.getBlockX());
            ps.setInt(6, loc.getBlockY());
            ps.setInt(7, loc.getBlockZ());
            ps.setString(8, data.getEntityType().name());
            ps.setString(9, data.isMythicMob() ? data.getMythicMobType() : null);
            ps.setInt(10, data.getDelay());
            ps.setInt(11, data.getRequiredPlayerRange());
            ps.setInt(12, data.getMinRadius());
            ps.setInt(13, data.getMaxRadius());
            ps.setInt(14, data.getMinAmount());
            ps.setInt(15, data.getMaxAmount());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to add spawner to database", e);
        }
    }

    /**
     * Remove a spawner record by its location.
     */
    public void removeSpawner(Location loc) {
        String sql = "DELETE FROM player_spawners WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, loc.getWorld().getName());
            ps.setInt(2, loc.getBlockX());
            ps.setInt(3, loc.getBlockY());
            ps.setInt(4, loc.getBlockZ());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to remove spawner from database", e);
        }
    }

    /**
     * Get the owner UUID of a spawner at the given location, or null if not found.
     */
    public UUID getOwner(Location loc) {
        String sql = "SELECT player_uuid FROM player_spawners WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, loc.getWorld().getName());
            ps.setInt(2, loc.getBlockX());
            ps.setInt(3, loc.getBlockY());
            ps.setInt(4, loc.getBlockZ());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return UUID.fromString(rs.getString("player_uuid"));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to query spawner owner", e);
        }
        return null;
    }

    /**
     * Get all spawners owned by a player.
     */
    public List<SpawnerRecord> getSpawners(UUID playerUuid) {
        List<SpawnerRecord> records = new ArrayList<>();
        String sql = "SELECT * FROM player_spawners WHERE player_uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    records.add(readRecord(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to query player spawners", e);
        }
        return records;
    }

    /**
     * Get all spawners owned by a player name (latest name stored).
     */
    public List<SpawnerRecord> getSpawnersByName(String playerName) {
        List<SpawnerRecord> records = new ArrayList<>();
        String sql = "SELECT * FROM player_spawners WHERE player_name = ? COLLATE NOCASE";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    records.add(readRecord(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to query spawners by player name", e);
        }
        return records;
    }

    private SpawnerRecord readRecord(ResultSet rs) throws SQLException {
        return new SpawnerRecord(
            rs.getString("player_uuid"),
            rs.getString("player_name"),
            rs.getString("spawner_id"),
            rs.getString("world"),
            rs.getInt("x"),
            rs.getInt("y"),
            rs.getInt("z"),
            rs.getString("entity_type"),
            rs.getString("mythic_mob_type"),
            rs.getInt("delay"),
            rs.getInt("required_player_range"),
            rs.getInt("min_radius"),
            rs.getInt("max_radius"),
            rs.getInt("min_amount"),
            rs.getInt("max_amount")
        );
    }

    /**
     * Close the database connection.
     */
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                logger.info("SpawnerDatabase closed.");
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to close SpawnerDatabase", e);
        }
    }

    /**
     * Immutable record representing a placed spawner in the database.
     */
    public static class SpawnerRecord {
        private final String playerUuid;
        private final String playerName;
        private final String spawnerId;
        private final String world;
        private final int x, y, z;
        private final String entityType;
        private final String mythicMobType;
        private final int delay;
        private final int requiredPlayerRange;
        private final int minRadius, maxRadius;
        private final int minAmount, maxAmount;

        public SpawnerRecord(String playerUuid, String playerName, String spawnerId,
                             String world, int x, int y, int z,
                             String entityType, String mythicMobType,
                             int delay, int requiredPlayerRange,
                             int minRadius, int maxRadius, int minAmount, int maxAmount) {
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.spawnerId = spawnerId;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.entityType = entityType;
            this.mythicMobType = mythicMobType;
            this.delay = delay;
            this.requiredPlayerRange = requiredPlayerRange;
            this.minRadius = minRadius;
            this.maxRadius = maxRadius;
            this.minAmount = minAmount;
            this.maxAmount = maxAmount;
        }

        public String getPlayerUuid()        { return playerUuid; }
        public String getPlayerName()        { return playerName; }
        public String getSpawnerId()         { return spawnerId; }
        public String getWorld()             { return world; }
        public int getX()                    { return x; }
        public int getY()                    { return y; }
        public int getZ()                    { return z; }
        public String getEntityType()        { return entityType; }
        public String getMythicMobType()     { return mythicMobType; }
        public int getDelay()                { return delay; }
        public int getRequiredPlayerRange()  { return requiredPlayerRange; }
        public int getMinRadius()            { return minRadius; }
        public int getMaxRadius()            { return maxRadius; }
        public int getMinAmount()            { return minAmount; }
        public int getMaxAmount()            { return maxAmount; }
    }
}
