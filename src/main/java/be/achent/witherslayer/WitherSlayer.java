package be.achent.witherslayer;

import be.achent.witherslayer.Commands.WitherSlayerCommands;
import be.achent.witherslayer.Commands.WitherSlayerTabCompleter;
import be.achent.witherslayer.Events.WitherSlayerEvents;
import be.achent.witherslayer.Events.WitherSlayerRespawnEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
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

public final class WitherSlayer extends JavaPlugin implements Listener {

    private FileConfiguration languageConfig;
    private File languageConfigFile;
    private File leaderboardFile;
    private FileConfiguration leaderboardConfig;
    private WitherSlayerRespawnEvent witherRespawnEvent;
    private final Map<UUID, Double> damageMap = new HashMap<>();
    private boolean debugEnabled;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        debugEnabled = getConfig().getBoolean("debug", false);
        loadLanguageConfig();
        loadLeaderboardConfig();
        updateConfigFile("config.yml", "config-default.yml");
        updateConfigFile("language.yml", "language-default.yml");

        witherRespawnEvent = new WitherSlayerRespawnEvent(this);
        new WitherSlayerEvents(this, witherRespawnEvent);
        witherRespawnEvent.runTaskTimer(this, 0L, 20L);
        loadWitherState();

        getCommand("witherslayer").setExecutor(new WitherSlayerCommands(this, damageMap, witherRespawnEvent));
        getCommand("witherslayer").setTabCompleter(new WitherSlayerTabCompleter());
    }

    @Override
    public void onDisable() {
        saveWitherState();
        if (witherRespawnEvent != null) {
            witherRespawnEvent.cancel();
        }
    }

    public void reloadConfigWithErrors(CommandSender sender) {
        try {
            reloadConfig();
            debugEnabled = getConfig().getBoolean("debug", false);
            reloadLanguageConfig();
            loadLeaderboardConfig();
            updateConfigFile("config.yml", "config-default.yml");
            updateConfigFile("language.yml", "language-default.yml");

            String spawnTimes = getConfig().getString("wither respawn.spawntimes");
            for (String time : spawnTimes.split(",")) {
                LocalTime.parse(time.trim(), DateTimeFormatter.ofPattern("HH:mm"));
            }

            if (witherRespawnEvent != null) {
                witherRespawnEvent.cancel();
            }

            witherRespawnEvent = new WitherSlayerRespawnEvent(this);
            new WitherSlayerEvents(this, witherRespawnEvent);
            witherRespawnEvent.runTaskTimer(this, 0L, 20L);

            sender.sendMessage(getLanguageMessage("messages.Reloaded"));
        } catch (DateTimeParseException e) {
            sender.sendMessage(getLanguageMessage("messages.Reload failed"));
            getLogger().severe("Format de temps invalide pour l'heure de réapparition : " + e.getParsedString());
        } catch (Exception e) {
            sender.sendMessage(getLanguageMessage("messages.Reload failed"));
            getLogger().severe("Impossible de recharger la configuration : " + e.getMessage());
        }
    }

    public List<String> getLanguageMessageList(String path) {
        List<String> messages = this.languageConfig.getStringList(path);
        if (!messages.isEmpty()) {
            messages.replaceAll(this::formatMessage);
            return messages;
        } else {
            logWarning("Le chemin de message '" + path + "' n'a pas été trouvé dans language.yml");
            return List.of("");
        }
    }

    public String getLanguageMessage(String path) {
        String message = this.languageConfig.getString(path);
        if (message != null) {
            return formatMessage(message);
        } else {
            logWarning("Le chemin de message '" + path + "' n'a pas été trouvé dans language.yml");
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
            logSevere("Fichier de configuration par défaut " + defaultFileName + " non trouvé.");
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
            logSevere("Erreur lors de la sauvegarde du fichier " + fileName + " : " + e.getMessage());
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
            logInfo("Données de dégats infligés remise à zéro.");
        }
    }

    public void loadLeaderboardConfig() {
        leaderboardFile = new File(getDataFolder(), "damageleaderboard.yml");

        if (!leaderboardFile.exists()) {
            try {
                if (leaderboardFile.createNewFile()) {
                    logInfo("Le fichier damageleaderboard.yml a été créé avec succès.");
                } else {
                    logWarning("Le fichier damageleaderboard.yml n'a pas pu être créé.");
                }
            } catch (IOException e) {
                logSevere("Erreur lors de la création du fichier de classement : " + e.getMessage());
            }
        }

        leaderboardConfig = YamlConfiguration.loadConfiguration(leaderboardFile);
    }

    public void saveLeaderboardConfig() {
        if (leaderboardConfig == null) {
            logWarning("Tentative de sauvegarde du classement alors que la configuration n'a pas été chargée.");
            return;
        }

        try {
            leaderboardConfig.save(leaderboardFile);
        } catch (IOException e) {
            logSevere("Erreur lors de la sauvegarde du fichier de classement : " + e.getMessage());
        }
    }

    public void loadDamageMap() {
        if (leaderboardConfig == null || !leaderboardConfig.contains("damage")) {
            logWarning("Aucune donnée de classement trouvée dans le fichier damageleaderboard.yml.");
            return;
        }

        damageMap.clear();

        for (String key : leaderboardConfig.getConfigurationSection("damage").getKeys(false)) {
            UUID playerUUID = UUID.fromString(key);
            double damage = leaderboardConfig.getDouble("damage." + key);
            damageMap.put(playerUUID, damage);
        }

        logInfo("Classement affiché avec succès.");
    }

    public void saveDamageLeaderboard() {
        if (leaderboardConfig == null) {
            logWarning("Tentative d'enregistrer le classement alors que la configuration n'a pas été chargée.");
            return;
        }

        for (Map.Entry<UUID, Double> entry : damageMap.entrySet()) {
            leaderboardConfig.set("damage." + entry.getKey().toString(), entry.getValue());

            OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
            String playerName = player.getName();
            if (playerName == null) {
                playerName = "Unknown";
            }

            logInfo("Les " + entry.getValue() + " dégats de " + playerName + " ont été enregistrés.");
        }

        saveLeaderboardConfig();
    }

    public void recreateLeaderboardFile() {
        if (leaderboardFile.exists()) {
            if (leaderboardFile.delete()) {
                logInfo("Fichier damageleaderboard.yml supprimé.");
            } else {
                logSevere("Erreur lors de la suppression du fichier damageleaderboard.yml.");
            }
        }
        try {
            if (leaderboardFile.createNewFile()) {
                logInfo("Le fichier damageleaderboard.yml a été recréé.");
            } else {
                logSevere("Le fichier damageleaderboard.yml n'a pas pu être recréé.");
            }
        } catch (IOException e) {
            logSevere("Erreur lors de la recréation du fichier de classement : " + e.getMessage());
        }
        loadLeaderboardConfig();
    }

    public void logInfo(String message) {
        if (debugEnabled) {
            getLogger().info(message);
        }
    }

    public void logWarning(String message) {
        if (debugEnabled) {
            getLogger().warning(message);
        }
    }

    public void logSevere(String message) {
        getLogger().severe(message);
    }

    public void saveWitherState() {
        try {
            File stateFile = new File(getDataFolder(), "witherstate.yml");
            FileConfiguration stateConfig = YamlConfiguration.loadConfiguration(stateFile);
            stateConfig.set("witherSpawned", witherRespawnEvent.isWitherSpawned());
            stateConfig.set("currentWitherUUID", witherRespawnEvent.getCurrentWitherUUID() != null ? witherRespawnEvent.getCurrentWitherUUID().toString() : null);
            stateConfig.save(stateFile);
        } catch (IOException e) {
            logSevere("Erreur lors de la sauvegarde de l'état du Wither : " + e.getMessage());
        }
    }

    public void loadWitherState() {
        File stateFile = new File(getDataFolder(), "witherstate.yml");
        if (stateFile.exists()) {
            FileConfiguration stateConfig = YamlConfiguration.loadConfiguration(stateFile);
            witherRespawnEvent.setWitherSpawned(stateConfig.getBoolean("witherSpawned", false));
            String witherUUIDString = stateConfig.getString("currentWitherUUID", null);
            if (witherUUIDString != null && !witherUUIDString.isEmpty()) {
                UUID witherUUID = UUID.fromString(witherUUIDString);
                witherRespawnEvent.setCurrentWitherUUID(witherUUID);
            }
        }
    }
}
