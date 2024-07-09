package be.achent.witherslayer.Commands;

import be.achent.witherslayer.WitherSlayer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class WitherSlayerCommands implements CommandExecutor {

    private final WitherSlayer plugin;
    private final Map<UUID, Double> damageMap;

    public WitherSlayerCommands(WitherSlayer plugin, Map<UUID, Double> damageMap) {
        this.plugin = plugin;
        this.damageMap = damageMap;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            if (args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("witherslayer.reload")) {
                    sender.sendMessage(plugin.getLanguageMessage("messages.No Permission"));
                    return true;
                }
                plugin.reloadConfig();
                plugin.reloadLanguageConfig();
                plugin.loadDamageLeaderboard();
                sender.sendMessage(plugin.getLanguageMessage("messages.Reloaded"));
                return true;
            } else if (args[0].equalsIgnoreCase("leaderboard")) {
                if (!sender.hasPermission("witherslayer.leaderboard")) {
                    sender.sendMessage(plugin.getLanguageMessage("messages.No Permission"));
                    return true;
                }

                plugin.loadDamageLeaderboard();

                if (damageMap.isEmpty()) {
                    sender.sendMessage(plugin.getLanguageMessage("messages.No Data"));
                    return true;
                }

                List<Map.Entry<UUID, Double>> sortedList = damageMap.entrySet().stream()
                        .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                        .collect(Collectors.toList());

                sender.sendMessage(plugin.getLanguageMessage("messages.Leaderboard header"));
                for (int i = 0; i < sortedList.size(); i++) {
                    UUID playerUUID = sortedList.get(i).getKey();
                    double damage = sortedList.get(i).getValue();
                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUUID);
                    String playerName = offlinePlayer != null && offlinePlayer.getName() != null ? offlinePlayer.getName() : "Unknown";

                    String message = plugin.getLanguageMessage("messages.Leaderboard position")
                            .replace("{position}", String.valueOf(i + 1))
                            .replace("{player}", playerName)
                            .replace("{damage}", String.format("%.2f", damage));
                    sender.sendMessage(message);
                }
                sender.sendMessage(plugin.getLanguageMessage("messages.Leaderboard footer"));
                return true;
            }
        }

        List<String> incorrectUsageMessages = plugin.getLanguageMessageList("messages.Incorrect usage");
        for (String message : incorrectUsageMessages) {
            sender.sendMessage(message);
        }
        return false;
    }
}
