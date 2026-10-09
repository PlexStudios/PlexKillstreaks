package com.plexdev.plexkillstreaks.manager;

import com.plexdev.plexkillstreaks.PlexKillstreaks;
import com.plexdev.plexkillstreaks.database.DatabaseManager;
import org.bukkit.entity.Player;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;

public class StreakManager {
    private final PlexKillstreaks plugin;
    private final DatabaseManager databaseManager;
    private final Map<UUID,Integer> currentStreaks = new HashMap<>();
    private final Map<UUID,Integer> highestStreaks = new HashMap<>();
    private final Set<UUID> loadedPlayers = new HashSet<>();
    private final Map<UUID,Long> playerSessions = new HashMap<>();
    private final Map<UUID,CompletableFuture<Void>> saveChains = new HashMap<>();
    private final Map<UUID,SaveState> pendingStates = new HashMap<>();
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private long nextSession;
    private int leaderboardRequests;
    private static final int MAX_LEADERBOARD_REQUESTS = 16;
    private volatile boolean shuttingDown;

    public StreakManager(PlexKillstreaks plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }
    public PlexKillstreaks getPlugin() { return plugin; }
    public synchronized int getCurrentStreak(Player player) { return currentStreaks.getOrDefault(player.getUniqueId(),0); }
    public synchronized int getHighestStreak(Player player) { return highestStreaks.getOrDefault(player.getUniqueId(),0); }
    public synchronized boolean isLoaded(Player player) { return loadedPlayers.contains(player.getUniqueId()); }
    public boolean isShuttingDown() { return shuttingDown; }
    public int getNextMilestone(Player player) { return plugin.getSettings().nextMilestone(getCurrentStreak(player)); }
    public int getKillsToNext(Player player) { return plugin.getSettings().killsToNext(getCurrentStreak(player)); }
    public int getLastMilestone(Player player) { return plugin.getSettings().lastMilestone(getCurrentStreak(player)); }

    public String getProgressBar(Player player) {
        int current = getCurrentStreak(player), next = getNextMilestone(player);
        if (next < 0 || !plugin.getConfig().getBoolean("settings.streak-menu.progress-bar.enabled",true)) return "";
        int length = plugin.getSettings().progressLength();
        int completed = (int) Math.floor(Math.min(1.0,(double) current / next) * length);
        return plugin.getConfig().getString("settings.streak-menu.progress-bar.completed","<red>■</red>").repeat(completed)
                + plugin.getConfig().getString("settings.streak-menu.progress-bar.remaining","<dark_gray>■</dark_gray>").repeat(length - completed);
    }

    public synchronized int addKill(Player player) {
        if (shuttingDown || !isLoaded(player)) return getCurrentStreak(player);
        int previous = getCurrentStreak(player);
        if (previous == Integer.MAX_VALUE) return previous;
        int current = previous + 1, highest = Math.max(current,getHighestStreak(player));
        currentStreaks.put(player.getUniqueId(),current);
        highestStreaks.put(player.getUniqueId(),highest);
        queueSave(player.getUniqueId(),current,highest,player.getName());
        return current;
    }

    public synchronized int resetStreak(Player player) {
        if (shuttingDown || !isLoaded(player)) return 0;
        int previous = getCurrentStreak(player);
        currentStreaks.put(player.getUniqueId(),0);
        queueSave(player.getUniqueId(),0,getHighestStreak(player),player.getName());
        return previous;
    }

    public synchronized void ensureLoaded(Player player) {
        if (shuttingDown || player == null || !player.isOnline() || isLoaded(player)) return;
        UUID uuid = player.getUniqueId();
        playerSessions.computeIfAbsent(uuid, ignored -> ++nextSession);
        try {
            SaveState pending = pendingStates.get(uuid);
            DatabaseManager.PlayerData data = pending == null ? databaseManager.loadPlayer(uuid)
                    : new DatabaseManager.PlayerData(pending.current(),pending.highest());
            applyLoaded(player,data);
        } catch (SQLException exception) {
            plugin.getLogger().warning("Failed fallback load for " + uuid + ": " + exception.getMessage());
        }
    }

    private void applyLoaded(Player player, DatabaseManager.PlayerData data) {
        UUID uuid = player.getUniqueId();
        currentStreaks.put(uuid,data.currentStreak());
        highestStreaks.put(uuid,data.highestStreak());
        loadedPlayers.add(uuid);
        queueSave(uuid,data.currentStreak(),data.highestStreak(),player.getName());
    }

