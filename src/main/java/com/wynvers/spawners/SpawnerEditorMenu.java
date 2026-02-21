package com.wynvers.spawners;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SpawnerEditorMenu implements Listener {

    private static final Map<Integer, String[]> SLOT_FIELDS = new HashMap<>();

    static {
        SLOT_FIELDS.put(10, new String[]{"delay",                "field-delay"});
        SLOT_FIELDS.put(13, new String[]{"required-player-range","field-required-player-range"});
        SLOT_FIELDS.put(28, new String[]{"min-radius",           "field-min-radius"});
        SLOT_FIELDS.put(30, new String[]{"max-radius",           "field-max-radius"});
        SLOT_FIELDS.put(32, new String[]{"min-amount",           "field-min-amount"});
        SLOT_FIELDS.put(34, new String[]{"max-amount",           "field-max-amount"});
    }

    private final WSpawners plugin;
    private final Map<UUID, String[]> pendingEdits = new HashMap<>();
    private String menuTitle;

    public SpawnerEditorMenu(WSpawners plugin) {
        this.plugin = plugin;
        refreshTitle();
    }

    private void refreshTitle() {
        menuTitle = plugin.getMessageManager().get("editor-title");
    }

    private MessageManager msg() {
        return plugin.getMessageManager();
    }

    public void open(Player player, SpawnerData data) {
        refreshTitle();
        Inventory inv = Bukkit.createInventory(null, 54, menuTitle);

        ItemStack pane = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, pane);

        String mobType = data.isMythicMob() ? "mm:" + data.getMythicMobType() : data.getEntityType().name();
        inv.setItem(4, makeItem(Material.SPAWNER,
                MessageManager.parseMiniMessage("<gold><bold>" + data.getId()),
                msg().get("editor-spawner-info-type", "type", mobType),
                msg().get("editor-spawner-info-display", "name", data.getDisplayName())));

        buildButton(inv, 10, data, "delay",                 msg().get("field-delay"),                   Material.CLOCK);
        buildButton(inv, 13, data, "required-player-range", msg().get("field-required-player-range"),   Material.ENDER_EYE);
        buildButton(inv, 28, data, "min-radius",            msg().get("field-min-radius"),              Material.CYAN_DYE);
        buildButton(inv, 30, data, "max-radius",            msg().get("field-max-radius"),              Material.BLUE_DYE);
        buildButton(inv, 32, data, "min-amount",            msg().get("field-min-amount"),              Material.GREEN_DYE);
        buildButton(inv, 34, data, "max-amount",            msg().get("field-max-amount"),              Material.LIME_DYE);

        inv.setItem(49, makeItem(Material.BARRIER, msg().get("editor-close")));
        player.openInventory(inv);
    }

    private void buildButton(Inventory inv, int slot, SpawnerData data, String field, String label, Material icon) {
        int current = getCurrentValue(data, field);
        inv.setItem(slot, makeItem(icon,
                MessageManager.parseMiniMessage("<yellow>" + label),
                msg().get("editor-button-value", "value", String.valueOf(current)),
                msg().get("editor-button-click")));
    }

    private int getCurrentValue(SpawnerData data, String field) {
        switch (field) {
            case "delay":                  return data.getDelay();
            case "required-player-range":  return data.getRequiredPlayerRange();
            case "min-radius":             return data.getMinRadius();
            case "max-radius":             return data.getMaxRadius();
            case "min-amount":             return data.getMinAmount();
            case "max-amount":             return data.getMaxAmount();
            default:                       return 0;
        }
    }

    private void applyValue(SpawnerData data, String field, int value) {
        switch (field) {
            case "delay":                  data.setDelay(value); break;
            case "required-player-range":  data.setRequiredPlayerRange(value); break;
            case "min-radius":             data.setMinRadius(value); break;
            case "max-radius":             data.setMaxRadius(value); break;
            case "min-amount":             data.setMinAmount(value); break;
            case "max-amount":             data.setMaxAmount(value); break;
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (event.getView().getTitle() == null) return;
        if (!event.getView().getTitle().equals(menuTitle)) return;

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        if (slot == 49) { player.closeInventory(); return; }

        String[] fieldInfo = SLOT_FIELDS.get(slot);
        if (fieldInfo == null) return;

        String spawnerId = plugin.getOpenEditorSpawnerId(player.getUniqueId());
        if (spawnerId == null) return;

        String fieldLabel = msg().get(fieldInfo[1]);

        player.closeInventory();
        pendingEdits.put(player.getUniqueId(), new String[]{spawnerId, fieldInfo[0], fieldLabel});
        player.sendMessage(msg().get("editor-input-prompt", "field", fieldLabel));
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (!pendingEdits.containsKey(uuid)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        String input = event.getMessage().trim();
        String[] info = pendingEdits.remove(uuid);

        if (input.equalsIgnoreCase("annuler")) {
            player.sendMessage(msg().get("editor-cancelled"));
            return;
        }

        int value;
        try {
            value = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            player.sendMessage(msg().get("editor-invalid-value"));
            return;
        }
        if (value < 0) {
            player.sendMessage(msg().get("editor-negative-value"));
            return;
        }

        String spawnerId = info[0];
        String fieldKey  = info[1];
        String label     = info[2];

        SpawnerData data = plugin.getSpawnerConfig().getSpawner(spawnerId);
        if (data == null) { player.sendMessage(msg().get("editor-spawner-not-found", "id", spawnerId)); return; }

        applyValue(data, fieldKey, value);

        Bukkit.getScheduler().runTask(plugin, () -> {
            FileConfiguration config = plugin.getConfig();
            plugin.getSpawnerConfig().saveField(config, spawnerId, fieldKey, value);
            plugin.saveConfig();
            plugin.getSpawnerConfig().loadSpawners(plugin.getConfig());
            SpawnerData updatedData = plugin.getSpawnerConfig().getSpawner(spawnerId);
            if (updatedData == null) {
                plugin.getLogger().severe("Spawner introuvable après rechargement : " + spawnerId);
                player.sendMessage(msg().get("editor-error-reload"));
                return;
            }
            player.sendMessage(msg().get("editor-saved",
                    "label", label,
                    "value", String.valueOf(value),
                    "id", spawnerId));
            plugin.getEditorMenu().open(player, updatedData);
            plugin.setOpenEditorSpawnerId(player.getUniqueId(), spawnerId);
        });
    }

    private ItemStack makeItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }
}
