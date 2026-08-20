package com.plexdev.plexkillstreaks.database;

import com.plexdev.plexkillstreaks.PlexKillstreaks;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class DatabaseManager {

    private final PlexKillstreaks plugin;
    private Connection connection;

    public DatabaseManager(PlexKillstreaks plugin) {
        this.plugin = plugin;
    }

    public synchronized void connect() throws SQLException {

        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        File databaseFile =
                new File(plugin.getDataFolder(), "data.db");

        connection = DriverManager.getConnection(
                "jdbc:sqlite:" + databaseFile.getAbsolutePath()
        );

        try (PreparedStatement statement =
                     connection.prepareStatement("""
                             CREATE TABLE IF NOT EXISTS player_streaks (
                                 uuid TEXT PRIMARY KEY,
                                 current_streak INTEGER NOT NULL DEFAULT 0,
                                 highest_streak INTEGER NOT NULL DEFAULT 0
                             )
                             """)) {

            statement.executeUpdate();
        }
    }

    public synchronized void savePlayer(
            UUID uuid,
            int currentStreak,
            int highestStreak
    ) {
        try {
            if (connection == null ||
                    connection.isClosed()) {
                return;
            }

            try (PreparedStatement statement =
                         connection.prepareStatement("""
                             INSERT INTO player_streaks
                                 (uuid, current_streak, highest_streak)
                             VALUES (?, ?, ?)
                             ON CONFLICT(uuid) DO UPDATE SET
                                 current_streak = excluded.current_streak,
                                 highest_streak = excluded.highest_streak
                                 """)) {

                        statement.setString(
                            1,
                            uuid.toString()
                        );

                        statement.setInt(
                            2,
                            currentStreak
                        );

                        statement.setInt(
                            3,
                            highestStreak
                        );

                        statement.executeUpdate();
                        }

        } catch (SQLException exception) {

            plugin.getLogger().severe(
                    "Failed to save streak data for "
                            + uuid + ": "
                            + exception.getMessage()
            );
        }
    }

    public synchronized PlayerData loadPlayer(
            UUID uuid
    ) throws SQLException {

        if (connection == null ||
                connection.isClosed()) {

            throw new SQLException(
                    "Database connection is not available."
            );
        }

        try (PreparedStatement statement =
                     connection.prepareStatement("""
                             SELECT current_streak, highest_streak
                             FROM player_streaks
                             WHERE uuid = ?
                             """)) {

            statement.setString(
                    1,
                    uuid.toString()
            );

            try (ResultSet result =
                         statement.executeQuery()) {

                if (result.next()) {

                    return new PlayerData(
                            result.getInt(
                                    "current_streak"
                            ),
                            result.getInt(
                                    "highest_streak"
                            )
                    );
                }
            }
        }

        return new PlayerData(0, 0);
    }

    public synchronized void close() {

        if (connection == null) {
            return;
        }

        try {

            if (!connection.isClosed()) {
                connection.close();
            }

        } catch (SQLException exception) {

            plugin.getLogger().severe(
                    "Failed to close database: "
                            + exception.getMessage()
            );

        } finally {
            connection = null;
        }
    }

    public record PlayerData(
            int currentStreak,
            int highestStreak
    ) {
    }
}