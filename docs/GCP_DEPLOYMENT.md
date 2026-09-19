# GCPデプロイ・初期構築・復旧手順

## 構成と保証範囲

単一Compute Engine VM（Debian 12）でVelocity / Lobby / PvPを動かす。
停止時間あり。試合状態のライブ移行・無停止更新は行わない。メンテナンス時間を事前告知する。

- 専用VPC。SSHはIAPのTCP 22のみ。バックエンドは127.0.0.1:25566/25567。
- 初期値では25565の許可ルールを作らない。player_cidrsに検証者のIP/32を設定してから検証する。
- /srv/poppyは別の100GBデータディスク。VM削除保護、データディスク/バックアップバケットのprevent_destroyを使用。
- 非root UID/GID 10001。読み取り専用コンテナ、書込先はdataとtmpfs。
- state/{pvp,lobby,proxy}にワールド・設定・レート等を保存。releasesにdigest固定の構成を保存。
- currentシンボリックリンクが確定リリースを指す。稼働中にソースをgit pullしない。
- /hub用PoppyLobby bridgeとVia系の設定もPvPに含める。未知/重複JARは起動時に拒否する。
- デプロイ・OS再起動中はホストファイアウォールでゲーム入口を閉じ、全サービスの準備完了後だけ開く。

## 0. 手動承認機能の前提（先に確認）

production EnvironmentのRequired reviewersを設定し、管理者によるbypassを無効にする。
Environmentのデプロイ対象はmainだけに制限する。mainの変更権限はVMの管理者相当として扱う。
workflow_dispatchでDEPLOY_PRODUCTIONを入力しても、Environmentの承認を代替しない。

**非公開リポジトリでは契約プランによってRequired reviewersが利用できない。**
GitHub Free/Pro/Teamではこの機能は公開リポジトリのみ。
現在のリポジトリの公開範囲は変更しない。機能が使えない場合は契約/別の承認方式を先に相談する。
workflowは承認ルールをAPIで確認できない場合、GCP認証前に失敗する。チェックを削除して進めない。
API権限不足でも同様に停止する。JSONキーへの置換や権限拡大で回避しない。

公式: https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments

## 1. GCPプロジェクトとTerraform

GCPプロジェクト作成・請求先紐付けは運営者が行う。このPRのCIはGCPを作成しない。
予算通知も設定する（通知は課金の強制停止ではない）。
Terraform 1.9.8を使用し、管理者としてCloud Shell等で以下を実行する。

```bash
gcloud auth application-default login
cp infra/gcp/terraform.tfvars.example infra/gcp/terraform.tfvars
# project_idと必要なら検証者IP/32を編集
terraform -chdir=infra/gcp init
terraform -chdir=infra/gcp fmt -check
terraform -chdir=infra/gcp validate
terraform -chdir=infra/gcp plan -out=first.tfplan
# 表示された台数/ディスク/権限/費用を確認してから
terraform -chdir=infra/gcp apply first.tfplan
```

API有効化・IAM作成の権限が必要。bootstrapは人の認証で行い、デプロイSAにTerraform適用権限は渡さない。
この構成は既存の新規プロジェクトにリソースを作るもので、プロジェクト/請求先自体はTerraformで作らない。
state/tfvars/planはGitへ入れず、初回apply後に暗号化・アクセス制限した場所へ保存する。
複数人運用に移る前にGCS backendへstateを移管する。生成された.terraform.lock.hclはレビューしてコミットする。
データディスクやバケットを作り直すplanになったら停止して確認する。

データディスクは初回、指定デバイスが完全に空の場合だけext4で初期化する。
既存ファイルシステムの再フォーマットはしない。以後のブートでは再インストールしない。
bootstrapはDocker/Google Cloud CLIの署名付きAPTリポジトリを設定する。
VMへの接続はIAP+OS Login。運営者自身にも適切なIAP/OS Login/SA使用権限を管理者が付与する。

## 2. GitHub Environment変数

productionへ以下を設定する。SA JSONキーやSSH秘密鍵をGitHubへ登録しない。

| 変数 | 値 |
|---|---|
| GCP_PROJECT_ID | 作成したproject_id |
| GCP_REGION | asia-northeast1等 |
| GCP_VM_NAME | Terraform vm_name出力 |
| GCP_VM_ZONE | vm_zone出力 |
| GCP_WORKLOAD_IDENTITY_PROVIDER | workload_identity_provider出力 |
| GCP_DEPLOY_SERVICE_ACCOUNT | deploy_service_account出力 |

