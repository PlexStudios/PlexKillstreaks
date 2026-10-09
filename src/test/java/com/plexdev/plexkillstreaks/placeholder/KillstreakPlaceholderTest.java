package com.plexdev.plexkillstreaks.placeholder;

import com.plexdev.plexkillstreaks.PlexKillstreaks;
import com.plexdev.plexkillstreaks.manager.StreakManager;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KillstreakPlaceholderTest {
    @Test void allEightPlaceholdersRemainMemoryOnly() {
        var plugin = mock(PlexKillstreaks.class); var manager = mock(StreakManager.class); var player = mock(Player.class);
        when(manager.isLoaded(player)).thenReturn(true);
        when(manager.getCurrentStreak(player)).thenReturn(7); when(manager.getHighestStreak(player)).thenReturn(25);
        when(manager.getNextMilestone(player)).thenReturn(10); when(manager.getProgressBar(player)).thenReturn("bar");
        when(manager.getKillsToNext(player)).thenReturn(3); when(manager.getLastMilestone(player)).thenReturn(5);
        var expansion = new KillstreakPlaceholder(plugin,manager);
        String[][] cases = {{"CURRENT","7"},{"highest","25"},{"next_milestone","10"},{"progress","bar"},{"is_active","true"},{"loaded","true"},{"kills_to_next","3"},{"last_milestone","5"}};
        for (var row : cases) assertEquals(row[1],expansion.onPlaceholderRequest(player,row[0]));
        assertNull(expansion.onPlaceholderRequest(player,"unknown"));
        assertEquals("false",expansion.onPlaceholderRequest(null,"loaded"));
        assertEquals("0",expansion.onPlaceholderRequest(null,"kills_to_next"));
        verify(manager,never()).ensureLoaded(any()); verify(manager,never()).getLeaderboard(anyInt(),anyInt());
    }
    @Test void unavailablePlayersKeepExistingSafeBehavior() {
        var expansion = new KillstreakPlaceholder(mock(PlexKillstreaks.class),mock(StreakManager.class));
        var player = mock(Player.class);
        for (String key : new String[]{"current","highest","next_milestone","progress","kills_to_next","last_milestone"}) assertEquals("0",expansion.onPlaceholderRequest(player,key));
        assertEquals("false",expansion.onPlaceholderRequest(player,"is_active"));
        assertEquals("false",expansion.onPlaceholderRequest(player,"loaded"));
        assertNull(expansion.onPlaceholderRequest(player,"rank"));
    }
}
