package be.achent.witherslayer.Events;

import be.achent.witherslayer.WitherSlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.scheduler.BukkitRunnable;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class WitherSlayerRespawnEvent extends BukkitRunnable implements Listener {

    private final WitherSlayer plugin;
    private Wither currentWither;

    public WitherSlayerRespawnEvent(WitherSlayer plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void run() {
        LocalTime now = LocalTime.now();
        List<String> spawntimes = parseTimes(plugin.getConfig().getString("wither respawn.spawntimes"));
        List<Integer> preSpawnTimes = parsePreSpawnTimes(plugin.getConfig().getString("wither respawn.respawn announcements"));

        for (String spawntime : spawntimes) {
            LocalTime spawnTime = LocalTime.parse(spawntime, DateTimeFormatter.ofPattern("HH:mm"));
            int secondsUntilSpawn = (int) java.time.Duration.between(now, spawnTime).getSeconds();

            for (int preTime : preSpawnTimes) {
                if (secondsUntilSpawn == preTime) {
                    Bukkit.broadcastMessage(plugin.getLanguageMessage("messages.Announcement before wither spawn").replace("{time}", formatTime(preTime)));
                    plugin.getLogger().info("Pre-spawn message sent for time: " + preTime);
                }
            }

            if (secondsUntilSpawn == 0 && (currentWither == null || currentWither.isDead())) {
                spawnWither();
                plugin.getLogger().info("Wither spawned at time: " + spawntime);
            }
        }
    }

    private List<String> parseTimes(String times) {
        List<String> timeList = new ArrayList<>();
        if (times != null && !times.isEmpty()) {
            String[] timeArray = times.split(",");
            for (String time : timeArray) {
                timeList.add(time.trim());
            }
        }
        return timeList;
    }

    private List<Integer> parsePreSpawnTimes(String times) {
        List<Integer> timeList = new ArrayList<>();
        if (times != null && !times.isEmpty()) {
            String[] timeArray = times.split(",");
            for (String time : timeArray) {
                try {
                    timeList.add(Integer.parseInt(time.trim()));
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid respawn announcements time format: " + time);
                }
            }
        }
        return timeList;
    }

    private void spawnWither() {
        String worldName = plugin.getConfig().getString("wither respawn.spawn location.world");
        World world = Bukkit.getWorld(worldName);

        if (world != null) {
            double x = plugin.getConfig().getDouble("wither respawn.spawn location.x");
            double y = plugin.getConfig().getDouble("wither respawn.spawn location.y");
            double z = plugin.getConfig().getDouble("wither respawn.spawn location.z");
            Location location = new Location(world, x, y, z);

            currentWither = (Wither) world.spawnEntity(location, EntityType.WITHER);

            double witherHealth = plugin.getConfig().getDouble("wither respawn.health");
            currentWither.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(witherHealth);
            currentWither.setHealth(witherHealth);

            Bukkit.broadcastMessage(plugin.getLanguageMessage("messages.Wither spawned").replace("{location}", location.toString()));
            plugin.getLogger().info("Wither spawned at location: " + location + " with health: " + witherHealth);

            plugin.clearDamageMap();
            plugin.clearLeaderboardFile();
        } else {
            plugin.getLogger().warning("World not found: " + worldName);
        }
    }

    public boolean forceSpawnWither() {
        if (currentWither == null || currentWither.isDead()) {
            spawnWither();
            plugin.getLogger().info("Wither forcefully spawned by command.");
            return true;
        } else {
            plugin.getLogger().info("Wither already exists and is not dead.");
            return false;
        }
    }

    @EventHandler
    public void onWitherDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Wither) {
            if (event.getEntity().equals(currentWither)) {
                plugin.saveDamageLeaderboard();
                plugin.clearDamageMap();
                witherDied();
                plugin.getLogger().info("Wither died, state reset.");
            }
        }
    }

    private void witherDied() {
        currentWither = null;
    }

    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;

        String minuteString = minutes == 1 ? plugin.getLanguageMessage("Time minute") : plugin.getLanguageMessage("Time minutes");
        String secondString = remainingSeconds == 1 ? plugin.getLanguageMessage("Time second") : plugin.getLanguageMessage("Time seconds");

        if (minutes > 0 && remainingSeconds > 0) {
            return minutes + " " + minuteString + plugin.getLanguageMessage("Time and") + remainingSeconds + " " + secondString;
        } else if (minutes > 0) {
            return minutes + " " + minuteString;
        } else {
            return remainingSeconds + " " + secondString;
        }
    }
}
