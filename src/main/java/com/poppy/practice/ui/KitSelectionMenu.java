package com.poppy.practice.ui;

import com.poppy.practice.kit.Kit;
import com.poppy.practice.match.BoxingRules;
import com.poppy.practice.match.ComboRules;
import com.poppy.practice.rating.RatingService;
import com.poppy.practice.util.ItemBuilder;
import com.poppy.practice.language.PlayerLanguage;
import org.bukkit.Material;

import java.util.Collection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Queue, editor and bot selectors deliberately share the same left-aligned layout. */
public final class KitSelectionMenu extends MenuHolder {
    public static final String CERTIFICATION_QUEUE_NAME = "&cCertification Queue";
    public enum Purpose { QUEUE, EDIT, BOT }

    private final Purpose purpose;
    private final Map<Integer, String> kitsBySlot = new HashMap<Integer, String>();

    public KitSelectionMenu(Purpose purpose, Collection<Kit> kits) {
        this(purpose, kits, null, null);
    }

    public KitSelectionMenu(Purpose purpose, Collection<Kit> kits, RatingService ratings, UUID viewerId) {
        this(purpose, kits, ratings, viewerId, PlayerLanguage.JAPANESE);
    }

    public KitSelectionMenu(Purpose purpose, Collection<Kit> kits, RatingService ratings, UUID viewerId,
                            PlayerLanguage language) {
        super(sizeFor(kits.size()), language.choose(purpose == Purpose.BOT ? "Bot対戦 | キット選択"
                : purpose == Purpose.QUEUE ? "対戦キュー | キット選択" : "キット編集 | キット選択", titleFor(purpose)));
        this.purpose = purpose;
        int slot = 0;
        for (Kit kit : kits) {
            if (slot >= getInventory().getSize() - 9) {
                break;
            }
            kitsBySlot.put(slot, kit.getId());
            getInventory().setItem(slot++, new ItemBuilder(kit.getIcon())
                    .durability(kit.getIconDurability())
                    .name("&c" + kit.getDisplayName())
                    .lore(language == PlayerLanguage.JAPANESE ? japaneseLoreFor(purpose, kit, ratings, viewerId)
                            : loreFor(purpose, kit, ratings, viewerId))
                    .build());
        }
        int footer = getInventory().getSize() - 9;
        for (int index = footer; index < getInventory().getSize(); index++) {
            getInventory().setItem(index, new ItemBuilder(Material.STAINED_GLASS_PANE)
                    .durability((short) 7).name(" ").build());
        }
        getInventory().setItem(footer, new ItemBuilder(Material.BOOK)
                .name(language.choose(purpose == Purpose.BOT ? "&c手順1: キットを選択"
                        : purpose == Purpose.QUEUE ? "&cランク対戦" : "&cキット配置",
                        purpose == Purpose.BOT ? "&cStep 1: Choose a Kit"
                        : purpose == Purpose.QUEUE ? "&cRanked Queue" : "&cKit Layouts"))
                .lore(language == PlayerLanguage.JAPANESE ? japaneseFooter(purpose, ratings != null)
                        : purpose == Purpose.QUEUE && ratings != null ? new String[] {
                        "&7Complete &f3 Tier Tests per kit &7to unlock.",
                        "&7Only opponents within &f100.000 ELO&7.",
                        "&7Equal ELO: winner +16.000 / loser -16.000.",
                        "&7Use the redstone to leave the queue."
                } : footerLoreFor(purpose))
                .build());
        getInventory().setItem(getCloseSlot(), new ItemBuilder(Material.BEDROCK)
                .name(language.choose("&c閉じる", "&cClose"))
                .lore(language.choose("&f左クリック: &7ロビーへ戻る", "&fLeft Click: &7Return to the lobby")).build());
        if (purpose == Purpose.QUEUE) {
            List<String> certificationLore = new ArrayList<String>();
            certificationLore.add(language.choose("&7固定設定のBotとの認定試合。", "&7Play fixed-bot certification matches."));
            certificationLore.add(language.choose("&7各キット3回でランク対戦を解放します。", "&7Complete 3 per kit to unlock Ranked Queue."));
            if (ratings != null && viewerId != null) {
                certificationLore.add("");
                for (Kit kit : kits) {
                    certificationLore.add("&7" + kit.getDisplayName() + ": "
                            + (ratings.isQualified(viewerId, kit.getId()) ? language.choose("&c認定済み", "&cCertified") : "&f"
                                + ratings.getPlacementCount(viewerId, kit.getId()) + "/3"));
                }
            }
            certificationLore.add("");
            certificationLore.add(language.choose("&f左クリック: &7認定キットを選択", "&fLeft Click: &7Select a certification kit"));
            getInventory().setItem(getCertificationSlot(), new ItemBuilder(Material.EXP_BOTTLE)
                    .name(language.choose("&c認定キュー", CERTIFICATION_QUEUE_NAME))
                    .lore(certificationLore.toArray(new String[certificationLore.size()])).build());
        }
    }

