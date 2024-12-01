package be.achent.witherslayer.Events;

import be.achent.witherslayer.WitherSlayer;
import net.milkbowl.vault.economy.Economy;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
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
        if (!(event.getEntity() instanceof Wither wither)) {
            return;
        }

        String witherWorldName = plugin.getConfig().getString("witherworld");

        if (!wither.getWorld().getName().equals(witherWorldName)) {
            return;
        }

        Entity damager = event.getDamager();
        double damage = event.getFinalDamage();

        if (damager instanceof Player player) {
            plugin.addDamage(player.getUniqueId(), damage);
        } else if (damager instanceof Arrow arrow) {
            if (arrow.getShooter() instanceof Player player) {
                plugin.addDamage(player.getUniqueId(), damage);
            }
        }
    }

    public static void giveMoney(Player player, double amount) {
        Economy econ = WitherSlayer.getEconomy();
        if (econ != null && amount > 0) {
            BigDecimal amountBD = new BigDecimal(amount);
            amountBD = amountBD.setScale(2, RoundingMode.HALF_UP);
            econ.depositPlayer(player, amountBD.doubleValue());
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Wither wither)) {
            return;
        }

        String witherWorldName = plugin.getConfig().getString("witherworld");

        if (!wither.getWorld().getName().equals(witherWorldName)) {
            plugin.logInfo("Wither killed in a non-target world, no rewards distributed.");
            return;
        }

        event.getDrops().clear();
        event.setDroppedExp(0);
        plugin.logInfo("Vanilla drops canceled");

        if (wither.getUniqueId().equals(witherRespawnEvent.getCurrentWitherUUID())) {
            plugin.logInfo("The killed wither is the current target, processing rewards event.");

            Map<UUID, Double> damageMap = plugin.getDamageMap();
            if (damageMap.isEmpty()) {
                plugin.logWarning("Damage map is empty!");
                return;
            }

            plugin.getDataConfig().set("last_wither.participants", getParticipantCount());
            plugin.saveDataFile();
            plugin.logInfo("Nombre de participants sauvegardé dans data.yml : " + getParticipantCount());

            List<Map.Entry<UUID, Double>> sortedPlayers = damageMap.entrySet()
                    .stream()
                    .sorted((entry1, entry2) -> Double.compare(entry2.getValue(), entry1.getValue()))
                    .collect(Collectors.toList());

            double totalDamage = sortedPlayers.stream().mapToDouble(Map.Entry::getValue).sum();
            plugin.logInfo("Total damage: " + totalDamage);

            applyGlobalRewards(sortedPlayers, totalDamage);
            applyRankRewards(sortedPlayers);

            Player killerEntity = wither.getKiller();
            String killerName = killerEntity != null ? killerEntity.getName() : "unknown";
            String killMessage = plugin.getLanguageMessage("messages.Kill message")
                    .replace("{player}", killerName)
                    .replace("{wither}", wither.getName());
            Bukkit.broadcastMessage(killMessage);
        } else {
            plugin.logWarning("Killed wither is not the current target.");
        }
    }

    private Rewards calculateRewards(UUID playerId, double playerDamage, double totalDamage) {
        String expType = plugin.getConfig().getString("Rewards.GlobalRewards.EXP.type", "global");
        String moneyType = plugin.getConfig().getString("Rewards.GlobalRewards.Money.type", "global");

        double expAmount = plugin.getConfig().getDouble("Rewards.GlobalRewards.EXP.amount", 0);
        double moneyAmount = plugin.getConfig().getDouble("Rewards.GlobalRewards.Money.amount", 0);

        String expEquation = plugin.getConfig().getString("Rewards.GlobalRewards.EXP.playerbased_equation", "");
        String moneyEquation = plugin.getConfig().getString("Rewards.GlobalRewards.Money.playerbased_equation", "");

        int amountPlayer = plugin.getDamageMap().size(); // Nombre de participants

        if (expType.equals("global")) {
            expAmount = expAmount * (playerDamage / totalDamage);
        } else {
            expAmount = evaluateEquation(expEquation, expAmount, playerDamage, totalDamage, amountPlayer, "EXP");
        }

        if (moneyType.equals("global")) {
            moneyAmount = moneyAmount * (playerDamage / totalDamage);
        } else {
            moneyAmount = evaluateEquation(moneyEquation, moneyAmount, playerDamage, totalDamage, amountPlayer, "Money");
        }

        return new Rewards(expAmount, moneyAmount);
    }

    private void applyGlobalRewards(List<Map.Entry<UUID, Double>> players, double totalDamage) {
        for (Map.Entry<UUID, Double> entry : players) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;

            double playerDamage = entry.getValue();

            Rewards rewards = calculateRewards(entry.getKey(), playerDamage, totalDamage);

            player.giveExp((int) rewards.exp());
            giveMoney(player, rewards.money());
        }
    }

    private void applyRankRewards(List<Map.Entry<UUID, Double>> players) {
        for (int rank = 0; rank < players.size(); rank++) {
            UUID playerId = players.get(rank).getKey();
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) continue;

            int rankPosition = rank + 1;
            List<String> commands = plugin.getConfig().getStringList("Rewards.RankRewards.rankcommands_" + rankPosition);
            if (commands == null || commands.isEmpty()) continue;

            double playerDamage = players.get(rank).getValue();
            double totalDamage = players.stream().mapToDouble(Map.Entry::getValue).sum();

            Rewards rewards = calculateRewards(playerId, playerDamage, totalDamage);

            for (String command : commands) {
                executeCommand(command, player, rankPosition, rewards);
            }
        }
    }

    private void executeCommand(String command, Player player, int rankPosition, Rewards rewards) {
        double damage = plugin.getDamageMap().get(player.getUniqueId());

        command = command.replace("{player}", player.getName())
                .replace("{position}", String.valueOf(rankPosition))
                .replace("{damage}", String.format("%.2f", damage))
                .replace("{exp}", String.valueOf((int) rewards.exp()))
                .replace("{money}", String.format("%.2f", rewards.money()));

        if (command.contains("{")) {
            int percentageStart = command.indexOf("{");
            int percentageEnd = command.indexOf("}");
            if (percentageStart != -1 && percentageEnd != -1 && percentageStart < percentageEnd) {
                String percentageString = command.substring(percentageStart + 1, percentageEnd);
                try {
                    int percentage = Integer.parseInt(percentageString);
                    if (percentage >= 0 && percentage <= 100) {
                        if (new java.util.Random().nextInt(100) < percentage) {
                            String commandToExecute = command.substring(percentageEnd + 1).trim();
                            plugin.logInfo("Executing conditional command: " + commandToExecute);
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandToExecute);
                        } else {
                            plugin.logInfo("Command skipped due to percentage condition: " + command);
                        }
                    } else {
                        plugin.logWarning("Invalid percentage value (must be 0-100): " + percentageString);
                    }
                } catch (NumberFormatException e) {
                    plugin.logWarning("Invalid percentage format: " + percentageString);
                }
                return;
            }
        }

        plugin.logInfo("Executing command: " + command);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    private int getParticipantCount() {
        return plugin.getDamageMap().size();
    }

    public record Rewards(double exp, double money) {
    }

    private double evaluateEquation(String equation, double amount, double playerDamage, double totalDamage, int amountPlayer, String resourceType) {
        equation = equation.replace("{amount}", String.valueOf(amount))
                .replace("{playerDamage}", String.valueOf(playerDamage))
                .replace("{totalDamage}", String.valueOf(totalDamage))
                .replace("{amountplayer}", String.valueOf(amountPlayer));

        try {
            Expression expression = new ExpressionBuilder(equation)
                    .variable("amount")
                    .variable("playerDamage")
                    .variable("totalDamage")
                    .variable("amountplayer")
                    .build()
                    .setVariable("amount", amount)
                    .setVariable("playerDamage", playerDamage)
                    .setVariable("totalDamage", totalDamage)
                    .setVariable("amountplayer", amountPlayer);

            double result = expression.evaluate();
            plugin.logInfo("Evaluated equation for " + resourceType + ": " + equation + " = " + result);
            return result;
        } catch (Exception e) {
            plugin.logWarning("Invalid equation syntax for " + resourceType + ": " + equation + ". Error: " + e.getMessage() + ". Using default amount: " + amount);
            return amount;
        }
    }
}
