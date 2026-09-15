# PoppyPractice

現在の実装・運用設定をまとめた文書は[現行仕様書（2026-09-15）](docs/CURRENT_SPECIFICATION_JA.md)を参照してください。Kit・認定/ELO・Bot・戦闘設定・検知・管理コマンド・ネットワークを整理し、配布既定値と運用値、過去から変更された仕様を区別しています。以下の初期実装に関する記述と異なる場合は現行仕様書を優先してください。

独立ロビー＋Velocityの起動は`run-network.bat`です。接続構成・コマンド・復元方法は[ネットワーク運用ガイド](docs/NETWORK.md)を参照してください。

GUI・試合管理の構成と開発時の確認項目は[アーキテクチャと動作確認](docs/architecture.md)を参照してください。

Kit別のBot認定3戦・ELO付きQueue・レート変動なしのDuel・キルエフェクトを追加しました。
使い方、採点式、保存先は[認定・Ranked運用ガイド](docs/RANKED.md)を参照してください。
PvPロビーの経験値ボトル「Certification Queue」またはQueue画面右下の同名ボタンから各Kitの認定を開始し、Settingsからキルエフェクトを選択できます。

Queue・Kit Editor・Bot戦のキット選択は同じ左詰めレイアウトで、下段に操作説明とCloseを表示します。Kit Editorでは左クリックでスタックごと持ち上げ・交換し、閉じると配置だけを保存します。Bot戦はキット選択後に分類別のBot Settingsへ進みます。Botの調整値は全プレイヤー共通で、変更は開いている各画面にも反映されます。全項目を既定値へ戻す操作はShift＋左クリックです。

試合結果は従来どおりチャット内の名前から開けます。左右の矢印で相手に切り替えられ、`/matchresult <プレイヤー名>`でも確認できます。

Botは剣を振り始める距離に入ると、相手の現在の目の位置へ正対します。近接中は先読み・ランダムな
照準誤差・旋回速度上限を適用せず、横移動中も頭と胴体の向きを揃えます。同じ高さなら水平を向き、
高低差がある場合は相手の高さへ追従します。遠距離の接近時は従来の照準設定を使い、回復の後退や
パールを投げるときの専用の向きは維持します。移動入力・KB・リーチ・CPSは変更しません。

## Boxing

QueueのNoDebuffの右隣にあるダイヤ剣を選ぶとBoxingに参加できます。
有効な近接ヒットを先に100回決めたプレイヤーが勝ちます。体力は減らず、通常のノックバックは
適用されます。装備はダイヤ剣1本のみで、防具・ポーション・パールはなく、スピードIIは常時付与です。
Kit EditorでもBoxingの剣の配置を変更できます。Bot戦にも対応しており、ロビーの「Bot Fight」
または`/bot`でBoxingを選び、設定画面の「Start Boxing Bot Match」（エメラルドブロック）から開始できます。
プレイヤー・Botのどちらかが有効な近接ヒットを先に100回決めると勝利です。

Boxing・NoDebuff・Comboは同じ10個のアリーナを共有し、使用中のアリーナへ別の試合は入りません。
新規の`arenas.yml`では`kits: [nodebuff, boxing, combo]`を使用します。従来の`kit: nodebuff`も読み込めます。
既存の生成済み`nodebuff_01`〜`nodebuff_10`は初期ワールド・スポーン座標のままであれば自動で
3モード対応になり、設定ファイルや建築を上書き・再生成しません。カスタムアリーナは自動変更しません。
対応モードを明示する場合は各アリーナへ`kits: [nodebuff, boxing, combo]`を追加してください。
`kits: [nodebuff]`や`kits: [boxing]`を指定すれば単一モード専用になり、旧`kit`より優先されます。
明示的な`kits: [nodebuff, boxing]`も自動では拡張しません。Comboで使う場合は`combo`を追加してください。

## Combo

Queue・Kit Editorの3番目にある上位金リンゴから選択できます。Comboは対人・Bot戦の両方に対応します。
Bot戦は`/bot`またはロビーのBot FightからComboを選び、設定画面で開始してください。

