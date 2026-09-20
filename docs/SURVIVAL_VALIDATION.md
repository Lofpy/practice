# Survival 検証記録 — 2026-09-20

対象: Paper 26.3 build 26 (ALPHA)、Velocity 4.2.0 build 30、ViaVersion / ViaBackwards 5.12.0。
ネットワークと管理コマンドは本番とは別のlocalhost専用環境で検証した。
全Minecraftプロセスは各環境の`run.bat`から起動し、コンソールの通常停止で保存した。
本番サーバー、クラウド設定、既存プレイヤーデータは変更していない。

## 自動テスト

| 対象 | 結果 |
| --- | --- |
| Practice / Java8 | 1049件、失敗0、スキップ1 |
| Lobby / Java8 | 12件、失敗0 |
| AscendingSurvival / Java25 | 36件、失敗0、Windowsリンク作成権限により2件スキップ |
| AscendingNetwork / Java25 | 20件、失敗0 |
| Survival追加デプロイテスト | 16件、失敗0 |

Windowsのデプロイテストでは`fcntl`のインポートだけをスタブにしているため、Linuxファイルロック自体の検証ではない。
Linux CIには通常の全テスト・イメージビルド・コンテナ確認を追加したが、このローカル作業ではDocker daemonが停止中のためコンテナの実起動は未実施。

## 実接続

- `127.0.0.1:25665`の検証プロキシ、`25667`の検証ロビー、`25668`の検証Survivalを使用。
- protocol777（26.3）でロビー→`/survival`→Survival→`/hub`→ロビーの往復成功。
- クライアントへの通信上で`Survival`タイトル、`Online: 1`、`Ping`、`X/Y/Z`の行を確認。
- protocol776（26.2）では`/survival`と`/server survival`をそれぞれ拒否し、ロビーに残留。
- 1.7.10～26.3の代表プロトコルでプロキシのサーバーリスト応答を確認。
- 本番のオンライン認証は変更していない。描画、GUI操作感、認証付きの実アカウント接続は別途確認が必要。

## ワールド管理

- `smoke_meadow`（通常ワールド）を作成し、PvP無効・難易度Hardを設定。
- 初期ワールドの削除要求が拒否されることを確認。
- 確認トークンを使って追加ワールドを保存・アンロード・アーカイブ。
- 正常停止・再起動後も`DELETED`のまま、元のディレクトリが再生成されないことを確認。
- 復元後、`world_gen_settings.dat`のSHA-256がアーカイブ時と一致。
- さらに再起動し、管理ワールドが再ロードされ、実際の難易度がHard、PvP設定がfalseで保持されることを確認。
- 別のNetherワールド`smoke_nether`の新規作成・再起動後のロードも成功。

26.3では追加ワールドが`survival/dimensions/minecraft/<name>`に格納される。
従来形式のフォルダ推定をやめ、実際のPaper APIの保存先・生成APIを使用する修正と回帰テストを追加した。
