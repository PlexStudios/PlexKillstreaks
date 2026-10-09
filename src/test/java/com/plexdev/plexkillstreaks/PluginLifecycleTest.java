package com.plexdev.plexkillstreaks;

import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PluginLifecycleTest {
    @TempDir Path directory;
    @Test void oldConfigBootsReloadsAndShutsDownWithoutPlaceholderApi() throws Exception {
        try (var bukkit = mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(org.bukkit.Bukkit::getUnsafe).thenReturn(mock(org.bukkit.UnsafeValues.class));
            var plugin = mock(PlexKillstreaks.class);
            var config = YamlConfiguration.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/legacy-config.yml"), StandardCharsets.UTF_8));
            var server = mock(Server.class); var plugins = mock(PluginManager.class); var scheduler = mock(BukkitScheduler.class);
            var cleanup = mock(BukkitTask.class); var command = mock(PluginCommand.class);
            when(plugin.getConfig()).thenReturn(config); when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
            when(plugin.getDataFolder()).thenReturn(directory.toFile()); when(plugin.getServer()).thenReturn(server);
            when(server.getPluginManager()).thenReturn(plugins); when(server.getScheduler()).thenReturn(scheduler);
            doReturn(List.of()).when(server).getOnlinePlayers();
            when(plugin.getCommand(anyString())).thenReturn(command);
            when(scheduler.runTaskTimer(eq(plugin),any(Runnable.class),eq(1200L),eq(1200L))).thenReturn(cleanup);
            doCallRealMethod().when(plugin).onEnable(); doCallRealMethod().when(plugin).onDisable();
            doCallRealMethod().when(plugin).reloadSettings();
            when(plugin.getSettings()).thenCallRealMethod(); when(plugin.getStreakManager()).thenCallRealMethod();
            when(plugin.getAntiFarmTracker()).thenCallRealMethod();
            plugin.onEnable();
            assertNotNull(plugin.getStreakManager()); assertEquals(10,plugin.getSettings().pageSize());
            assertTrue(plugin.getSettings().rewardsEnabled());
            verify(plugins,never()).disablePlugin(any());
            verify(command).setTabCompleter(any());
            var pair = new java.util.UUID(0,1);
            assertTrue(plugin.getAntiFarmTracker().tryCount(pair,new java.util.UUID(0,2)));
            config.set("anti-farming.window-seconds",60);
            plugin.reloadSettings(); assertEquals(0,plugin.getAntiFarmTracker().size());
            assertEquals(60,plugin.getSettings().windowSeconds());
            assertTrue(plugin.getAntiFarmTracker().tryCount(pair,new java.util.UUID(0,2)));
            config.set("anti-farming.max-counted-kills-per-victim",1);
            plugin.reloadSettings(); assertEquals(0,plugin.getAntiFarmTracker().size());
            assertEquals(1,plugin.getSettings().maxCountedKillsPerVictim());
            assertTrue(plugin.getAntiFarmTracker().tryCount(pair,new java.util.UUID(0,2)));
            var tracker = plugin.getAntiFarmTracker();
            config.set("anti-farming.notify-killer",false); plugin.reloadSettings();
            assertSame(tracker,plugin.getAntiFarmTracker()); assertFalse(plugin.getSettings().notifyKiller());
            config.set("anti-farming.enabled",false); config.set("leaderboard.page-size",4);
            plugin.reloadSettings(); assertEquals(4,plugin.getSettings().pageSize()); assertEquals(0,plugin.getAntiFarmTracker().size());
            plugin.onDisable(); verify(cleanup).cancel(); assertTrue(plugin.getStreakManager().isShuttingDown());
            assertFalse(config.contains("settings.milestone-rewards"));
        }
    }
}