- ダイヤ剣：Sharpness V。
- ダイヤ防具2セット：装着済み1セット＋予備1セット。全てProtection IV・Unbreaking III。
- 飲むSpeed IIポーション6本、上位金リンゴ64個、金人参64個。
- エンダーパール16個。Comboだけクールダウン8秒で、経験値バーの表示にも反映します。

初期配置は剣・パール・上位金リンゴ・Speed IIをホットバー左から、金人参を右端に置きます。
残りのSpeed II5本と予備防具はインベントリ内です。Kit Editorでは数量・エンチャントを変えずに
配置を保存できます。カウントダウン中は従来どおり飲むポーションだけ使用できます。

専用KBと無敵時間は`runtime/plugins/PoppyPractice/config.yml`の`combo`で調整します。
共通の`runtime/knockback.yml`や他キットの無敵時間・パールクールダウンは変更しません。
両参加者へ別々の一時KBプロファイルを適用し、試合終了・切断・開始失敗・プラグイン停止時には
参加前のプロファイルと無敵時間を復元します。元が共通KBを使用する状態（null）の場合もそのまま復元します。

```yaml
combo:
  no-damage-ticks: 2
  knockback:
    fall-height: 3.0
    fall-speed: 0.08
    horizontal: 0.30
    vertical: 0.10
    vertical-min: -1.0
    vertical-max: 0.15
    friction-horizontal: 2.0
    friction-vertical: 2.0
    extra-horizontal: 0.10
    extra-vertical: 0.0
    wtap-extra-horizontal: 0.10
    wtap-extra-vertical: 0.0
```

`no-damage-ticks`は同等ダメージの次のヒットが再び入るまでのtick数（整数0〜100）です。
初期値2は通常20TPSで約0.1秒、0はこの待ち時間なしです。WindSpigot内部の最大無敵カウンターには
設定値の2倍を設定します。これは内部判定がカウンターの前半だけを無敵とするためで、設定値を
利用者側で2倍にする必要はありません。より強い攻撃の差分ダメージなど、通常のダメージ処理は維持します。

`horizontal` / `vertical`は通常ヒットの水平・垂直KB、`vertical-min` / `vertical-max`は垂直速度の下限・上限、
`friction-*`は元の速度を割る値、`extra-*`はスプリント攻撃の追加KB、`wtap-extra-*`はW-tap時の追加KBです。
ほかに`stop-sprint`、`add-horizontal` / `add-vertical`、`projectiles.*`もCombo専用に指定できます。
KBの単位・計算方式はWindSpigotの同名プロパティと同じです。初期KBは調整用の出発点で、実際の操作感は対戦で調整してください。

`fall-height`はCombo独自の落下切り替え高さ（0〜50マス、初期値3.0）です。
最後に接地していた位置からこの高さまで浮いたら、着地するまで上向きKBを抑え、一定の遅い速度で地面へ降ろします。
連続で殴られても再び上へ持ち上げず、水平KBの設定とダメージ判定は変更しません。着地後は通常のKBに戻ります。
`0`でこのルールを無効化できます。高さは毎tickの判定基準であり、厳密な高度固定やテレポートではありません。
`fall-speed`は切り替え後の落下速度（0.04〜0.5マス/tick、初期値0.08）です。小さいほどゆっくり落下し、
20TPSでは初期値で毎秒約1.6マス、0.05なら毎秒約1マス降ります。自由落下のようには加速しません。
極端な低速での浮遊判定を避けるため0.04未満は拒否します。`fall-height: 0`ではこの速度制御も無効です。
Botは移動処理のY成分だけを制御します。実プレイヤーは移動履歴と直近のKBから水平速度を推定して落下補正するため、
高pingで連続被弾する場合などの水平移動への影響は実際の対戦で確認してください。

管理者（OPまたは`practice.admin`権限）は`/combokb`からも確認・変更できます。

