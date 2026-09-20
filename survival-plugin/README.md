# AscendingSurvival

Minecraft Java **26.3**, Paper **26.3 build 26 (ALPHA)**、Java **25** 向けの独立した Survival プラグインです。最新バージョン限定の接続判定は Velocity 側の `AscendingNetwork` が実クライアントのプロトコルで行います。このプラグイン自体はバージョン変換を行いません。

## 表示・プレイ

- 赤いタイトル `Survival` と `Online` / `Ping` / `X` / `Y` / `Z` を英語で表示。
- `Online` は Survival サーバー内のプレイヤー数。座標は負数もブロック座標に切り下げ。
- 標準では 2 tick ごとに更新し、変更された内容のみ送信。
- 通常のサバイバルです。持ち物・装備・経験値・空腹度・死亡時の扱いは Paper / Minecraft の通常動作を保持し、プレイヤーデータはワールド内に永続保存します。Practice の持ち物とは共有しません。
- `/hub` または `/lobby` で接続ロビーへ戻ります。

## 管理コマンド

権限 `ascending.survival.admin` (標準: OP)。ワールド生成は同期処理なので、生成中に負荷が増える可能性があります。

| コマンド | 動作 |
| --- | --- |
| `/sworld list` | 初期・管理ワールドと状態を一覧表示 |
| `/sworld create <name> [normal\|nether\|end] [seed]` | 新しいワールドを作成 (既存フォルダは取り込まない) |
| `/sworld tp <name>` | 対象ワールドのスポーン地点へ移動 |
| `/sworld set <name> difficulty <peaceful\|easy\|normal\|hard>` | 難易度 |
| `/sworld set <name> pvp <true\|false>` | PvP の可否 |
| `/sworld set <name> time <day\|night\|0..23999>` | 時刻を変更 (時間経過の固定は gamerule を使用) |
| `/sworld set <name> weather <clear\|rain\|thunder>` | 天候 |
| `/sworld set <name> spawn` | 実行者の現在地をスポーン地点に設定 |
| `/sworld set <name> border <16..59999968>` | ワールド境界の直径 |
| `/sworld set <name> gamerule <rule> <value>` | 真偽値・整数のゲームルール (Tab 補完対応) |
| `/sworld edit [on\|off]` | 管理者の Creative 編集モード (ブロックを直接配置・破壊) |
| `/sworld delete <name>` | 削除確認コードを発行 |
| `/sworld confirm <token>` | 確認したワールドをアンロードしアーカイブへ移動 |
| `/sworld restore <name>` | 削除した管理ワールドを復元 |

編集モードはログアウト・ロビー移動・プラグイン終了時に元のゲームモードへ戻します。クラッシュ時も次回ログイン時に復元します。Creative 中に管理者が行ったブロック編集やアイテム変更を元に戻す機能ではありません。WorldEdit などの範囲編集コマンドは含みません。

## 削除の安全性と永続データ

- 初期ワールド (`primary-world: survival`) と対応する Nether / End は削除できません。
- `/sworld create` で作成し `managed-worlds.yml` に登録されたワールドだけが削除・復元対象です。
- 確認コードは管理者・ワールドに紐づき、標準 30 秒で失効し、1 回だけ使えます。
- プレイヤーの初期ワールドへの移動と保存・アンロードが成功した場合だけ操作します。
- 削除は完全消去ではなく `<world-container>/.ascending-deleted-worlds/<name>--<UUID>` への移動です。リストに DELETED として残り、`restore` で元通りに戻せます。
- パスの遡り・予約名・リンク経由で管理範囲外に出る操作を拒否します。再帰削除は行いません。
- レジストリは置換保存します。移動前に `DELETE_PENDING` / `RESTORE_PENDING` を保存し、クラッシュ後は実際の保存場所から復旧します。両方存在・両方不在など曖昧な状態では自動生成せず、安全のため初期化を停止します。
- ワールド、`plugins/AscendingSurvival`、`.ascending-deleted-worlds` をセットでバックアップしてください。
- Paper 26.3 の新しい保存形式に対応します。管理ワールドの保存先は、Paper の `World#getWorldPath()` と照合した `<level-name>/dimensions/minecraft/<name>` です。ワールドごとの地形・設定・UUID を含むディメンションフォルダ全体を移動し、共有 `level.dat` や `players` は移動しません。レジストリに保存先を記録し、異なる保存先への操作を拒否します。

## ビルド

```powershell
mvn --batch-mode -f survival-plugin/pom.xml verify
```

`JAVA_HOME` は Java 25 JDK に設定してください。出力は `survival-plugin/target/AscendingSurvival-0.1.0.jar`。本番起動や ALPHA 更新はこのコマンドでは行いません。
