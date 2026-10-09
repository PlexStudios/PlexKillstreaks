package com.plexdev.plexkillstreaks;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class PackagingTest {
    @Test
    void processedDescriptorUsesProjectVersion() throws Exception {
        try (var stream = getClass().getResourceAsStream("/plugin.yml")) {
            assertNotNull(stream);
            var yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            assertEquals("1.1.0", yaml.getString("version"));
            assertEquals("com.plexdev.plexkillstreaks.PlexKillstreaks", yaml.getString("main"));
            assertTrue(yaml.getStringList("commands.streak.aliases").contains("streaks"));
            assertTrue(yaml.contains("commands.streakreload"));
            assertTrue(yaml.contains("permissions.plexkillstreaks.streak"));
            assertTrue(yaml.contains("permissions.plexkillstreaks.reload"));
            assertTrue(yaml.getStringList("softdepend").contains("PlaceholderAPI"));
        }
    }
}
