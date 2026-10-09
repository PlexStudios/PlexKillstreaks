package com.plexdev.plexkillstreaks.listener;

import com.plexdev.plexkillstreaks.manager.StreakManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerDataListener implements Listener {

    private final StreakManager streakManager;

    public PlayerDataListener(StreakManager streakManager) {
        this.streakManager = streakManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {

        streakManager.loadPlayer(
                event.getPlayer()
        );
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {

        streakManager.removePlayer(
                event.getPlayer()
        );
    }
}
