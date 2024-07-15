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

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class WitherSlayerRespawnEvent extends BukkitRunnable implements Listener {

    private final WitherSlayer plugin;
    private Wither currentWither;
    private boolean witherSpawned;
    private boolean errorLogged = false;

    public WitherSlayerRespawnEvent(WitherSlayer plugin) {
        this.plugin = plugin;
        this.witherSpawned = false;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void run() {
        LocalTime now = LocalTime.now().withNano(0);
        List<String> spawntimes = parseTimes(plugin.getConfig().getString("wither respawn.spawntimes"));
        List<Integer> preSpawnTimes = parsePreSpawnTimes(plugin.getConfig().getString("wither respawn.respawn announcements"));

        for (String spawntime : spawntimes) {
            try {
                LocalTime spawnTime = LocalTime.parse(spawntime, DateTimeFormatter.ofPattern("HH:mm")).withNano(0);
                Duration duration = Duration.between(now, spawnTime);
                int secondsUntilSpawn = (int) duration.getSeconds();

                for (int preTime : preSpawnTimes) {
                    if (secondsUntilSpawn == preTime) {
                        String timeMessage = formatTime(preTime);
                        Bukkit.broadcastMessage(plugin.getLanguageMessage("messages.Announcement before wither spawn").replace("{time}", timeMessage));
                        plugin.logInfo("Message d'annonce d'apparition dans " + preTime + " secondes effectué");
                    }
                }

                if (secondsUntilSpawn == 0) {
                    plugin.logInfo("Tentative de réapparition d'un wither à " + spawntime);
                    if (currentWither == null || currentWither.isDead()) {
                        plugin.logInfo("currentWither est null ou mort, appel à spawnWither");
                        spawnWither();
                    } else {
                        plugin.logInfo("Un wither est déjà présent à l'emplacement : " + currentWither.getLocation());
                        Bukkit.broadcastMessage(plugin.getLanguageMessage("messages.Wither already present"));
                        plugin.logWarning("Tentative de réapparition d'un wither mais un wither est déjà présent.");
                    }
                }
            } catch (DateTimeParseException e) {
                if (!errorLogged) {
                    plugin.logSevere("Format de temps invalide pour l'heure de réapparition : " + spawntime);
                    errorLogged = true;
                }
            }
        }
    }

    public void resetErrorLogged() {
        this.errorLogged = false;
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
                    plugin.logSevere("Format de temps de réapparition invalide dans le fichier config.yml" + time);
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

            witherSpawned = true;
            plugin.saveWitherState();

            Bukkit.broadcastMessage(plugin.getLanguageMessage("messages.Wither spawned").replace("{location}", location.toString()));
            plugin.logInfo("Wither apparu à l'endroit : " + location + " avec " + witherHealth + " PV");
        } else {
            plugin.logWarning("Le monde " + worldName + " n'est pas trouvable");
        }
    }

    public boolean forceSpawnWither() {
        if (currentWither == null || currentWither.isDead()) {
            spawnWither();
            plugin.logInfo("Wither invoqué de force par commande.");
            plugin.saveWitherState();
            return true;
        } else {
            plugin.logInfo("Tentative d'invocation d'un wither avec un wither déjà présent");
            return false;
        }
    }

    @EventHandler
    public void onWitherDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Wither) {
            if (event.getEntity().equals(currentWither)) {
                Bukkit.getScheduler().runTaskLater(plugin, plugin::recreateLeaderboardFile, 0L);
                Bukkit.getScheduler().runTaskLater(plugin, plugin::saveDamageLeaderboard, 10L);
                Bukkit.getScheduler().runTaskLater(plugin, plugin::clearDamageMap, 20L);
                witherDied();
                plugin.saveWitherState();
                plugin.logInfo("Wither mort, état réinitialisé.");
            }
        }
    }

    private void witherDied() {
        currentWither = null;
        witherSpawned = false;
    }

    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        String minuteWord = minutes == 1 ? plugin.getLanguageMessage("messages.Time minute") : plugin.getLanguageMessage("messages.Time minutes");
        String secondWord = remainingSeconds == 1 ? plugin.getLanguageMessage("messages.Time second") : plugin.getLanguageMessage("messages.Time seconds");

        if (minutes > 0 && remainingSeconds > 0) {
            return minutes + " " + minuteWord + plugin.getLanguageMessage("messages.Time and") + remainingSeconds + " " + secondWord;
        } else if (minutes > 0) {
            return minutes + " " + minuteWord;
        } else {
            return remainingSeconds + " " + secondWord;
        }
    }

    public boolean isWitherSpawned() {
        return witherSpawned;
    }

    public void setWitherSpawned(boolean witherSpawned) {
        this.witherSpawned = witherSpawned;
    }
}
