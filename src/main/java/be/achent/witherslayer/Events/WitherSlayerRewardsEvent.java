package be.achent.witherslayer.Events;

import be.achent.witherslayer.WitherSlayer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class WitherSlayerRewardsEvent {
    private final WitherSlayer plugin;

    private static final Random random = new Random();

    public WitherSlayerRewardsEvent(WitherSlayer plugin) {
        this.plugin = plugin;
    }

    public static void executeRewards(WitherSlayer plugin, Map<Player, Integer> damageMap) {
        FileConfiguration config = plugin.getConfig();
        int totalDamage = damageMap.values().stream().mapToInt(Integer::intValue).sum();
        int expReward = config.getInt("Rewards.EXP");

        for (Map.Entry<Player, Integer> entry : damageMap.entrySet()) {
            Player player = entry.getKey();
            int playerDamage = entry.getValue();
            int playerExp = (int) ((double) playerDamage / totalDamage * expReward);

            player.giveExp(playerExp);

            int rank = getRank(damageMap, player);
            List<String> rankCommands = config.getStringList("Rewards.rankcommand_" + rank);

            for (String command : rankCommands) {
                if (command.contains("{")) {
                    String[] parts = command.split("\\{");
                    double chance = Double.parseDouble(parts[1].split("\\}")[0]);

                    if (random.nextDouble() * 100 < chance) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parts[1].replace("{player}", player.getName()));
                    }
                } else {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{player}", player.getName()));
                }
            }
        }
    }

    private static int getRank(Map<Player, Integer> damageMap, Player player) {
        int rank = 1;
        for (Map.Entry<Player, Integer> entry : damageMap.entrySet()) {
            if (entry.getValue() > damageMap.get(player)) {
                rank++;
            }
        }
        return rank;
    }
}