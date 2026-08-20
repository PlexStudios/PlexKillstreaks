package com.plexdev.plexkillstreaks;

import com.plexdev.plexkillstreaks.command.ReloadCommand;
import com.plexdev.plexkillstreaks.command.StreakCommand;
import com.plexdev.plexkillstreaks.database.DatabaseManager;
import com.plexdev.plexkillstreaks.listener.KillListener;
import com.plexdev.plexkillstreaks.listener.PlayerDataListener;
import com.plexdev.plexkillstreaks.manager.StreakManager;
import com.plexdev.plexkillstreaks.placeholder.KillstreakPlaceholder;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

public final class PlexKillstreaks extends JavaPlugin {

    private StreakManager streakManager;
    private DatabaseManager databaseManager;

    @Override
    public void onEnable() {

        saveDefaultConfig();

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

            getCommand("streak")
                    .setExecutor(
                            new StreakCommand(
                                    this,
                                    streakManager
                            )
                    );
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