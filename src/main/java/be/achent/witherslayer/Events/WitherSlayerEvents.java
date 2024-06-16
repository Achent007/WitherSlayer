package be.achent.witherslayer.Events;

import be.achent.witherslayer.WitherSlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class WitherSlayerEvents implements Listener {
    public final WitherSlayer plugin;
    public static final Map<Player, Integer> damageMap = new HashMap<>();

    public WitherSlayerEvents(WitherSlayer plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntityType() == EntityType.WITHER) {
            Entity damager = event.getDamager();
            int damage = (int) event.getDamage();
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
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Wither)) {
            return;
        }

        Wither wither = (Wither) event.getEntity();

        event.getDrops().clear();
        event.setDroppedExp(0);

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

        for (int rank = 0; rank < sortedPlayers.size(); rank++) {
            UUID playerId = sortedPlayers.get(rank).getKey();
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) continue;

            double playerDamage = sortedPlayers.get(rank).getValue();
            int rankPosition = rank + 1;

            double damagePercentage = (playerDamage / totalDamage);
            int rewardExp = (int) (plugin.getConfig().getInt("Rewards.EXP") * damagePercentage);
            player.giveExp(rewardExp);

            plugin.giveRewardsToPlayer(player, rankPosition, playerDamage);

            plugin.getLogger().info("Player " + player.getName() + " received " + rewardExp + " EXP for dealing " + playerDamage + " damage.");
        }

        plugin.clearDamageMap();
    }
}
