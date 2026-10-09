package com.plexdev.plexkillstreaks.database;

import com.plexdev.plexkillstreaks.PlexKillstreaks;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.sql.*;
import java.util.UUID;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatabaseManagerTest {
    @TempDir Path directory;
    DatabaseManager database;
    final UUID legacy = new UUID(0, 1);

    @BeforeEach void setup() {
        var plugin = mock(PlexKillstreaks.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        database = new DatabaseManager(plugin);
    }

    Connection raw() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("data.db"));
    }

    @AfterEach void close() { database.close(); }

    @Test void legacySchemaMigratesAdditivelyAndIdempotently() throws Exception {
        try (var connection = raw(); var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE player_streaks(uuid TEXT PRIMARY KEY,current_streak INTEGER NOT NULL,highest_streak INTEGER NOT NULL,extra TEXT)");
            sql.execute("INSERT INTO player_streaks VALUES ('" + legacy + "',7,25,'preserved')");
        }
        database.connect();
        database.close();
        database.connect();
        assertEquals(new DatabaseManager.PlayerData(7,25), database.loadPlayer(legacy));
        try (var connection = raw(); var sql = connection.createStatement(); var result = sql.executeQuery("SELECT extra,last_known_name FROM player_streaks")) {
            assertTrue(result.next());
            assertEquals("preserved", result.getString(1));
            assertNull(result.getString(2));
        }
    }

    @Test void leaderboardOrdersAndPaginatesPersistedPlayers() throws Exception {
        database.connect();
        database.savePlayer(new UUID(0,3), 0, 25, "Third");
        database.savePlayer(legacy, 7, 25, "First");
        database.savePlayer(new UUID(0,2), 0, 50, "Leader");
        database.savePlayer(new UUID(0,4), 0, 0, "Zero");
        var first = database.getLeaderboard(1, 2);
        assertEquals(3, first.total());
        assertEquals(2, first.pages());
        assertEquals(java.util.List.of("Leader", "First"), first.entries().stream().map(DatabaseManager.LeaderboardEntry::player).toList());
        assertEquals("Third", database.getLeaderboard(2,2).entries().getFirst().player());
        assertEquals(2, database.getLeaderboard(Integer.MAX_VALUE,2).page());
    }

    @Test void unnamedRowsUseStoredUuidAndOldSavePreservesName() throws Exception {
        database.connect();
        database.savePlayer(legacy, 1, 9);
        assertEquals(legacy.toString(), database.getLeaderboard(1,10).entries().getFirst().player());
        database.savePlayer(legacy, 2, 9, "Known");
        database.savePlayer(legacy, 3, 9);
        assertEquals("Known", database.getLeaderboard(1,10).entries().getFirst().player());
    }

    @Test void emptyLeaderboardExcludesZeroScores() throws Exception {
        database.connect();
        database.savePlayer(legacy,0,0);
        assertTrue(database.getLeaderboard(1,10).entries().isEmpty());
        assertEquals(1,database.getLeaderboard(1,10).pages());
    }

    @Test void closedConnectionSaveFails() throws Exception {
        database.connect();
        database.close();
        assertThrows(SQLException.class, () -> database.savePlayer(legacy,1,1));
    }

    @Test void sqliteRejectedWritePropagates() throws Exception {
        database.connect();
        try (var connection = raw(); var sql = connection.createStatement()) {
            sql.execute("CREATE TRIGGER reject_write BEFORE INSERT ON player_streaks BEGIN SELECT RAISE(ABORT,'write rejected'); END");
        }
        assertThrows(SQLException.class, () -> database.savePlayer(legacy,1,1));
    }
}