    public synchronized void loadPlayer(Player player) {
        if (shuttingDown) return;
        UUID uuid = player.getUniqueId();
        long session = ++nextSession;
        playerSessions.put(uuid,session);
        loadedPlayers.remove(uuid);
        currentStreaks.remove(uuid);
        highestStreaks.remove(uuid);
        var prior = saveChains.getOrDefault(uuid,CompletableFuture.completedFuture(null));
        prior.handle((unused,failure) -> null).thenRunAsync(() -> {
            DatabaseManager.PlayerData data = null;
            SQLException error = null;
            try { data = databaseManager.loadPlayer(uuid); }
            catch (SQLException exception) { error = exception; }
            var loaded = data;
            var failed = error;
            synchronized (this) {
                if (shuttingDown) return;
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    synchronized (StreakManager.this) {
                        if (shuttingDown || !Objects.equals(playerSessions.get(uuid),session)
                                || !player.isOnline() || loadedPlayers.contains(uuid)) return;
                        if (failed != null) {
                            plugin.getLogger().warning("Failed to load streak data for " + uuid + ": " + failed.getMessage());
                            return;
                        }
                        applyLoaded(player,loaded);
                    }
                });
            }
        },databaseExecutor);
    }

    public synchronized CompletableFuture<Void> savePlayer(Player player) {
        if (shuttingDown || !isLoaded(player)) return CompletableFuture.failedFuture(new IllegalStateException("Streak data is unavailable."));
        return queueSave(player.getUniqueId(),getCurrentStreak(player),getHighestStreak(player),player.getName());
    }

    public synchronized void removePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        if (!shuttingDown && isLoaded(player)) queueSave(uuid,getCurrentStreak(player),getHighestStreak(player),player.getName());
        playerSessions.remove(uuid);
        loadedPlayers.remove(uuid);
        currentStreaks.remove(uuid);
        highestStreaks.remove(uuid);
    }

    private CompletableFuture<Void> queueSave(UUID uuid, int current, int highest, String name) {
        var previous = saveChains.getOrDefault(uuid,CompletableFuture.completedFuture(null));
        pendingStates.put(uuid,new SaveState(current,highest));
        var next = previous.handle((unused,failure) -> null).thenRunAsync(() -> {
            try { databaseManager.savePlayer(uuid,current,highest,name); }
            catch (SQLException exception) {
                plugin.getLogger().severe("Failed to save streak data for " + uuid + ": " + exception.getMessage());
                throw new CompletionException(exception);
            }
        },databaseExecutor);
        saveChains.put(uuid,next);
        next.whenComplete((unused,failure) -> {
            synchronized (this) {
                if (saveChains.get(uuid) == next) { saveChains.remove(uuid); pendingStates.remove(uuid); }
            }
        });
        return next;
    }

    public synchronized CompletableFuture<DatabaseManager.LeaderboardPage> getLeaderboard(int page, int pageSize) {
        if (shuttingDown) return CompletableFuture.failedFuture(new IllegalStateException("Plugin is shutting down."));
        if (leaderboardRequests >= MAX_LEADERBOARD_REQUESTS) {
            return CompletableFuture.failedFuture(new RejectedExecutionException("Leaderboard is busy."));
        }
        leaderboardRequests++;
        return CompletableFuture.supplyAsync(() -> {
            try { return databaseManager.getLeaderboard(page,pageSize); }
            catch (SQLException exception) { throw new CompletionException(exception); }
        },databaseExecutor).whenComplete((result,failure) -> {
            synchronized (this) { leaderboardRequests--; }
        });
    }

    public void shutdown() {
        CompletableFuture<?>[] pending;
        synchronized (this) {
            if (shuttingDown) return;
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (isLoaded(player)) queueSave(player.getUniqueId(),getCurrentStreak(player),getHighestStreak(player),player.getName());
            }
            shuttingDown = true;
            pending = saveChains.values().toArray(CompletableFuture<?>[]::new);
        }
        try { CompletableFuture.allOf(pending).join(); }
        catch (CompletionException exception) { }
        databaseExecutor.shutdown();
        boolean interrupted = false;
        for (;;) {
            try { if (databaseExecutor.awaitTermination(1,TimeUnit.SECONDS)) break; }
            catch (InterruptedException exception) { interrupted = true; }
        }
        if (interrupted) Thread.currentThread().interrupt();
        synchronized (this) {
            loadedPlayers.clear(); currentStreaks.clear(); highestStreaks.clear(); playerSessions.clear();
        }
    }
    private record SaveState(int current, int highest) {}
}
