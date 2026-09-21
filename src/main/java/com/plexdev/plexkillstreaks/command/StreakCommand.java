package com.plexdev.plexkillstreaks.command;

import com.plexdev.plexkillstreaks.manager.StreakManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class StreakCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final StreakManager streakManager;
    private final MiniMessage miniMessage =
            MiniMessage.miniMessage();

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

        if (args.length > 0) {

            sender.sendMessage(
                    "Usage: /" + label
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

        streakManager.ensureLoaded(player);

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
            Player player,
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
}