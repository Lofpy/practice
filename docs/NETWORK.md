# Poppy Network: ロビーとPvPの分離

2026-09-12時点の構成です。Minecraft **Java版1.7.10～26.2（最新正式版）**向けに設定しています。
Bedrock版と開発中のスナップショットは対象外です。将来の最新版対応にはVelocity・Via系の更新が必要です。

```text
プレイヤー → 公開TCP25565 / Velocity（Minecraftアカウント認証）
                          ├─ lobby / 127.0.0.1:25567 / 独立ロビー
                          └─ pvp   / 127.0.0.1:25566 / 従来のPvP
```

## 起動・停止

ルートの`C:\pvp\run-network.bat`で3プロセスを起動します。内部でも各サーバーの`run.bat`を使います。
一括コンソールでは次のように対象を付けて入力します。

```text
status
pvp list
lobby list
proxy velocity info
stopall
```

`stopall`はプロキシを停止してから両バックエンドを正常保存・停止します。保存中のプロセスを強制終了しません。
コンソールをウィンドウの×で閉じたり、タスクマネージャーで強制終了したりせず、`stopall`を使用してください。

個別の起動場所：

| サーバー | 起動ファイル | Java | 最大メモリ |
| --- | --- | --- | --- |
| プロキシ | `network\proxy\run.bat` | 管理下のTemurin25 | 512MB |
| ロビー | `network\lobby\run.bat` | 既存Temurin17 | 1GB |
| PvP | `runtime\run.bat` | 既存Temurin17 | 既存設定4GB |

