package be.achent.witherslayer.Events;

import be.achent.witherslayer.WitherSlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WitherSlayerEvents implements Listener {
    private final WitherSlayer plugin;
    public static final Map<Player, Integer> damageMap = new HashMap<>();

    public WitherSlayerEvents(WitherSlayer plugin, Map<UUID, Double> damageMap) {
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
                damageMap.put(player, damageMap.getOrDefault(player, 0) + damage);
            } else if (damager instanceof Arrow) {
                Arrow arrow = (Arrow) damager;
                if (arrow.getShooter() instanceof Player) {
                    Player player = (Player) arrow.getShooter();
                    damageMap.put(player, damageMap.getOrDefault(player, 0) + damage);
                }
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Wither) {
            Player killer = ((Wither) entity).getKiller();
            if (killer != null) {
                Bukkit.broadcastMessage(plugin.getLanguageMessage("messages.Kill message").replace("{player}", killer.getName()));
                WitherSlayerRewardsEvent.executeRewards(plugin, damageMap);
            }
        }
    }

    public static Map<Player, Integer> getLeaderboard() {
        return damageMap;
    }
}
