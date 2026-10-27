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
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public final class PlexKillstreaks extends JavaPlugin {

    private StreakManager streakManager;
    private DatabaseManager databaseManager;
    private PluginSettings settings;
    private AntiFarmTracker antiFarmTracker;
    private org.bukkit.scheduler.BukkitTask cleanupTask;
    private long lastCapacityWarning;
    private FileConfiguration activeConfig;
    private KillstreakPlaceholder placeholder;

    @Override
    public FileConfiguration getConfig() {
        if (activeConfig == null) reloadConfig();
        return activeConfig;
    }

    @Override
    public void reloadConfig() {
        var candidate = new YamlConfiguration();
        try {
            candidate.load(new File(getDataFolder(), "config.yml"));
            try (var defaults = getResource("config.yml")) {
                if (defaults != null) {
                    candidate.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defaults, StandardCharsets.UTF_8)));
                }
            }
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalArgumentException("Could not reload config.yml; active configuration was retained.", exception);
        }
        applySettings(PluginSettings.load(candidate, getLogger()));
        activeConfig = candidate;
    }

    public PluginSettings getSettings() { return settings; }
    public AntiFarmTracker getAntiFarmTracker() { return antiFarmTracker; }

    public void reloadSettings() {
        applySettings(PluginSettings.load(getConfig(), getLogger()));
    }

    private void applySettings(PluginSettings candidate) {
        PluginSettings previous = settings;
        settings = candidate;
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
                .isPluginEnabled("PlaceholderAPI")) {

            placeholder = new KillstreakPlaceholder(
                    this,
                    streakManager
            );

            if (placeholder.register()) getLogger().info(
                    "PlaceholderAPI integration enabled."
            );
            else getLogger().warning("PlaceholderAPI expansion registration failed.");
        }

        getLogger().info(
                "Plex Killstreaks has been enabled."
        );
    }

    @Override
    public void onDisable() {
        if (placeholder != null) { placeholder.unregister(); placeholder = null; }
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