Javaのグローバル設定やPATHは変更しません。個別起動と一括起動を重複させないでください。
統合コンソールの記録は`network\console\`、各サーバー自身の記録は各ディレクトリの`logs\`です。

## プレイヤー操作

- 接続先はこれまでと同じホストの`25565`です。最初は必ず独立ロビーへ入ります。
- ロビーのコンパスを右クリックし、ダイヤ剣の「Poppy Practice」を選択するとPvPへ移動します。`/pvp`でも移動できます。
- PvPから`/hub`で独立ロビーへ戻ります。試合中の移動は既存の退出処理と同じ扱いです。
- PvPの`/spawn`と従来の`/lobby`は引き続き**PvP内部の待機場所**へ戻ります。
- 独立ロビーの`/hub`・`/lobby`・`/spawn`はロビースポーンへ戻ります。

独立ロビーは65×65の足場とガラス柵、装飾を持つ専用Voidワールドです。
ダメージ、空腹、通常プレイヤーのブロック操作・アイテム持ち出しを防止しています。
ロビーを編集する場合は、**ロビー側で権限を付与された管理者**が`/lobbybuild`を使います。
バックエンド間でOP権限は自動共有しません。必要な場合は一括コンソールの`lobby op <名前>`で明示的に付与してください。

## バージョン構成

| コンポーネント | 固定したバージョン |
| --- | --- |
| Velocity | 4.1.1 build24 |
| ロビー/PvPのサーバー本体 | WindSpigot2.1.3 / Minecraft1.8.8 |
| ViaVersion | 5.11.0 |
| ViaBackwards | 5.11.0 |
| ViaRewind | 4.1.3 |

Via系3つは**ロビーとPvPそれぞれのpluginsフォルダー**に置きます。Velocityには置きません。
ViaRewindにはViaVersionとViaBackwardsの両方が必要です。
新しいクライアントでもゲーム内容・戦闘・アイテムは1.8.8のものです。最新版サーバーの新ブロック等が追加されるわけではありません。

PvPのProtocolSupportは互換変換の重複を避けてバックアップへ移動します。設定フォルダーは保持します。
ChatterKB・ReachGuardにはViaVersion経由で元のクライアント版を解決する処理を追加しました。
既存の検知対象プロトコル設定は変更しません。対応していない現代版を誤って1.8とみなして検知しないようにしています。

## 認証と保護

- Velocity：`online-mode = true`。購入済みJava版アカウントで認証します。
- バックエンド：`server-ip=127.0.0.1`、`online-mode=false`、`settings.bungeecord: true`。
- Velocity：`player-info-forwarding-mode = "LEGACY"`。1.8.8に対応する形式でUUID・IP・スキンを転送します。
- 古いクライアントに存在しない公開鍵署名を必須にしないため、`force-key-authentication=false`です。Minecraftアカウントのオンライン認証は無効化しません。

バックエンドのoffline-mode警告はこの構成では想定内です。ただし**25566/25567を外部公開したり、server-ipを空に戻したりしないでください**。
同じPC上で信頼できない利用者にプロセスを動かさせないことも必要です。別PCへ分離する場合は別途ファイアウォール・トンネル設計が必要になります。
ルーター／Windows Firewallで公開するのは**TCP25565のみ**です。外部ネットワークからの接続可否はローカル検証とは別に確認してください。

## セットアップと復元

初回作成：`scripts\setup-network.ps1`。配布元のSHA256を検証し、既存のPvPや既存ネットワーク設定は上書きしません。
既存運営者が承諾済みの`runtime\eula.txt`を同じWindSpigotのロビーへ引き継ぎます。新たな利用規約の承諾処理は行いません。

全サーバーを停止してから`powershell -ExecutionPolicy Bypass -File scripts\enable-network.ps1`を実行すると切り替えます。
変更前のファイルは`network\backups\`、適用情報は`network\enabled.json`に保存されます。
通常構成へ戻す場合は全サーバーを停止し、`scripts\disable-network.ps1`を使用してください。
ワールド、プレイヤーデータ、Kit、KB、PoppyPracticeの設定内容は移行・復元で削除しません。

更新時は先にバックアップして正常停止してください。`setup-network.ps1`は既存JARを上書きしないため、
`build-plugin.ps1` / `build-lobby-plugin.ps1`で生成したJARを必要なバックエンドへ個別配置して再起動します。
Java/Via/Velocityの固定版・配布元・ハッシュは`network-template\artifacts.json`に記録しています。
ViaVersionのJava21推奨警告は現行5.11.0のJava17動作を妨げませんが、将来Via系を更新する際は要件を再確認してください。

## 検証範囲

`node scripts\network-probe.js 25565`は1.7.10・1.8.9から26.2までの代表プロトコルでサーバーリスト応答を検証します。
`--auth-check`を付けると、本番プロキシがMinecraftアカウント認証を要求することも確認します。
`--legacy-login`を付けたログイン・往復確認は、認証を切った**一時的なlocalhost:25568**の検証プロキシ専用です。
本番の認証を無効にして実行してはいけません。

サーバーリスト応答は全バージョンの実プレイ保証ではありません。26.2実クライアントでの描画、入力、GUI、PvP操作感は別途確認してください。

2026-09-12の導入確認では、PvP側753件・ロビー側10件の自動テストに成功しました。
1.7.10/1.8.9では実際のログイン、コンパス受信、`/pvp`移動、`/hub`帰還とコンパス再受信を確認しています。
`node scripts\network-modern-probe.js`では26.2/protocol776のLogin→Configuration→Play、
レジストリ交換、ワールド参加、インベントリ・位置情報の通信を検証します（localhost:25568専用）。
`node scripts\network-modern-probe.js --roundtrip`では26.2のロビー→PvP→ロビーの往復も検証します。導入時に成功を確認しています。
公開プロキシへの認証要求確認、バックエンドのlocalhost限定、移行前後のPvP設定・KB設定のハッシュ一致も確認しています。
自動接続プローブは画面の描画や人間の操作感まで検証するものではありません。

## 公式資料

- [Waterfall保守終了](https://papermc.io/news/announcing-the-end-of-life-of-waterfall/)
- [Velocity対応バージョン](https://docs.papermc.io/velocity/server-compatibility/)
- [Velocityのバックエンド保護](https://docs.papermc.io/velocity/security/)
- [ViaVersion導入方法](https://github.com/ViaVersion/ViaVersion/wiki/Installation)
- [Mojangのバージョン一覧](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json)
