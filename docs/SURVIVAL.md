# Survival 運用ガイド

## モードと対応バージョン

独立ロビーのコンパスから **Survival** を選ぶか、`/survival` で移動します。
Survival 内の `/hub`・`/lobby` は独立ロビーに戻ります。Practice の `/lobby` は従来どおり Practice 内部の待機場所です。

2026-09-20 時点の最新正式クライアント **Java 26.3（protocol 777）だけ**を許可します。
1.7.10 などの旧バージョンはロビー・Practice を引き続き利用できますが、Survival に移動できません。
Velocity が元のクライアントのプロトコルを検証するため、GUI 以外のサーバー移動にも制限が適用されます。
Survival バックエンドには ViaVersion / ViaBackwards / ViaRewind を導入しません。

Paper 26.3 build 26 は **ALPHA** です。運営者の了承に基づいて固定しています。
本番デプロイは別途実施してください。自動更新は行わず、将来の最新版対応では Paper・Velocity・Via 系・API・ゲートの設定をまとめて更新し、接続を検証します。

| 構成要素 | 固定バージョン |
| --- | --- |
| Survival | Paper 26.3 build 26 / Java 25 |
| Proxy | Velocity 4.2.0 build 30 |
| Lobby・Practice の変換 | ViaVersion / ViaBackwards 5.12.0、ViaRewind 4.1.3 |
| バージョン制限 | AscendingNetwork、26.3 / 777 |
| Survival 管理・スコアボード | AscendingSurvival |

配布 URL と SHA-256 は `network-template/artifacts.json` に記録しています。
最新リリースの確認元は [Minecraft 26.3](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3)、
サーバービルドは [Paper の配布 API](https://fill.papermc.io/v3/projects/paper/versions/26.3/builds)です。

## スコアボード

赤を基調とした英語表記で表示します。

```text
       Survival
----------------------
Online: 12
Ping: 35 ms
X: -120
Y: 64
Z: 340
----------------------
```

`Online` は **Survival サーバー内の人数**です。ロビーや Practice の人数は含みません。
座標は現在いるワールドのブロック座標で、負の座標も正しく切り下げます。
既定では2tickごとに更新し、内容が変わったときだけ再送します。

## 管理コマンド

管理権限は `ascending.survival.admin`（既定では OP）です。OP はサーバー間で自動共有しません。
`/sworld` で構文を確認できます。ワールド名は安全な英数字・区切り文字だけに制限します。

| コマンド | 用途 |
| --- | --- |
| `/sworld list` | ワールド一覧 |
| `/sworld create <name> [normal\|nether\|end] [seed]` | 新しい管理対象ワールドを作成 |
| `/sworld tp <name>` | ワールドに移動 |
| `/sworld set <name> ...` | 難易度・PvP・時刻・天候・スポーン・ボーダー・ゲームルールを設定 |
| `/sworld edit [on\|off]` | 建築用のクリエイティブ編集モードを切り替え |
| `/sworld delete <name>` | 削除確認トークンを発行 |
| `/sworld confirm <token>` | 確認対象のワールドを退避 |
| `/sworld restore <name>` | 退避したワールドを復元 |

編集は Minecraft の建築・破壊操作を使う管理者モードです。WorldEdit の範囲コピーなどを追加するものではありません。
メインワールドと標準の Nether / End は削除できません。削除対象はプラグインが管理する追加ワールドに限ります。
確認は実行者と対象に紐づき、有効期限があります。ワールドは永久消去せず、専用の退避先へ移動します。
退避したワールドもディスクを使用するため、定期バックアップと容量監視は必要です。

## データとネットワーク

```text
プレイヤー → Velocity :25565（オンライン認証）
               ├─ Lobby    127.0.0.1:25567（既存 WindSpigot）
               ├─ Practice 127.0.0.1:25566（既存 WindSpigot）
               └─ Survival 127.0.0.1:25568（Paper 26.3）
```

Survival のワールド・所持品・経験値・エンダーチェスト等は、専用バックエンドの通常の Minecraft データとして保存します。
Practice の Kit、ELO、認定結果、所持品を共有・上書きしません。Survival 内の追加ワールド間ではプレイヤーデータを共有します。
26.3のディメンション別保存構造に対応し、管理対象の保存先はPaper APIから取得します。
追加ワールドの削除で、全体の`level.dat`や共有プレイヤーデータを移動・削除することはありません。

既存の 1.8 バックエンドに合わせて Velocity の転送方式は `LEGACY` を維持します。
Survival は localhost のみで待ち受け、Paper の BungeeCord 転送設定を有効にします。
バックエンドの `online-mode=false` は認証済みプロキシ経由専用の設定です。
**25566 / 25567 / 25568 は公開しないでください。外部公開は 25565 のみです。**

ローカル起動も検証も各サーバーの `run.bat` を使用します。構築手順は [ネットワーク運用ガイド](NETWORK.md)、
コンテナ・本番反映は [GCP デプロイガイド](GCP_DEPLOYMENT.md)を参照してください。
既存の EULA 承諾が存在しない環境では起動せず、利用規約を自動承諾しません。

初回構築は `scripts/setup-network.ps1`、既存構成の更新は全サーバーを正常停止したうえで
`scripts/setup-network.ps1 -UpdateExisting` を使用します。変更するJAR・接続設定は
`network/backups/survival-update-*` に保存します。既存のワールド・プレイヤーデータは上書きしません。
モダンプラグインだけをビルドする場合は `scripts/build-modern-plugins.ps1` を使用できます。

Survival の追加分だけメモリ・ディスク・バックアップ量が増えます。VM の容量を確認してから本番へ反映してください。
既定の最大Javaヒープは合計7.5GiB（Practice 4GiB、Lobby 1GiB、Survival 2GiB、Proxy 512MiB）です。
このほかにJavaヒープ外・OS・DB用のメモリが必要です。

## 検証

- Java8のPractice／Lobbyと、Java25のSurvival／Proxyプラグインを別々にビルドします。
- `node scripts/survival-network-probe.js` は隔離した `127.0.0.1:25665` の検証用プロキシ専用です。
  26.3でロビー→Survival→ロビーを往復し、タイトル・Online・Ping・座標の受信を確認します。
- `--old` は26.2で `/survival` と `/server survival` が拒否され、ロビーに残ることを確認します。
- プローブ用に本番認証を無効化しないでください。プローブは描画や人間の操作感の検証を代替しません。
- Windows環境ではリンク作成権限がないため一部のリンク安全性テストがスキップされます。Linux CIで全件を実行します。
