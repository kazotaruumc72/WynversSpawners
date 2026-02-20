package com.wynvers.spawners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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

public class WynversSpawners extends JavaPlugin implements Listener {

    private SpawnerConfig spawnerConfig;
    private SpawnerEditorMenu editorMenu;
    private SpawnerTickManager tickManager;

    private NamespacedKey mythicMobTypeKey;
    private NamespacedKey spawnerIdKey;
    private NamespacedKey minRadiusKey;
    private NamespacedKey maxRadiusKey;
    private NamespacedKey minAmountKey;
    private NamespacedKey maxAmountKey;

    private boolean mythicMobsEnabled = false;
    private final Map<UUID, String> openEditorSpawnerIds = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        mythicMobTypeKey = new NamespacedKey(this, "mythic_mob_type");
        spawnerIdKey     = new NamespacedKey(this, "spawner_id");
        minRadiusKey     = new NamespacedKey(this, "min_radius");
        maxRadiusKey     = new NamespacedKey(this, "max_radius");
        minAmountKey     = new NamespacedKey(this, "min_amount");
        maxAmountKey     = new NamespacedKey(this, "max_amount");

        spawnerConfig = new SpawnerConfig(getLogger());
        spawnerConfig.loadSpawners(getConfig());

        editorMenu  = new SpawnerEditorMenu(this);
        tickManager = new SpawnerTickManager(this);

        mythicMobsEnabled = Bukkit.getPluginManager().getPlugin("MythicMobs") != null;
        if (mythicMobsEnabled) getLogger().info("MythicMobs detected!");

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(editorMenu, this);

