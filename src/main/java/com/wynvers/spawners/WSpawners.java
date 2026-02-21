package com.wynvers.spawners;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.wynvers.spawners.SpawnerDatabase.SpawnerRecord;

public class WSpawners extends JavaPlugin implements Listener {

    private static final int BSTATS_PLUGIN_ID = 29665;

    private SpawnerConfig spawnerConfig;
    private SpawnerEditorMenu editorMenu;
    private SpawnerTickManager tickManager;
    private SpawnerDatabase database;
    private MessageManager messageManager;

    private NamespacedKey mythicMobTypeKey;
    private NamespacedKey spawnerIdKey;
    private NamespacedKey minRadiusKey;
    private NamespacedKey maxRadiusKey;
    private NamespacedKey minAmountKey;
    private NamespacedKey maxAmountKey;

    private boolean mythicMobsEnabled = false;
    private final Map<UUID, String> openEditorSpawnerIds = new HashMap<>();

    // bStats counters
    private final AtomicInteger spawnersPlaced = new AtomicInteger(0);
    private final AtomicInteger mythicSpawns   = new AtomicInteger(0);
    private final AtomicInteger vanillaSpawns  = new AtomicInteger(0);

    @Override
    public void onEnable() {
        saveDefaultConfig();

        mythicMobTypeKey = new NamespacedKey(this, "mythic_mob_type");
        spawnerIdKey     = new NamespacedKey(this, "spawner_id");
        minRadiusKey     = new NamespacedKey(this, "min_radius");
        maxRadiusKey     = new NamespacedKey(this, "max_radius");
        minAmountKey     = new NamespacedKey(this, "min_amount");
        maxAmountKey     = new NamespacedKey(this, "max_amount");

        messageManager = new MessageManager(this);

        spawnerConfig = new SpawnerConfig(getLogger());
        spawnerConfig.loadSpawners(getConfig());

        editorMenu  = new SpawnerEditorMenu(this);
        tickManager = new SpawnerTickManager(this);
        tickManager.setSparkEnabled(getConfig().getBoolean("spark-particles", true));
        tickManager.setMaxSpawnsPerTick(getConfig().getInt("max-spawns-per-tick", 4));

        database = new SpawnerDatabase(getDataFolder(), getLogger());

        mythicMobsEnabled = Bukkit.getPluginManager().getPlugin("MythicMobs") != null;
        if (mythicMobsEnabled) getLogger().info("MythicMobs detected!");

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(editorMenu, this);

        tickManager.start();
        initBStats();

        getLogger().info("WSpawners enabled!");
    }

    private void initBStats() {
        Metrics metrics = new Metrics(this, BSTATS_PLUGIN_ID);

        metrics.addCustomChart(new SingleLineChart("spawners_placed", () -> spawnersPlaced.getAndSet(0)));
        metrics.addCustomChart(new SingleLineChart("mythic_spawns",   () -> mythicSpawns.getAndSet(0)));

        metrics.addCustomChart(new SimplePie("spawn_type_ratio", () -> {
            int m = mythicSpawns.get(), v = vanillaSpawns.get();
            if (m == 0 && v == 0) return "None";
            return m >= v ? "MythicMobs" : "Vanilla";
        }));

        metrics.addCustomChart(new SimplePie("mythicmobs_enabled",
                () -> mythicMobsEnabled ? "Enabled" : "Disabled"));

        metrics.addCustomChart(new SimplePie("configured_spawner_types", () -> {
            int c = spawnerConfig.getAllSpawners().size();
            if (c == 0)       return "0";
            else if (c <= 5)  return "1-5";
            else if (c <= 15) return "6-15";
            else              return "16+";
        }));

        getLogger().info("bStats Metrics initialized (ID: " + BSTATS_PLUGIN_ID + ")");
    }

    @Override
    public void onDisable() {
        tickManager.stop();
        if (database != null) database.close();
        getLogger().info("WSpawners disabled!");
    }

