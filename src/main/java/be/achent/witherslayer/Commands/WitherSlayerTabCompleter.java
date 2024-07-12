package be.achent.witherslayer.Commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WitherSlayerTabCompleter implements TabCompleter {

    private static final List<String> ARGUMENTS = Arrays.asList("leaderboard", "reload", "forcespawn");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            for (String a : ARGUMENTS) {
                if (a.toLowerCase().startsWith(args[0].toLowerCase())) {
                    if (sender.hasPermission("witherslayer." + a)) {
                        result.add(a);
                    }
                }
            }
            return result;
        }
        return null;
    }
}