WIFはリポジトリ/所有者の数値ID、main、production、指定workflow、手動起動イベントを制限する。
デプロイSAは対象Artifact Registryへの書込、対象VMの管理ログイン、対象IAPトンネルのみ。
Computeのメタデータ読取はproject範囲。VMのroot権限はデプロイに必要なため明示している。
VMのSAは対象Registry読取とバックアップ作成のみ。バックアップの上書き/削除権限は持たない。

## 3. 初回データ移行とEULA（デプロイ前）

旧Windowsネットワークをstopallで正常停止し、別のバックアップを確保してからコピーする。
旧環境は削除しない。全ワールド、playerdata、plugins設定/レート/キット、KB、ops等を保持する。

| 旧ディレクトリ | VMの移行先 |
|---|---|
| runtime | /srv/poppy/state/pvp |
| network/lobby | /srv/poppy/state/lobby |
| network/proxy | /srv/poppy/state/proxy |

実行JAR、Windows起動スクリプト、キャッシュ/ログは移行不要。
plugins内のJARは新イメージが管理する。ProtocolSupport、バージョン付きの旧PoppyPractice JAR、
独自の追加JARは自動削除しないので、移行先に含めず退避して互換性を確認する。
移行後はstate配下の所有者をUID/GID 10001へ変更する。ファイル権限も読書き可能にする。
例: `sudo chown -R 10001:10001 /srv/poppy/state`（この対象を確認してから実行）。

新規構築ならpvp/lobby両方のeula.txtを運営者自身で作成する。
Minecraft EULAを読み同意する場合だけeula=trueとする。CI/起動スクリプトは同意を自動生成しない。
既存の同意ファイルを移行してよいかは運営者が判断する。

既存設定は上書きしない。次の設定を移行先で確認する。

- proxy: online-mode=true、bind=0.0.0.0:25565、LEGACY転送、lobby/pvp宛先は127.0.0.1。
- backend: server-ip=127.0.0.1、online-mode=false、enable-rcon=false、portは25566/25567。
- backend spigot.yml: settings.bungeecord=true。
- PvP plugins/PoppyLobby/config.ymlはnetwork-template/pvp-lobby.ymlのbridge用設定。

不一致の場合は起動を拒否する。バックエンドのonline-mode=falseだけを単体公開しない。
初回移行データにもシンボリックリンク/特殊ファイルを含めない（整合バックアップは拒否する）。

## 4. リリース

1. mainへマージする前にCIのTerraform/Java/デプロイ単体/コンテナsmokeが成功したことを確認。
2. メンテナンスを告知し、GitHub ActionsのDeploy production to GCPをmainから起動。
3. DEPLOY_PRODUCTIONを入力し、production Environmentで承認する。
4. workflowは起動時のmainコミットをビルド/テストし、成果物を一意のタグでpushする。
5. 実際の配置はタグでなくsha256 digest。Javaベースdigest/上流JAR checksumもmanifestへ記録する。

ベースイメージは各リリースで公式タグをdigestに解決して記録する。
同じコミットを再ビルドするとAPT更新等で別digestになり得る。復旧は既存digestを再利用する。
Registryの旧リリースを手動/自動削除しない。current/前世代のdigestは必ず保持する。

ホスト側の更新順:

1. ファイルロック、イメージ取得、EULA確認（ここまでは旧環境を停止しない）。
2. 入口を閉鎖。proxyのend → lobbyのstop → pvpのstop。各終了を確認。
3. タイムアウトならkillせず中止。journalを残し、入口を閉じたまま運営者へ返す。
4. 全writer停止後にstateをアーカイブしchecksumを計算。GCSへ世代0条件で保存。
5. 新PvP → Lobby → Proxyを起動。plugin有効化ログとMinecraft status応答でreadiness確認。
6. 全て成功したらcurrentを原子的に切替え、journalを消して入口を開く。
7. 起動失敗なら新writerを正常停止、失敗stateを退避、バックアップを検証/復元し旧digestを起動。

バックアップ作成/転送失敗時はアップグレードせず旧環境を再開する。
新環境を正常停止できない場合、復元を強行しない。
CIの接続が切れてもsystemd上の更新は継続する。再起動等で中断された処理はjournalが残り、
自動再開しない。GitHubの赤表示だけを見て別デプロイを連打しない。

## 5. バックアップ・障害復旧

- 毎回の停止後バックアップ: ローカルとGCS。GCSは7日間保持制限、30日後削除。
- 日次ディスクスナップショット: 7世代。**稼働中のためcrash-consistent**であり、停止後backupと区別。
- ローカルbackup/failed-state/旧releaseは自動削除しない。容量を監視し、
  遠隔backupを復元テストしてから運営者が対象を指定して整理する。
- 基盤の強制停止・OOM・電源断まで正常保存を保証できない。
- 定期的な別VMへの復元訓練を行う。バックアップは取得成功だけで復元保証とはしない。