    // ---- Public accessors ----
    public SpawnerConfig getSpawnerConfig()                    { return spawnerConfig; }
    public SpawnerEditorMenu getEditorMenu()                   { return editorMenu; }
    public SpawnerTickManager getTickManager()                 { return tickManager; }
    public SpawnerDatabase getDatabase()                       { return database; }
    public MessageManager getMessageManager()                  { return messageManager; }
    public boolean isMythicMobsEnabled()                      { return mythicMobsEnabled; }
    public NamespacedKey getMythicMobTypeKey()                 { return mythicMobTypeKey; }
    public NamespacedKey getSpawnerIdKey()                     { return spawnerIdKey; }
    public NamespacedKey getMinRadiusKey()                     { return minRadiusKey; }
    public NamespacedKey getMaxRadiusKey()                     { return maxRadiusKey; }
    public NamespacedKey getMinAmountKey()                     { return minAmountKey; }
    public NamespacedKey getMaxAmountKey()                     { return maxAmountKey; }
    public String getOpenEditorSpawnerId(UUID uuid)            { return openEditorSpawnerIds.get(uuid); }
    public void setOpenEditorSpawnerId(UUID uuid, String id)   { openEditorSpawnerIds.put(uuid, id); }
    public void trackMythicSpawn()                             { mythicSpawns.incrementAndGet(); }
    public void trackVanillaSpawn()                            { vanillaSpawns.incrementAndGet(); }

