package com.ascendingmc.survival;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public final class RegenerationCompletionTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void noCompletedOperationDoesNotRequireRelocation() throws Exception {
        assertTrue(RegenerationCompletion.load(temporary.getRoot().toPath()).isEmpty());
    }

    @Test public void canonicalMarkerIsReadAndCumulativeDimensionsAreRecognized() throws Exception {
        String id = UUID.randomUUID().toString();
        Files.writeString(temporary.getRoot().toPath().resolve("regeneration-completed.properties"),
                "format=1\nid=" + id + "\ndimensions=overworld,the_nether,event\noperation=RESTORE\n");
        RegenerationCompletion marker = RegenerationCompletion.load(temporary.getRoot().toPath()).orElseThrow();
        assertTrue(marker.unseen(null));
        assertFalse(marker.unseen(id));
        assertTrue(marker.unseen(UUID.randomUUID().toString()));
        assertTrue(marker.affects("minecraft:overworld"));
        assertTrue(marker.affects("minecraft:event"));
        assertFalse(marker.affects("minecraft:the_end"));
        assertFalse(marker.affects("other:event"));
        assertFalse(marker.affects(null));
    }

    @Test public void rejectsInvalidIncompleteOrAmbiguousMarker() {
        for (String name : new String[]{"", "../overworld", "minecraft:overworld", "OVERWORLD", "overworld,", "overworld,overworld"}) {
            Properties values = valid();
            values.setProperty("dimensions", name);
            assertThrows(name, IOException.class, () -> RegenerationCompletion.parse(values));
        }
        for (String id : new String[]{"", "1-1-1-1-1", "broken", "A1234567-1234-1234-1234-123456789ABC"}) {
            Properties values = valid();
            values.setProperty("id", id);
            assertThrows(id, IOException.class, () -> RegenerationCompletion.parse(values));
        }
        Properties future = valid();
        future.setProperty("format", "2");
        assertThrows(IOException.class, () -> RegenerationCompletion.parse(future));
    }

    @Test public void oversizedOrDirectoryMarkerFailsClosed() throws Exception {
        Path marker = temporary.getRoot().toPath().resolve("regeneration-completed.properties");
        Files.writeString(marker, "x".repeat(65537));
        assertThrows(IOException.class, () -> RegenerationCompletion.load(temporary.getRoot().toPath()));
        Files.delete(marker);
        Files.createDirectory(marker);
        assertThrows(IOException.class, () -> RegenerationCompletion.load(temporary.getRoot().toPath()));
    }

    private static Properties valid() {
        Properties properties = new Properties();
        properties.setProperty("format", "1");
        properties.setProperty("id", UUID.randomUUID().toString());
        properties.setProperty("dimensions", "overworld,the_end");
        return properties;
    }
}
