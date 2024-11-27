package be.achent.witherslayer.Placeholders;

import be.achent.witherslayer.WitherSlayer;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class WitherSlayerPlaceholder extends PlaceholderExpansion {

    private final WitherSlayer plugin;

    public WitherSlayerPlaceholder(WitherSlayer plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "witherslayer";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Achent";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (identifier.equals("nextspawn")) {
            String nextSpawnTimeStr = plugin.getWitherRespawnEvent().getNextSpawnTime();
            if (nextSpawnTimeStr == null || nextSpawnTimeStr.isEmpty()) {
                return "Unknown";
            }

            try {
                DateTimeFormatter formatterWithDate = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                DateTimeFormatter formatterWithoutDate = DateTimeFormatter.ofPattern("HH:mm");

                LocalDateTime nextSpawnTime;
                if (nextSpawnTimeStr.length() > 5) { // Si la chaîne est au format complet
                    nextSpawnTime = LocalDateTime.parse(nextSpawnTimeStr, formatterWithDate);
                } else {
                    nextSpawnTime = LocalDateTime.now().withHour(Integer.parseInt(nextSpawnTimeStr.split(":")[0]))
                            .withMinute(Integer.parseInt(nextSpawnTimeStr.split(":")[1]));
                }

                return nextSpawnTime.format(DateTimeFormatter.ofPattern("HH:mm")); // Affiche seulement l'heure et les minutes
            } catch (DateTimeParseException e) {
                plugin.getLogger().warning("Failed to parse nextSpawnTime: " + nextSpawnTimeStr);
                return "Invalid Date Format";
            }
        }
        return null;
    }
}