    // ---- Commands ----
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("spawner")) return false;
        if (args.length == 0) { sender.sendMessage(messageManager.get("command-usage")); return true; }
        switch (args[0].toLowerCase()) {
            case "list":       handleList(sender);           return true;
            case "give":       handleGive(sender, args);     return true;
            case "reload":     handleReload(sender);         return true;
            case "myspawners": handleMySpawners(sender);     return true;
            case "info":       handleInfo(sender, args);     return true;
            default: sender.sendMessage(messageManager.get("unknown-command", "command", args[0])); return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("spawner")) return Collections.emptyList();
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(Arrays.asList("give", "list", "reload", "myspawners", "info"));
            for (String sub : subs)
                if (sub.startsWith(args[0].toLowerCase())) completions.add(sub);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) completions.add(p.getName());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            for (String id : spawnerConfig.getAllSpawners().keySet())
                if (id.toLowerCase().startsWith(args[2].toLowerCase())) completions.add(id);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) completions.add(p.getName());
        }
        Collections.sort(completions);
        return completions;
    }

    private void handleList(CommandSender sender) {
        Map<String, SpawnerData> all = spawnerConfig.getAllSpawners();
        if (all.isEmpty()) { sender.sendMessage(messageManager.get("list-empty")); return; }
        sender.sendMessage(messageManager.get("list-header"));
        for (Map.Entry<String, SpawnerData> entry : all.entrySet()) {
            SpawnerData data = entry.getValue();
            String type = data.isMythicMob() ? "mm:" + data.getMythicMobType() : data.getEntityType().name();
            sender.sendMessage(messageManager.get("list-entry", "id", entry.getKey(), "type", type));
        }
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(messageManager.get("give-usage")); return; }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { sender.sendMessage(messageManager.get("give-player-not-found", "player", args[1])); return; }
        SpawnerData data = spawnerConfig.getSpawner(args[2].toLowerCase());
        if (data == null) { sender.sendMessage(messageManager.get("give-unknown-spawner", "id", args[2])); return; }
        target.getInventory().addItem(createSpawnerItem(data));
        sender.sendMessage(messageManager.get("give-success", "name", data.getDisplayName(), "player", target.getName()));
    }

    private void handleReload(CommandSender sender) {
        reloadConfig();
        spawnerConfig.loadSpawners(getConfig());
        messageManager.reload();
        tickManager.setSparkEnabled(getConfig().getBoolean("spark-particles", true));
        tickManager.setMaxSpawnsPerTick(getConfig().getInt("max-spawns-per-tick", 4));
        sender.sendMessage(messageManager.get("reload-success"));
    }

    private void handleMySpawners(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messageManager.get("myspawners-not-player"));
            return;
        }
        Player player = (Player) sender;
        List<SpawnerRecord> records = database.getSpawners(player.getUniqueId());
        if (records.isEmpty()) {
            player.sendMessage(messageManager.get("myspawners-empty"));
            return;
        }
        player.sendMessage(messageManager.get("myspawners-header"));
        for (SpawnerRecord r : records) {
            String type = r.getMythicMobType() != null ? "mm:" + r.getMythicMobType() : r.getEntityType();
            player.sendMessage(messageManager.get("myspawners-entry",
                    "spawner_id", r.getSpawnerId(),
                    "world", r.getWorld(),
                    "x", String.valueOf(r.getX()),
                    "y", String.valueOf(r.getY()),
                    "z", String.valueOf(r.getZ()),
                    "type", type));
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(messageManager.get("info-usage")); return; }
        String targetName = args[1];
        List<SpawnerRecord> records = database.getSpawnersByName(targetName);
        if (records.isEmpty()) {
            sender.sendMessage(messageManager.get("info-empty"));
            return;
        }
        sender.sendMessage(messageManager.get("info-header", "player", targetName));
        for (SpawnerRecord r : records) {
            String type = r.getMythicMobType() != null ? "mm:" + r.getMythicMobType() : r.getEntityType();
            sender.sendMessage(messageManager.get("info-entry",
                    "spawner_id", r.getSpawnerId(),
                    "world", r.getWorld(),
                    "x", String.valueOf(r.getX()),
                    "y", String.valueOf(r.getY()),
                    "z", String.valueOf(r.getZ()),
                    "type", type));
        }
    }

    // ---- Item creation ----
    public ItemStack createSpawnerItem(SpawnerData data) {
        ItemStack item = new ItemStack(data.getMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(data.getDisplayName());
            meta.setLore(data.getLore());
            if (meta instanceof BlockStateMeta) {
                BlockStateMeta blockMeta = (BlockStateMeta) meta;
                BlockState state = blockMeta.getBlockState();
                if (state instanceof CreatureSpawner) {
                    CreatureSpawner spawner = (CreatureSpawner) state;
                    spawner.setSpawnedType(EntityType.VILLAGER);
                    spawner.setDelay(data.getDelay());
                    spawner.setMinSpawnDelay(data.getDelay());
                    spawner.setMaxSpawnDelay(data.getDelay());
                    spawner.setRequiredPlayerRange(data.getRequiredPlayerRange());
                    spawner.getPersistentDataContainer().set(spawnerIdKey, PersistentDataType.STRING, data.getId());
                    if (data.isMythicMob())
                        spawner.getPersistentDataContainer().set(mythicMobTypeKey, PersistentDataType.STRING, data.getMythicMobType());
                    spawner.getPersistentDataContainer().set(minRadiusKey, PersistentDataType.INTEGER, data.getMinRadius());
                    spawner.getPersistentDataContainer().set(maxRadiusKey, PersistentDataType.INTEGER, data.getMaxRadius());
                    spawner.getPersistentDataContainer().set(minAmountKey, PersistentDataType.INTEGER, data.getMinAmount());
                    spawner.getPersistentDataContainer().set(maxAmountKey, PersistentDataType.INTEGER, data.getMaxAmount());
                    blockMeta.setBlockState(spawner);
                }
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    // ---- Events ----

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item.getType() != Material.SPAWNER) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;
        if (!(meta instanceof BlockStateMeta)) return;
        BlockStateMeta blockMeta = (BlockStateMeta) meta;
        BlockState itemState = blockMeta.getBlockState();
        if (!(itemState instanceof CreatureSpawner)) return;
        CreatureSpawner itemSpawner = (CreatureSpawner) itemState;
        String spawnerId = itemSpawner.getPersistentDataContainer().get(spawnerIdKey, PersistentDataType.STRING);
        if (spawnerId == null) return;
        BlockState placedState = event.getBlockPlaced().getState();
        if (!(placedState instanceof CreatureSpawner)) return;
        CreatureSpawner placedSpawner = (CreatureSpawner) placedState;
        placedSpawner.setSpawnedType(EntityType.VILLAGER);
        placedSpawner.setDelay(itemSpawner.getDelay());
        placedSpawner.setMinSpawnDelay(itemSpawner.getMinSpawnDelay());
        placedSpawner.setMaxSpawnDelay(itemSpawner.getMaxSpawnDelay());
        placedSpawner.setRequiredPlayerRange(itemSpawner.getRequiredPlayerRange());
        copyPDC(itemSpawner, placedSpawner, spawnerIdKey, PersistentDataType.STRING);
        copyPDC(itemSpawner, placedSpawner, mythicMobTypeKey, PersistentDataType.STRING);
        copyPDCInt(itemSpawner, placedSpawner, minRadiusKey);
        copyPDCInt(itemSpawner, placedSpawner, maxRadiusKey);
        copyPDCInt(itemSpawner, placedSpawner, minAmountKey);
        copyPDCInt(itemSpawner, placedSpawner, maxAmountKey);
        placedSpawner.update();
        SpawnerData data = spawnerConfig.getSpawner(spawnerId);
        int delay = data != null ? data.getDelay() : itemSpawner.getDelay();
        tickManager.register(event.getBlockPlaced().getLocation(), delay);
        spawnersPlaced.incrementAndGet();

        // Record in database
        if (data != null) {
            database.addSpawner(event.getPlayer().getUniqueId(), event.getPlayer().getName(),
                    spawnerId, event.getBlockPlaced().getLocation(), data);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.SPAWNER) return;
        BlockState state = block.getState();
        if (!(state instanceof CreatureSpawner)) return;
        CreatureSpawner cs = (CreatureSpawner) state;
        String spawnerId = cs.getPersistentDataContainer().get(spawnerIdKey, PersistentDataType.STRING);
        if (spawnerId == null) return;
        tickManager.unregister(block.getLocation());
        Player player = event.getPlayer();

        // Check permissions: admin or owner with wspawners.use
        UUID ownerUuid = database.getOwner(block.getLocation());
        boolean isOwner = ownerUuid != null && ownerUuid.equals(player.getUniqueId());
        boolean isAdmin = player.hasPermission("wspawner.admin");

        if (!isAdmin && !(isOwner && player.hasPermission("wspawners.use"))) {
            event.setCancelled(true);
            player.sendMessage(messageManager.get("spawner-no-permission-break"));
            return;
        }

        SpawnerData data = spawnerConfig.getSpawner(spawnerId);
        if (data == null) return;
        event.setDropItems(false);
        event.setExpToDrop(0);
        ItemStack spawnerItem = createSpawnerItem(data);
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(spawnerItem);
        if (!overflow.isEmpty()) block.getWorld().dropItemNaturally(block.getLocation(), spawnerItem);
        player.sendMessage(messageManager.get("spawner-recovered", "name", data.getDisplayName()));

        // Remove from database
        database.removeSpawner(block.getLocation());
    }

    @EventHandler
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        CreatureSpawner cs = event.getSpawner();
        String spawnerId = cs.getPersistentDataContainer().get(spawnerIdKey, PersistentDataType.STRING);
        if (spawnerId == null) return;
        event.setCancelled(true);
        if (!tickManager.isRegistered(cs.getLocation())) {
            SpawnerData data = spawnerConfig.getSpawner(spawnerId);
            int delay = data != null ? data.getDelay() : cs.getDelay();
            tickManager.register(cs.getLocation(), delay);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.SPAWNER) return;
        Player player = event.getPlayer();
        if (!player.hasPermission("wspawner.admin")) return;
        BlockState state = block.getState();
        if (!(state instanceof CreatureSpawner)) return;
        CreatureSpawner cs = (CreatureSpawner) state;
        String spawnerId = cs.getPersistentDataContainer().get(spawnerIdKey, PersistentDataType.STRING);
        if (spawnerId == null) { player.sendMessage(messageManager.get("spawner-not-managed")); return; }
        SpawnerData data = spawnerConfig.getSpawner(spawnerId);
        if (data == null) { player.sendMessage(messageManager.get("spawner-data-not-found", "id", spawnerId)); return; }
        event.setCancelled(true);
        openEditorSpawnerIds.put(player.getUniqueId(), spawnerId);
        editorMenu.open(player, data);
    }

    // ---- PDC helpers ----
    private <T> void copyPDC(CreatureSpawner src, CreatureSpawner dst, NamespacedKey key,
                              org.bukkit.persistence.PersistentDataType<T, T> type) {
        T val = src.getPersistentDataContainer().get(key, type);
        if (val != null) dst.getPersistentDataContainer().set(key, type, val);
    }

    private void copyPDCInt(CreatureSpawner src, CreatureSpawner dst, NamespacedKey key) {
        Integer val = src.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        if (val != null) dst.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, val);
    }
}
