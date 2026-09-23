package com.ascendingmc.survival;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Online commands only reserve work. Files are swapped exclusively by the offline launcher. */
final class RegenerationCommands {
    private static final String PREFIX = "§cSurvival §8» §f";
    private final Logger logger;
    private final Path dataRoot;
    private final String levelName;
    private final Supplier<List<World>> worlds;
    private final DeletionConfirmations confirmations;
    private final Map<String, RegenerationPlan> proposals = new HashMap<>();

    RegenerationCommands(JavaPlugin plugin, String levelName, Supplier<List<World>> worlds, long lifetimeMillis)
            throws IOException {
        this(plugin.getLogger(), plugin.getServer().getWorldContainer().toPath(), levelName, worlds, lifetimeMillis);
    }

    RegenerationCommands(Logger logger, Path dataRoot, String levelName, Supplier<List<World>> worlds,
                         long lifetimeMillis) throws IOException {
        this.logger = logger;
        this.dataRoot = dataRoot.toRealPath();
        this.levelName = levelName;
        this.worlds = worlds;
        this.confirmations = new DeletionConfirmations(System::currentTimeMillis, lifetimeMillis);
    }

    void execute(CommandSender sender, String[] args) throws IOException {
        if (args.length < 2) {
            help(sender);
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "status" -> {
                requireLength(args, 2, "/sworld regenerate status");
                RegenerationPlan pending = OfflineWorldRegenerator.readPending(dataRoot);
                if (pending == null) say(sender, "再生成・復元の予約はありません。");
                else say(sender, "予約: " + pending.operation() + " [" + String.join(", ", pending.dimensions())
                        + "] ID: " + pending.id() + "。次回の正常な停止・起動時に処理します。");
            }
            case "cancel" -> {
                requireLength(args, 2, "/sworld regenerate cancel");
                boolean cancelled = OfflineWorldRegenerator.cancelPending(dataRoot);
                say(sender, cancelled ? "再生成・復元の予約を取り消しました。" : "取り消す予約はありません。");
                if (cancelled) logger.info(sender.getName() + " cancelled offline world regeneration.");
            }
            case "confirm" -> confirm(sender, args);
            case "restore" -> {
                requireLength(args, 3, "/sworld regenerate restore <backup-id>");
                requireNoPending();
                propose(sender, OfflineWorldRegenerator.restorePlan(dataRoot, args[2]));
            }
            default -> {
                requireLength(args, 2, "/sworld regenerate <world|all>");
                requireNoPending();
                List<String> dimensions = new ArrayList<>();
                for (World world : worlds.get()) {
                    if (args[1].equalsIgnoreCase("all") || world.getName().equalsIgnoreCase(args[1])
                            || world.getKey().getKey().equalsIgnoreCase(args[1])) {
                        dimensions.add(verifiedDimension(world));
                    }
                }
                if (dimensions.isEmpty()) throw new IllegalArgumentException("対象の読み込み済みワールドがありません: " + args[1]);
                propose(sender, RegenerationPlan.regenerate(levelName, dimensions));
            }
        }
    }

    private void propose(CommandSender sender, RegenerationPlan plan) throws IOException {
        verifyTargets(plan);
        String administrator = administrator(sender);
        String token = confirmations.request(administrator, plan.id());
        proposals.put(administrator, plan);
        say(sender, "対象: §c" + String.join(", ", plan.dimensions()));
        if (plan.operation() == RegenerationPlan.Operation.REGENERATE) {
            say(sender, "建築物・地形・チェスト内のアイテム・エンティティを初期化し、同じシードで再生成します。");
        } else {
            say(sender, "退避した地形に復元します。現在の地形も別のバックアップに退避します。");
        }
        say(sender, "プレイヤーの所持品・エンダーチェスト・実績は保持します。旧地形は削除せず退避します。");
        say(sender, "期限内に確認: §c/sworld regenerate confirm " + token);
        say(sender, "確認は予約のみです。Survivalを正常停止し、通常の起動スクリプトから再起動してください。");
    }

    private void confirm(CommandSender sender, String[] args) throws IOException {
        requireLength(args, 3, "/sworld regenerate confirm <token>");
        requireNoPending();
        String administrator = administrator(sender);
        String id = confirmations.consume(administrator, args[2]);
        if (id == null) throw new IllegalArgumentException("確認コードが違うか、有効期限が切れています。");
        RegenerationPlan plan = proposals.remove(administrator);
        if (plan == null || !plan.id().equals(id)) throw new IllegalArgumentException("確認対象が見つかりません。もう一度予約してください。");
        verifyTargets(plan);
        for (World world : worlds.get()) {
            if (plan.dimensions().contains(world.getKey().getKey())) world.save();
        }
        OfflineWorldRegenerator.request(dataRoot, plan);
        say(sender, "予約しました。次回起動前に処理します。ID / バックアップID: §c" + plan.id());
        say(sender, "確認: /sworld regenerate status | 取消: /sworld regenerate cancel");
        if (plan.operation() == RegenerationPlan.Operation.REGENERATE) {
            say(sender, "復元予約: /sworld regenerate restore " + plan.id());
        }
        logger.info(sender.getName() + " queued " + plan.operation() + " " + plan.id()
                + " for dimensions " + plan.dimensions());
    }

    private void verifyTargets(RegenerationPlan plan) throws IOException {
        if (!plan.levelName().equals(levelName)) throw new IOException("Backup belongs to another level.");
        List<String> loaded = new ArrayList<>();
        for (World world : worlds.get()) loaded.add(verifiedDimension(world));
        if (!loaded.containsAll(plan.dimensions())) {
            throw new IllegalArgumentException("対象ワールドの状態が変わりました。すべて読み込み済みであることを確認してください。");
        }
    }

    private String verifiedDimension(World world) throws IOException {
        if (!world.getKey().getNamespace().equals("minecraft")) {
            throw new IOException("Unsupported world namespace: " + world.getKey());
        }
        String dimension = world.getKey().getKey();
        Path expected = dataRoot.resolve(levelName).resolve("dimensions/minecraft").resolve(dimension).normalize();
        if (!world.getWorldPath().toAbsolutePath().normalize().equals(expected)) {
            throw new IOException("World directory does not match the offline regeneration layout: " + world.getName());
        }
        return dimension;
    }

    void requireNoPending() throws IOException {
        if (OfflineWorldRegenerator.readPending(dataRoot) != null) {
            throw new IllegalArgumentException("再生成・復元が予約済みです。先に /sworld regenerate cancel または再起動してください。");
        }
    }

    void forget(Player player) {
        String id = player.getUniqueId().toString();
        confirmations.forget(id);
        proposals.remove(id);
    }

    private static String administrator(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId().toString() : "console:" + sender.getName();
    }

    private static void requireLength(String[] args, int expected, String usage) {
        if (args.length != expected) throw new IllegalArgumentException(usage);
    }

    private static void say(CommandSender sender, String text) { sender.sendMessage(PREFIX + text); }

    static void help(CommandSender sender) {
        say(sender, "/sworld regenerate <world|all> | confirm <token> | status | cancel");
        say(sender, "/sworld regenerate restore <backup-id> (復元も確認と再起動が必要)");
        say(sender, "all は初期の通常世界・Nether・Endと、読み込み済みの管理ワールドすべてが対象です。");
    }
}