    private static String[] japaneseFooter(Purpose purpose, boolean ranked) {
        if (purpose == Purpose.BOT) return new String[] { "&7手順1: NoDebuff・Boxing・Comboから選択。",
                "&7手順2: Bot設定を調整。", "&7開始ボタンを押すと対戦を開始します。", "&7キット選択だけでは試合は始まりません。" };
        if (purpose == Purpose.EDIT) return new String[] { "&7保存した配置は試合に適用されます。", "&7アイテムの種類や個数は変更できません。" };
        return ranked ? new String[] { "&7各キット&f3回の認定&7で解放。", "&7ELO差&f100.000以内&7の相手と対戦。",
                "&7同ELOなら勝者+16.000 / 敗者-16.000。", "&7レッドストーンでキューから退出。" }
                : new String[] { "&7キットを選んで対戦キューに参加します。", "&7レッドストーンでキューから退出できます。" };
    }

    private static String[] japaneseLoreFor(Purpose purpose, Kit kit, RatingService ratings, UUID viewerId) {
        List<String> lines = new ArrayList<String>();
        if (purpose == Purpose.EDIT) {
            lines.add("&7アイテムの配置のみ変更できます。");
            lines.add("&7種類・個数・エンチャントは変わりません。");
        } else if (BoxingRules.isBoxing(kit.getId())) {
            lines.add("&7先に&f" + BoxingRules.HITS_TO_WIN + "ヒット&7で勝利。");
            lines.add("&7体力ダメージなし、ノックバックあり。");
            lines.add("&7ダイヤの剣と常時移動速度II。");
            lines.add("&7回復ポーション・エンダーパールなし。");
        } else if (ComboRules.isCombo(kit.getId())) {
            lines.add("&7専用ノックバックで高速コンボ。");
            lines.add("&7専用のヒット無敵時間設定。");
            lines.add("&7ダイヤ装備2組と鋭さVの剣。");
            lines.add("&7移動速度II・エンチャント金リンゴ・ニンジン。");
            lines.add("&7エンダーパールのクールダウン: &f8秒");
        } else {
            lines.add(purpose == Purpose.BOT ? "&7NoDebuffキットでBotと対戦。" : "&7同じキットのプレイヤーと対戦。");
            lines.add("&7回復ポーション・エンダーパールあり。");
        }
        lines.add("");
        if (purpose == Purpose.QUEUE && ratings != null && viewerId != null) {
            if (ratings.isQualified(viewerId, kit.getId())) {
                lines.add("&7自分のELO: &c" + RatingService.format(ratings.getRatingMilli(viewerId, kit.getId())));
                lines.add("&7対戦相手とのELO差: &f100.000以内");
                lines.add("&f左クリック: &7ランク対戦に参加");
            } else {
                lines.add("&7認定試合: &f" + ratings.getPlacementCount(viewerId, kit.getId()) + "/3");
                lines.add("&c未解放: このキットで認定を3回完了してください。");
                lines.add("&7下の認定キュー、または &f/tier " + kit.getId());
            }
        } else lines.add(purpose == Purpose.BOT ? "&f左クリック: &7Bot設定へ"
                : purpose == Purpose.EDIT ? "&f左クリック: &7配置を編集" : "&f左クリック: &7キューに参加");
        return lines.toArray(new String[lines.size()]);
    }

    public Purpose getPurpose() {
        return purpose;
    }

    public String kitIdAt(int rawSlot) {
        return kitsBySlot.get(rawSlot);
    }

    public int getCloseSlot() {
        return getInventory().getSize() - 5;
    }

    /** Queue-only footer action, separate from all kit and close slots. */
    public int getCertificationSlot() {
        return purpose == Purpose.QUEUE ? getInventory().getSize() - 1 : -1;
    }

    static int sizeFor(int kitCount) {
        return Math.min(54, Math.max(18, ((kitCount + 8) / 9 + 1) * 9));
    }

    static String titleFor(Purpose purpose) {
        if (purpose == Purpose.BOT) {
            return "Bot Fight | Select a Kit";
        }
        return purpose == Purpose.QUEUE ? "Queue | Select a Kit" : "Kit Editor | Select a Kit";
    }

