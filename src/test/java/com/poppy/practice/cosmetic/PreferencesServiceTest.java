package com.poppy.practice.cosmetic;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.Assert.*;

public class PreferencesServiceTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();

    @Test
    public void unsetPlayersAndBotsUseLightningWithoutCreatingAFile() {
        PreferencesService service = service(temporary.getRoot());
        assertEquals(KillEffect.LIGHTNING, service.getKillEffect(first));
        assertEquals(KillEffect.LIGHTNING, service.getKillEffect(null));
        assertTrue(service.isWritable());
        assertFalse(file().exists());
    }

    @Test
    public void eachUuidSelectionSurvivesRestartIndependently() {
        PreferencesService service = service(temporary.getRoot());
        assertTrue(service.setKillEffect(first, KillEffect.EXPLOSION));
        assertTrue(service.setKillEffect(second, KillEffect.REDSTONE));
        PreferencesService reloaded = service(temporary.getRoot());
        assertEquals(KillEffect.EXPLOSION, reloaded.getKillEffect(first));
        assertEquals(KillEffect.REDSTONE, reloaded.getKillEffect(second));
        assertTrue(reloaded.setKillEffect(first, KillEffect.LIGHTNING));
        assertEquals(KillEffect.LIGHTNING, service(temporary.getRoot()).getKillEffect(first));
        assertEquals(KillEffect.REDSTONE, service(temporary.getRoot()).getKillEffect(second));
    }

    @Test
    public void malformedYamlIsNotOverwrittenOnSelection() throws Exception {
        byte[] original = "version: [invalid\nplayers: precious data\n".getBytes(StandardCharsets.UTF_8);
        Files.write(file().toPath(), original);
        PreferencesService service = service(temporary.getRoot());
        assertFalse(service.isWritable());
        assertFalse(service.setKillEffect(first, KillEffect.REDSTONE));
        assertArrayEquals(original, Files.readAllBytes(file().toPath()));
    }

    @Test
    public void unknownFutureVersionsOrEffectsAreReadOnlyNotReset() throws Exception {
        for (String contents : new String[] {
                "version: 2\nplayers: {}\n",
                "version: 1\nplayers:\n  " + first + ":\n    kill-effect: UNKNOWN\n",
                "version: 1\nplayers:\n  not-a-uuid:\n    kill-effect: LIGHTNING\n",
                "version: 1\nplayers: [broken]\n" }) {
            byte[] original = contents.getBytes(StandardCharsets.UTF_8);
            Files.write(file().toPath(), original);
            PreferencesService service = service(temporary.getRoot());
            assertFalse(service.isWritable());
            assertFalse(service.setKillEffect(first, KillEffect.EXPLOSION));
            assertArrayEquals(original, Files.readAllBytes(file().toPath()));
        }
    }

    @Test
    public void additionalFieldsSurviveAnUnrelatedPreferenceChange() throws Exception {
        Files.write(file().toPath(), ("version: 1\nmetadata: keep-this\nplayers:\n  " + first
                + ":\n    kill-effect: REDSTONE\n    future-setting: retained\n").getBytes(StandardCharsets.UTF_8));
        PreferencesService service = service(temporary.getRoot());
        assertTrue(service.setKillEffect(second, KillEffect.EXPLOSION));
        String saved = new String(Files.readAllBytes(file().toPath()), StandardCharsets.UTF_8);
        assertTrue(saved.contains("keep-this"));
        assertTrue(saved.contains("future-setting: retained"));
        assertEquals(KillEffect.REDSTONE, service(temporary.getRoot()).getKillEffect(first));
    }

    @Test
    public void failedSaveRetainsPreviousInMemorySelection() throws Exception {
        PreferencesService service = service(temporary.getRoot());
        assertTrue(service.setKillEffect(first, KillEffect.REDSTONE));
        File preserved = new File(temporary.getRoot(), "original.yml");
        Files.move(file().toPath(), preserved.toPath());
        assertTrue(file().mkdir());
        Files.write(new File(file(), "prevent-replacement").toPath(), new byte[] { 1 });
        assertFalse(service.setKillEffect(first, KillEffect.EXPLOSION));
        assertEquals(KillEffect.REDSTONE, service.getKillEffect(first));
        assertTrue(preserved.isFile());
        assertEquals(0, temporary.getRoot().listFiles((directory, name) -> name.endsWith(".tmp")).length);
    }

    @Test
    public void nonOwnerThreadCannotReadOrMutatePreferences() throws Exception {
        final PreferencesService service = service(temporary.getRoot());
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        Thread worker = new Thread(() -> {
            try { service.setKillEffect(first, KillEffect.EXPLOSION); }
            catch (Throwable thrown) { error.set(thrown); }
        });
        worker.start();
        worker.join();
        assertTrue(error.get() instanceof IllegalStateException);
        assertFalse(file().exists());
        assertEquals(KillEffect.LIGHTNING, service.getKillEffect(first));
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingPlayerCannotBePersisted() {
        service(temporary.getRoot()).setKillEffect(null, KillEffect.REDSTONE);
    }

    private PreferencesService service(File directory) {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        return new PreferencesService(directory, logger);
    }

    private File file() { return new File(temporary.getRoot(), "cosmetic-preferences.yml"); }
}