```text
/combokb                              # 現在の主要設定を表示
/combokb view all                     # 投射物を含む全設定を表示
/combokb view horizontal              # 水平KBだけを確認
/combokb set horizontal 0.30          # 通常ヒットの水平KB
/combokb set vertical 0.10            # 通常ヒットの垂直KB
/combokb set fall-height 3            # 3マス浮いたら着地まで上向きKBを抑制
/combokb set fall-height 0            # 高さによる落下切り替えを無効化
/combokb set fall-speed 0.08          # 切り替え後の落下速度（マス/tick）
/combokb set fall-speed 0.05          # さらにゆっくり落下
/combokb set extra-horizontal 0.10    # スプリント攻撃の追加水平KB
/combokb set no-damage-ticks 2        # 無敵時間（tick）
/combokb set stop-sprint true         # 攻撃時のスプリント停止
/combokb set projectiles.pearl.horizontal 0.4
```

項目名と値はTab補完に対応します。上記のKB項目に加え、`vertical-min` / `vertical-max`、
`friction-*`、`extra-vertical`、`wtap-extra-*`、`add-*`、全投射物の水平・垂直KBを指定できます。
数値範囲は`friction-*`が1〜100、通常・投射物KBと追加水平KBが0〜4、
垂直上下限・追加垂直KB・`add-*`が-4〜4です。`stop-sprint`は`true` / `false`のみです。
コマンドによる変更は`config.yml`へ自動保存され、reloadせず**進行中・カウントダウン中を含むCombo対人・Bot戦へ即時反映**します。
以降に開始するCombo戦にも同じ値を使用します。人間側・Bot側の両方が対象で、他キットには影響しません。
通常のKB強度は変更後のヒットから新しい設定を使用します。
`fall-height`は進行中のCombo戦にも反映され、設定した高さに達していれば落下へ切り替わります。
`fall-speed`はすでに落下へ切り替わった参加者にも即時反映します。
試合終了時には試合前のKB・無敵時間へ戻します。入力不正は拒否し、更新失敗時はチャットにエラーを表示するためサーバーログを確認してください。

ファイルを直接変更した場合は試合がない状態で`/practice reload`を実行します。変更は次に開始するCombo戦から適用します。
不正な数値や垂直下限が上限を超える設定は拒否し、reload時は前の有効なCombo設定を保持します。

Combo Bot戦でも、人間側・Bot側とも対人Comboと同じ専用KBと無敵時間を使用します。
Botのパールも8秒で、開始直後の8秒間は投げません。所持している16個だけを消費します。
Botは上位金リンゴを通常の食事時間（32tick）をかけて食べ、再生・衝撃吸収なども通常処理で付与します。
食べている間は相手に背を向けて離れようとし、攻撃・パール・スプリントを止め、食事中の移動速度低下も適用します。
再生効果の残りが短いときや低体力時に再使用し、上位金リンゴ64個を使い切ったら補充しません。
空腹時は所持している金人参を食べ、防具の残り耐久が10以下になると実際の予備と交換します。
予備が尽きても防具を生成・修理しません。スピードIIは従来のBotと同様に常時付与です。
Comboには回復スプラッシュがないため、Bot SettingsのHealing行は使用せずグレー表示にします。
終了・切断・開始失敗時は両者の専用設定を復元し、次のNoDebuff・Boxing戦には持ち越しません。

## ReachGuard（リーチ検知）

ReachGuardは、プレイヤー間の近接攻撃をサーバー処理前に検証し、攻撃者の目線から相手のAABBまでの距離が不正なhitを検知します。デフォルトは`OBSERVE`（検知のみ）で、通知とJSONLログを記録し、攻撃の無効化や補正は行いません。攻撃の無効化が必要な場合だけ、`/reachguard mode protect`で防御を有効にします。

```text
OBSERVE（観測のみ） → /reachguard inspect とJSONLを確認 → /reachguard mode protect
```

現在の距離設定は、誤検知を許容する厳格な設定です。クライアント向けAABB拡張とgeometry epsilonを`0`にし、通常のプレイヤーAABBまでの実測距離が`3.000`マス以上なら、`PROTECT`／`STRICT`の両方でhitを無効化します。Minecraft 1.7.10のProtocol 5とMinecraft 1.8.xのProtocol 47へ同じ境界を適用します。未対応Protocol、同期履歴が不足している場合、テレポート直後、極端なPing・Jitter、TPS低下時は安全のためfail-openで攻撃を通します。

