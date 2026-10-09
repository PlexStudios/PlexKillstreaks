package com.plexdev.plexkillstreaks.manager;

import com.plexdev.plexkillstreaks.PlexKillstreaks;
import com.plexdev.plexkillstreaks.config.PluginSettings;
import com.plexdev.plexkillstreaks.database.DatabaseManager;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.*;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StreakManagerTest {
    PlexKillstreaks plugin;
    DatabaseManager database;
    StreakManager manager;
    Player player;
    BlockingQueue<Runnable> callbacks;
    Logger logger;
    @BeforeEach void setup() throws Exception {
        plugin = mock(PlexKillstreaks.class);
        database = mock(DatabaseManager.class);
        player = mock(Player.class);
        logger = mock(Logger.class);
        callbacks = new LinkedBlockingQueue<>();
        var server = mock(Server.class);
        var scheduler = mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(plugin.getLogger()).thenReturn(logger);
        when(player.getUniqueId()).thenReturn(new UUID(0,1));
        when(player.getName()).thenReturn("Player");
        when(player.isOnline()).thenReturn(true);
        doReturn(List.of(player)).when(server).getOnlinePlayers();
        doAnswer(call -> { callbacks.add(call.getArgument(1)); return null; }).when(scheduler).runTask(eq(plugin),any(Runnable.class));
        when(database.loadPlayer(any())).thenReturn(new DatabaseManager.PlayerData(2,8));
        var config = new YamlConfiguration();
        config.set("settings.sounds.milestone", ""); config.set("settings.sounds.streak-ended", "");
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getSettings()).thenReturn(PluginSettings.load(config,logger));
        manager = new StreakManager(plugin,database);
    }
    void callback() throws Exception { var task = callbacks.poll(5,TimeUnit.SECONDS); assertNotNull(task); task.run(); }
    @AfterEach void shutdown() { manager.shutdown(); }
    @Test void delayedLoadDoesNotOverwriteFallbackGameplay() throws Exception {
        manager.loadPlayer(player);
        var delayed = callbacks.poll(5,TimeUnit.SECONDS); assertNotNull(delayed);
        manager.ensureLoaded(player);
        assertEquals(3,manager.addKill(player));
        delayed.run();
        assertEquals(3,manager.getCurrentStreak(player));
        assertEquals(8,manager.getHighestStreak(player));
    }
    @Test void failedFallbackAndAsyncLoadRemainUnavailable() throws Exception {
        when(database.loadPlayer(any())).thenThrow(new SQLException("unavailable"));
        manager.loadPlayer(player); callback(); manager.ensureLoaded(player);
        assertFalse(manager.isLoaded(player));
        manager.addKill(player); manager.resetStreak(player);
        assertThrows(CompletionException.class, () -> manager.savePlayer(player).join());
        verify(database,never()).savePlayer(any(),anyInt(),anyInt(),any());
    }
    @Test void delayedFailureDoesNotClearSuccessfulFallback() throws Exception {
        when(database.loadPlayer(any())).thenThrow(new SQLException("unavailable")).thenReturn(new DatabaseManager.PlayerData(2,8));
        manager.loadPlayer(player);
        var delayed = callbacks.poll(5,TimeUnit.SECONDS); assertNotNull(delayed);
        manager.ensureLoaded(player); manager.addKill(player); delayed.run();
        assertTrue(manager.isLoaded(player)); assertEquals(3,manager.getCurrentStreak(player));
    }
    @Test void retryAfterLoadFailureSucceeds() throws Exception {
        when(database.loadPlayer(any())).thenThrow(new SQLException("unavailable")).thenReturn(new DatabaseManager.PlayerData(2,8));
        manager.ensureLoaded(player); assertFalse(manager.isLoaded(player));
        manager.ensureLoaded(player); assertTrue(manager.isLoaded(player));
    }
    @Test void staleSessionCannotOverwriteReconnect() throws Exception {
        manager.loadPlayer(player);
        var stale = callbacks.poll(5,TimeUnit.SECONDS); assertNotNull(stale);
        manager.removePlayer(player);
        when(database.loadPlayer(any())).thenReturn(new DatabaseManager.PlayerData(7,9));
        manager.loadPlayer(player); callback(); stale.run();
        assertEquals(7,manager.getCurrentStreak(player));
    }
    @Test void quitBeforeLoadDoesNotWriteZero() throws Exception {
        manager.loadPlayer(player); manager.removePlayer(player); callback();
        assertFalse(manager.isLoaded(player));
        verify(database,never()).savePlayer(any(),anyInt(),anyInt(),any());
    }
    @Test void laterSaveRecoversAfterFailedFutureInOrder() throws Exception {
        manager.ensureLoaded(player);
        manager.savePlayer(player).get(5,TimeUnit.SECONDS);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(call -> { entered.countDown(); assertTrue(release.await(5,TimeUnit.SECONDS)); throw new SQLException("rejected"); })
                .doNothing().when(database).savePlayer(any(),anyInt(),anyInt(),any());
        var failed = manager.savePlayer(player);
        assertTrue(entered.await(5,TimeUnit.SECONDS));
        var recovered = manager.savePlayer(player);
        release.countDown();
        assertThrows(ExecutionException.class, () -> failed.get(5,TimeUnit.SECONDS));
        recovered.get(5,TimeUnit.SECONDS);
        verify(logger,times(1)).severe(contains("Failed to save"));
    }
    @Test void leaderboardRunsOnDatabaseWorker() throws Exception {
        Thread owner = Thread.currentThread();
        when(database.getLeaderboard(1,10)).thenAnswer(call -> { assertNotSame(owner,Thread.currentThread()); return new DatabaseManager.LeaderboardPage(List.of(),1,1,0); });
        assertEquals(0,manager.getLeaderboard(1,10).get(5,TimeUnit.SECONDS).total());
    }
    @Test void shutdownDrainsLoadedState() throws Exception {
        manager.ensureLoaded(player); manager.addKill(player); manager.shutdown();
        verify(database,atLeastOnce()).savePlayer(player.getUniqueId(),3,8,"Player");
        assertTrue(manager.getLeaderboard(1,10).isCompletedExceptionally());
    }
    @Test void leaderboardQueueIsBoundedAndAdmissionRecovers() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(database.getLeaderboard(1,10)).thenAnswer(call -> {
            entered.countDown(); assertTrue(release.await(5,TimeUnit.SECONDS));
            return new DatabaseManager.LeaderboardPage(List.of(),1,1,0);
        });
        List<CompletableFuture<?>> requests = new ArrayList<>();
        try {
            for (int i=0;i<16;i++) requests.add(manager.getLeaderboard(1,10));
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            assertTrue(manager.getLeaderboard(1,10).isCompletedExceptionally());
        } finally { release.countDown(); }
        CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).get(5,TimeUnit.SECONDS);
        assertEquals(0,manager.getLeaderboard(1,10).get(5,TimeUnit.SECONDS).total());
    }
}
