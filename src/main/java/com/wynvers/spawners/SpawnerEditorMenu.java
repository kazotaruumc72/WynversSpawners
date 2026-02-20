package com.wynvers.spawners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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

    private static final String MENU_TITLE = ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "\u2699 Admin Spawner Editor";

    private static final Map<Integer, String[]> SLOT_FIELDS = new HashMap<>();

    static {
        SLOT_FIELDS.put(10, new String[]{"delay",                "Delay (ticks)"});
        SLOT_FIELDS.put(13, new String[]{"required-player-range","Required Player Range"});
        SLOT_FIELDS.put(28, new String[]{"min-radius",           "Min Radius"});
        SLOT_FIELDS.put(30, new String[]{"max-radius",           "Max Radius"});
        SLOT_FIELDS.put(32, new String[]{"min-amount",           "Min Amount"});
        SLOT_FIELDS.put(34, new String[]{"max-amount",           "Max Amount"});
    }

    private final WynversSpawners plugin;
    private final Map<UUID, String[]> pendingEdits = new HashMap<>();

    public SpawnerEditorMenu(WynversSpawners plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, SpawnerData data) {
        Inventory inv = Bukkit.createInventory(null, 54, MENU_TITLE);

        ItemStack pane = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, pane);

        String mobType = data.isMythicMob() ? "mm:" + data.getMythicMobType() : data.getEntityType().name();
        inv.setItem(4, makeItem(Material.SPAWNER,
                ChatColor.GOLD + "" + ChatColor.BOLD + data.getId(),
                ChatColor.GRAY + "Type: " + ChatColor.WHITE + mobType,
                ChatColor.GRAY + "Display: " + data.getDisplayName()));

        buildButton(inv, 10, data, "delay",                 "Delay (ticks)",           Material.CLOCK);
        buildButton(inv, 13, data, "required-player-range", "Required Player Range",   Material.ENDER_EYE);
        buildButton(inv, 28, data, "min-radius",            "Min Radius",              Material.CYAN_DYE);
        buildButton(inv, 30, data, "max-radius",            "Max Radius",              Material.BLUE_DYE);
        buildButton(inv, 32, data, "min-amount",            "Min Amount",              Material.GREEN_DYE);
        buildButton(inv, 34, data, "max-amount",            "Max Amount",              Material.LIME_DYE);

        inv.setItem(49, makeItem(Material.BARRIER, ChatColor.RED + "Fermer"));
        player.openInventory(inv);
    }

    private void buildButton(Inventory inv, int slot, SpawnerData data, String field, String label, Material icon) {
        int current = getCurrentValue(data, field);
        inv.setItem(slot, makeItem(icon,
                ChatColor.YELLOW + label,
                ChatColor.GRAY + "Valeur actuelle: " + ChatColor.WHITE + current,
                ChatColor.AQUA + "Clic gauche " + ChatColor.GRAY + "pour modifier"));
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
        if (!event.getView().getTitle().equals(MENU_TITLE)) return;

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        if (slot == 49) { player.closeInventory(); return; }

        String[] fieldInfo = SLOT_FIELDS.get(slot);
        if (fieldInfo == null) return;

        String spawnerId = plugin.getOpenEditorSpawnerId(player.getUniqueId());
        if (spawnerId == null) return;

        player.closeInventory();
        pendingEdits.put(player.getUniqueId(), new String[]{spawnerId, fieldInfo[0], fieldInfo[1]});
        player.sendMessage(ChatColor.GOLD + "[WynversSpawners] " + ChatColor.WHITE
                + "Entrez la nouvelle valeur pour " + ChatColor.YELLOW + fieldInfo[1]
                + ChatColor.GRAY + " (ou tapez " + ChatColor.RED + "annuler" + ChatColor.GRAY + ") :");
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
            player.sendMessage(ChatColor.GRAY + "Modification annul\u00e9e.");
            return;
        }

        int value;
        try {
            value = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Valeur invalide ! Entrez un nombre entier.");
            return;
        }
        if (value < 0) {
            player.sendMessage(ChatColor.RED + "La valeur doit \u00eatre \u00e9gale ou sup\u00e9rieure \u00e0 0.");
            return;
        }

        String spawnerId = info[0];
        String fieldKey  = info[1];
        String label     = info[2];

        SpawnerData data = plugin.getSpawnerConfig().getSpawner(spawnerId);
        if (data == null) { player.sendMessage(ChatColor.RED + "Spawner introuvable : " + spawnerId); return; }

        applyValue(data, fieldKey, value);

        Bukkit.getScheduler().runTask(plugin, () -> {
            FileConfiguration config = plugin.getConfig();
            plugin.getSpawnerConfig().saveField(config, spawnerId, fieldKey, value);
            plugin.saveConfig();
            player.sendMessage(ChatColor.GREEN + "[WynversSpawners] " + ChatColor.WHITE
                    + label + ChatColor.GRAY + " mis \u00e0 jour \u00e0 " + ChatColor.YELLOW + value
                    + ChatColor.GRAY + " pour " + ChatColor.WHITE + spawnerId
                    + ChatColor.GRAY + " et sauvegard\u00e9 dans config.yml.");
            plugin.getEditorMenu().open(player, data);
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
