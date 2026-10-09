package com.plexdev.plexkillstreaks.listener;

import com.plexdev.plexkillstreaks.manager.StreakManager;
import com.plexdev.plexkillstreaks.config.PluginSettings;
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
        if (streakManager.getPlugin().getSettings().isWorldDisabled(victim.getWorld().getName())) {
            return;
        }
        Player killer = victim.getKiller();
        int rewardStreak = 0;

        /*
         * Handle the killer.
         */
        if (killer != null && killer != victim) {

            streakManager.ensureLoaded(killer);

            if (streakManager.isLoaded(killer) && allowKill(killer, victim)) {

                int previous = streakManager.getCurrentStreak(killer);

                int streak =
                        streakManager.addKill(killer);

                if (streak > previous) {
                    sendActionBar(
                            killer,
                            streak
                    );

                    sendMilestone(
                            killer,
                            streak
                    );
                    rewardStreak = streak;
                }
            }
        }

        /*
         * Handle the victim.
         */
        streakManager.ensureLoaded(victim);

        if (streakManager.isLoaded(victim)) {
            int oldStreak =
                streakManager.getCurrentStreak(victim);

            if (oldStreak >= 3) {

            sendStreakEnded(
                    victim,
                    oldStreak
            );
            }

            streakManager.resetStreak(victim);
        }
        if (rewardStreak > 0) runRewards(killer, rewardStreak);
    }

    private boolean allowKill(Player killer, Player victim) {
        var plugin = streakManager.getPlugin();
        if (!plugin.getSettings().antiFarmEnabled()) return true;
        if (plugin.getAntiFarmTracker().tryCount(killer.getUniqueId(), victim.getUniqueId())) return true;
        if (plugin.getAntiFarmTracker().isSaturated()) plugin.warnAntiFarmCapacity();
        if (plugin.getSettings().notifyKiller()) {
            String message = plugin.getConfig().getString("messages.anti-farming",
                    "<prefix> <gray>This kill did not count because you have already killed this player too many times recently.</gray>");
            killer.sendMessage(miniMessage.deserialize(formatMessage(message, killer, streakManager.getCurrentStreak(killer))));
        }
        return false;
    }

    private void runRewards(Player player, int streak) {
        var plugin = streakManager.getPlugin();
        PluginSettings.Milestone milestone = plugin.getSettings().milestone(streak);
        if (!plugin.getSettings().rewardsEnabled() || milestone == null) return;
        String name = player.getName();
        String uuid = player.getUniqueId().toString();
        String highest = String.valueOf(streakManager.getHighestStreak(player));
        for (var reward : milestone.rewards()) {
            String command = reward.command().replace("{player}", name).replace("{uuid}", uuid)
                    .replace("{streak}", String.valueOf(streak)).replace("{highest}", highest);
            try {
                if (!Bukkit.dispatchCommand(reward.console() ? Bukkit.getConsoleSender() : player, command)) {
                    plugin.getLogger().warning("Milestone " + streak + " reward command was not accepted.");
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Milestone " + streak + " reward command failed (" + exception.getClass().getSimpleName() + ").");
            }
        }
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

        String message =
                streakManager.getPlugin()
                        .getConfig()
                        .getString(
                                "settings.action-bar.format",
                                "<red>🔥</red> <white>Kill Streak: <red>{streak}</red>"
                        );

        if (message == null) {
            return;
        }

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

        var milestone = streakManager.getPlugin().getSettings().milestone(streak);
        if (milestone == null) {
            return;
        }

        String message = milestone.message();

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

        if (prefix == null) {
            prefix = "";
        }

        return message
                .replace(
                        "<prefix>",
                        prefix
                )
                .replace(
                        "{player}",
                        player.getName()
                )
                .replace(
                        "{streak}",
                        String.valueOf(streak)
                );
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

        Sound sound = configPath.equals("settings.sounds.milestone")
                ? streakManager.getPlugin().getSettings().milestoneSound()
                : streakManager.getPlugin().getSettings().endedSound();
        if (sound != null) {
            player.playSound(
                    player.getLocation(),
                    sound,
                    1.0f,
                    1.0f
            );

        }
    }
}
