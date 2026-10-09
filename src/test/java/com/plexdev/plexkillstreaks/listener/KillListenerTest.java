package com.plexdev.plexkillstreaks.listener;

import com.plexdev.plexkillstreaks.PlexKillstreaks;
import com.plexdev.plexkillstreaks.config.PluginSettings;
import com.plexdev.plexkillstreaks.database.DatabaseManager;
import com.plexdev.plexkillstreaks.manager.*;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KillListenerTest {
    PlexKillstreaks plugin;
    StreakManager manager;
    DatabaseManager database;
    YamlConfiguration config;
    Player killer, victim;
    PlayerDeathEvent death;
    World world;
    KillListener listener;
    MockedStatic<Bukkit> bukkit;
    List<String> commands;
    List<CommandSender> senders;
    org.bukkit.command.ConsoleCommandSender console;
    @BeforeEach void setup() throws Exception {
        plugin = mock(PlexKillstreaks.class); database = mock(DatabaseManager.class);
        var server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        killer = mock(Player.class); victim = mock(Player.class); world = mock(World.class);
        when(world.getName()).thenReturn("arena"); when(victim.getWorld()).thenReturn(world);
        when(killer.getUniqueId()).thenReturn(new UUID(0,1)); when(victim.getUniqueId()).thenReturn(new UUID(0,2));
        when(killer.getName()).thenReturn("Killer"); when(victim.getName()).thenReturn("Victim");
        when(killer.isOnline()).thenReturn(true); when(victim.isOnline()).thenReturn(true);
        when(victim.getKiller()).thenReturn(killer);
        when(database.loadPlayer(any())).thenReturn(new DatabaseManager.PlayerData(0,0));
        config = new YamlConfiguration();
        config.set("settings.sounds.enabled", false);
        config.set("settings.sounds.milestone", ""); config.set("settings.sounds.streak-ended", "");
        config.set("milestones.1.enabled", true); config.set("milestones.1.message", "Milestone {player}");
        config.set("milestones.1.rewards",List.of("console:say {player} {uuid} {streak} {highest}","player:help","give {player} apple"));
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getSettings()).thenAnswer(call -> PluginSettings.load(config,plugin.getLogger()));
        when(plugin.getAntiFarmTracker()).thenReturn(new AntiFarmTracker(180,2,()->0));
        manager = new StreakManager(plugin,database); manager.ensureLoaded(killer); manager.ensureLoaded(victim);
        death = mock(PlayerDeathEvent.class); when(death.getEntity()).thenReturn(victim);
        listener = new KillListener(manager);
        commands = new ArrayList<>(); senders = new ArrayList<>(); console = mock(org.bukkit.command.ConsoleCommandSender.class);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
        bukkit.when(() -> Bukkit.dispatchCommand(any(),anyString())).thenAnswer(call -> { senders.add(call.getArgument(0)); commands.add(call.getArgument(1)); return true; });
    }
    @AfterEach void close() { manager.shutdown(); bukkit.close(); }
    @Test void exactMilestoneRoutesCommandsWithAllReplacements() {
        listener.onPlayerDeath(death);
        assertEquals(List.of("say Killer 00000000-0000-0000-0000-000000000001 1 1","help","give Killer apple"),commands);
        assertEquals(List.of(console,killer,console),senders);
        assertEquals(1,manager.getCurrentStreak(killer));
    }
    @Test void nonExactDoesNotReward() { manager.addKill(killer); listener.onPlayerDeath(death); assertTrue(commands.isEmpty()); }
    @Test void disabledMilestoneDoesNotReward() { config.set("milestones.1.enabled",false); listener.onPlayerDeath(death); assertTrue(commands.isEmpty()); }
    @Test void rewardsToggleKeepsAnnouncements() {
        config.set("settings.milestone-rewards.enabled",false); listener.onPlayerDeath(death);
        assertTrue(commands.isEmpty()); bukkit.verify(() -> Bukkit.broadcast(any(net.kyori.adventure.text.Component.class)));
    }
    @Test void announcementsToggleKeepsRewards() {
        config.set("settings.milestone-announcements.enabled",false); listener.onPlayerDeath(death); assertEquals(3,commands.size());
    }
    @Test void throwingAndFalseRewardsDoNotStopLaterCommands() {
        bukkit.when(() -> Bukkit.dispatchCommand(any(),anyString())).thenAnswer(call -> {
            String value = call.getArgument(1); commands.add(value);
            if (value.startsWith("say")) throw new IllegalArgumentException("bad command");
            return !value.equals("help");
        });
        commands.clear();
        listener.onPlayerDeath(death); assertEquals(3,commands.size());
    }
    @Test void ignoredFarmKillStillResetsVictim() {
        listener.onPlayerDeath(death); listener.onPlayerDeath(death); manager.addKill(victim); commands.clear();
        clearInvocations(killer);
        bukkit.clearInvocations();
        listener.onPlayerDeath(death);
        assertEquals(2,manager.getCurrentStreak(killer)); assertEquals(2,manager.getHighestStreak(killer));
        assertEquals(0,manager.getCurrentStreak(victim));
        assertTrue(commands.isEmpty()); verify(killer,never()).sendActionBar(any(net.kyori.adventure.text.Component.class));
        bukkit.verify(() -> Bukkit.broadcast(any(net.kyori.adventure.text.Component.class)),never());
        verify(killer).sendMessage(any(net.kyori.adventure.text.Component.class));
    }
    @Test void disabledWorldLeavesBothPlayersUntouched() {
        config.set("settings.disabled-worlds", List.of("ARENA")); manager.addKill(victim);
        listener.onPlayerDeath(death);
        assertEquals(0,manager.getCurrentStreak(killer)); assertEquals(1,manager.getCurrentStreak(victim));
        assertTrue(commands.isEmpty()); assertEquals(0,plugin.getAntiFarmTracker().size());
    }
    @Test void unavailableKillerCannotRewardButVictimResets() throws Exception {
        manager.removePlayer(killer); manager.getLeaderboard(1,10).get(5,java.util.concurrent.TimeUnit.SECONDS);
        when(database.loadPlayer(killer.getUniqueId())).thenThrow(new SQLException("unavailable"));
        manager.addKill(victim); listener.onPlayerDeath(death);
        assertFalse(manager.isLoaded(killer)); assertEquals(0,manager.getCurrentStreak(victim)); assertTrue(commands.isEmpty());
    }
    @Test void environmentalDeathResetsVictim() {
        when(victim.getKiller()).thenReturn(null); manager.addKill(victim); listener.onPlayerDeath(death);
        assertEquals(0,manager.getCurrentStreak(victim)); assertTrue(commands.isEmpty());
    }
    @Test void reentrantCommandCannotReplayCapturedMilestone() {
        when(plugin.getAntiFarmTracker()).thenReturn(new AntiFarmTracker(180,1,()->0));
        bukkit.when(() -> Bukkit.dispatchCommand(any(),anyString())).thenAnswer(call -> {
            commands.add(call.getArgument(1)); if (commands.size()==1) listener.onPlayerDeath(death); return true;
        });
        commands.clear();
        listener.onPlayerDeath(death); assertEquals(3,commands.size()); assertEquals(1,manager.getCurrentStreak(killer));
    }
    @Test void rewardCausedDeathCannotUseOrEraseVictimsEndedStreak() {
        manager.addKill(victim); manager.addKill(victim); manager.addKill(victim);
        var other = mock(Player.class);
        when(other.getUniqueId()).thenReturn(new UUID(0,3)); when(other.getName()).thenReturn("Other");
        when(other.isOnline()).thenReturn(true); when(other.getWorld()).thenReturn(world); when(other.getKiller()).thenReturn(victim);
        var nested = mock(PlayerDeathEvent.class); when(nested.getEntity()).thenReturn(other);
        bukkit.when(() -> Bukkit.dispatchCommand(any(),anyString())).thenAnswer(call -> {
            commands.add(call.getArgument(1)); if (commands.size()==1) listener.onPlayerDeath(nested); return true;
        });
        commands.clear(); listener.onPlayerDeath(death);
        assertEquals(1,manager.getCurrentStreak(victim)); assertEquals(3,manager.getHighestStreak(victim));
    }
    @Test void firstAndSecondKillsCanTriggerMilestonesNormally() {
        config.set("milestones.2.enabled",true); config.set("milestones.2.message","Second milestone");
        config.set("milestones.2.rewards",List.of("console:say second"));
        listener.onPlayerDeath(death); listener.onPlayerDeath(death);
        assertEquals(2,manager.getCurrentStreak(killer)); assertEquals(2,manager.getHighestStreak(killer));
        assertEquals(4,commands.size()); assertEquals("say second",commands.getLast());
        bukkit.verify(() -> Bukkit.broadcast(any(net.kyori.adventure.text.Component.class)),times(2));
        verify(killer,never()).sendMessage(any(net.kyori.adventure.text.Component.class));
    }
    @Test void rejectedKillHonorsDisabledNotifications() {
        config.set("anti-farming.notify-killer",false);
        listener.onPlayerDeath(death); listener.onPlayerDeath(death); listener.onPlayerDeath(death);
        assertEquals(2,manager.getCurrentStreak(killer));
        verify(killer,never()).sendMessage(any(net.kyori.adventure.text.Component.class));
    }
    @Test void disabledProtectionDoesNotTrackOrRejectPairs() {
        config.set("anti-farming.enabled",false);
        for(int i=0;i<3;i++) listener.onPlayerDeath(death);
        assertEquals(3,manager.getCurrentStreak(killer)); assertEquals(0,plugin.getAntiFarmTracker().size());
    }
}
