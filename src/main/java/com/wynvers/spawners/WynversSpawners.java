package com.wynvers.spawners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class WynversSpawners extends JavaPlugin implements Listener {

    private static final double BLOCK_CENTER_OFFSET = 0.5;

    private SpawnerConfig spawnerConfig;
    private NamespacedKey mythicMobTypeKey;
    private boolean mythicMobsEnabled = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        mythicMobTypeKey = new NamespacedKey(this, "mythic_mob_type");
        spawnerConfig = new SpawnerConfig(getLogger());
        spawnerConfig.loadSpawners(getConfig());

        mythicMobsEnabled = Bukkit.getPluginManager().getPlugin("MythicMobs") != null;
        if (mythicMobsEnabled) {
            getLogger().info("MythicMobs detected! Custom mob spawners enabled.");
        }

        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("WynversSpawners enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("WynversSpawners disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("spawner")) {
            return false;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /spawner <give|list|reload>");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list":
                handleList(sender);
                return true;
            case "give":
                handleGive(sender, args);
                return true;
            case "reload":
                handleReload(sender);
                return true;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown sub-command: " + subCommand);
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("spawner")) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("give", "list", "reload");
            String input = args[0].toLowerCase();
            for (String sub : subCommands) {
                if (sub.startsWith(input)) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            String input = args[1].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                String name = player.getName();
                if (name.toLowerCase().startsWith(input)) {
                    completions.add(name);
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            String input = args[2].toLowerCase();
            for (String id : spawnerConfig.getAllSpawners().keySet()) {
                String lowerid = id.toLowerCase();
                if (lowerid.startsWith(input)) {
                    completions.add(id);
                }
            }
        }

        Collections.sort(completions);
        return completions;
    }

    private void handleList(CommandSender sender) {
        Map<String, SpawnerData> all = spawnerConfig.getAllSpawners();
        if (all.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No spawners configured.");
            return;
        }
        sender.sendMessage(ChatColor.GREEN + "Available spawners:");
        for (Map.Entry<String, SpawnerData> entry : all.entrySet()) {
            SpawnerData data = entry.getValue();
            String typeDisplay = data.isMythicMob()
                    ? "mm:" + data.getMythicMobType()
                    : data.getEntityType().name();
            sender.sendMessage(ChatColor.GRAY + " - " + ChatColor.WHITE + entry.getKey()
                    + ChatColor.GRAY + " (" + typeDisplay + ")");
        }
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /spawner give <player> <spawner_id>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
            return;
        }

        String spawnerId = args[2].toLowerCase();
        SpawnerData data = spawnerConfig.getSpawner(spawnerId);
        if (data == null) {
            sender.sendMessage(ChatColor.RED + "Unknown spawner: " + spawnerId);
            return;
        }

        ItemStack item = createSpawnerItem(data);
        target.getInventory().addItem(item);
        sender.sendMessage(ChatColor.GREEN + "Gave " + data.getDisplayName()
                + ChatColor.GREEN + " to " + target.getName());
    }

    private void handleReload(CommandSender sender) {
        reloadConfig();
        spawnerConfig.loadSpawners(getConfig());
        sender.sendMessage(ChatColor.GREEN + "WynversSpawners config reloaded!");
    }

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
                    spawner.setMinSpawnDelay(data.getMinSpawnDelay());
                    spawner.setMaxSpawnDelay(data.getMaxSpawnDelay());
                    spawner.setSpawnCount(data.getSpawnCount());
                    spawner.setSpawnRange(data.getSpawnRange());
                    spawner.setRequiredPlayerRange(data.getRequiredPlayerRange());
                    if (data.isMythicMob()) {
                        spawner.getPersistentDataContainer().set(
                                mythicMobTypeKey, PersistentDataType.STRING, data.getMythicMobType());
                    }
                    blockMeta.setBlockState(spawner);
                }
            }

            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item.getType() != Material.SPAWNER) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return;
        }

        if (meta instanceof BlockStateMeta) {
            BlockStateMeta blockMeta = (BlockStateMeta) meta;
            BlockState itemState = blockMeta.getBlockState();
            if (itemState instanceof CreatureSpawner) {
                CreatureSpawner itemSpawner = (CreatureSpawner) itemState;
                BlockState placedState = event.getBlockPlaced().getState();
                if (placedState instanceof CreatureSpawner) {
                    CreatureSpawner placedSpawner = (CreatureSpawner) placedState;
                    placedSpawner.setSpawnedType(itemSpawner.getSpawnedType());
                    placedSpawner.setDelay(itemSpawner.getDelay());
                    placedSpawner.setMinSpawnDelay(itemSpawner.getMinSpawnDelay());
                    placedSpawner.setMaxSpawnDelay(itemSpawner.getMaxSpawnDelay());
                    placedSpawner.setSpawnCount(itemSpawner.getSpawnCount());
                    placedSpawner.setSpawnRange(itemSpawner.getSpawnRange());
                    placedSpawner.setRequiredPlayerRange(itemSpawner.getRequiredPlayerRange());
                    String mmType = itemSpawner.getPersistentDataContainer()
                            .get(mythicMobTypeKey, PersistentDataType.STRING);
                    if (mmType != null) {
                        placedSpawner.getPersistentDataContainer().set(
                                mythicMobTypeKey, PersistentDataType.STRING, mmType);
                    }
                    placedSpawner.update();
                }
            }
        }
    }

    @EventHandler
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        CreatureSpawner spawner = event.getSpawner();
        String mmType = spawner.getPersistentDataContainer()
                .get(mythicMobTypeKey, PersistentDataType.STRING);
        if (mmType == null) {
            return;
        }
        if (!mythicMobsEnabled) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        try {
            io.lumine.mythic.bukkit.MythicBukkit.inst().getMobManager()
                    .spawnMob(mmType, spawner.getLocation().add(BLOCK_CENTER_OFFSET, 0, BLOCK_CENTER_OFFSET));
        } catch (Exception e) {
            getLogger().warning("Failed to spawn MythicMobs mob '" + mmType + "': " + e.getMessage());
        }
    }
}
