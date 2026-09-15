package com.poppy.practice.command;

import com.poppy.practice.rating.CertificationResetPlan;
import com.poppy.practice.rating.RatingService;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Real ledger transactions remain isolated from every live player and server file. */
public class TierResetCommandTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void deniedPermissionCannotPreviewConfirmCancelOrReadSuggestions() throws Exception {
        Fixture f = fixture();
        when(f.admin.hasPermission("practice.admin")).thenReturn(false);
        f.run("all");
        f.run("confirm", "anything");
        f.run("cancel");
        assertTrue(f.tabs(f.admin, "").isEmpty());
        verifyZeroInteractions(f.access);
        assertEquals(3, f.ratings.getPlacementCount(f.alice, "nodebuff"));
        assertEquals(3, f.messages.size());
        assertFalse(f.contains("Reset preview:"));
    }

    @Test public void allPreviewReportsScopeWithoutChangingLedgerOrCreatingBackups() throws Exception {
        Fixture f = fixture();
        byte[] before = Files.readAllBytes(f.ledger().toPath());
        f.run("all");
        assertTrue(f.contains("ALL PLAYERS / all"));
        assertTrue(f.contains("Players: 2 | Player/kit records: 4 | Placements: 8"));
        assertTrue(f.contains("including ranked ELO"));
        assertTrue(f.contains("Qualified ratings: 2"));
        assertEquals(1, f.tabs(f.admin, "confirm", "").size());
        assertArrayEquals(before, Files.readAllBytes(f.ledger().toPath()));
        assertArrayEquals(new String[] {"ratings.yml"}, f.directory.list());
        verify(f.access).busyReason(null);
        verify(f.access, never()).notifyReset(any(CertificationResetPlan.class));
    }

    @Test public void allPlayersCanBeResetAndConfirmationCannotBeReplayed() throws Exception {
        Fixture f = fixture();
        f.run("all");
        String token = f.token();
        f.run("confirm", token);
        assertEquals(0, f.ratings.getPlacementCount(f.alice, "nodebuff"));
        assertEquals(0, f.ratings.getPlacementCount(f.alice, "boxing"));
        assertEquals(0, f.ratings.getPlacementCount(f.alice, "combo"));
        assertEquals(0, f.ratings.getPlacementCount(f.bob, "nodebuff"));
        assertTrue(f.contains("Certification reset complete: 4 player/kit records, 8 placements"));
        assertTrue(f.contains("Backup:"));
        assertTrue(f.tabs(f.admin, "confirm", "").isEmpty());
        f.run("confirm", token);
        assertTrue(f.contains("No matching confirmation"));
        verify(f.access, times(1)).notifyReset(any(CertificationResetPlan.class));
        assertEquals(0, new RatingService(f.directory, f.logger)
                .getPlacementCount(f.alice, "nodebuff"));
    }

    @Test public void knownNameSingleKitOnlyResetsSelectedPlayersSelectedKit() throws Exception {
        Fixture f = fixture();
        f.run("Alice", "BoXiNg");
        assertTrue(f.contains("/ boxing"));
        f.run("confirm", f.token());
        assertEquals(0, f.ratings.getPlacementCount(f.alice, "boxing"));
        assertEquals(3, f.ratings.getPlacementCount(f.alice, "nodebuff"));
        assertEquals(1, f.ratings.getPlacementCount(f.alice, "combo"));
        assertEquals(3, f.ratings.getPlacementCount(f.bob, "nodebuff"));
        verify(f.access).resolvePlayer("Alice");
        verify(f.access, times(2)).busyReason(f.alice);
    }

    @Test public void canonicalUuidDoesNotTriggerNameLookupAndDefaultsToAllKits() throws Exception {
        Fixture f = fixture();
        f.run(f.alice.toString().toUpperCase());
        f.run("confirm", f.token());
        assertEquals(0, f.ratings.getPlacementCount(f.alice, "nodebuff"));
        assertEquals(0, f.ratings.getPlacementCount(f.alice, "boxing"));
        assertEquals(0, f.ratings.getPlacementCount(f.alice, "combo"));
        assertEquals(3, f.ratings.getPlacementCount(f.bob, "nodebuff"));
        verify(f.access, never()).resolvePlayer(anyString());
    }

    @Test public void allPlayersSingleKitPreservesOtherKits() throws Exception {
        Fixture f = fixture();
        f.run("ALL", "nodebuff");
        f.run("confirm", f.token());
        assertEquals(0, f.ratings.getPlacementCount(f.alice, "nodebuff"));
        assertEquals(0, f.ratings.getPlacementCount(f.bob, "nodebuff"));
        assertEquals(1, f.ratings.getPlacementCount(f.alice, "boxing"));
        assertEquals(1, f.ratings.getPlacementCount(f.alice, "combo"));
    }

    @Test public void wrongTokenOrOtherSenderCannotExecutePendingReset() throws Exception {
        Fixture f = fixture();
        f.run("Alice");
        String token = f.token();
        f.run("confirm", "wrong-token");
        Player other = admin(UUID.randomUUID(), "OtherAdmin", new ArrayList<String>());
        f.command.onCommand(other, null, "tierreset", new String[] {"confirm", token});
        assertTrue(f.tabs(other, "confirm", "").isEmpty());
        assertEquals(3, f.ratings.getPlacementCount(f.alice, "nodebuff"));
        assertEquals(token, f.token());
        f.run("confirm", token);
        assertEquals(0, f.ratings.getPlacementCount(f.alice, "nodebuff"));
    }

    @Test public void sameDisplayNameDoesNotSharePlayerConfirmation() throws Exception {
        Fixture f = fixture();
        f.run("all");
        Player impostor = admin(UUID.randomUUID(), "Admin", new ArrayList<String>());
        f.command.onCommand(impostor, null, "tierreset", new String[] {"confirm", f.token()});
        assertEquals(3, f.ratings.getPlacementCount(f.alice, "nodebuff"));
        verify(f.access, never()).notifyReset(any(CertificationResetPlan.class));
    }

    @Test public void confirmationExpiresAtExactlySixtySeconds() throws Exception {
        Fixture f = fixture();
        f.run("all");
        String token = f.token();
        f.clock.addAndGet(60000L);
        f.run("confirm", token);
        assertEquals(3, f.ratings.getPlacementCount(f.alice, "nodebuff"));
        assertTrue(f.contains("expired"));
        assertTrue(f.tabs(f.admin, "confirm", "").isEmpty());
    }

    @Test public void confirmationIsStillValidImmediatelyBeforeDeadline() throws Exception {
        Fixture f = fixture();
        f.run("all");
        String token = f.token();
        f.clock.addAndGet(59999L);
        f.run("confirm", token);
        assertEquals(0, f.ratings.getPlacementCount(f.alice, "nodebuff"));
    }

    @Test public void cancelInvalidatesOnlyCurrentAdministratorsPendingReset() throws Exception {
        Fixture f = fixture();
        f.run("Alice");
        String token = f.token();
        Player other = admin(UUID.randomUUID(), "Other", new ArrayList<String>());
        f.command.onCommand(other, null, "tierreset", new String[] {"Bob"});
        assertEquals(1, f.tabs(other, "confirm", "").size());
        f.run("cancel");
        f.run("confirm", token);
        assertTrue(f.contains("cancelled. No results were changed"));
        assertEquals(3, f.ratings.getPlacementCount(f.alice, "nodebuff"));
        assertEquals(1, f.tabs(other, "confirm", "").size());
    }

    @Test public void permissionIsRecheckedAtConfirmationTime() throws Exception {
        Fixture f = fixture();
        f.run("all");
        String token = f.token();
        when(f.admin.hasPermission("practice.admin")).thenReturn(false);
        f.run("confirm", token);
        assertEquals(3, f.ratings.getPlacementCount(f.alice, "nodebuff"));
        verify(f.access, times(1)).busyReason(null);
        verify(f.access, never()).notifyReset(any(CertificationResetPlan.class));
    }

    @Test public void queuedPlayerCannotPreviewAndNoConfirmationIsCreated() throws Exception {
        Fixture f = fixture();
        when(f.access.busyReason(f.alice)).thenReturn("A selected player is queued.");
        f.run("Alice");
        assertTrue(f.contains("is queued"));
        assertFalse(f.contains("Reset preview:"));
        assertTrue(f.tabs(f.admin, "confirm", "").isEmpty());
        assertEquals(3, f.ratings.getPlacementCount(f.alice, "nodebuff"));
    }

    @Test public void queueOrMatchStartedAfterPreviewPreventsResetAndConsumesConfirmation() throws Exception {
        for (String reason : Arrays.asList("A selected player is queued.", "A selected player is in a match.")) {
            Fixture f = fixture();
            f.run("Alice");
            String token = f.token();
            when(f.access.busyReason(f.alice)).thenReturn(reason);
            f.run("confirm", token);
            assertEquals(3, f.ratings.getPlacementCount(f.alice, "nodebuff"));
            assertTrue(f.contains("Preview the reset again after it finishes"));
            assertTrue(f.tabs(f.admin, "confirm", "").isEmpty());
            when(f.access.busyReason(f.alice)).thenReturn(null);
            f.run("confirm", token);
            assertEquals(3, f.ratings.getPlacementCount(f.alice, "nodebuff"));
        }
    }

    @Test public void placementChangedSincePreviewCannotBeSilentlyDeleted() throws Exception {
        Fixture f = fixture();
        f.run("Alice", "boxing");
        String token = f.token();
        f.ratings.recordPlacement(f.alice, "boxing", UUID.randomUUID(), 70);
        f.run("confirm", token);
        assertEquals(2, f.ratings.getPlacementCount(f.alice, "boxing"));
        assertTrue(f.contains("Reset did not complete:"));
        assertFalse(f.contains("Certification reset complete:"));
        assertTrue(f.tabs(f.admin, "confirm", "").isEmpty());
        verify(f.access, never()).notifyReset(any(CertificationResetPlan.class));
    }

    @Test public void unknownNameInvalidKitAndEmptySelectionDoNotCreateConfirmation() throws Exception {
        Fixture f = fixture();
        f.run("NeverJoined");
        assertTrue(f.contains("Unknown or ambiguous player"));
        f.run("Alice", "custom");
        assertTrue(f.contains("[nodebuff|boxing|combo|all]"));
        f.run(UUID.randomUUID().toString(), "combo");
        assertTrue(f.contains("No certification results match"));
        assertTrue(f.tabs(f.admin, "confirm", "").isEmpty());
        assertEquals(3, f.ratings.getPlacementCount(f.alice, "nodebuff"));
    }

    @Test public void malformedUuidNeverSelectsCanonicalPlayersByTruncation() throws Exception {
        Fixture f = fixture();
        f.run("1-1-1-1-1");
        verify(f.access).resolvePlayer("1-1-1-1-1");
        assertTrue(f.contains("Unknown or ambiguous player"));
        assertTrue(f.tabs(f.admin, "confirm", "").isEmpty());
    }

    @Test public void newPreviewIncludingInvalidSelectionInvalidatesOldToken() throws Exception {
        Fixture f = fixture();
        f.run("all");
        String old = f.token();
        f.run("Alice", "not-a-kit");
        f.run("confirm", old);
        assertTrue(f.contains("No matching confirmation"));
        assertEquals(3, f.ratings.getPlacementCount(f.alice, "nodebuff"));
        f.run("Alice");
        String next = f.token();
        f.run("Bob");
        f.run("confirm", next);
        assertEquals(3, f.ratings.getPlacementCount(f.bob, "nodebuff"));
        f.run("confirm", f.token());
        assertEquals(0, f.ratings.getPlacementCount(f.bob, "nodebuff"));
        assertEquals(3, f.ratings.getPlacementCount(f.alice, "nodebuff"));
    }

    @Test public void tabCompletionFiltersNamesKitsAndOnlyOwnersCurrentToken() throws Exception {
        Fixture f = fixture();
        assertEquals(Arrays.asList("all", "Alice"), f.tabs(f.admin, "a"));
        assertEquals(Arrays.asList("boxing"), f.tabs(f.admin, "Alice", "B"));
        assertTrue(f.tabs(f.admin, "cancel", "").isEmpty());
        assertTrue(f.tabs(f.admin, "confirm", "").isEmpty());
        f.run("all");
        String token = f.token();
        assertEquals(Arrays.asList(token), f.tabs(f.admin, "confirm", token.substring(0, 3)));
        f.clock.addAndGet(60000L);
        assertTrue(f.tabs(f.admin, "confirm", "").isEmpty());
    }

    @Test public void successfulResetIsNotReportedFailedWhenOnlineNotificationThrows() throws Exception {
        Fixture f = fixture();
        doThrow(new IllegalStateException("player disconnected")).when(f.access)
                .notifyReset(any(CertificationResetPlan.class));
        f.run("Alice", "boxing");
        String token = f.token();
        f.run("confirm", token);
        assertEquals(0, f.ratings.getPlacementCount(f.alice, "boxing"));
        assertTrue(f.contains("Certification reset complete:"));
        assertFalse(f.contains("Reset did not complete:"));
        assertTrue(f.tabs(f.admin, "confirm", "").isEmpty());
        f.run("confirm", token);
        verify(f.access, times(1)).notifyReset(any(CertificationResetPlan.class));
    }

    @Test public void consoleCanPreviewAndConfirmUsingItsOwnStableIdentity() throws Exception {
        Fixture f = fixture();
        CommandSender console = mock(CommandSender.class);
        when(console.getName()).thenReturn("CONSOLE");
        when(console.hasPermission("practice.admin")).thenReturn(true);
        f.command.onCommand(console, null, "tierreset", new String[] {"all"});
        String token = f.tabs(console, "confirm", "").get(0);
        f.command.onCommand(console, null, "tierreset", new String[] {"confirm", token});
        assertEquals(0, f.ratings.getPlacementCount(f.alice, "nodebuff"));
        verify(f.access).notifyReset(any(CertificationResetPlan.class));
    }

    private Fixture fixture() throws Exception { return new Fixture(temporary.newFolder()); }

    private static Player admin(UUID id, String name, List<String> messages) {
        Player sender = mock(Player.class);
        when(sender.getUniqueId()).thenReturn(id);
        when(sender.getName()).thenReturn(name);
        when(sender.hasPermission("practice.admin")).thenReturn(true);
        doAnswer(invocation -> {
            messages.add(ChatColor.stripColor((String) invocation.getArguments()[0]));
            return null;
        }).when(sender).sendMessage(anyString());
        return sender;
    }

    private static final class Fixture {
        final File directory;
        final Logger logger = Logger.getAnonymousLogger();
        final UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
        final List<String> messages = new ArrayList<String>();
        final Player admin = admin(UUID.randomUUID(), "Admin", messages);
        final TierResetCommand.Access access = mock(TierResetCommand.Access.class);
        final AtomicLong clock = new AtomicLong(1000L);
        final RatingService ratings;
        final TierResetCommand command;

        Fixture(File directory) {
            this.directory = directory;
            logger.setLevel(Level.OFF);
            ratings = new RatingService(directory, logger);
            for (UUID player : Arrays.asList(alice, bob)) {
                for (int i = 0; i < 3; i++) {
                    ratings.recordPlacement(player, "nodebuff", UUID.randomUUID(), 50.0D);
                }
            }
            ratings.recordPlacement(alice, "boxing", UUID.randomUUID(), 60.0D);
            ratings.recordPlacement(alice, "combo", UUID.randomUUID(), 70.0D);
            when(access.resolvePlayer("Alice")).thenReturn(alice);
            when(access.resolvePlayer("Bob")).thenReturn(bob);
            when(access.onlineNames()).thenReturn(Arrays.asList("Alice", "Bob"));
            command = new TierResetCommand(ratings, access, logger, clock::get);
        }

        File ledger() { return new File(directory, "ratings.yml"); }
        boolean contains(String part) { return messages.stream().anyMatch(text -> text.contains(part)); }
        void run(String... args) { assertTrue(command.onCommand(admin, null, "tierreset", args)); }
        List<String> tabs(CommandSender sender, String... args) {
            return command.onTabComplete(sender, null, "tierreset", args);
        }
        String token() {
            List<String> tokens = tabs(admin, "confirm", "");
            assertEquals("Expected exactly one pending confirmation", 1, tokens.size());
            return tokens.get(0);
        }
    }
}
