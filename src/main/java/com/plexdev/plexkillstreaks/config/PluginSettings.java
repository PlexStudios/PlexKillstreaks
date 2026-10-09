package com.plexdev.plexkillstreaks.config;

import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.*;
import java.util.logging.Logger;

public record PluginSettings(
        boolean rewardsEnabled, boolean antiFarmEnabled, long windowSeconds, int maxCountedKillsPerVictim,
        boolean notifyKiller, int pageSize, int progressLength,
        Set<String> disabledWorlds, NavigableMap<Integer, Milestone> milestones,
        Sound milestoneSound, Sound endedSound) {

    public static PluginSettings load(FileConfiguration config, Logger logger) {
        Set<String> worlds = new HashSet<>();
        Object worldList = config.get("settings.disabled-worlds");
        if (worldList instanceof List<?> list) {
            for (Object value : list) {
                if (value instanceof String world && !world.isBlank()) worlds.add(world.toLowerCase(Locale.ROOT));
                else logger.warning("Ignoring invalid disabled-world entry.");
            }
        } else if (worldList != null) logger.warning("settings.disabled-worlds must be a list; using no exclusions.");
        TreeMap<Integer, Milestone> milestones = new TreeMap<>();
        var section = config.getConfigurationSection("milestones");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                int number;
                try { number = Integer.parseInt(key); }
                catch (NumberFormatException exception) { logger.warning("Ignoring invalid milestone: " + key); continue; }
                if (number <= 0) { logger.warning("Ignoring nonpositive milestone: " + key); continue; }
                String path = "milestones." + key;
                if (!config.getBoolean(path + ".enabled", false)) continue;
                List<Reward> rewards = new ArrayList<>();
                Object rewardList = config.get(path + ".rewards", null);
                if (rewardList instanceof List<?> list) {
                    for (Object value : list) {
                        if (!(value instanceof String text) || text.isBlank()) {
                            logger.warning("Ignoring invalid reward at milestone " + key); continue;
                        }
                        text = text.trim();
                        boolean console = true;
                        int colon = text.indexOf(':');
                        int space = text.indexOf(' ');
                        if (colon >= 0 && (space < 0 || colon < space)) {
                            String type = text.substring(0, colon).toLowerCase(Locale.ROOT);
                            if (!type.equals("console") && !type.equals("player")) {
                                logger.warning("Ignoring invalid reward type at milestone " + key); continue;
                            }
                            console = type.equals("console");
                            text = text.substring(colon + 1).trim();
                        }
                        if (text.startsWith("/")) text = text.substring(1).trim();
                        if (text.isBlank()) { logger.warning("Ignoring empty reward at milestone " + key); continue; }
                        rewards.add(new Reward(console, text));
                    }
                } else if (rewardList != null) logger.warning("Rewards must be a list at milestone " + key);
                milestones.put(number, new Milestone(number, config.getString(path + ".message", ""), List.copyOf(rewards)));
            }
        }
        String windowPath = "anti-farming.window-seconds";
        if (!config.contains(windowPath, true) && config.contains("anti-farming.same-victim-cooldown-seconds", true)) {
            windowPath = "anti-farming.same-victim-cooldown-seconds";
        }
        return new PluginSettings(config.getBoolean("settings.milestone-rewards.enabled", true),
                config.getBoolean("anti-farming.enabled", true),
                integer(config, windowPath, 180, 0, 31_536_000, logger),
                (int) integer(config, "anti-farming.max-counted-kills-per-victim", 2, 1, Integer.MAX_VALUE, logger),
                config.getBoolean("anti-farming.notify-killer", true),
                (int) integer(config, "leaderboard.page-size", 10, 1, 100, logger),
                (int) integer(config, "settings.streak-menu.progress-bar.length", 10, 1, 100, logger),
                Set.copyOf(worlds), Collections.unmodifiableNavigableMap(milestones),
                sound(config,"settings.sounds.milestone", "ENTITY_PLAYER_LEVELUP", logger),
                sound(config,"settings.sounds.streak-ended", "BLOCK_NOTE_BLOCK_BASS", logger));
    }

    private static long integer(FileConfiguration config, String path, long fallback, long min, long max, Logger logger) {
        Object value = config.get(path);
        if (value == null) return fallback;
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            long number = ((Number) value).longValue();
            if (number >= min && number <= max) return number;
        }
        logger.warning("Invalid " + path + "; using " + fallback + ".");
        return fallback;
    }

    private static Sound sound(FileConfiguration config, String path, String fallback, Logger logger) {
        String name = config.getString(path, fallback);
        if (name == null || name.isBlank()) return null;
        try { return Sound.valueOf(name.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { logger.warning("Invalid " + path + "; sound disabled."); return null; }
    }

    public boolean isWorldDisabled(String world) { return disabledWorlds.contains(world.toLowerCase(Locale.ROOT)); }
    public int nextMilestone(int current) { var next = milestones.higherKey(current); return next == null ? -1 : next; }
    public int killsToNext(int current) { int next = nextMilestone(current); return next < 0 ? 0 : next - current; }
    public int lastMilestone(int current) { var last = milestones.floorKey(current); return last == null ? 0 : last; }
    public Milestone milestone(int streak) { return milestones.get(streak); }
    public record Milestone(int streak, String message, List<Reward> rewards) {}
    public record Reward(boolean console, String command) {}
}
