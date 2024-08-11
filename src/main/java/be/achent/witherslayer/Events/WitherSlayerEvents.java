package be.achent.witherslayer.Events;

import be.achent.witherslayer.WitherSlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

public class WitherSlayerEvents implements Listener {

    private final WitherSlayer plugin;
    private final WitherSlayerRespawnEvent witherRespawnEvent;

    public WitherSlayerEvents(WitherSlayer plugin, WitherSlayerRespawnEvent witherRespawnEvent) {
        this.plugin = plugin;
        this.witherRespawnEvent = witherRespawnEvent;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Wither)) {
            return;
        }

        Wither wither = (Wither) event.getEntity();
        String witherWorldName = plugin.getConfig().getString("witherworld");

        if (!wither.getWorld().getName().equals(witherWorldName)) {
            return;
        }

        Entity damager = event.getDamager();
        double damage = event.getFinalDamage();

        if (damager instanceof Player) {
            Player player = (Player) damager;
            plugin.addDamage(player.getUniqueId(), damage);
        } else if (damager instanceof Arrow) {
            Arrow arrow = (Arrow) damager;
            if (arrow.getShooter() instanceof Player) {
                Player player = (Player) arrow.getShooter();
                plugin.addDamage(player.getUniqueId(), damage);
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Wither)) {
            return;
        }

        Wither wither = (Wither) event.getEntity();
        String witherWorldName = plugin.getConfig().getString("witherworld");

        if (!wither.getWorld().getName().equals(witherWorldName)) {
            plugin.getLogger().info("Wither killed in a non-target world, no rewards distributed.");
            return;
        }

        event.getDrops().clear();
        event.setDroppedExp(0);
        plugin.logInfo("Vanilla drops canceled");

        if (wither.getUniqueId().equals(witherRespawnEvent.getCurrentWitherUUID())) {
            plugin.getLogger().info("The killed wither is the current target, processing rewards event.");
            Map<UUID, Double> damageMap = plugin.getDamageMap();
            if (damageMap.isEmpty()) {
                plugin.getLogger().warning("Damage map is empty!");
                return;
            }

            List<Map.Entry<UUID, Double>> sortedPlayers = damageMap.entrySet()
                    .stream()
                    .sorted((entry1, entry2) -> Double.compare(entry2.getValue(), entry1.getValue()))
                    .collect(Collectors.toList());

            double totalDamage = sortedPlayers.stream().mapToDouble(Map.Entry::getValue).sum();
            plugin.getLogger().info("Total damage: " + totalDamage);

            for (int rank = 0; rank < sortedPlayers.size(); rank++) {
                UUID playerId = sortedPlayers.get(rank).getKey();
                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline()) {
                    continue;
                }

                double playerDamage = sortedPlayers.get(rank).getValue();
                int rankPosition = rank + 1;

                double damagePercentage = playerDamage / totalDamage;
                int rewardExp = (int) (plugin.getConfig().getInt("Rewards.EXP") * damagePercentage);

                plugin.getLogger().info("Rewarding player: " + player.getName() + " with rank: " + rankPosition + ", damage: " + playerDamage + ", exp: " + rewardExp);

                player.giveExp(rewardExp);
                executeRewards(player, rankPosition, playerDamage, rewardExp);
            }

            Player killerEntity = wither.getKiller();
            String killerName = killerEntity != null ? killerEntity.getName() : "unknown";
            String killMessage = plugin.getLanguageMessage("messages.Kill message")
                    .replace("{player}", killerName)
                    .replace("{wither}", wither.getName());
            Bukkit.broadcastMessage(killMessage);
        } else {
            plugin.getLogger().warning("Killed wither is not the current target.");
        }
    }

    private void executeRewards(Player player, int rank, double damage, int rewardExp) {
        List<String> commands = plugin.getConfig().getStringList("Rewards.rankcommands_" + rank);
        Random random = new Random();

        if (commands == null || commands.isEmpty()) {
            plugin.getLogger().warning("No commands found for rank " + rank);
            return;
        }

        for (String command : commands) {
            command = command.replace("{player}", player.getName())
                    .replace("{position}", String.valueOf(rank))
                    .replace("{damage}", String.format("%.2f", damage))
                    .replace("{exp}", String.valueOf(rewardExp));

            if (command.contains("{")) {
                int percentageStart = command.indexOf("{");
                int percentageEnd = command.indexOf("}");
                if (percentageStart != -1 && percentageEnd != -1 && percentageStart < percentageEnd) {
                    String percentageString = command.substring(percentageStart + 1, percentageEnd);
                    try {
                        int percentage = Integer.parseInt(percentageString);
                        if (random.nextInt(100) < percentage) {
                            String commandToExecute = command.substring(percentageEnd + 1).trim();
                            plugin.getLogger().info("Executing conditional command: " + commandToExecute);
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandToExecute);
                        }
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Invalid percentage format: " + percentageString);
                    }
                }
            } else {
                plugin.getLogger().info("Executing command: " + command);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            }
        }
    }
}
