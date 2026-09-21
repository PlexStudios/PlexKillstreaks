package com.plexdev.plexkillstreaks.manager;

import com.plexdev.plexkillstreaks.PlexKillstreaks;
import com.plexdev.plexkillstreaks.database.DatabaseManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class StreakManager {

    private final PlexKillstreaks plugin;
    private final DatabaseManager databaseManager;

    private final Map<UUID, Integer> currentStreaks = new HashMap<>();
    private final Map<UUID, Integer> highestStreaks = new HashMap<>();
    private final Set<UUID> loadedPlayers = new HashSet<>();
    private final Map<UUID, Long> playerSessions = new HashMap<>();
    private final Map<UUID, CompletableFuture<Void>> saveChains = new HashMap<>();

    private final ExecutorService databaseExecutor =
            Executors.newSingleThreadExecutor();

    private boolean shuttingDown = false;

    public StreakManager(
            PlexKillstreaks plugin,
            DatabaseManager databaseManager
    ) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public PlexKillstreaks getPlugin() {
        return plugin;
    }

    public synchronized int getCurrentStreak(Player player) {
        return currentStreaks.getOrDefault(
                player.getUniqueId(),
                0
        );
    }

    public synchronized int getHighestStreak(Player player) {
        return highestStreaks.getOrDefault(
                player.getUniqueId(),
                0
        );
    }

    public synchronized boolean isLoaded(Player player) {
        return loadedPlayers.contains(
                player.getUniqueId()
        );
    }

    public int getNextMilestone(Player player) {

        int current = getCurrentStreak(player);

        ConfigurationSection milestones =
                plugin.getConfig()
                        .getConfigurationSection("milestones");

        if (milestones == null) {
            return -1;
        }

        int next = Integer.MAX_VALUE;

        for (String key : milestones.getKeys(false)) {

            try {

                int milestone =
                        Integer.parseInt(key);

                boolean enabled =
                        milestones.getBoolean(
                                key + ".enabled",
                                false
                        );

                if (enabled &&
                        milestone > current &&
                        milestone < next) {

                    next = milestone;
                }

            } catch (NumberFormatException ignored) {
            }
        }

        return next == Integer.MAX_VALUE
                ? -1
                : next;
    }

    public String getProgressBar(Player player) {

        int current =
                getCurrentStreak(player);

        int next =
                getNextMilestone(player);

        if (next == -1) {
            return "";
        }

        boolean enabled =
                plugin.getConfig()
                        .getBoolean(
                                "settings.streak-menu.progress-bar.enabled",
                                true
                        );

        if (!enabled) {
            return "";
        }

        int length =
                Math.max(
                        0,
                        plugin.getConfig()
                                .getInt(
                                        "settings.streak-menu.progress-bar.length",
                                        10
                                )
                );

        String completed =
                plugin.getConfig()
                        .getString(
                                "settings.streak-menu.progress-bar.completed",
                                "<red>■</red>"
                        );

        String remaining =
                plugin.getConfig()
                        .getString(
                                "settings.streak-menu.progress-bar.remaining",
                                "<dark_gray>■</dark_gray>"
                        );

        if (completed == null) {
            completed = "";
        }

        if (remaining == null) {
            remaining = "";
        }

        double percentage =
                Math.min(
                        1.0,
                        (double) current / next
                );

        int completedAmount =
                (int) Math.floor(
                        percentage * length
                );

        int remainingAmount =
                length - completedAmount;

        return completed.repeat(completedAmount)
                + remaining.repeat(remainingAmount);
    }

    public synchronized int addKill(Player player) {

        if (!isLoaded(player)) {
            return getCurrentStreak(player);
        }

        UUID uuid =
                player.getUniqueId();

        int newStreak =
                getCurrentStreak(player) + 1;

        currentStreaks.put(
                uuid,
                newStreak
        );

        int highest =
                getHighestStreak(player);

        if (newStreak > highest) {

            highest = newStreak;

            highestStreaks.put(
                    uuid,
                    highest
            );
        }

        queueSave(
                uuid,
                newStreak,
                highest
        );

        return newStreak;
    }

    public synchronized int resetStreak(Player player) {

        if (!isLoaded(player)) {
            return 0;
        }

        UUID uuid =
                player.getUniqueId();

        int oldStreak =
                getCurrentStreak(player);

        currentStreaks.put(
                uuid,
                0
        );

        queueSave(
                uuid,
                0,
                getHighestStreak(player)
        );

        return oldStreak;
    }

    public synchronized void ensureLoaded(Player player) {

        if (player == null || !player.isOnline()) {
            return;
        }

        UUID uuid = player.getUniqueId();

        if (loadedPlayers.contains(uuid)) {
            return;
        }

        try {

            DatabaseManager.PlayerData data =
                    databaseManager.loadPlayer(uuid);

            currentStreaks.put(
                    uuid,
                    data.currentStreak()
            );

            highestStreaks.put(
                    uuid,
                    data.highestStreak()
            );

        } catch (Throwable throwable) {

            plugin.getLogger().warning(
                    "Failed fallback load for "
                            + player.getName()
                            + ": "
                            + throwable.getMessage()
            );

            currentStreaks.putIfAbsent(
                    uuid,
                    0
            );

            highestStreaks.putIfAbsent(
                    uuid,
                    0
            );
        }

        loadedPlayers.add(uuid);
    }

    public void loadPlayer(Player player) {

        UUID uuid =
                player.getUniqueId();

        long sessionId =
                System.nanoTime();

        synchronized (this) {

            playerSessions.put(
                    uuid,
                    sessionId
            );

            loadedPlayers.remove(uuid);
        }

        plugin.getLogger().info(
                "Loading streak data for "
                        + player.getName()
                        + "..."
        );

        databaseExecutor.execute(() -> {

            DatabaseManager.PlayerData data;

            try {

                data =
                        databaseManager.loadPlayer(uuid);

            } catch (Throwable exception) {

                plugin.getLogger().severe(
                        "Failed to load streak data for "
                                + player.getName()
                                + ": "
                                + exception.getMessage()
                );

                plugin.getServer()
                        .getScheduler()
                        .runTask(
                                plugin,
                                () -> {

                                    synchronized (StreakManager.this) {

                                        Long currentSession =
                                                playerSessions.get(uuid);

                                        if (currentSession == null ||
                                                currentSession != sessionId) {
                                            return;
                                        }

                                        if (!player.isOnline()) {
                                            return;
                                        }

                                        /*
                                         * Keep the player usable even if
                                         * the database temporarily fails.
                                         */
                                        currentStreaks.put(
                                                uuid,
                                                0
                                        );

                                        highestStreaks.put(
                                                uuid,
                                                0
                                        );

                                        loadedPlayers.add(uuid);

                                        plugin.getLogger().warning(
                                                "Using temporary zero streak data for "
                                                        + player.getName()
                                                        + " because the database load failed."
                                        );
                                    }
                                }
                        );

                return;
            }

            plugin.getServer()
                    .getScheduler()
                    .runTask(
                            plugin,
                            () -> {

                                synchronized (StreakManager.this) {

                                    Long currentSession =
                                            playerSessions.get(uuid);

                                    if (currentSession == null ||
                                            currentSession != sessionId) {
                                        return;
                                    }

                                    if (!player.isOnline()) {
                                        return;
                                    }

                                    currentStreaks.put(
                                            uuid,
                                            data.currentStreak()
                                    );

                                    highestStreaks.put(
                                            uuid,
                                            data.highestStreak()
                                    );

                                    loadedPlayers.add(uuid);

                                    plugin.getLogger().info(
                                            "Loaded streak data for "
                                                    + player.getName()
                                                    + " (Current: "
                                                    + data.currentStreak()
                                                    + ", Highest: "
                                                    + data.highestStreak()
                                                    + ")"
                                    );
                                }
                            }
                    );
        });
    }

    public synchronized void removePlayer(Player player) {

        UUID uuid =
                player.getUniqueId();

        Long session =
                playerSessions.remove(uuid);

        if (session == null) {
            return;
        }

        if (!loadedPlayers.contains(uuid)) {

            loadedPlayers.remove(uuid);
            currentStreaks.remove(uuid);
            highestStreaks.remove(uuid);

            return;
        }

        int current =
                getCurrentStreak(player);

        int highest =
                getHighestStreak(player);

        queueSave(
                uuid,
                current,
                highest
        );

        currentStreaks.remove(uuid);
        highestStreaks.remove(uuid);
        loadedPlayers.remove(uuid);
    }

    private synchronized void queueSave(
            UUID uuid,
            int current,
            int highest
    ) {

        CompletableFuture<Void> previous =
                saveChains.getOrDefault(
                        uuid,
                        CompletableFuture.completedFuture(null)
                );

        CompletableFuture<Void> next =
                previous.thenRunAsync(
                        () -> {

                            try {

                                databaseManager.savePlayer(
                                        uuid,
                                        current,
                                        highest
                                );

                            } catch (Exception exception) {

                                plugin.getLogger().severe(
                                        "Failed to save streak data for "
                                                + uuid
                                                + ": "
                                                + exception.getMessage()
                                );

                                throw exception;
                            }

                        },
                        databaseExecutor
                );

        saveChains.put(
                uuid,
                next
        );

        next.whenComplete(
                (unused, throwable) -> {

                    synchronized (this) {

                        if (saveChains.get(uuid) == next) {
                            saveChains.remove(uuid);
                        }
                    }
                }
        );
    }

    public void shutdown() {

        synchronized (this) {
            shuttingDown = true;
        }

        CompletableFuture<?>[] pending;

        synchronized (this) {

            pending =
                    saveChains.values()
                            .toArray(
                                    new CompletableFuture<?>[0]
                            );
        }

        if (pending.length > 0) {

            try {

                CompletableFuture.allOf(
                        pending
                ).join();

            } catch (Exception exception) {

                plugin.getLogger().severe(
                        "Some streak data could not be saved during shutdown."
                );
            }
        }

        databaseExecutor.shutdown();

        try {

            if (!databaseExecutor.awaitTermination(
                    10,
                    TimeUnit.SECONDS
            )) {

                databaseExecutor.shutdownNow();
            }

        } catch (InterruptedException exception) {

            Thread.currentThread().interrupt();

            databaseExecutor.shutdownNow();
        }
    }
}