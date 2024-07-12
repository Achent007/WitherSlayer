package be.achent.witherslayer;

import be.achent.witherslayer.Commands.WitherSlayerCommands;
import be.achent.witherslayer.Commands.WitherSlayerTabCompleter;
import be.achent.witherslayer.Events.WitherSlayerEvents;
import be.achent.witherslayer.Events.WitherSlayerRespawnEvent;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class WitherSlayer extends JavaPlugin implements Listener {

    private static WitherSlayer plugin;

    private FileConfiguration languageConfig;
    private File languageConfigFile;
    private File leaderboardFile;
    private FileConfiguration leaderboardConfig;
    private WitherSlayerRespawnEvent witherRespawnEvent;
    private final Map<UUID, Double> damageMap = new HashMap<>();

    @Override
    public void onEnable() {
        plugin = this;

        saveDefaultConfig();
        reloadConfig();
        loadLanguageConfig();
        loadLeaderboardConfig();
        updateConfigFile("config.yml", "config-default.yml");
        updateConfigFile("language.yml", "language-default.yml");

        new WitherSlayerEvents(this);
        witherRespawnEvent = new WitherSlayerRespawnEvent(this);
        witherRespawnEvent.runTaskTimer(this, 0L, 20L);

        getCommand("witherslayer").setExecutor(new WitherSlayerCommands(this, damageMap, witherRespawnEvent));
        getCommand("witherslayer").setTabCompleter(new WitherSlayerTabCompleter());
    }

    public static WitherSlayer getInstance() {
        return plugin;
    }

    public void reloadConfigWithErrors(CommandSender sender) {
        try {
            reloadConfig();
            reloadLanguageConfig();
            loadLeaderboardConfig();
            updateConfigFile("config.yml", "config-default.yml");
            updateConfigFile("language.yml", "language-default.yml");

            String spawnTimes = getConfig().getString("wither respawn.spawntimes");
            for (String time : spawnTimes.split(",")) {
                LocalTime.parse(time.trim(), DateTimeFormatter.ofPattern("HH:mm"));
            }

            sender.sendMessage(getLanguageMessage("messages.Reloaded"));
        } catch (DateTimeParseException e) {
            sender.sendMessage(getLanguageMessage("messages.Reload failed"));
            getLogger().severe("Invalid time format in configuration: " + e.getParsedString());
        } catch (Exception e) {
            sender.sendMessage(getLanguageMessage("messages.Reload failed"));
            getLogger().severe("Failed to reload the configuration: " + e.getMessage());
        }
    }

    public List<String> getLanguageMessageList(String path) {
        List<String> messages = this.languageConfig.getStringList(path);
        if (!messages.isEmpty()) {
            messages.replaceAll(this::formatMessage);
            return messages;
        } else {
            getLogger().warning("Le chemin de message '" + path + "' n'a pas été trouvé dans language.yml");
            return List.of("");
        }
    }

    public String getLanguageMessage(String path) {
        String message = this.languageConfig.getString(path);
        if (message != null) {
            return formatMessage(message);
        } else {
            getLogger().warning("Le chemin de message '" + path + "' n'a pas été trouvé dans language.yml");
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
            getLogger().log(Level.SEVERE, "Fichier de configuration par défaut " + defaultFileName + " non trouvé.");
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
            getLogger().log(Level.SEVERE, "Erreur lors de la sauvegarde du fichier " + fileName + " : " + e.getMessage());
        }
    }

    public void addDamage(UUID playerUUID, double damage) {
        damageMap.put(playerUUID, damageMap.getOrDefault(playerUUID, 0.0) + damage);
    }

    public Map<UUID, Double> getDamageMap() {
        return damageMap;
    }

    public void clearDamageMap() {
        if (!damageMap.isEmpty()) {
            damageMap.clear();
            plugin.getLogger().info("Damage map has been cleared.");
        }
    }

    public void loadLeaderboardConfig() {
        leaderboardFile = new File(getDataFolder(), "damageleaderboard.yml");

        if (!leaderboardFile.exists()) {
            try {
                if (leaderboardFile.createNewFile()) {
                    getLogger().info("Le fichier damageleaderboard.yml a été créé avec succès.");
                } else {
                    getLogger().warning("Le fichier damageleaderboard.yml n'a pas pu être créé.");
                }
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Erreur lors de la création du fichier de classement : " + e.getMessage());
            }
        }

        leaderboardConfig = YamlConfiguration.loadConfiguration(leaderboardFile);
    }

    public void saveLeaderboardConfig() {
        if (leaderboardConfig == null) {
            getLogger().warning("Tentative de sauvegarde du classement alors que la configuration n'a pas été chargée.");
            return;
        }

        for (Map.Entry<UUID, Double> entry : damageMap.entrySet()) {
            leaderboardConfig.set("damage." + entry.getKey().toString(), entry.getValue());
        }
        try {
            leaderboardConfig.save(leaderboardFile);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Erreur lors de la sauvegarde du fichier de classement : " + e.getMessage());
        }
    }

    public void loadDamageMap() {
        if (leaderboardConfig == null || !leaderboardConfig.contains("damage")) {
            getLogger().warning("Aucune donnée de classement trouvée dans le fichier damageleaderboard.yml.");
            return;
        }

        damageMap.clear();

        for (String key : leaderboardConfig.getConfigurationSection("damage").getKeys(false)) {
            UUID playerUUID = UUID.fromString(key);
            double damage = leaderboardConfig.getDouble("damage." + key);
            damageMap.put(playerUUID, damage);
        }

        getLogger().info("Damage map loaded with " + damageMap.size() + " entries.");
    }

    public void saveDamageLeaderboard() {
        if (leaderboardConfig == null) {
            getLogger().warning("Tentative d'enregistrer le classement alors que la configuration n'a pas été chargée.");
            return;
        }

        for (Map.Entry<UUID, Double> entry : damageMap.entrySet()) {
            leaderboardConfig.set("damage." + entry.getKey().toString(), entry.getValue());
        }

        saveLeaderboardConfig();
    }

    public void clearLeaderboardFile() {
        if (leaderboardConfig != null) {
            leaderboardConfig.set("damage", null);
            saveLeaderboardConfig();
            getLogger().info("Leaderboard file has been cleared.");
        }
    }
}
