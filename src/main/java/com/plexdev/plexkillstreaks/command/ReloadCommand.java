package com.plexdev.plexkillstreaks.command;

import com.plexdev.plexkillstreaks.PlexKillstreaks;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ReloadCommand implements CommandExecutor {

    private final PlexKillstreaks plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ReloadCommand(PlexKillstreaks plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {

        if (!sender.hasPermission("plexkillstreaks.reload")) {
            sender.sendMessage(
                    miniMessage.deserialize(
                            "<red>You don't have permission to do that.</red>"
                    )
            );
            return true;
        }

        plugin.reloadConfig();

        sender.sendMessage(
                miniMessage.deserialize(
                        "<red><bold>Plex Killstreaks</bold></red> <dark_gray>»</dark_gray> <gray>Configuration reloaded.</gray>"
                )
        );

        plugin.getLogger().info(
                sender.getName() + " reloaded the configuration."
        );

        return true;
    }
}