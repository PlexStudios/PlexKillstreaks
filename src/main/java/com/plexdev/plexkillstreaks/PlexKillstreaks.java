package com.plexdev.plexkillstreaks;

import com.plexdev.plexkillstreaks.command.ReloadCommand;
import com.plexdev.plexkillstreaks.command.StreakCommand;
import com.plexdev.plexkillstreaks.database.DatabaseManager;
import com.plexdev.plexkillstreaks.listener.KillListener;
import com.plexdev.plexkillstreaks.listener.PlayerDataListener;
import com.plexdev.plexkillstreaks.manager.StreakManager;
import com.plexdev.plexkillstreaks.manager.AntiFarmTracker;
import com.plexdev.plexkillstreaks.config.PluginSettings;
import com.plexdev.plexkillstreaks.placeholder.KillstreakPlaceholder;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

public final class PlexKillstreaks extends JavaPlugin {

    private StreakManager streakManager;
    private DatabaseManager databaseManager;
    private PluginSettings settings;
    private AntiFarmTracker antiFarmTracker;
    private org.bukkit.scheduler.BukkitTask cleanupTask;
    private long lastCapacityWarning;

    public PluginSettings getSettings() { return settings; }
    public AntiFarmTracker getAntiFarmTracker() { return antiFarmTracker; }

    public void reloadSettings() {
        PluginSettings previous = settings;
        settings = PluginSettings.load(getConfig(), getLogger());
        if (previous == null || previous.windowSeconds() != settings.windowSeconds()
                || previous.maxCountedKillsPerVictim() != settings.maxCountedKillsPerVictim()
                || previous.antiFarmEnabled() != settings.antiFarmEnabled()) {
            antiFarmTracker = new AntiFarmTracker(settings.windowSeconds(), settings.maxCountedKillsPerVictim(), () -> System.nanoTime() / 1_000_000_000L);
        }
    }

    public void warnAntiFarmCapacity() {
        long now = System.nanoTime();
        if (lastCapacityWarning == 0 || now - lastCapacityWarning >= java.util.concurrent.TimeUnit.MINUTES.toNanos(1)) {
            getLogger().warning("Anti-farming pair capacity reached; new pairs are temporarily rejected until entries expire.");
            lastCapacityWarning = now;
        }
    }

    @Override
    public void onEnable() {

        saveDefaultConfig();
        reloadSettings();

        databaseManager =
                new DatabaseManager(this);

        try {

            databaseManager.connect();

        } catch (SQLException exception) {

            getLogger().severe(
                    "Could not connect to the SQLite database."
            );

            exception.printStackTrace();

            getServer()
                    .getPluginManager()
                    .disablePlugin(this);

            return;
        }

        streakManager =
                new StreakManager(
                        this,
                        databaseManager
                );

        cleanupTask = getServer().getScheduler().runTaskTimer(this, () -> antiFarmTracker.cleanup(), 1200L, 1200L);

        for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) {
            streakManager.loadPlayer(player);
        }

        getServer()
                .getPluginManager()
                .registerEvents(
                        new KillListener(
                                streakManager
                        ),
                        this
                );

        getServer()
                .getPluginManager()
                .registerEvents(
                        new PlayerDataListener(
                                streakManager
                        ),
                        this
                );

        if (getCommand("streak") != null) {

            StreakCommand streakCommand = new StreakCommand(this, streakManager);
            getCommand("streak").setExecutor(streakCommand);
            getCommand("streak").setTabCompleter(streakCommand);
        }

        if (getCommand("streakreload") != null) {

            getCommand("streakreload")
                    .setExecutor(
                            new ReloadCommand(this)
                    );
        }

        if (getServer()
                .getPluginManager()
                .getPlugin("PlaceholderAPI") != null) {

            new KillstreakPlaceholder(
                    this,
                    streakManager
            ).register();

            getLogger().info(
                    "PlaceholderAPI integration enabled."
            );
        }

        getLogger().info(
                "Plex Killstreaks has been enabled."
        );
    }

    @Override
    public void onDisable() {
        if (cleanupTask != null) cleanupTask.cancel();
        if (antiFarmTracker != null) antiFarmTracker.clear();

        if (streakManager != null) {

            getLogger().info(
                    "Saving pending streak data..."
            );

            streakManager.shutdown();
        }

        if (databaseManager != null) {
            databaseManager.close();
        }

        getLogger().info(
                "Plex Killstreaks has been disabled."
        );
    }

    public StreakManager getStreakManager() {
        return streakManager;
    }
}