管理コマンドは次のとおりです。

- `/reachguard status [player]`: モード、bridge、処理数、Protocolの対応状態と現在モード、プレイヤーのVLや直近判定を表示
- `/reachguard inspect <player>`: 直近の違反・判定証拠を表示
- `/reachguard alerts <on|off>`: 実行したスタッフのリアルタイム通知を切り替え
- `/reachguard mode <observe|protect|strict>`: 判定モードを変更
- `/reachguard exempt <player> <seconds>`: プレイヤーを指定秒数だけ判定対象外に設定
- `/reachguard unexempt <player>`: 一時除外を解除
- `/reachguard resetvl <player>`: Reach／Stale／Invalid Entityの全VLをリセット
- `/reachguard reload`: `config.yml`のReachGuard設定を再読み込み

主要設定は`config.yml`の`reachguard`セクションにあります。

- `mode`: `OBSERVE`、`PROTECT`、`STRICT`の切り替え
- `reach.*`: AABB拡張値、記録開始距離、信頼度別のキャンセル閾値。既定値ではAABBを拡張せず、実測距離が3.000マス以上ならPROTECT／STRICTで無効化
- `tracking.*`: 攻撃者・対象の履歴数とspawn／teleport／respawn／world移動後の猶予
- `lag-compensation.*`: 最大巻き戻し時間、Stale Attack判定距離、Ping・Jitter上限
- `server-health.*`: 厳格判定に必要なTPSと、fail-openにする最大tick時間
- `damage-event-guard.*`: packetで許可されていないBukkitダメージを止める二次防御
- `violation.*`: VL加算、減衰、通知・詳細通知・kick候補の閾値
- `alerts.*`: スタッフ通知の有効化、最小VL、通知間隔
- `logging.*`: JSONL記録、保持日数、非同期キュー容量
- `debug.*`: デバッグ表示対象と、PROTECT／STRICT中も正常hitを記録するかの設定（OBSERVEは常に全判定を記録）

証拠ログは`runtime/plugins/PoppyPractice/reachguard-logs/reachguard-YYYY-MM-DD.jsonl`へ日ごとに保存されます。`reachguard.debug.enabled: true`にすると、無効化したhit（距離超過、Stale Attack、無効Entity、permitなし）をチャットにも表示します。特定プレイヤーだけ確認する場合は`reachguard.debug.target-player`へ名前を設定します。

JSONLの`excess`は判定閾値からの超過量、`vlExcess`は仕様のVL加算表に合わせた`measuredReach - baseReach`、`vlAdded`はその攻撃で実際に加算されるVLを表します（Stale／Invalidは`vlExcess: 0`）。

この環境にはPacketEventsを導入していないため、`PacketBridge`は`NMS_DIRECT`実装を使用します。ProtocolSupportがProtocol 5のpacketをWindSpigot 1.8.8形式へ変換した後に監視するため、Protocol 5と47の双方へ同じ追跡・判定・攻撃無効化を適用します。WindSpigot 1.8.8のpacket型はbridge内だけに隔離しており、コア判定を変更せず将来PacketEventsや別のbridgeへ差し替えられます。

## ChatterKB Normalizer

Protocol 5（Minecraft 1.7.10）とProtocol 47（Minecraft 1.8.x）の
マウスチャタリングによる重複Attackを検出し、
正常な1回目の水平速度0.6倍減衰を残したまま、追加減衰だけをVelocityで補正します。
パケットはWindSpigotのデコード後に監視し、ProtocolSupportで変換された
1.7.10のpacketにも同じ判定と補正を適用します。

初期モードは`CHATTER_ONLY`（チャタリングのみ補正）です。通常攻撃の減速は残し、
チャタリングが確認された重複攻撃の追加減速を補正します。確定重複ヒットの無効化も
既定で有効です。補正せず検知だけにする場合は`/chatterkb mode DETECT_ONLY`を使用します。無効化された
ヒットは、`/chatterkb debug <player> on`または`/practice debug on`のときに
`[ChatterKB] Hit disabled`としてチャットへ理由・gap・frame差・scoreを表示します。

