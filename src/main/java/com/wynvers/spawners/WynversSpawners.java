package com.wynvers.spawners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public class WynversSpawners extends JavaPlugin implements Listener {

    private SpawnerConfig spawnerConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        spawnerConfig = new SpawnerConfig(getLogger());
        spawnerConfig.loadSpawners(getConfig());

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

    private void handleList(CommandSender sender) {
        Map<String, SpawnerData> all = spawnerConfig.getAllSpawners();
        if (all.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No spawners configured.");
            return;
        }
        sender.sendMessage(ChatColor.GREEN + "Available spawners:");
        for (Map.Entry<String, SpawnerData> entry : all.entrySet()) {
            sender.sendMessage(ChatColor.GRAY + " - " + ChatColor.WHITE + entry.getKey()
                    + ChatColor.GRAY + " (" + entry.getValue().getEntityType().name() + ")");
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
                    placedSpawner.update();
                }
            }
        }
    }
}
