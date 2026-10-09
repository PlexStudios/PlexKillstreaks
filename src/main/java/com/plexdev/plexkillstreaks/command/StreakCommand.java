package com.plexdev.plexkillstreaks.command;

import com.plexdev.plexkillstreaks.manager.StreakManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.TabExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class StreakCommand implements TabExecutor {

    private final JavaPlugin plugin;
    private final StreakManager streakManager;
    private final MiniMessage miniMessage =
            MiniMessage.miniMessage();
    private final java.util.Set<CommandSender> leaderboardSenders = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public StreakCommand(
            JavaPlugin plugin,
            StreakManager streakManager
    ) {
        this.plugin = plugin;
        this.streakManager = streakManager;
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {

        if (args.length > 0 && args[0].equalsIgnoreCase("top")) {
            return showLeaderboard(sender, label, args);
        }

        if (args.length > 0) {

            sender.sendMessage(
                    "Usage: /" + label + " [top [page]]"
            );

            return true;
        }

        if (!(sender instanceof Player player)) {

            sender.sendMessage(
                    "This command can only be used by players."
            );

            return true;
        }

        if (!player.hasPermission("plexkillstreaks.streak")) {

            send(
                    player,
                    plugin.getConfig()
                            .getString(
                                    "messages.no-permission",
                                    "<prefix> <red>You don't have permission to use this command."
                            )
            );

            return true;
        }

        if (!streakManager.isLoaded(player)) {

            send(
                    player,
                    plugin.getConfig()
                            .getString(
                                    "messages.loading",
                                    "<prefix> <gray>Your streak data is still loading..."
                            )
            );

            return true;
        }

        int current =
                streakManager.getCurrentStreak(player);

        int highest =
                streakManager.getHighestStreak(player);

        int next =
                streakManager.getNextMilestone(player);

        String header =
                plugin.getConfig()
                        .getString(
                                "messages.streak.header",
                                ""
                        );

        String separator =
                plugin.getConfig()
                        .getString(
                                "messages.streak.separator",
                                ""
                        );

        String currentMessage =
                plugin.getConfig()
                        .getString(
                                "messages.streak.current",
                                "<gray>Current Streak: <red>{current}</red>"
                        );

        String highestMessage =
                plugin.getConfig()
                        .getString(
                                "messages.streak.highest",
                                "<gray>Highest Streak: <gold>{highest}</gold>"
                        );

        String nextMessage;

        if (next == -1) {

            nextMessage =
                    plugin.getConfig()
                            .getString(
                                    "messages.streak.no-next",
                                    "<gold>🏆</gold> <gray>You have reached every configured milestone!"
                            );

        } else {

            nextMessage =
                    plugin.getConfig()
                            .getString(
                                    "messages.streak.next",
                                    "<yellow>⚡</yellow> <gray>Next Milestone: <red>{next}</red>"
                            )
                            .replace(
                                    "{next}",
                                    String.valueOf(next)
                            );
        }

        String progress =
                plugin.getConfig()
                        .getString(
                                "messages.streak.progress",
                                "<gray>Progress: {bar} <dark_gray>({current}/{next})</dark_gray>"
                        );

        if (next == -1) {

            progress = "";

        } else {

            progress =
                    progress
                            .replace(
                                    "{bar}",
                                    streakManager
                                            .getProgressBar(player)
                            )
                            .replace(
                                    "{current}",
                                    String.valueOf(current)
                            )
                            .replace(
                                    "{next}",
                                    String.valueOf(next)
                            );
        }

        String footer =
                plugin.getConfig()
                        .getString(
                                "messages.streak.footer",
                                ""
                        );

        send(player, header);
        send(player, separator);
        send(
                player,
                currentMessage.replace(
                        "{current}",
                        String.valueOf(current)
                )
        );

        send(
                player,
                highestMessage.replace(
                        "{highest}",
                        String.valueOf(highest)
                )
        );

        send(player, nextMessage);
        send(player, progress);
        send(player, separator);
        send(player, footer);

        return true;
    }

    private void send(
            CommandSender player,
            String message
    ) {

        if (message == null ||
                message.isBlank()) {
            return;
        }

        String prefix =
                plugin.getConfig()
                        .getString(
                                "branding.prefix",
                                ""
                        );

        message =
                message.replace(
                        "<prefix>",
                        prefix
                );

        player.sendMessage(
                miniMessage.deserialize(message)
        );
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("plexkillstreaks.top")
                && "top".startsWith(args[0].toLowerCase(java.util.Locale.ROOT))) {
            return java.util.List.of("top");
        }
        return java.util.List.of();
    }

    private boolean showLeaderboard(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("plexkillstreaks.top")) {
            send(sender, plugin.getConfig().getString("messages.no-permission", "<prefix> <red>You don't have permission to use this command.</red>"));
            return true;
        }
        int page = 1;
        try {
            if (args.length > 2) throw new NumberFormatException();
            if (args.length == 2) page = Integer.parseInt(args[1]);
            if (page < 1) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            sender.sendMessage("Usage: /" + label + " top [positive page number]");
            return true;
        }
        int pageSize = streakManager.getPlugin().getSettings().pageSize();
        if (!leaderboardSenders.add(sender)) {
            send(sender, plugin.getConfig().getString("messages.leaderboard.pending", "<prefix> <gray>Your leaderboard is already loading.</gray>"));
            return true;
        }
        send(sender, plugin.getConfig().getString("messages.leaderboard.loading", "<prefix> <gray>Loading leaderboard...</gray>"));
        streakManager.getLeaderboard(page, pageSize).whenComplete((result, failure) -> {
            if (streakManager.isShuttingDown() || !plugin.isEnabled()) { leaderboardSenders.remove(sender); return; }
            try {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    leaderboardSenders.remove(sender);
                    if (streakManager.isShuttingDown() || !plugin.isEnabled()
                            || (sender instanceof Player player && !player.isOnline())) return;
                    if (failure != null) {
                        Throwable cause = failure;
                        while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) cause = cause.getCause();
                        if (cause instanceof java.util.concurrent.RejectedExecutionException) {
                            send(sender, plugin.getConfig().getString("messages.leaderboard.busy", "<prefix> <gray>The leaderboard is busy. Please try again shortly.</gray>"));
                            return;
                        }
                        plugin.getLogger().warning("Leaderboard query failed: " + cause.getMessage());
                        send(sender, plugin.getConfig().getString("messages.leaderboard.error", "<prefix> <red>Unable to load the leaderboard. Please try again.</red>"));
                        return;
                    }
                    if (result.entries().isEmpty()) {
                        send(sender, plugin.getConfig().getString("messages.leaderboard.empty", "<prefix> <gray>No leaderboard entries were found.</gray>"));
                        return;
                    }
                    long rank = (long) (result.page() - 1) * pageSize + 1;
                    sendLeaderboardLine(sender,"header", "<dark_gray>━━━━━━━━ <red><bold>TOP KILLSTREAKS</bold></red> <dark_gray>━━━━━━━━</dark_gray>", rank, "", "", result.page(), result.pages());
                    for (var entry : result.entries()) {
                        sendLeaderboardLine(sender,"entry", "<red>#{rank}</red> <white>{player}</white> <dark_gray>»</dark_gray> <gold>{highest}</gold>", rank++, entry.player(), String.valueOf(entry.highest()), result.page(), result.pages());
                    }
                    sendLeaderboardLine(sender,"footer", "<gray>Page {page}/{pages}</gray>", rank - 1, "", "", result.page(), result.pages());
                });
            } catch (org.bukkit.plugin.IllegalPluginAccessException exception) {
                leaderboardSenders.remove(sender);
                if (!streakManager.isShuttingDown()) plugin.getLogger().warning("Could not deliver leaderboard result: plugin is disabled.");
            }
        });
        return true;
    }

    private void sendLeaderboardLine(CommandSender sender, String key, String fallback, long rank, String player, String highest, int page, int pages) {
        String text = plugin.getConfig().getString("messages.leaderboard." + key, fallback)
                .replace("{rank}", String.valueOf(rank)).replace("{player}", MiniMessage.miniMessage().escapeTags(player))
                .replace("{highest}", highest).replace("{page}", String.valueOf(page)).replace("{pages}", String.valueOf(pages));
        send(sender,text);
    }
}