    static String[] footerLoreFor(Purpose purpose) {
        if (purpose == Purpose.BOT) {
            return new String[] {
                    "&7Step 1: Choose NoDebuff, Boxing or Combo.",
                    "&7Step 2: Adjust the bot settings.",
                    "&7Then click Start to begin your match.",
                    "&7Choosing a kit does not start a match."
            };
        }
        return new String[] {
                purpose == Purpose.QUEUE ? "&7Choose a kit to enter matchmaking."
                        : "&7Saved layouts are applied to your matches.",
                purpose == Purpose.QUEUE ? "&7Use the redstone to leave the queue."
                        : "&7Item types and amounts stay unchanged."
        };
    }

    static String[] loreFor(Purpose purpose, Kit kit) {
        if (purpose == Purpose.BOT) {
            if (ComboRules.isCombo(kit.getId())) {
                return new String[] {
                        "&7Fast combos with dedicated knockback.",
                        "&7Separate hit-invulnerability settings.",
                        "&7The bot uses enchanted golden apples to heal.",
                        "&7Two diamond armor sets and a Sharpness V sword.",
                        "&7Ender pearl cooldown: &f8 seconds&7.",
                        "", "&eLeft Click: &fContinue to bot settings"
                };
            }
            if (BoxingRules.isBoxing(kit.getId())) {
                return new String[] {
                        "&7First to &f" + BoxingRules.HITS_TO_WIN + " hits &7wins.",
                        "&7No health damage. Knockback is enabled.",
                        "&7Diamond sword and permanent Speed II.",
                        "&7No healing potions or ender pearls.",
                        "", "&eLeft Click: &fContinue to bot settings"
                };
            }
            return new String[] {
                    "&7Defeat the bot using your NoDebuff kit.",
                    "&7Healing potions and ender pearls enabled.",
                    "", "&eLeft Click: &fContinue to bot settings"
            };
        }
        String action = purpose == Purpose.QUEUE
                ? "&eLeft Click: &fJoin queue" : "&eLeft Click: &fEdit layout";
        if (ComboRules.isCombo(kit.getId())) {
            if (purpose == Purpose.EDIT) {
                return new String[] {
                        "&7Change item positions only.",
                        "&7Item types, amounts and enchants stay unchanged.",
                        "", action
                };
            }
            return new String[] {
                    "&7Fast combos with dedicated knockback.",
                    "&7Separate hit-invulnerability settings.",
                    "&7Two diamond armor sets and a Sharpness V sword.",
                    "&7Speed II, enchanted golden apples and carrots.",
                    "&7Ender pearl cooldown: &f8 seconds&7.",
                    "", action
            };
        }
        if (BoxingRules.isBoxing(kit.getId())) {
            return new String[] {
                    "&7First to &f" + BoxingRules.HITS_TO_WIN + " hits &7wins.",
                    "&7No health damage. Knockback is enabled.",
                    "&7Diamond sword and permanent Speed II.",
                    purpose == Purpose.QUEUE ? "&7Find another Boxing player." : "&7Change item positions only.",
                    "", action
            };
        }
        return new String[] { purpose == Purpose.QUEUE
                ? "&7Find a player using this kit." : "&7Change item positions only.", "", action };
    }

    static String[] loreFor(Purpose purpose, Kit kit, RatingService ratings, UUID viewerId) {
        String[] base = loreFor(purpose, kit);
        if (purpose != Purpose.QUEUE || ratings == null || viewerId == null) {
            return base;
        }
        List<String> lines = new ArrayList<String>(Arrays.asList(base));
        // Replace the old generic click hint, retaining kit-specific gameplay information above it.
        lines.remove(lines.size() - 1);
        boolean qualified = ratings.isQualified(viewerId, kit.getId());
        if (qualified) {
            lines.add("&7Your ELO: &c" + RatingService.format(ratings.getRatingMilli(viewerId, kit.getId())));
            lines.add("&7Opponent ELO difference: &f100.000 or less");
            lines.add("&eLeft Click: &fJoin Ranked Queue");
        } else {
            lines.add("&7Tier Tests: &e" + ratings.getPlacementCount(viewerId, kit.getId()) + "/3");
            lines.add("&cLocked: complete 3 Tier Tests for this kit.");
            lines.add("&7Open Certification Queue below or use &f/tier " + kit.getId());
        }
        return lines.toArray(new String[lines.size()]);
    }
}
