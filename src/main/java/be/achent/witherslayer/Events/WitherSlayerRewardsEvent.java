package be.achent.witherslayer.Events;

import be.achent.witherslayer.WitherSlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import static org.bukkit.Bukkit.getLogger;

public class WitherSlayerRewardsEvent implements Listener {

    private final WitherSlayer plugin;

    public WitherSlayerRewardsEvent(WitherSlayer plugin) {
        this.plugin = plugin;
    }

    public void executeRewards(Player player, int rank, double damage) {
        List<String> commands = plugin.getConfig().getStringList("Rewards.rankcommands_" + rank);
        Random random = new Random();

        Map<UUID, Double> damageMap = plugin.getDamageMap();
        double playerDamage = damageMap.getOrDefault(player.getUniqueId(), 0.0);
        List<Map.Entry<UUID, Double>> sortedPlayers = damageMap.entrySet()
                .stream()
                .sorted((entry1, entry2) -> Double.compare(entry2.getValue(), entry1.getValue()))
                .collect(Collectors.toList());
        double totalDamage = sortedPlayers.stream().mapToDouble(Map.Entry::getValue).sum();
        double damagePercentage = playerDamage / totalDamage;

        int rewardExp = (int) (plugin.getConfig().getInt("Rewards.EXP") * damagePercentage);

        player.giveExp(rewardExp);

        for (String command : commands) {
            if (command.contains("{")) {
                int percentageStart = command.indexOf("{");
                int percentageEnd = command.indexOf("}");
                if (percentageStart != -1 && percentageEnd != -1 && percentageStart < percentageEnd) {
                    String percentageString = command.substring(percentageStart + 1, percentageEnd);
                    try {
                        int percentage = Integer.parseInt(percentageString);
                        if (random.nextInt(100) < percentage) {
                            String commandToExecute = command.substring(percentageEnd + 1).trim();
                            commandToExecute = commandToExecute.replace("{player}", player.getName())
                                    .replace("{position}", String.valueOf(rank))
                                    .replace("{damage}", String.format("%.2f", damage));
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandToExecute);
                        }
                    } catch (NumberFormatException e) {
                        getLogger().warning("Invalid percentage format: " + percentageString);
                    }
                }
            } else {
                String commandToExecute = command.replace("{player}", player.getName())
                        .replace("{position}", String.valueOf(rank))
                        .replace("{damage}", String.format("%.2f", damage));
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandToExecute);
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Wither)) {
            return;
        }

        Wither wither = (Wither) event.getEntity();

        Map<UUID, Double> damageMap = plugin.getDamageMap();
        if (damageMap.isEmpty()) {
            plugin.getLogger().warning("Damage map is empty! Cannot proceed with rewards.");
            return;
        }

        List<Map.Entry<UUID, Double>> sortedPlayers = damageMap.entrySet()
                .stream()
                .sorted((entry1, entry2) -> Double.compare(entry2.getValue(), entry1.getValue()))
                .collect(Collectors.toList());

        double totalDamage = sortedPlayers.stream().mapToDouble(Map.Entry::getValue).sum();

        for (int rank = 0; rank < sortedPlayers.size(); rank++) {
            UUID playerId = sortedPlayers.get(rank).getKey();
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) continue;

            double playerDamage = sortedPlayers.get(rank).getValue();
            double damagePercentage = (playerDamage / totalDamage);
            int rewardExp = (int) (plugin.getConfig().getInt("Rewards.EXP") * damagePercentage);
            player.giveExp(rewardExp);

            executeRewards(player, rank + 1, playerDamage);

            plugin.getLogger().info("Player " + player.getName() + " received " + rewardExp + " EXP for dealing " + playerDamage + " damage.");
        }

        plugin.saveDamageLeaderboard();
        plugin.clearDamageMap();
    }
}