        tickManager.start();
        getLogger().info("WynversSpawners enabled!");
    }

    @Override
    public void onDisable() {
        tickManager.stop();
        getLogger().info("WynversSpawners disabled!");
    }

    // ---- Public accessors ----
    public SpawnerConfig getSpawnerConfig()                    { return spawnerConfig; }
    public SpawnerEditorMenu getEditorMenu()                   { return editorMenu; }
    public SpawnerTickManager getTickManager()                 { return tickManager; }
    public boolean isMythicMobsEnabled()                      { return mythicMobsEnabled; }
    public NamespacedKey getMythicMobTypeKey()                 { return mythicMobTypeKey; }
    public NamespacedKey getSpawnerIdKey()                     { return spawnerIdKey; }
    public NamespacedKey getMinRadiusKey()                     { return minRadiusKey; }
    public NamespacedKey getMaxRadiusKey()                     { return maxRadiusKey; }
    public NamespacedKey getMinAmountKey()                     { return minAmountKey; }
    public NamespacedKey getMaxAmountKey()                     { return maxAmountKey; }
    public String getOpenEditorSpawnerId(UUID uuid)            { return openEditorSpawnerIds.get(uuid); }
    public void setOpenEditorSpawnerId(UUID uuid, String id)   { openEditorSpawnerIds.put(uuid, id); }

    // ---- Commands ----
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("spawner")) return false;
        if (args.length == 0) { sender.sendMessage(ChatColor.YELLOW + "Usage: /spawner <give|list|reload>"); return true; }
        switch (args[0].toLowerCase()) {
            case "list":   handleList(sender);       return true;
            case "give":   handleGive(sender, args); return true;
            case "reload": handleReload(sender);     return true;
            default: sender.sendMessage(ChatColor.RED + "Unknown sub-command: " + args[0]); return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("spawner")) return Collections.emptyList();
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : Arrays.asList("give", "list", "reload"))
                if (sub.startsWith(args[0].toLowerCase())) completions.add(sub);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) completions.add(p.getName());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            for (String id : spawnerConfig.getAllSpawners().keySet())
                if (id.toLowerCase().startsWith(args[2].toLowerCase())) completions.add(id);
        }
        Collections.sort(completions);
        return completions;
    }

    private void handleList(CommandSender sender) {
        Map<String, SpawnerData> all = spawnerConfig.getAllSpawners();
        if (all.isEmpty()) { sender.sendMessage(ChatColor.YELLOW + "No spawners configured."); return; }
        sender.sendMessage(ChatColor.GREEN + "Available spawners:");
        for (Map.Entry<String, SpawnerData> entry : all.entrySet()) {
            SpawnerData data = entry.getValue();
            String type = data.isMythicMob() ? "mm:" + data.getMythicMobType() : data.getEntityType().name();
            sender.sendMessage(ChatColor.GRAY + " - " + ChatColor.WHITE + entry.getKey() + ChatColor.GRAY + " (" + type + ")");
        }
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(ChatColor.RED + "Usage: /spawner give <player> <spawner_id>"); return; }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]); return; }
        SpawnerData data = spawnerConfig.getSpawner(args[2].toLowerCase());
        if (data == null) { sender.sendMessage(ChatColor.RED + "Unknown spawner: " + args[2]); return; }
        target.getInventory().addItem(createSpawnerItem(data));
        sender.sendMessage(ChatColor.GREEN + "Gave " + data.getDisplayName() + ChatColor.GREEN + " to " + target.getName());
    }

    private void handleReload(CommandSender sender) {
        reloadConfig();
        spawnerConfig.loadSpawners(getConfig());
        sender.sendMessage(ChatColor.GREEN + "WynversSpawners config reloaded!");
    }

    // ---- Item creation ----
    private ItemStack createSpawnerItem(SpawnerData data) {
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
                    // Afficher VILLAGER dans le spawner en main (mob neutre visible)
                    spawner.setSpawnedType(EntityType.VILLAGER);
                    spawner.setDelay(data.getDelay());
                    spawner.setMinSpawnDelay(data.getDelay());
                    spawner.setMaxSpawnDelay(data.getDelay());
                    spawner.setRequiredPlayerRange(data.getRequiredPlayerRange());
                    // Stocker toutes les clés PDC
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

        // Vérifier que c'est un spawner WynversSpawners
        String spawnerId = itemSpawner.getPersistentDataContainer().get(spawnerIdKey, PersistentDataType.STRING);
        if (spawnerId == null) return;

        BlockState placedState = event.getBlockPlaced().getState();
        if (!(placedState instanceof CreatureSpawner)) return;
        CreatureSpawner placedSpawner = (CreatureSpawner) placedState;

        // Copier les propriétés vanilla
        placedSpawner.setSpawnedType(EntityType.VILLAGER);
        placedSpawner.setDelay(itemSpawner.getDelay());
        placedSpawner.setMinSpawnDelay(itemSpawner.getMinSpawnDelay());
        placedSpawner.setMaxSpawnDelay(itemSpawner.getMaxSpawnDelay());
        placedSpawner.setRequiredPlayerRange(itemSpawner.getRequiredPlayerRange());

        // Copier toutes les clés PDC
        copyPDC(itemSpawner, placedSpawner, spawnerIdKey, PersistentDataType.STRING);
        copyPDC(itemSpawner, placedSpawner, mythicMobTypeKey, PersistentDataType.STRING);
        copyPDCInt(itemSpawner, placedSpawner, minRadiusKey);
        copyPDCInt(itemSpawner, placedSpawner, maxRadiusKey);
        copyPDCInt(itemSpawner, placedSpawner, minAmountKey);
        copyPDCInt(itemSpawner, placedSpawner, maxAmountKey);
        placedSpawner.update();

        // Enregistrer dans le tick manager
        SpawnerData data = spawnerConfig.getSpawner(spawnerId);
        int delay = data != null ? data.getDelay() : itemSpawner.getDelay();
        tickManager.register(event.getBlockPlaced().getLocation(), delay);
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
    }

    /** Bloquer complètement le spawn vanilla pour nos spawners (le scheduler custom prend le relais) */
    @EventHandler
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        CreatureSpawner cs = event.getSpawner();
        String spawnerId = cs.getPersistentDataContainer().get(spawnerIdKey, PersistentDataType.STRING);
        if (spawnerId == null) return;
        // Annuler le spawn vanilla : notre scheduler s'en occupe
        event.setCancelled(true);
        // Si le spawner vient d'être posé mais pas encore enregistré (ex: serveur restart)
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
        if (spawnerId == null) { player.sendMessage(ChatColor.RED + "Ce spawner n'est pas g\u00e9r\u00e9 par WynversSpawners."); return; }
        SpawnerData data = spawnerConfig.getSpawner(spawnerId);
        if (data == null) { player.sendMessage(ChatColor.RED + "SpawnerData introuvable pour l'ID: " + spawnerId); return; }
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
