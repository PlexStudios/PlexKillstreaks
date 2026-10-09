package com.plexdev.plexkillstreaks.command;

import com.plexdev.plexkillstreaks.PlexKillstreaks;
import com.plexdev.plexkillstreaks.config.PluginSettings;
import com.plexdev.plexkillstreaks.database.DatabaseManager;
import com.plexdev.plexkillstreaks.manager.StreakManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Server;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StreakCommandTest {
    PlexKillstreaks plugin; StreakManager manager; StreakCommand executor; CommandSender sender;
    Command command; CompletableFuture<DatabaseManager.LeaderboardPage> result;
    BlockingQueue<Runnable> callbacks; List<String> messages;
    @BeforeEach void setup() {
        plugin = mock(PlexKillstreaks.class); manager = mock(StreakManager.class); sender = mock(CommandSender.class); command = mock(Command.class);
        when(plugin.isEnabled()).thenReturn(true); when(plugin.getLogger()).thenReturn(mock(Logger.class));
        when(manager.getPlugin()).thenReturn(plugin);
        var config = new YamlConfiguration(); config.set("settings.sounds.milestone", ""); config.set("settings.sounds.streak-ended", "");
        when(plugin.getConfig()).thenReturn(config);
        var settings = PluginSettings.load(config,plugin.getLogger());
        when(plugin.getSettings()).thenReturn(settings);
        when(sender.hasPermission("plexkillstreaks.top")).thenReturn(true);
        var server = mock(Server.class); var scheduler = mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server); when(server.getScheduler()).thenReturn(scheduler);
        callbacks = new LinkedBlockingQueue<>(); messages = new ArrayList<>();
        doAnswer(call -> { callbacks.add(call.getArgument(1)); return null; }).when(scheduler).runTask(eq(plugin),any(Runnable.class));
        doAnswer(call -> { messages.add(PlainTextComponentSerializer.plainText().serialize(call.getArgument(0))); return null; }).when(sender).sendMessage(any(Component.class));
        result = new CompletableFuture<>(); when(manager.getLeaderboard(anyInt(),anyInt())).thenReturn(result);
        executor = new StreakCommand(plugin,manager);
    }
    void run(String... args) { executor.onCommand(sender,command,"streak",args); }
    void deliver() throws Exception { var callback = callbacks.poll(5,TimeUnit.SECONDS); assertNotNull(callback); callback.run(); }
    @Test void consoleTopLoadsThenFormatsAsyncResult() throws Exception {
        run("top","2"); assertTrue(messages.getFirst().contains("Loading"));
        Thread worker = new Thread(() -> result.complete(new DatabaseManager.LeaderboardPage(List.of(new DatabaseManager.LeaderboardEntry("id","Winner",25)),2,3,21)));
        worker.start(); worker.join(); assertEquals(1,messages.size()); deliver();
        assertTrue(messages.stream().anyMatch(text -> text.contains("#11") && text.contains("Winner") && text.contains("25")));
        assertTrue(messages.stream().anyMatch(text -> text.contains("2/3")));
    }
    @Test void topPermissionWorksWithoutStreakPermission() { run("top"); verify(manager).getLeaderboard(1,10); }
    @Test void streakPermissionDoesNotGrantTop() {
        when(sender.hasPermission("plexkillstreaks.top")).thenReturn(false); when(sender.hasPermission("plexkillstreaks.streak")).thenReturn(true);
        run("top"); verify(manager,never()).getLeaderboard(anyInt(),anyInt()); assertFalse(messages.isEmpty());
    }
    @Test void invalidPagesNeverQuery() {
        for (String value : new String[]{"0","-1","bad","2147483648"}) run("top",value);
        run("top","1","extra"); verify(manager,never()).getLeaderboard(anyInt(),anyInt());
    }
    @Test void unknownSubcommandAndNoArgConsoleRemainUseful() {
        run("unknown"); verify(sender).sendMessage(contains("top"));
        run(); verify(sender).sendMessage(contains("only be used by players"));
    }
    @Test void emptyAndFailureAreDistinct() throws Exception {
        run("top"); result.complete(new DatabaseManager.LeaderboardPage(List.of(),1,1,0)); deliver();
        assertTrue(messages.getLast().contains("No leaderboard"));
        result = new CompletableFuture<>(); when(manager.getLeaderboard(anyInt(),anyInt())).thenReturn(result);
        run("top"); result.completeExceptionally(new IllegalStateException("private failure")); deliver();
        assertTrue(messages.getLast().contains("Unable")); assertFalse(messages.getLast().contains("private failure"));
    }
    @Test void shutdownSuppressesCallbacks() {
        run("top"); when(manager.isShuttingDown()).thenReturn(true);
        result.complete(new DatabaseManager.LeaderboardPage(List.of(),1,1,0)); assertTrue(callbacks.isEmpty());
    }
    @Test void tabCompletionSuggestsTop() {
        assertEquals(List.of("top"),executor.onTabComplete(sender,command,"streak",new String[]{"t"}));
        assertTrue(executor.onTabComplete(sender,command,"streak",new String[]{"other"}).isEmpty());
    }
    @Test void aliasUsesSameTopHandler() { executor.onCommand(sender,command,"streaks",new String[]{"top"}); verify(manager).getLeaderboard(1,10); }
    @Test void disconnectedPlayerDoesNotReceiveResults() throws Exception {
        var player = mock(Player.class); when(player.hasPermission("plexkillstreaks.top")).thenReturn(true);
        executor.onCommand(player,command,"streak",new String[]{"top"}); clearInvocations(player);
        result.complete(new DatabaseManager.LeaderboardPage(List.of(),1,1,0)); deliver();
        verify(player,never()).sendMessage(any(Component.class));
    }
    @Test void unloadedStreakCommandDoesNotUseSynchronousFallback() {
        var player = mock(Player.class); when(player.hasPermission("plexkillstreaks.streak")).thenReturn(true);
        executor.onCommand(player,command,"streak",new String[0]);
        verify(manager,never()).ensureLoaded(any()); verify(player).sendMessage(any(Component.class));
    }
    @Test void loadedNoArgPlayerKeepsCurrentHighestAndProgressMenu() {
        var player = mock(Player.class); when(player.hasPermission("plexkillstreaks.streak")).thenReturn(true);
        when(manager.isLoaded(player)).thenReturn(true);
        when(manager.getCurrentStreak(player)).thenReturn(7); when(manager.getHighestStreak(player)).thenReturn(25);
        when(manager.getNextMilestone(player)).thenReturn(10); when(manager.getProgressBar(player)).thenReturn("bar");
        doAnswer(call -> { messages.add(PlainTextComponentSerializer.plainText().serialize(call.getArgument(0))); return null; }).when(player).sendMessage(any(Component.class));
        executor.onCommand(player,command,"streaks",new String[0]);
        assertTrue(messages.stream().anyMatch(text -> text.contains("Current Streak: 7")));
        assertTrue(messages.stream().anyMatch(text -> text.contains("Highest Streak: 25")));
        assertTrue(messages.stream().anyMatch(text -> text.contains("Next Milestone: 10")));
        assertTrue(messages.stream().anyMatch(text -> text.contains("bar") && text.contains("7/10")));
    }
    @Test void noArgStreakPreservesConfigurablePermissionDenial() {
        var player = mock(Player.class);
        plugin.getConfig().set("messages.no-permission","<red>Custom denial</red>");
        doAnswer(call -> { messages.add(PlainTextComponentSerializer.plainText().serialize(call.getArgument(0))); return null; }).when(player).sendMessage(any(Component.class));
        executor.onCommand(player,command,"streak",new String[0]);
        assertEquals(List.of("Custom denial"),messages); verify(manager,never()).isLoaded(any());
    }
    @Test void duplicateTopRequestsAreBoundedAndReleasedAfterDelivery() throws Exception {
        run("top"); run("top"); verify(manager,times(1)).getLeaderboard(1,10);
        assertTrue(messages.getLast().contains("already loading"));
        result.completeExceptionally(new java.sql.SQLException("database rejected query")); deliver();
        verify(plugin.getLogger()).warning(contains("database rejected query"));
        result = new CompletableFuture<>(); when(manager.getLeaderboard(anyInt(),anyInt())).thenReturn(result);
        run("top"); verify(manager,times(2)).getLeaderboard(1,10);
    }
}