主な管理コマンド:

- `/chatterkb status [player]`
- `/chatterkb debug <player> on|off`
- `/chatterkb mode <OFF|DETECT_ONLY|CHATTER_ONLY|STRICT_NORMALIZE>`
- `/chatterkb reload`
- `/chatterkb reset <player>`
- `/chatterkb export <player> [seconds]`

## Hit Debug Room

`/hitdebug`で通常対戦から分離されたVoid上のヒット検証室へ移動します。

- `PassiveDummy`: AIなし。通常どおりノックバックする連続ヒット用ダミー。
- `NoSprintGuard`: 自身はノックバックせず、3ブロック以内で常に通常攻撃。
- `SprintGuard`: 自身はノックバックせず、3ブロック以内で常にダッシュ攻撃。
- `PearlTester`: 15ブロック離れた2地点へ約3秒ごとにパールを投げて往復。

ルーム参加プレイヤーは死亡せず、攻撃・KB処理後に最大HPへ自動復元されます。
4体のダミーは最大HPへ復元され、防具も毎tick初期状態へ修復されるため破損しません。

`/hitdebug reset`で4体を初期位置・最大HPへ戻し、`/hitdebug leave`でロビーへ戻ります。
攻撃Botの範囲・間隔とパールの周期は`config.yml`の`hit-debug-room`で変更できます。

設定は`config.yml`の`chatter-kb`にあります。問題がある場合は
`/chatterkb mode DETECT_ONLY`、緊急停止時は`/chatterkb mode OFF`を使用します。
対応Protocolは`chatter-kb.target.protocols`で管理し、初期値ではProtocol 5と47の
両方に同じ検出・補正処理を適用します。

Minecraft Java Edition 1.7.10・1.8.9クライアント向けの、WindSpigot 2.1.3（Minecraft 1.8.8）NoDebuff・Boxing・Combo 1v1サーバーです。
共有された設計書の初期版（非ランク、メモリ内状態、単一サーバー、複数アリーナ）を実装しています。

## 実装済み

- 参加時のロビー初期化と2つの操作アイテム
- 選択と同時にキュー参加するNoDebuff・Boxing・ComboキットGUI
- ロビーでオンライン人数を表示する「Poppy Practice」スコアボード
- キット別FIFOキューと空きアリーナへの自動マッチング
- 5秒カウントダウン（飲用ポーションは使用可能）中の移動・攻撃・その他の操作・コマンド制限
- 対戦相手だけを攻撃可能にするダメージ制御
- エンダーパール投擲後の16秒クールダウン
- 致死ダメージ、切断、管理者コマンドによる試合終了
- 二重終了防止、アリーナ解放、待機中ペアの再マッチング
- ロビーアイテムまたは`/bot`から開始できる1人用Bot戦
- ロビー・ワールド保護
- `/queue join|leave`、`/bot start|leave`、`/spawn`（別名 `/lobby`）、`/practice reload|forcestop|debug`

## Windowsでサーバーを組み立てる

初回だけインターネット接続が必要です。PowerShellでリポジトリ直下から実行します。

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup-server.ps1
```

この処理は、Eclipse Temurin 17 JDK、Apache Maven、WindSpigot公式リリース、1.7.10接続用ProtocolSupportを
`%LOCALAPPDATA%\PoppyPracticeTools`へ取得し、`runtime`へWindSpigot 2.1.3、PoppyPractice、ProtocolSupport、サーバー設定を配置します。
ProtocolSupportの暗号化リスナーは、`online-mode=true`を維持したまま接続できるようWindSpigot API向けに再コンパイルされます。
PATHやレジストリは変更しません。

Minecraft EULAを確認し、同意する場合のみ次を実行してください。

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup-server.ps1 -AcceptEula
powershell -ExecutionPolicy Bypass -File .\runtime\start.ps1
```

`runtime\run.bat`をダブルクリックして起動することもできます。どちらの方法でも、
セットアップ済みのTemurin 17を明示的に使用し、Windows側の既定Javaは使用しません。

