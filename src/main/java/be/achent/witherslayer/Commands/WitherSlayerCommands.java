package be.achent.witherslayer.Commands;

import be.achent.witherslayer.WitherSlayer;
import be.achent.witherslayer.Events.WitherSlayerRespawnEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class WitherSlayerCommands implements CommandExecutor {

    private final WitherSlayer plugin;
    private final Map<UUID, Double> damageMap;
    private final WitherSlayerRespawnEvent witherRespawnEvent;

    public WitherSlayerCommands(WitherSlayer plugin, Map<UUID, Double> damageMap, WitherSlayerRespawnEvent witherRespawnEvent) {
        this.plugin = plugin;
        this.damageMap = damageMap;
        this.witherRespawnEvent = witherRespawnEvent;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            sendUsageMessages(sender);
            return false;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                return handleReload(sender);
            case "leaderboard":
                return handleLeaderboard(sender);
            case "forcespawn":
                return handleForceSpawn(sender);
            default:
                sendUsageMessages(sender);
                return false;
        }
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("witherslayer.reload")) {
            sender.sendMessage(plugin.getLanguageMessage("messages.No Permission"));
            return false;
        }
        plugin.reloadConfigWithErrors(sender);
        return true;
    }

    private boolean handleLeaderboard(CommandSender sender) {
        if (!sender.hasPermission("witherslayer.leaderboard")) {
            sender.sendMessage(plugin.getLanguageMessage("messages.No Permission"));
            return false;
        }
        plugin.loadDamageMap();

        if (damageMap.isEmpty()) {
            sender.sendMessage(plugin.getLanguageMessage("messages.No Data"));
            return true;
        }

        List<Map.Entry<UUID, Double>> sortedList = damageMap.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .collect(Collectors.toList());

        sender.sendMessage(plugin.getLanguageMessage("messages.Leaderboard header"));
        for (int i = 0; i < sortedList.size(); i++) {
            sendLeaderboardEntry(sender, sortedList.get(i), i);
        }
        sender.sendMessage(plugin.getLanguageMessage("messages.Leaderboard footer"));
        return true;
    }

    private boolean handleForceSpawn(CommandSender sender) {
        if (!sender.hasPermission("witherslayer.forcespawn")) {
            sender.sendMessage(plugin.getLanguageMessage("messages.No Permission"));
            return false;
        }

        if (witherRespawnEvent.forceSpawnWither()) {
            sender.sendMessage(plugin.getLanguageMessage("messages.Force spawned"));
        } else {
            sender.sendMessage(plugin.getLanguageMessage("messages.Error spawning wither"));
        }
        return true;
    }

    private void sendLeaderboardEntry(CommandSender sender, Map.Entry<UUID, Double> entry, int index) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
        String playerName = player.getName() != null ? player.getName() : "Unknown";
        String message = plugin.getLanguageMessage("messages.Leaderboard position")
                .replace("{position}", String.valueOf(index + 1))
                .replace("{player}", playerName)
                .replace("{damage}", String.format("%.0f", entry.getValue()));
        sender.sendMessage(message);
    }

    private void sendUsageMessages(CommandSender sender) {
        List<String> incorrectUsageMessages = plugin.getLanguageMessageList("messages.Incorrect usage");
        incorrectUsageMessages.forEach(sender::sendMessage);
    }
}
