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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
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
import java.util.Random;
import java.util.UUID;

public class WynversSpawners extends JavaPlugin implements Listener {

    private static final double BLOCK_CENTER_OFFSET = 0.5;
    private final Random random = new Random();

    private SpawnerConfig spawnerConfig;
    private SpawnerEditorMenu editorMenu;
    private NamespacedKey mythicMobTypeKey;
    private NamespacedKey spawnerIdKey;
    private NamespacedKey minRadiusKey;
    private NamespacedKey maxRadiusKey;
    private NamespacedKey minAmountKey;
    private NamespacedKey maxAmountKey;
    private boolean mythicMobsEnabled = false;

    // Track which spawner ID each player has open in the editor
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

        editorMenu = new SpawnerEditorMenu(this);

        mythicMobsEnabled = Bukkit.getPluginManager().getPlugin("MythicMobs") != null;
        if (mythicMobsEnabled) getLogger().info("MythicMobs detected! Custom mob spawners enabled.");

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(editorMenu, this);
        getLogger().info("WynversSpawners enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("WynversSpawners disabled!");
    }

    // ---- Public accessors for SpawnerEditorMenu ----
    public SpawnerConfig getSpawnerConfig() { return spawnerConfig; }
    public SpawnerEditorMenu getEditorMenu() { return editorMenu; }
    public String getOpenEditorSpawnerId(UUID uuid) { return openEditorSpawnerIds.get(uuid); }
    public void setOpenEditorSpawnerId(UUID uuid, String id) { openEditorSpawnerIds.put(uuid, id); }

