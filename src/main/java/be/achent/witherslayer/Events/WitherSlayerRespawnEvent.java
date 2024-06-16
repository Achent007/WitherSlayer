package be.achent.witherslayer.Events;

import be.achent.witherslayer.WitherSlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Wither;
import org.bukkit.scheduler.BukkitRunnable;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class WitherSlayerRespawnEvent extends BukkitRunnable {

    private final WitherSlayer plugin;

    public WitherSlayerRespawnEvent(WitherSlayer plugin) {
        this.plugin = plugin;
    }
    @Override
    public void run() {
        LocalTime now = LocalTime.now();
        String currentTime = now.format(DateTimeFormatter.ofPattern("HH:mm"));
        List<String> spawntimes = parseTimes(plugin.getConfig().getString("wither respawn.spawntimes"));
        List<Integer> preSpawnTimes = parsePreSpawnTimes(plugin.getConfig().getString("wither respawn.respawn announcements"));

        for (String spawntime : spawntimes) {
            LocalTime spawnTime = LocalTime.parse(spawntime, DateTimeFormatter.ofPattern("HH:mm"));
            int secondsUntilSpawn = (int) java.time.Duration.between(now, spawnTime).getSeconds();

            plugin.getLogger().info("Checking spawn time: " + spawntime + " - Seconds until spawn: " + secondsUntilSpawn); // Log spawn time check

            for (int preTime : preSpawnTimes) {
                if (secondsUntilSpawn == preTime) {
                    Bukkit.broadcastMessage(plugin.getLanguageMessage("messages.Announcement before wither spawn").replace("{time}", String.valueOf(preTime)));
                    plugin.getLogger().info("Pre-spawn message sent for time: " + preTime); // Log pre-spawn message
                }
            }

            if (secondsUntilSpawn == 0) {
                spawnWither();
                plugin.getLogger().info("Wither spawned at time: " + spawntime); // Log wither spawn
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

            world.spawn(location, Wither.class);
            Bukkit.broadcastMessage(plugin.getLanguageMessage("messages.Wither Spawned").replace("{location}", location.toString()));
            plugin.getLogger().info("Wither spawned at location: " + location); // Log wither spawn location
        } else {
            plugin.getLogger().warning("World not found: " + worldName); // Log world not found
        }
    }
}
