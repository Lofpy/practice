package com.poppy.practice.ui;

import com.poppy.practice.kit.BoxingKit;
import com.poppy.practice.kit.NoDebuffKit;
import com.poppy.practice.rating.RatingService;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.Arrays;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.Assert.*;

public class RankedKitSelectionMenuTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void qualifiedKitDisplaysThreeDecimalEloAndStrictRange() {
        RatingService ratings = new RatingService(temporary.getRoot(), Logger.getAnonymousLogger());
        UUID player = UUID.randomUUID();
        for (int i = 0; i < 3; i++) { ratings.recordPlacement(player, "nodebuff", UUID.randomUUID(), 50); }
        String[] lore = KitSelectionMenu.loreFor(KitSelectionMenu.Purpose.QUEUE,
                new NoDebuffKit(), ratings, player);
        String text = Arrays.toString(lore);
        assertTrue(text.contains("Your ELO: &c1650.000"));
        assertTrue(text.contains("100.000 or less"));
        assertEquals("&eLeft Click: &fJoin Ranked Queue", lore[lore.length - 1]);
        assertFalse(text.contains("Locked"));
    }

    @Test public void unqualifiedKitShowsProgressInsteadOfOfferingAnUnlockedQueue() {
        RatingService ratings = new RatingService(temporary.getRoot(), Logger.getAnonymousLogger());
        UUID player = UUID.randomUUID();
        ratings.recordPlacement(player, "boxing", UUID.randomUUID(), 100);
        String text = Arrays.toString(KitSelectionMenu.loreFor(KitSelectionMenu.Purpose.QUEUE,
                new BoxingKit(), ratings, player));
        assertTrue(text.contains("Tier Tests: &e1/3"));
        assertTrue(text.contains("Locked"));
        assertTrue(text.contains("/tier boxing"));
        assertTrue(text.contains("100 hits"));
        assertFalse(text.contains("Left Click"));
        assertFalse(text.contains("Your ELO"));
    }

    @Test public void otherKitsRemainLockedAndEditorBotDescriptionsStayUnchanged() {
        RatingService ratings = new RatingService(temporary.getRoot(), Logger.getAnonymousLogger());
        UUID player = UUID.randomUUID();
        for (int i = 0; i < 3; i++) { ratings.recordPlacement(player, "nodebuff", UUID.randomUUID(), 50); }
        assertTrue(Arrays.toString(KitSelectionMenu.loreFor(KitSelectionMenu.Purpose.QUEUE,
                new BoxingKit(), ratings, player)).contains("Tier Tests: &e0/3"));
        for (KitSelectionMenu.Purpose purpose : new KitSelectionMenu.Purpose[] {
                KitSelectionMenu.Purpose.EDIT, KitSelectionMenu.Purpose.BOT }) {
            assertArrayEquals(KitSelectionMenu.loreFor(purpose, new NoDebuffKit()),
                    KitSelectionMenu.loreFor(purpose, new NoDebuffKit(), ratings, player));
        }
    }
}