    // ---- Commands ----
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("spawner")) return false;

        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /spawner <give|list|reload>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "list":   handleList(sender);       return true;
            case "give":   handleGive(sender, args); return true;
            case "reload": handleReload(sender);     return true;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown sub-command: " + args[0]);
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("spawner")) return Collections.emptyList();

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            for (String sub : Arrays.asList("give", "list", "reload")) {
                if (sub.startsWith(args[0].toLowerCase())) completions.add(sub);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) completions.add(p.getName());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            for (String id : spawnerConfig.getAllSpawners().keySet()) {
                if (id.toLowerCase().startsWith(args[2].toLowerCase())) completions.add(id);
            }
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
            String typeDisplay = data.isMythicMob() ? "mm:" + data.getMythicMobType() : data.getEntityType().name();
            sender.sendMessage(ChatColor.GRAY + " - " + ChatColor.WHITE + entry.getKey()
                    + ChatColor.GRAY + " (" + typeDisplay + ")");
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

    // ---- Spawner item creation ----
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
                    spawner.setSpawnedType(data.getEntityType());
                    spawner.setDelay(data.getDelay());
                    spawner.setMinSpawnDelay(data.getDelay());
                    spawner.setMaxSpawnDelay(data.getDelay());
                    spawner.setSpawnCount(data.getSpawnCount());
                    spawner.setSpawnRange(data.getSpawnRange());
                    spawner.setRequiredPlayerRange(data.getRequiredPlayerRange());

                    // Store spawner ID so we can look it up in editor
                    spawner.getPersistentDataContainer().set(spawnerIdKey, PersistentDataType.STRING, data.getId());

                    if (data.isMythicMob()) {
                        spawner.getPersistentDataContainer().set(mythicMobTypeKey, PersistentDataType.STRING, data.getMythicMobType());
                    }
                    if (data.getMinRadius() > 0) {
                        spawner.getPersistentDataContainer().set(minRadiusKey, PersistentDataType.INTEGER, data.getMinRadius());
                    }
                    if (data.getMaxRadius() > 0) {
                        spawner.getPersistentDataContainer().set(maxRadiusKey, PersistentDataType.INTEGER, data.getMaxRadius());
                    }
                    if (data.getMinAmount() > 0) {
                        spawner.getPersistentDataContainer().set(minAmountKey, PersistentDataType.INTEGER, data.getMinAmount());
                    }
                    if (data.getMaxAmount() > 0) {
                        spawner.getPersistentDataContainer().set(maxAmountKey, PersistentDataType.INTEGER, data.getMaxAmount());
                    }
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
        BlockState placedState = event.getBlockPlaced().getState();
        if (!(placedState instanceof CreatureSpawner)) return;

        CreatureSpawner placedSpawner = (CreatureSpawner) placedState;
        placedSpawner.setSpawnedType(itemSpawner.getSpawnedType());
        placedSpawner.setDelay(itemSpawner.getDelay());
        placedSpawner.setMinSpawnDelay(itemSpawner.getMinSpawnDelay());
        placedSpawner.setMaxSpawnDelay(itemSpawner.getMaxSpawnDelay());
        placedSpawner.setSpawnCount(itemSpawner.getSpawnCount());
        placedSpawner.setSpawnRange(itemSpawner.getSpawnRange());
        placedSpawner.setRequiredPlayerRange(itemSpawner.getRequiredPlayerRange());

        // Copy all PDC keys
        copyPDC(itemSpawner, placedSpawner, spawnerIdKey, PersistentDataType.STRING);
        copyPDC(itemSpawner, placedSpawner, mythicMobTypeKey, PersistentDataType.STRING);
        copyPDCInt(itemSpawner, placedSpawner, minRadiusKey);
        copyPDCInt(itemSpawner, placedSpawner, maxRadiusKey);
        copyPDCInt(itemSpawner, placedSpawner, minAmountKey);
        copyPDCInt(itemSpawner, placedSpawner, maxAmountKey);
        placedSpawner.update();
    }

    /** Admin right-click on a placed spawner block -> open editor */
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

        // Identify which SpawnerData this block corresponds to
        String spawnerId = cs.getPersistentDataContainer().get(spawnerIdKey, PersistentDataType.STRING);
        if (spawnerId == null) {
            player.sendMessage(ChatColor.RED + "Ce spawner n'est pas g\u00e9r\u00e9 par WynversSpawners.");
            return;
        }

        SpawnerData data = spawnerConfig.getSpawner(spawnerId);
        if (data == null) {
            player.sendMessage(ChatColor.RED + "SpawnerData introuvable pour l'ID: " + spawnerId);
            return;
        }

        event.setCancelled(true);
        openEditorSpawnerIds.put(player.getUniqueId(), spawnerId);
        editorMenu.open(player, data);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        CreatureSpawner spawner = event.getSpawner();
        String mmType = spawner.getPersistentDataContainer().get(mythicMobTypeKey, PersistentDataType.STRING);

        if (mmType == null || mmType.isEmpty()) return;

        if (!mythicMobsEnabled) {
            event.setCancelled(true);
            getLogger().warning("Spawner with MythicMob type '" + mmType + "' attempted to spawn, but MythicMobs is not loaded!");
            return;
        }

        event.setCancelled(true);

        Integer minRadius = spawner.getPersistentDataContainer().get(minRadiusKey, PersistentDataType.INTEGER);
        Integer maxRadius = spawner.getPersistentDataContainer().get(maxRadiusKey, PersistentDataType.INTEGER);
        Integer minAmount = spawner.getPersistentDataContainer().get(minAmountKey, PersistentDataType.INTEGER);
        Integer maxAmount = spawner.getPersistentDataContainer().get(maxAmountKey, PersistentDataType.INTEGER);

        int actualMinRadius = (minRadius != null && minRadius > 0) ? minRadius : 0;
        int actualMaxRadius = (maxRadius != null && maxRadius > 0) ? maxRadius : 0;
        int actualMinAmount = (minAmount != null && minAmount > 0) ? minAmount : 1;
        int actualMaxAmount = (maxAmount != null && maxAmount > 0) ? maxAmount : 1;

        int spawnAmount = (actualMaxAmount > actualMinAmount)
                ? actualMinAmount + random.nextInt(actualMaxAmount - actualMinAmount + 1)
                : actualMinAmount;

        for (int i = 0; i < spawnAmount; i++) {
            try {
                double offsetX = BLOCK_CENTER_OFFSET;
                double offsetZ = BLOCK_CENTER_OFFSET;

                if (actualMaxRadius > 0) {
                    int radius = (actualMaxRadius > actualMinRadius)
                            ? actualMinRadius + random.nextInt(actualMaxRadius - actualMinRadius + 1)
                            : actualMinRadius;
                    double angle    = random.nextDouble() * 2 * Math.PI;
                    double distance = Math.sqrt(random.nextDouble()) * radius;
                    offsetX = BLOCK_CENTER_OFFSET + Math.cos(angle) * distance;
                    offsetZ = BLOCK_CENTER_OFFSET + Math.sin(angle) * distance;
                }

                io.lumine.mythic.bukkit.MythicBukkit.inst().getMobManager()
                        .spawnMob(mmType, spawner.getLocation().add(offsetX, 0, offsetZ));
            } catch (Exception e) {
                getLogger().warning("Failed to spawn MythicMobs mob '" + mmType + "': " + e.getMessage());
            }
        }
    }

    // ---- PDC helpers ----
    private <T> void copyPDC(CreatureSpawner src, CreatureSpawner dst, NamespacedKey key, org.bukkit.persistence.PersistentDataType<T, T> type) {
        T val = src.getPersistentDataContainer().get(key, type);
        if (val != null) dst.getPersistentDataContainer().set(key, type, val);
    }

    private void copyPDCInt(CreatureSpawner src, CreatureSpawner dst, NamespacedKey key) {
        Integer val = src.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        if (val != null) dst.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, val);
    }
}
