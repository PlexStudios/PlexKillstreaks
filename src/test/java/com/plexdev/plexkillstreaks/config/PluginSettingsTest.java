package com.plexdev.plexkillstreaks.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.bukkit.Bukkit;
import org.bukkit.UnsafeValues;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class PluginSettingsTest {
    static org.mockito.MockedStatic<Bukkit> bukkit;
    @BeforeAll static void server() {
        bukkit = org.mockito.Mockito.mockStatic(Bukkit.class);
        var unsafe = org.mockito.Mockito.mock(UnsafeValues.class);
        bukkit.when(Bukkit::getUnsafe).thenReturn(unsafe);
    }
    @org.junit.jupiter.api.AfterAll static void closeServer() { bukkit.close(); }
    final Logger logger = Logger.getAnonymousLogger();
    @Test void oldConfigUsesSafeDefaults() {
        var config = YamlConfiguration.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/legacy-config.yml"), StandardCharsets.UTF_8));
        var settings = PluginSettings.load(config, logger);
        assertTrue(settings.rewardsEnabled());
        assertTrue(settings.antiFarmEnabled());
        assertEquals(180, settings.windowSeconds());
        assertEquals(2, settings.maxCountedKillsPerVictim());
        assertTrue(settings.notifyKiller());
        assertEquals(10, settings.pageSize());
        assertFalse(settings.isWorldDisabled("lobby"));
        assertEquals(10, settings.nextMilestone(7));
        assertTrue(settings.milestone(5).rewards().isEmpty());
    }
    @Test void oldMilestonesDoNotInheritNewDefaultRewards() {
        var config = YamlConfiguration.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/legacy-config.yml"), StandardCharsets.UTF_8));
        var defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        config.setDefaults(defaults);
        assertTrue(PluginSettings.load(config,logger).milestone(5).rewards().isEmpty());
        assertEquals(1,PluginSettings.load(defaults,logger).milestone(5).rewards().size());
    }
    @Test void remainingKillsUsesNextEnabledMilestone() {
        var config = new YamlConfiguration();
        for (String key : new String[]{"3","5","10","25"}) config.set("milestones." + key + ".enabled",true);
        var settings = PluginSettings.load(config,logger);
        assertEquals(10,settings.nextMilestone(7)); assertEquals(3,settings.killsToNext(7)); assertEquals(5,settings.lastMilestone(7));
    }
    @Test void milestonesUseOnlyPositiveEnabledValues() {
        var config = new YamlConfiguration();
        for (String value : new String[]{"3","5","10","25","bad","-1","0"}) config.set("milestones." + value + ".enabled", true);
        config.set("milestones.10.enabled", false);
        var settings = PluginSettings.load(config, logger);
        assertEquals(3, settings.nextMilestone(0));
        assertEquals(5, settings.nextMilestone(3));
        assertEquals(25, settings.nextMilestone(7));
        assertEquals(18, settings.killsToNext(7));
        assertEquals(5, settings.lastMilestone(7));
        assertEquals(0, settings.lastMilestone(0));
        assertEquals(-1, settings.nextMilestone(25));
        assertEquals(0, settings.killsToNext(25));
    }
    @Test void malformedValuesFallBackAndWorldsMatchIgnoringCase() {
        var config = new YamlConfiguration();
        config.set("anti-farming.window-seconds", -1);
        config.set("leaderboard.page-size", "bad");
        config.set("settings.streak-menu.progress-bar.length", Integer.MAX_VALUE);
        config.set("settings.disabled-worlds", java.util.List.of("LoBBy", 12, ""));
        config.set("settings.sounds.milestone", "INVALID_SOUND");
        var settings = PluginSettings.load(config, logger);
        assertEquals(180, settings.windowSeconds());
        assertEquals(2, settings.maxCountedKillsPerVictim());
        assertEquals(10, settings.pageSize());
        assertEquals(10, settings.progressLength());
        assertTrue(settings.isWorldDisabled("lobby"));
        assertNull(settings.milestoneSound());
    }
    @Test void rewardTypesValidateWithoutDiscardingValidCommands() {
        var config = new YamlConfiguration();
        config.set("milestones.5.enabled", true);
        config.set("milestones.5.rewards", java.util.List.of("console:give {player} apple", "player:help", "say {uuid} {streak} {highest}", "invalid:help", "console:", 3));
        var rewards = PluginSettings.load(config, logger).milestone(5).rewards();
        assertEquals(3, rewards.size());
        assertTrue(rewards.get(0).console());
        assertFalse(rewards.get(1).console());
        assertTrue(rewards.get(2).console());
        assertEquals("say {uuid} {streak} {highest}", rewards.get(2).command());
    }
    @Test void reloadReplacesSnapshotWithoutMutatingYaml() {
        var config = new YamlConfiguration();
        var first = PluginSettings.load(config, logger);
        config.set("leaderboard.page-size", 4);
        var second = PluginSettings.load(config, logger);
        assertEquals(10, first.pageSize());
        assertEquals(4, second.pageSize());
        assertFalse(config.contains("anti-farming"));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"0","-1","invalid","2147483648","1.5"})
    void invalidThresholdUsesTwo(String value) {
        var config = new YamlConfiguration();
        Object parsed;
        try { parsed = Long.valueOf(value); } catch (NumberFormatException exception) { parsed = value; }
        config.set("anti-farming.max-counted-kills-per-victim",parsed);
        assertEquals(2,PluginSettings.load(config,logger).maxCountedKillsPerVictim());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"-1","invalid","31536001","9223372036854775807","1.5"})
    void invalidWindowUses180(String value) {
        var config = new YamlConfiguration();
        Object parsed;
        try { parsed = Long.valueOf(value); } catch (NumberFormatException exception) { parsed = value; }
        config.set("anti-farming.window-seconds",parsed);
        assertEquals(180,PluginSettings.load(config,logger).windowSeconds());
    }
    @Test void rcWindowFallbackOnlyAppliesWhenNewKeyIsAbsent() {
        var config = new YamlConfiguration();
        config.set("anti-farming.same-victim-cooldown-seconds",90);
        var defaults = new YamlConfiguration(); defaults.set("anti-farming.window-seconds",180); config.setDefaults(defaults);
        assertEquals(90,PluginSettings.load(config,logger).windowSeconds());
        config.set("anti-farming.window-seconds",60);
        assertEquals(60,PluginSettings.load(config,logger).windowSeconds());
        config.set("anti-farming.window-seconds","invalid");
        assertEquals(180,PluginSettings.load(config,logger).windowSeconds());
    }
    @Test void zeroWindowAndThresholdOneAreAccepted() {
        var config = new YamlConfiguration(); config.set("anti-farming.window-seconds",0);
        config.set("anti-farming.max-counted-kills-per-victim",1);
        var settings = PluginSettings.load(config,logger);
        assertEquals(0,settings.windowSeconds()); assertEquals(1,settings.maxCountedKillsPerVictim());
    }
}
