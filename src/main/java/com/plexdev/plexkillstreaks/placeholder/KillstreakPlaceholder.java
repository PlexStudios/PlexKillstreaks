package com.plexdev.plexkillstreaks.placeholder;

import com.plexdev.plexkillstreaks.PlexKillstreaks;
import com.plexdev.plexkillstreaks.manager.StreakManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class KillstreakPlaceholder extends PlaceholderExpansion {

    private final PlexKillstreaks plugin;
    private final StreakManager streakManager;

    public KillstreakPlaceholder(
            PlexKillstreaks plugin,
            StreakManager streakManager
    ) {
        this.plugin = plugin;
        this.streakManager = streakManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "plexkillstreaks";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Plex";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return false;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(
            Player player,
            @NotNull String params
    ) {

        if (player == null) {
            return "0";
        }

        if (!streakManager.isLoaded(player)) {
            return switch (params.toLowerCase()) {
                case "current",
                     "highest",
                     "next_milestone",
                     "progress" -> "0";

                case "is_active",
                     "loaded" -> "false";

                default -> null;
            };
        }

        return switch (params.toLowerCase()) {

            case "current" ->
                    String.valueOf(
                            streakManager.getCurrentStreak(player)
                    );

            case "highest" ->
                    String.valueOf(
                            streakManager.getHighestStreak(player)
                    );

            case "next_milestone" -> {

                int next =
                        streakManager.getNextMilestone(player);

                yield next == -1
                        ? "0"
                        : String.valueOf(next);
            }

            case "progress" ->
                    streakManager.getProgressBar(player);

            case "is_active" ->
                    String.valueOf(
                            streakManager.getCurrentStreak(player) > 0
                    );

            case "loaded" ->
                    "true";

            default -> null;
        };
    }
}