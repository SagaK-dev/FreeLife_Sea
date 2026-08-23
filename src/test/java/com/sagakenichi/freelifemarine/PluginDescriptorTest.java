package com.sagakenichi.freelifemarine;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PluginDescriptorTest {

    @Test
    void descriptorRegistersBothPrimaryAndDedicatedCommandsWithoutFixedUsageHint() throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("plugin.yml")) {
            assertNotNull(stream, "plugin.yml must be present in the test classpath");
            String descriptor = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(descriptor.contains("version: 1.12.6"));
            assertTrue(descriptor.contains("softdepend: [Geyser-Spigot, floodgate]"));
            assertTrue(descriptor.contains("  marine:\n"));
            assertTrue(descriptor.contains("  freelifesea:\n"));
            assertTrue(descriptor.contains("aliases: [flsea]"));
            assertFalse(descriptor.contains("usage:"),
                    "Bukkit usage text must not replace real client-side tab suggestions");
        }
    }
}
