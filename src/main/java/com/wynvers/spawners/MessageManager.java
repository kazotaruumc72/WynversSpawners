package com.wynvers.spawners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * Manages all plugin messages loaded from messages.yml.
 * Messages use MiniMessage format and are serialized to legacy strings for Spigot compatibility.
 */
public class MessageManager {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER =
            LegacyComponentSerializer.builder()
                    .hexColors()
                    .useUnusualXRepeatedCharacterHexFormat()
                    .build();

    private final JavaPlugin plugin;
    private final Logger logger;
    private YamlConfiguration messages;

    public MessageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        reload();
    }

    /**
     * (Re)load messages.yml from the plugin data folder, copying defaults if needed.
     */
    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(file);

        // Merge defaults from the jar so new keys are always available
        InputStream defaults = plugin.getResource("messages.yml");
        if (defaults != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaults, StandardCharsets.UTF_8));
            messages.setDefaults(defConfig);
        }
        logger.info("messages.yml loaded.");
    }

    /**
     * Get the raw MiniMessage string for a key (before placeholder replacement).
     */
    public String getRaw(String key) {
        return messages.getString(key, "<red>Missing message: " + key);
    }

    /**
     * Parse a MiniMessage string into a legacy-formatted string (§-codes).
     * Placeholders ({key}) should be replaced BEFORE calling this method.
     */
    public static String toLegacy(String miniMessageText) {
        Component component = MINI_MESSAGE.deserialize(miniMessageText);
        return LEGACY_SERIALIZER.serialize(component);
    }

    /**
     * Get a message as a legacy string, with placeholder replacement.
     *
     * @param key          The message key in messages.yml
     * @param replacements Pairs of placeholder/value: "player", "Steve", "id", "zombie_spawner"
     */
    public String get(String key, String... replacements) {
        String raw = getRaw(key);
        raw = applyReplacements(raw, replacements);
        return toLegacy(raw);
    }

    /**
     * Parse a MiniMessage string (e.g. from config.yml item display name) into a legacy string.
     */
    public static String parseMiniMessage(String miniMessageText) {
        return toLegacy(miniMessageText);
    }

    /**
     * Replace {key} placeholders in a string.
     */
    private static String applyReplacements(String text, String... replacements) {
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return text;
    }
}
