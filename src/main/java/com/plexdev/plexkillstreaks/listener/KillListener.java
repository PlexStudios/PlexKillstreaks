package com.plexdev.plexkillstreaks.listener;

import com.plexdev.plexkillstreaks.manager.StreakManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class KillListener implements Listener {

    private final StreakManager streakManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public KillListener(StreakManager streakManager) {
        this.streakManager = streakManager;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {

        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer != null && killer != victim) {

            int streak = streakManager.addKill(killer);

            if (streakManager.isLoaded(killer)) {
                sendActionBar(killer, streak);
                sendMilestone(killer, streak);
            }
        }

        if (!streakManager.isLoaded(victim)) {
            return;
        }

        int oldStreak =
                streakManager.getCurrentStreak(victim);

        if (oldStreak >= 3) {
            sendStreakEnded(victim, oldStreak);
        }

        streakManager.resetStreak(victim);
    }

    private void sendActionBar(
            Player player,
            int streak
    ) {

        if (!streakManager.getPlugin()
                .getConfig()
                .getBoolean(
                        "settings.action-bar.enabled",
                        true
                )) {
            return;
        }

        String message = streakManager.getPlugin()
                .getConfig()
                .getString(
                        "settings.action-bar.format",
                        "<red>🔥</red> <white>Kill Streak: <red>{streak}</red>"
                );

        message = message.replace(
                "{streak}",
                String.valueOf(streak)
        );

        player.sendActionBar(
                miniMessage.deserialize(message)
        );
    }

    private void sendMilestone(
            Player player,
            int streak
    ) {

        if (!streakManager.getPlugin()
                .getConfig()
                .getBoolean(
                        "settings.milestone-announcements.enabled",
                        true
                )) {
            return;
        }

        String path =
                "milestones." + streak;

        if (!streakManager.getPlugin()
                .getConfig()
                .getBoolean(
                        path + ".enabled",
                        false
                )) {
            return;
        }

        String message =
                streakManager.getPlugin()
                        .getConfig()
                        .getString(
                                path + ".message"
                        );

        if (message == null || message.isBlank()) {
            return;
        }

        message = formatMessage(
                message,
                player,
                streak
        );

        Bukkit.broadcast(
                miniMessage.deserialize(message)
        );

        playSound(
                player,
                "settings.sounds.milestone"
        );
    }

    private void sendStreakEnded(
            Player player,
            int streak
    ) {

        if (!streakManager.getPlugin()
                .getConfig()
                .getBoolean(
                        "settings.streak-ended-announcements.enabled",
                        true
                )) {
            return;
        }

        String message =
                streakManager.getPlugin()
                        .getConfig()
                        .getString(
                                "messages.streak-ended",
                                "<prefix> <white>{player}</white><gray>'s <red>{streak}</red> kill streak has ended!"
                        );

        message = formatMessage(
                message,
                player,
                streak
        );

        Bukkit.broadcast(
                miniMessage.deserialize(message)
        );

        playSound(
                player,
                "settings.sounds.streak-ended"
        );
    }

    private String formatMessage(
            String message,
            Player player,
            int streak
    ) {

        String prefix =
                streakManager.getPlugin()
                        .getConfig()
                        .getString(
                                "branding.prefix",
                                "<red><bold>KILLSTREAK</bold></red> <dark_gray>»</dark_gray>"
                        );

        return message
                .replace("<prefix>", prefix)
                .replace("{player}", player.getName())
                .replace("{streak}", String.valueOf(streak));
    }

    private void playSound(
            Player player,
            String configPath
    ) {

        if (!streakManager.getPlugin()
                .getConfig()
                .getBoolean(
                        "settings.sounds.enabled",
                        true
                )) {
            return;
        }

        String soundName =
                streakManager.getPlugin()
                        .getConfig()
                        .getString(configPath);

        if (soundName == null || soundName.isBlank()) {
            return;
        }

        try {

            Sound sound =
                    Sound.valueOf(
                            soundName.toUpperCase()
                    );

            player.playSound(
                    player.getLocation(),
                    sound,
                    1.0f,
                    1.0f
            );

        } catch (IllegalArgumentException exception) {

            streakManager.getPlugin()
                    .getLogger()
                    .warning(
                            "Invalid sound in config: "
                                    + soundName
                    );
        }
    }
}