クライアントはMinecraft 1.7.10または1.8.9で`localhost:25565`へ接続します。別PCから接続する場合はWindows Firewallとルーターを安全に設定してください。`online-mode=true`は維持してください。

## 開発ビルド

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-plugin.ps1
```

成果物は`target/PoppyPractice-0.1.0.jar`です。

## 設定

- `src/main/resources/config.yml`: ロビー、カウントダウン、メッセージ
- `src/main/resources/arenas.yml`: アリーナID、キット、2地点
- 稼働後は`runtime/plugins/PoppyPractice/config.yml`と`arenas.yml`を編集します。
- 進行中マッチがある間、`/practice reload`は安全のため拒否されます。

## Bot戦

ロビー中央の「Bot Fight」を右クリックするか、`/bot`・`/bot start`・`/bot settings`を実行すると
キット選択画面を開きます。NoDebuff（回復スプラッシュ）・Boxing（ダイヤ剣）・Combo（上位金リンゴ）を選ぶと、
選択したキット名を表示したBot Settingsへ進みます。この時点では試合は始まりません。
設定画面の下段中央にある「Start <Kit> Bot Match」（エメラルドブロック）を左クリックすると、
空いている対応アリーナでBot戦を開始します。左下の「Back to Kit Selection」でキットを選び直せます。
キット選択・設定の閲覧・戻る・閉じるでは、参加中のQueueを解除しません。

Bot Settingsは上から次の5分類を1行ずつに配置し、見出しと色で区別しています。

- General：スキン、最大体力。中央には選択中のキットを表示。
- Movement：横移動のON/OFF、接近・後退距離、横移動の強さと切り替え間隔。
- Aim：視点の回転速度、移動予測、照準の誤差。
- Combat：素振り開始距離、リーチ、CPS、スプリントリセット。
- Healing：回復のON/OFF、回復体力・間隔・所持数、後退、緊急回復、相手の回復。

各項目には現在値と操作説明を表示します。数値は左クリックで増加、右クリックで減少、
Shiftで大きく変更、中央クリックでその項目を初期化します。ON/OFF項目は左右クリックで切り替えます。
Boxingでは使わない最大体力・回復設定、Comboでは回復スプラッシュ設定をグレー表示し、クリックしても変更しません。
調整値は全プレイヤー共通で即時保存され、次に開始するBot戦から適用されます。
下段の「Reset All Settings」はShift＋左クリックで、選択中のキット以外も含む全設定を
従来のHard相当の既定値へ戻します。通常の左クリックではリセットしません。

Bot戦を途中終了する場合は`/bot leave`を使用します。
Botも通常対戦と同じアリーナを使用するため、使用中アリーナはキュー対戦から除外されます。
Boxingでも共有の移動・照準・攻撃設定を使用しますが、回復・パールは使用せず、体力と空腹は
減りません。NoDebuff / Boxing / Comboの選択はその試合だけに適用され、共有Bot設定や他の人の
開始モードを変更しません。

Bot SettingsのMovement行にある「Strafe Enabled」で横移動をON / OFFに切り替えられます。
設定キーは`bot.movement.strafe-enabled`で、初期値は`true`（ON）です。
OFFでは左右の移動入力だけを止め、通常の攻撃、接近・後退、回復、ノックバックは
維持します。NoDebuff / Boxing / Comboに適用され、各モードの回復・パール制限は変わりません。
横移動の強度`bot.movement.strafe-input`は保持され、再びONにすると同じ強度を使用します。
これは共有Bot設定として保存され、次に開始する試合から適用されます。

BotはZombieではなく、WindSpigotの`EntityPlayer`を使ったプレイヤーNPCです。相手のスキンと
選択したモードの装備を使用し、視点を滑らかに動かしながら、距離管理、ランダムなストレイフ、
W-tapを行います。NoDebuffでは低体力時にスプラッシュ回復も行います。自発的なジャンプはしません。

Botの水平移動は、実プレイヤーのクライアントに相当する速度と、サーバーのKB計算用速度を
分離しています。前のKBや走行速度が次のヒットへ余分に足されることを防ぎ、受信したKBは
加算ではなく置き換えます。接触で生じたサーバー側の水平速度にも、地上・空中の摩擦と
微小速度の停止処理を適用します。スプリント中の攻撃では、相手の無敵時間中でも水平速度を一度だけ
`0.6`倍にします。サーバー側の攻撃処理による減速と二重には適用しません。
被弾中は近距離の距離調整より前進入力を優先しますが、KB自体を打ち消す処理ではありません。
ガード中は移動入力を`0.2`倍にし、自然な落下は落下距離・ダメージ判定へ通知します。
縦方向の物理と全体のKBプロファイルは変更しません。

調整は`config.yml`の`bot`で行います。

- `copy-player-skin`: 対戦相手のスキンをNPCへコピーするか
- `maximum-health`: Botの体力。`20.0`で10ハート
- `movement.strafe-enabled`: 横移動の有効 / 無効（初期値`true`）
- `movement.*`: 接近・後退距離、ストレイフ入力、切り替え間隔
- `aim.*`: 1tickあたりの最大視点移動、移動予測、照準誤差
- `combat.attack-range`: 攻撃可能距離
- `combat.minimum-cps` / `maximum-cps`: クリック速度のランダム範囲
- `combat.sprint-reset-ticks`: 有効ヒット後、次のtickから前進キーを離すtick数。`0`で追加のW-tapなし
- `healing.*`: 回復開始体力、再使用間隔、所持ポーション数

攻撃はプレイヤーと同じNMS処理を通るため、Bot専用の固定攻撃力はありません。剣・防具・
エンチャント、`windspigot.yml`の無敵時間、`knockback.yml`のKBがそのまま適用されます。
Botはジャンプせず、試合中はスピードIIが常時付与されます。
スプリントの追加KBフラグは実際の開始・停止時だけ更新します。無敵時間中でも設定CPSに
従ってクリックを続けますが、無敵時間を理由にクリック頻度を増やすことはありません。

スプラッシュポーションは`/practice potion`で現在値を確認できます。`without-speed`と
`speed-ii`の2プロファイルがあり、当たり判定、X/Y速度、出現位置、本人との衝突待ち
時間をそれぞれ独立して変更できます。

```text
/practice potion without-speed x-speed 1.0
/practice potion without-speed y-speed 1.0
/practice potion speed-ii x-speed 1.1
/practice potion speed-ii y-speed 1.1
/practice potion speed-ii self-collision-delay-ticks 0
```

設定項目は`hitbox-size`、`x-speed`、`y-speed`、`spawn-forward-offset`、
`spawn-height-offset`、`self-collision-delay-ticks`です。コマンドでの変更は即時反映されます。

無敵時間とノックバックはWindSpigot側で設定します。プラグイン側の重複設定と
`/practice combat`は削除されています。

- `runtime/windspigot.yml`の`settings.hit-delay`: 無敵時間。`0`でなし、`20`でバニラ既定値
- `runtime/knockback.yml`: KBプロファイル。初期値は移行前と同じ0.4/0.4、friction 2.0、ダッシュ加算0.5/0.1
- ゲーム内ではWindSpigotの`/kb`コマンドでもKBプロファイルを編集できます。
- WindSpigot設定を変更した後はサーバーを再起動してください。

同梱の`server.properties`は`practice`というフラットワールドを生成します。既定座標はその地表（Y=4）に合わせています。実運用ではロビーと各アリーナの建築後、座標を`arenas.yml`へ反映してください。

## 初回確認

1. サーバーコンソールで`op <プレイヤー名>`を実行します。
2. 2アカウントで参加し、それぞれKit SelectorからNoDebuffを選択します。選択時点で自動的にキューへ参加します。
3. カウントダウン中にスピード・耐火ポーションを飲めること、5秒後に戦闘が開始し、致死ダメージまたは切断後に両者がロビーへ戻ることを確認します。
4. `/practice debug`でMatchが0、使用アリーナが`AVAILABLE`であることを確認します。

詳細な手動テストは[ACCEPTANCE_CHECKLIST.md](ACCEPTANCE_CHECKLIST.md)を参照してください。
