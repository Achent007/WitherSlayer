package be.achent.witherslayer;

import be.achent.witherslayer.Commands.WitherSlayerCommands;
import be.achent.witherslayer.Commands.WitherSlayerTabCompleter;
import be.achent.witherslayer.Events.WitherSlayerEvents;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class WitherSlayer extends JavaPlugin implements Listener {

    private static WitherSlayer plugin;

    private FileConfiguration languageConfig;
    private File languageConfigFile;
    private final Map<UUID, Double> damageMap = new HashMap<>();

    @Override
    public void onEnable() {
        plugin = this;

        saveDefaultConfig();
        reloadConfig();
        loadLanguageConfig();
        loadDamageLeaderboard();
        updateConfigFile("config.yml", "config-default.yml");
        updateConfigFile("language.yml", "language-default.yml");

        new WitherSlayerEvents(this);

        getCommand("witherslayer").setExecutor(new WitherSlayerCommands(this, damageMap));
        getCommand("witherslayer").setTabCompleter(new WitherSlayerTabCompleter());
    }

    public static WitherSlayer getInstance() {
        return plugin;
    }

    public List<String> getLanguageMessageList(String path) {
        List<String> messages = this.languageConfig.getStringList(path);
        if (!messages.isEmpty()) {
            messages.replaceAll(this::formatMessage);
            return messages;
        } else {
            getLogger().warning("Message path '" + path + "' not found in language.yml");
            return List.of("");
        }
    }

    public String getLanguageMessage(String path) {
        String message = this.languageConfig.getString(path);
        if (message != null) {
            return formatMessage(message);
        } else {
            getLogger().warning("Message path '" + path + "' not found in language.yml");
            return "";
        }
    }

    public void reloadLanguageConfig() {
        if (languageConfigFile == null) {
            languageConfigFile = new File(getDataFolder(), "language.yml");
        }
        if (!languageConfigFile.exists()) {
            saveResource("language.yml", false);
        }
        languageConfig = YamlConfiguration.loadConfiguration(languageConfigFile);

        InputStream defaultStream = getResource("language-default.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream));
            languageConfig.setDefaults(defaultConfig);
        }
    }

    private void loadLanguageConfig() {
        File languageFile = new File(getDataFolder(), "language.yml");
        if (!languageFile.exists()) {
            saveResource("language.yml", false);
        }
        languageConfig = YamlConfiguration.loadConfiguration(languageFile);
    }

    public String formatMessage(String message) {
        if (message == null) {
            return "";
        }
        String prefix = this.languageConfig.getString("messages.prefix");
        message = message.replace("{prefix}", prefix);
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private void updateConfigFile(String fileName, String defaultFileName) {
        File configFile = new File(getDataFolder(), fileName);
        if (!configFile.exists()) {
            saveResource(fileName, false);
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        InputStream defaultConfigStream = getResource(defaultFileName);
        if (defaultConfigStream == null) {
            getLogger().log(Level.SEVERE, "Default configuration file " + defaultFileName + " not found.");
            return;
        }

        FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultConfigStream));
        for (String key : defaultConfig.getKeys(true)) {
            if (!config.contains(key)) {
                config.set(key, defaultConfig.get(key));
            }
        }

        try {
            config.save(configFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Map<UUID, Double> getDamageMap() {
        return damageMap;
    }

    public void addDamage(UUID playerId, double damage) {
        damageMap.put(playerId, damageMap.getOrDefault(playerId, 0.0) + damage);
    }

    public void clearDamageMap() {
        damageMap.clear();
    }

    public void saveDamageLeaderboard() {
        File leaderboardFile = new File(getDataFolder(), "damageleaderboard.yml");
        FileConfiguration leaderboardConfig = YamlConfiguration.loadConfiguration(leaderboardFile);

        for (Map.Entry<UUID, Double> entry : damageMap.entrySet()) {
            leaderboardConfig.set(entry.getKey().toString(), entry.getValue());
        }

        try {
            leaderboardConfig.save(leaderboardFile);
        } catch (IOException e) {
            getLogger().severe("Could not save damage leaderboard to damageleaderboard.yml");
            e.printStackTrace();
        }
    }

    public void loadDamageLeaderboard() {
        File leaderboardFile = new File(getDataFolder(), "damageleaderboard.yml");
        if (!leaderboardFile.exists()) {
            return;
        }

        FileConfiguration leaderboardConfig = YamlConfiguration.loadConfiguration(leaderboardFile);
        for (String key : leaderboardConfig.getKeys(false)) {
            UUID playerId = UUID.fromString(key);
            double damage = leaderboardConfig.getDouble(key);
            damageMap.put(playerId, damage);
        }
    }
}
