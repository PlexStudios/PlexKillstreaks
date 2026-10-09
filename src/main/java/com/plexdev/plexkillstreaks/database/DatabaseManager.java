package com.plexdev.plexkillstreaks.database;

import com.plexdev.plexkillstreaks.PlexKillstreaks;
import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DatabaseManager {
    private final PlexKillstreaks plugin;
    private Connection connection;

    public DatabaseManager(PlexKillstreaks plugin) {
        this.plugin = plugin;
    }

    public synchronized void connect() throws SQLException {
        if (!plugin.getDataFolder().isDirectory() && !plugin.getDataFolder().mkdirs()) {
            throw new SQLException("Cannot create plugin data directory.");
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + new File(plugin.getDataFolder(), "data.db").getAbsolutePath());
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS player_streaks (
                        uuid TEXT PRIMARY KEY,
                        current_streak INTEGER NOT NULL DEFAULT 0,
                        highest_streak INTEGER NOT NULL DEFAULT 0
                    )
                    """);
            boolean hasName = false;
            try (var columns = statement.executeQuery("PRAGMA table_info(player_streaks)")) {
                while (columns.next()) {
                    if ("last_known_name".equalsIgnoreCase(columns.getString("name"))) {
                        hasName = true;
                    }
                }
            }
            if (!hasName) {
                statement.executeUpdate("ALTER TABLE player_streaks ADD COLUMN last_known_name TEXT");
            }
        } catch (SQLException exception) {
            try { connection.close(); } catch (SQLException closeFailure) { exception.addSuppressed(closeFailure); }
            connection = null;
            throw exception;
        }
    }

    private void requireConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("Database connection is not available.");
        }
    }

    public synchronized void savePlayer(UUID uuid, int currentStreak, int highestStreak) throws SQLException {
        savePlayer(uuid, currentStreak, highestStreak, null);
    }

    public synchronized void savePlayer(UUID uuid, int currentStreak, int highestStreak, String name) throws SQLException {
        requireConnection();
        if (currentStreak < 0 || highestStreak < currentStreak) {
            throw new SQLException("Invalid streak values.");
        }
        try (var statement = connection.prepareStatement("""
                INSERT INTO player_streaks (uuid,current_streak,highest_streak,last_known_name)
                VALUES (?,?,?,?)
                ON CONFLICT(uuid) DO UPDATE SET
                    current_streak = excluded.current_streak,
                    highest_streak = excluded.highest_streak,
                    last_known_name = COALESCE(excluded.last_known_name,player_streaks.last_known_name)
                """)) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, currentStreak);
            statement.setInt(3, highestStreak);
            statement.setString(4, name == null || name.isBlank() ? null : name);
            statement.executeUpdate();
        }
    }

    public synchronized PlayerData loadPlayer(UUID uuid) throws SQLException {
        requireConnection();
        try (var statement = connection.prepareStatement("SELECT current_streak,highest_streak FROM player_streaks WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (var result = statement.executeQuery()) {
                if (result.next()) {
                    int current = result.getInt(1);
                    int highest = result.getInt(2);
                    if (current < 0 || highest < current) {
                        throw new SQLException("Invalid stored streak values for " + uuid);
                    }
                    return new PlayerData(current, highest);
                }
            }
        }
        return new PlayerData(0, 0);
    }

    public synchronized LeaderboardPage getLeaderboard(int requestedPage, int pageSize) throws SQLException {
        requireConnection();
        if (requestedPage < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("Invalid leaderboard pagination.");
        }
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            long total;
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT COUNT(*) FROM player_streaks WHERE highest_streak > 0")) {
                total = result.getLong(1);
            }
            int pages = (int) Math.min(Integer.MAX_VALUE, Math.max(1, (total + pageSize - 1) / pageSize));
            int page = Math.min(requestedPage, pages);
            List<LeaderboardEntry> entries = new ArrayList<>();
            try (var statement = connection.prepareStatement("""
                    SELECT uuid,last_known_name,highest_streak FROM player_streaks
                    WHERE highest_streak > 0 ORDER BY highest_streak DESC,uuid ASC LIMIT ? OFFSET ?
                    """)) {
                statement.setInt(1, pageSize);
                statement.setLong(2, (long) (page - 1) * pageSize);
                try (var result = statement.executeQuery()) {
                    while (result.next()) {
                        String uuid = result.getString(1);
                        String name = result.getString(2);
                        entries.add(new LeaderboardEntry(uuid, name == null || name.isBlank() ? uuid : name, result.getInt(3)));
                    }
                }
            }
            connection.commit();
            return new LeaderboardPage(List.copyOf(entries), page, pages, total);
        } catch (SQLException exception) {
            try { connection.rollback(); } catch (SQLException rollbackFailure) { exception.addSuppressed(rollbackFailure); }
            throw exception;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    public synchronized void close() {
        if (connection == null) { return; }
        try { connection.close(); }
        catch (SQLException exception) { plugin.getLogger().severe("Failed to close database: " + exception.getMessage()); }
        finally { connection = null; }
    }

    public record PlayerData(int currentStreak, int highestStreak) {}
    public record LeaderboardEntry(String uuid, String player, int highest) {}
    public record LeaderboardPage(List<LeaderboardEntry> entries, int page, int pages, long total) {}
}
