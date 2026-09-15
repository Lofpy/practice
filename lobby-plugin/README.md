# PoppyLobby

既存の PoppyPractice とは独立した、WindSpigot 1.8.8 用のネットワークロビーです。
Java 8 バイトコードでビルドし、サーバー自体は Java 17 で起動します。
1.7.10〜最新クライアントへの変換・認証・転送はプロキシ側で管理します。

## ロビーバックエンドへの導入

1. `scripts/build-lobby-plugin.ps1` を実行します。
2. `lobby-plugin/target/PoppyLobby-0.1.0.jar` を、新規ロビーバックエンドの `plugins` にコピーします。
3. 新規ワールド専用の `server.properties` に `level-name=lobby`、`allow-nether=false` を設定します。
4. ロビーの `bukkit.yml` で `settings.allow-end: false` と、次のジェネレーターを設定します。

```yaml
worlds:
  lobby:
    generator: PoppyLobby
```

生成する床は X/Z=-32〜32 の 65×65、Y=64、外側はすべて奈落です。
ガラスの手すり、街灯、アーチがあり、初期地点は `(0.5, 65, 8.5)` です。
既存のワールドを上書きする機能はありません。新規の `level-name` で導入してください。

5. プロキシのバックエンド名を `lobby` と `pvp` とし、BungeeCord 互換プラグインメッセージを有効にします。
   Velocity は `bungee-plugin-message-channel = true` を使用します。
   バックエンドはプロキシからのみ接続可能にし、IP 転送・認証設定はネットワークの手順に従ってください。

## 操作

- コンパスを右クリック → ダイヤ剣の Poppy Practice を左クリックして `pvp` へ転送。
- `/pvp` でも同じサーバーへ転送。
- ロビー側の `/hub`、`/lobby`、`/spawn` はロビースポーンに戻ります。
- OP または `poppylobby.build` 保有者が `/lobbybuild` で編集を明示的に有効化できます。
  通常は OP を含めてアイテム移動・設置・破壊を保護します。再実行または再ログインで保護状態に戻ります。
- 通常参加者のダメージ・空腹・アイテム取得/投棄・インベントリ操作・ブロック操作を無効化します。
- スコアボードの Online は**このロビーバックエンドの**接続数です。ネットワーク全体の人数ではありません。

## PvP 側に `/hub` のみ追加する

同じ JAR を PvP 側にも配置し、**初回起動前**に `plugins/PoppyLobby/config.yml` を作成します。

```yaml
lobby-server: false
hub-server: lobby
```

このモードは `/hub` → `Connect lobby` の転送だけを提供します。
イベント監視、定期タスク、ワールド生成、テレポート、インベントリ変更、スコアボード変更は登録しません。
`/pvp`、`/lobby`、`/spawn`、`/lobbybuild` は登録せず、PoppyPractice の既存コマンド・ゲーム処理を維持します。
モードを変更したときはバックエンドを再起動してください。

## 設定

- `lobby-server`: `true` で独立ロビー、`false` で `/hub` だけの PvP ブリッジ。
- `world-name`: ロビーのワールド名。`level-name` と合わせます。
- `practice-server`: 転送先のプロキシ内部サーバー名。初期値 `pvp`。
- `hub-server`: ブリッジモードの戻り先。初期値 `lobby`。
- `scoreboard-title`: 色コード `&` を使用可能。1.8 制限のため 32 文字まで。
- `welcome-message`: ロビー参加時のメッセージ。

ビルド: `powershell -ExecutionPolicy Bypass -File scripts/build-lobby-plugin.ps1`