状態確認:
```bash
sudo cat /srv/poppy/transaction.json
sudo readlink -f /srv/poppy/current
sudo journalctl -u 'poppy-deploy-*' -n 200
sudo systemctl status poppy-network
```

中断/停止タイムアウトの原因を除去し、残るwriterが正常停止可能になったら:
```bash
sudo python3 /opt/poppy/deploy.py recover
```
recoverはjournalの候補を再び正常停止し、指定backupをchecksum検証して旧データ/旧releaseを復元する。
バックアップ前の中断ならデータを書き換えず旧releaseを再起動する。
旧releaseがない初回失敗では入口を閉じて初期stateだけ戻す。
journalを手動で削除して稼働中のデータへ復元を行わない。

ローカルディスク故障では管理者の資格情報でGCSからアーカイブ/checksumを別の場所へ取得し、
内容/UID/GIDを確認して停止中の新しいデータディスクへ復元する。
VMのSAにはbackup読取/削除権限を追加しない。元ディスクを捨てず、復旧対象を確認する。

## 検証の区分

CIはTerraformの構文/プロバイダ検証、両Mavenテスト、デプロイ異常系、
3イメージのbuild、非root/entrypoint、EULA未同意時の拒否、Velocityの実起動/status/正常終了を検査する。
CIではMinecraft EULAに同意せず、実バックエンドのworld/plugin起動は行わない。

GCPプロジェクト未作成のため、初回plan、IAP/OS Login/WIF、GCS転送、実ディスク、
Java17上の実バックエンド、実クライアントのlobby↔pvp、レート/KB保持、
本番相当の正常停止/失敗時復元、VM再起動は運営者の承認とEULA同意後の検証が必要。
ゲーム入口の一般公開はこれらが終わった後にする。CI成功と本番投入可能は同義ではない。
# PostgreSQLを利用する既存サーバーの追加要件

DBを有効にした構成では、初回デプロイ前に本番DBを復元してください。
デプロイ処理は空のDBを自動作成せず、次の既存構成を要求します。

- コンテナ名: `poppy-postgres`（ゲームのComposeとは別管理）
- イメージ: 復元確認済みの PostgreSQL 18。同じコンテナを再利用し、アプリのデプロイではDBイメージを更新しません。
- bind mount: `/srv/poppy/postgres-production` → `/var/lib/postgresql`
- `PGDATA=/var/lib/postgresql/18/docker`
- 接続先: `127.0.0.1:54329`。外部公開しません。
- 管理者: `poppy_admin`、アプリ用DB・ロール: `poppy_practice`
- アプリ用パスワード: `/srv/poppy/secrets/postgres-app-password.txt`、所有者 `10001:10001`、モード `0400`
- Practice設定の `storage.postgres.password-file`: `/run/secrets/postgres-app-password.txt`
- `namespace` と `storage-writer.id` は移行元の値を保持し、旧サーバーとの同時書き込みを禁止します。

ゲームComposeはアプリ用パスワードだけを読み取り専用でマウントします。
管理者パスワードや秘密の値をGitHubへ登録する必要はありません。

## デプロイ時のDB保護

ゲームを正常停止した後、`pg_ctl stop -m fast` でPostgreSQLを正常停止します。
停止を確認できない場合は強制終了せず、入口を閉じたまま処理を中断します。
`state` と `postgres-production` 全体を同じアーカイブに保存し、SHA-256とともにGCSへアップロードします。
DBを起動してスキーマテーブルに接続できることを確認してから、ゲームを起動します。
新しいゲームの起動に失敗した場合は、全書き込み元を止めて両方のディレクトリを復元します。
復元前のデータは `failed-data-*` に保持します。復元中断後は既存の `recover` コマンドで再試行できます。

物理バックアップの復元には保存時と同一のDBイメージIDを要求します。
PostgreSQLのバージョン更新、パスワード変更、他のDB追加はこのアプリデプロイと分けて計画してください。
DB全体の復元はロールとパスワードハッシュも戻すため、対応する秘密ファイルを別途保持してください。
旧形式のゲームデータだけのアーカイブは、自動DB復元には使えません。
バックアップとGCS転送中もDBは停止するため、データ量に応じて停止時間が増えます。
実行中DBのファイルをtarでコピーする運用には変更しないでください。
参考: https://www.postgresql.org/docs/18/backup-file.html

CIの `database-restore` は使い捨てPostgreSQLを実際に起動し、ゲームファイルとDBの変更、
スキーマバージョンの変更、復元途中の欠落を模擬して、両方を元に戻せることを検証します。
本番でのゲーム接続確認と、デプロイ前後のデータ確認は別途必要です。

