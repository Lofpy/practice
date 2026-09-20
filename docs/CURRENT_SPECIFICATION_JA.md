# Poppy Network / Poppy Practice 現行仕様書

文書バージョン：1.0  
確認日：2026年9月15日（JST）  
対象：このリポジトリの実装、および `runtime/`・`network/` の運用設定

## この文書の読み方

現在までに実装された仕様を、プレイヤー機能・戦闘処理・管理機能・運用構成に分けてまとめた文書です。過去の要望をそのまま列挙するのではなく、後から変更・撤廃された仕様を除き、現在のソースを基準にしています。

- **固定仕様**：ゲームルールやコード内の定数。通常の設定ファイルでは変更しません。
- **配布既定値**：`src/main/resources/config.yml` と設定クラスの初期値。
- **運用設定値**：確認日時点の `runtime/plugins/PoppyPractice/config.yml` などに保存された値。実行中プロセスの一時的なコマンド変更を直接照会した値ではありません。
- 時間の `tick` はサーバー処理単位です。20 TPSなら1 tick＝50 ms、20 tick＝1秒。負荷によって実時間は延びます。
- 体力の単位はHPで、20 HP＝ハート10個。距離の単位はブロックです。
- 接続互換性とアンチチート対応範囲は別です。新しいクライアントが接続できても、すべての検知機能の対象になるわけではありません。

本書作成に伴うサーバー停止・設定変更・認定データの削除は行っていません。既存README・チェックリストの初期実装に関する記述と食い違う場合は、本書の現行仕様と参照先のソースを優先してください。

## 目次

1. [システム構成・接続](#system)
2. [ロビー・メニュー・スコアボード](#lobby)
3. [Kitと標準アイテム配置](#kits)
4. [Kit Editor](#kit-editor)
5. [試合の進行・アイテム操作](#match)
6. [認定戦・ELO・Ranked Queue](#ranked)
7. [Duel・キルエフェクト](#duel)
8. [試合結果・統計の定義](#results)
9. [通常Bot・認定Bot](#bots)
10. [ポーション・パール](#projectiles)
11. [ノックバック・無敵時間・Combo設定](#knockback)
12. [ChatterKB・ReachGuard](#anticheat)
13. [Ping・ダメージデバッグ・検証室](#debug)
14. [ワールド・アリーナ](#worlds)
15. [コマンド・権限](#commands)
16. [保存・認定リセット・運用](#operations)
17. [旧仕様との相違・制約・検証状況](#limitations)
18. [実装・設定ファイルの参照先](#sources)

<a id="system"></a>
## 1. システム構成・接続

### 1.1 構成

公開接続口はVelocityで、接続先として独立ロビーとPvPサーバーを分離しています。Waterfallではなく、現在の実装は**Velocity**です。

```text
Java版クライアント
       │ 公開TCP 25565 / 正規アカウント認証
       ▼
Velocityプロキシ
       ├── lobby : 127.0.0.1:25567 ── PoppyLobby / 接続ロビー
       └── pvp   : 127.0.0.1:25566 ── PoppyPractice / PvP・認定・Bot
```

| 要素 | 現在の構成 |
| --- | --- |
| プロキシ | Velocity 4.1.1-build24 |
| PvP・ロビーのサーバー本体 | WindSpigot 2.1.3、Minecraft 1.8.8 |
| クライアント変換 | ViaVersion 5.11.0、ViaBackwards 5.11.0、ViaRewind 4.1.3 |
| 独自プラグイン | PoppyPractice 0.1.0、PoppyLobby 0.1.0 |
| プロキシのJava | 専用Temurin 25系 |
| バックエンドのJava | 専用Temurin 17 |
| PoppyPracticeのビルド対象 | Java 8互換バイトコード、WindSpigotの `v1_8_R3` NMSを使用 |

Java 8でサーバーを起動する構成ではありません。過去の `UnsupportedClassVersionError` を避けるため、起動スクリプトが専用Javaを選び、Windowsの既定Javaには依存しません。

### 1.2 対応バージョン

運用構成の対象は **Java版1.7.10から26.2まで**です。上限26.2は2026年9月12日の固定アーティファクト情報に基づくもので、将来の最新版へ自動追従する保証ではありません。

- ゲーム内容・装備・戦闘はバックエンドの1.8.8仕様です。新しいブロックや現代版戦闘を追加する機能ではありません。
- Bedrock版・スナップショットは本構成の対象外です。
- 本番はVia系で変換します。旧ProtocolSupportとの重複変換は避ける構成です。
- ChatterKB・ReachGuardが検証対象とする原クライアントは1.7.10（protocol 5）と1.8系（protocol 47）です。

### 1.3 認証と公開範囲

- Velocityは `online-mode=true`。バックエンドはプロキシ経由の認証情報を受けるため `online-mode=false`、BungeeCord互換転送を有効にします。
- 転送方式は `LEGACY`。バックエンドは `127.0.0.1` にのみバインドし、25566・25567を外部へ公開しません。
- 公開するのは25565だけです。バックエンド単体を外部公開する運用は認証を迂回される危険があります。
- 同一ホスト内の不正なプロセスまで防ぐ仕組みではありません。複数ホストへ分離するときは、転送方式・ファイアウォール・安全な通信経路を再設計します。

詳細：[ネットワーク運用ガイド](NETWORK.md)

<a id="lobby"></a>
## 2. ロビー・メニュー・スコアボード

### 2.1 二つのロビー

| 場所 | 主な用途 | 移動操作 |
| --- | --- | --- |
| 独立した接続ロビー | ネットワーク参加直後の待機・サーバー選択 | コンパス→Poppy Practice、または `/pvp` |
| PvP内のロビー | Queue・Kit編集・通常Bot・認定・Settings | PvP内で `/spawn` または `/lobby` |

PvPから接続ロビーへ戻る操作は `/hub` です。対戦中の移動・切断は、その試合の途中退出ルールの対象になります。`/lobby` と `/hub` は同じ接続先ではありません。

接続ロビーではダメージ・空腹・アイテム持出し・通常の建築を保護します。管理者も、明示的に `/lobbybuild` を有効にした場合だけ建築できます。再度実行または再ログインで保護に戻ります。接続ロビーとPvPのOP設定は別です。

### 2.2 PvPロビーのホットバー

スロットは左から1～9です。空きスロットにはアイテムを配りません。

| スロット | アイテム | 用途 |
| ---: | --- | --- |
| 1 | ダイヤの剣 | Queue / Kit Selector |
| 3 | 本 | Kit Editor |
| 5 | 鉄の剣 | Bot Fight |
| 7 | 経験値ボトル | Certification Queue |
| 9 | コンパレーター | Settings |

Queue参加中は上記アイテムを消し、**9番目のレッドストーン「Leave Queue」だけ**を渡します。Queue解除後は通常のロビーアイテムへ戻します。Queue未参加時に解除用レッドストーンは持たせません。

Queue・Kit Editor・通常BotのKit選択は共通の左詰めレイアウトです。順番はNoDebuff、Boxing、Combo。アイコンはそれぞれ回復IIスプラッシュ、ダイヤの剣、上位金リンゴです。Queue画面の右下にもCertification Queueへの入口と認定進捗を表示します。

### 2.3 スコアボード

| 状態 | 表示内容 |
| --- | --- |
| PvPロビー・Queue | 赤・太字の `Practice`、上下の区切り線、3KitのELOまたは `Tier n/3`、左寄せの `Online` |
| 対人戦 | 同じ基本レイアウト、両者の該当KitのELOまたは認定進捗、双方のPing |
| 通常Bot・認定Bot戦 | 同じ基本レイアウト、本人とBotのPing。対人用ELO行は表示しない |
| Boxing | 上記に `Your Hits`、`Their Hits`、`Difference` を追加。ヒット数は `n/100` |
| 独立した接続ロビー | 別プラグインの `Poppy Network` タイトルとロビーのオンライン数 |

PvPの区切り線は17文字相当で、以前の広い表示から縮めたレイアウトです。タイトルの配置や実際の幅はMinecraftの描画に依存し、ピクセル単位の固定幅・独自中央寄せ処理はありません。

PvPスコアボードは2 tickごとに確認し、変化した行を更新します。`Online` は表示しているバックエンド単体の人数で、ネットワーク全体の合計ではありません。

<a id="kits"></a>
## 3. Kitと標準アイテム配置

### 3.1 比較

| 項目 | NoDebuff | Boxing | Combo |
| --- | --- | --- | --- |
| 勝利条件 | 相手のHPを0にする | 有効近接ヒットを先に100回 | 相手のHPを0にする |
| ダイヤ剣 | Sharpness II / Unbreaking III | エンチャントなし | Sharpness V |
| ダイヤ防具 | 装着1セット、Protection II / Unbreaking III。ブーツにFeather Falling IV | なし | 装着1セット＋予備1セット。全てProtection IV / Unbreaking III |
| 回復IIスプラッシュ | 29本 | なし | なし |
| 飲用Speed II | 3本 | なし、Speed II常時付与 | 6本 |
| 飲用耐火 | 1本、実用上無期限 | なし | なし |
| 上位金リンゴ | なし | なし | 64個 |
| 金ニンジン | 64個 | なし | 64個 |
| エンダーパール | 16個、運用CD16秒 | なし | 16個、独立CD8秒 |
| 体力・空腹 | 通常の戦闘・空腹処理 | HPダメージなし、満腹維持 | 通常の戦闘・空腹処理 |
| KB・無敵時間 | WindSpigot共通設定 | WindSpigot共通設定 | Combo独立設定 |

Comboの剣には現在Unbreakingを付けていません。防具のUnbreaking IIIとは別です。NoDebuffの耐火とBoxingのSpeed IIの長時間効果には `Integer.MAX_VALUE` tickを使用し、試合終了・ロビー復帰で解除します。

### 3.2 標準ホットバー

| Kit | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| NoDebuff | 剣 | パール16 | 耐火 | Speed II | 回復 | 回復 | 回復 | 回復 | 金ニンジン64 |
| Boxing | 剣 | 空 | 空 | 空 | 空 | 空 | 空 | 空 | 空 |
| Combo | 剣 | パール16 | 上位金リンゴ64 | Speed II | 空 | 空 | 空 | 空 | 金ニンジン64 |

NoDebuffの収納27枠は先頭2枠にSpeed II、残り25枠に回復を1本ずつ置きます。Comboの収納1行目先頭5枠に残りのSpeed II、2行目先頭4枠に予備の頭・胴・脚・足装備を置きます。

個人のKit配置を保存している場合は、その配置を優先します。通常Botの回復所持数はBot設定で標準29本から変更できます。プレイヤーKitの数量変更機能ではありません。

<a id="kit-editor"></a>
## 4. Kit Editor

PvPロビーで試合・Queueに参加していないときに利用できます。Kit Editor→Kit選択→36枠の編集画面へ進みます。最下段9枠がホットバーです。

- 左クリックでスタック全体を持ち上げ、置く・交換する操作ができます。
- 種類・数・エンチャント・ポーション効果は変更できません。配置のみの編集です。
- 分割する右クリック、Shift移動、数字キー、ドラッグ、持出しは許可しません。
- 装着中の防具4枠は対象外。Comboの予備防具は収納アイテムなので移動できます。
- 閉じると保存します。カーソル上のアイテムは編集画面の空き枠へ戻します。
- 保存前に標準Kitと同一のアイテム集合であることを検証し、不正な変更は保存しません。
- UUID・Kit別に配置の対応だけを `kit-layouts.yml` に保存し、次の試合に適用します。不正・欠落した配置は標準配置へ戻します。

通常の試合インベントリ整理と、保存用Kit Editorは別機能です。試合中の並べ替えを自動で次のKit配置として保存する仕様ではありません。

<a id="match"></a>
## 5. 試合の進行・アイテム操作

```text
PvPロビー → Ranked Queue → STARTING → FIGHTING → ENDING → PvPロビー
          └ Bot / 認定 / Duel ──┘
```

### 5.1 準備と開始

アリーナを予約し、参加者をSurvivalへ変更して体力などをリセットし、Kitと個人配置を適用します。カウントダウンの運用設定・配布既定値は **5秒** です。

| 操作 | STARTING | FIGHTING | ENDING |
| --- | --- | --- | --- |
| 位置移動 | 禁止 | 許可 | 禁止 |
| 視点変更 | 許可 | 許可 | 許可 |
| インベントリ整理 | 許可 | 許可 | 保護 |
| 飲むポーション | 許可 | 許可 | 禁止 |
| 食べ物・パール・スプラッシュ | 禁止 | Kit・CDに従い許可 | 禁止 |
| 攻撃 | 禁止 | 許可 | 禁止 |
| アイテム投棄 | 禁止 | 剣以外は戦闘中の規則に従う | 禁止 |
| 拾得 | 禁止 | 許可 | 禁止 |

通常コマンドの一部も試合中に制限します。管理・Bot退出・Ping・Combo調整などの許可対象は継続利用できます。ロビー復帰時は持物・効果・体力・空腹・経験値・火炎・落下状態をリセットし、Adventureへ戻します。

### 5.2 飲用後の瓶削除・開始前補充

飲用完了後、次tickに空のガラス瓶1個を削除します。

**飲み終えた時点がSTARTINGの場合だけ**、空いた同じホットバースロットに収納内の回復IIスプラッシュを1本補充します。次tickの処理時にFIGHTINGへ進んでいても、同じ試合なら補充できます。飲み始めが開始前でも、飲み終わりがFIGHTINGの場合は自動補充しません。収納に回復がなければ生成しません。

補充品の誤投擲を防ぐため、そのスロットには使用ガードを付けます。STARTING中は解除せず、FIGHTINGに入り、右クリック入力が **350 ms以上途切れた後の次の右クリック** から使用できます。旧クライアントに明確な右クリック解放通知がないため、入力の休止で推定します。

これは補充されたスロットを対象にする機能で、**試合開始後に全アイテムを3秒禁止する実装ではありません**。別スロットのアイテムまで一律に禁止しません。

### 5.3 戦闘・終了

- 攻撃は同じ試合の相手を対象とします。Boxing以外の通常戦闘では落下ダメージも有効です。
- 剣ガードの有効回数は双方それぞれ1試合 **19回**。右クリック数ではなく、ガード中に有効な近接被弾を防いだ回数です。
- 19回目は軽減し、20回目以降は実ダメージのガード軽減も無効にします。Botも同じ制限です。
- ガード解除は剣をガードしている状態だけを対象とし、食事や飲用まで中断させません。
- 死亡またはBoxing100ヒットの自然決着で戦績を確定し、チャット・エフェクトを表示します。
- **60 tick（通常3秒）** のENDING中もアリーナを予約し、終了後にロビーへ戻して解放します。
- 切断・強制終了・停止・内部異常時は即時後処理を優先します。自然決着と同じ演出・結果表示をすべて保証するものではありません。

<a id="ranked"></a>
## 6. 認定戦・ELO・Ranked Queue

### 6.1 認定の単位と参加条件

**NoDebuff・Boxing・Comboそれぞれに独立した認定3戦とELO**を持ちます。1Kitの認定で他KitのQueueは解放されません。

入口はCertification Queueアイテム、Queue画面の同名ボタン、または `/tier [kit]` です。認定用Queueという名称ですが、同時参加の人間を待つのではなく、Kitを選んで空きアリーナで固定Botとの認定戦を開始します。

- 固定プリセットのBotを使用し、通常Bot設定GUIの変更は持ち込みません。
- 勝敗を問わず、自然決着まで終えた1戦を認定1回として数えます。
- 通常Bot練習、切断、途中退出、強制終了、停止、開始前終了は認定に含みません。
- 同じKitの3戦完了後に追加認定を繰り返して初期レートを引き直すことはできません。管理者によるリセットが必要です。

### 6.2 採点モデル `fixed-balanced-v2`

各試合を0～100点で採点し、最初の3試合の平均から初期ELOを決めます。

| Kit | ヒット比率 | 勝敗 | 残りHP | ポーション精度 |
| --- | ---: | ---: | ---: | ---: |
| NoDebuff | 50% | 20% | 10% | 20% |
| Boxing | 80% | 20% | ― | ― |
| Combo | 65% | 25% | 10% | ― |

各指標を0～1の範囲で扱います。

- ヒット比率＝自分の有効ヒット数÷双方の合計。合計0なら0。
- 勝敗＝勝ち1、負け0。
- 残りHP＝試合終了時のHP÷20。死亡決着の敗者は0です。BoxingではHPを採点に使いません。
- ポーション精度＝自己回復HP÷（投げた回復ポーション数×8）。詳細は[統計](#results)参照。
- NoDebuffで回復ポーション未使用の場合、精度の重みを除き、残り80%を100%へ再正規化します。

```text
基礎点      = 100 × 各指標の加重平均
1試合の点数 = min(100, 基礎点 × 1.20)
初期ELO     = 1500 + 3 × 最初の3試合の平均点
初期範囲    = 1500.000 ～ 1800.000
```

従来モデルより総合スコアを20%緩和します。実測の各指標やポーション精度自体は変更せず、基礎点0は0のままです。例: 基礎点50→60点、70→84点。保存済みの認定スコア・ELOは再計算せず、更新後の新しい認定戦から適用します。既存の認定が途中なら、保存済みの旧スコアと新スコアの3戦平均になります。

これは本サーバーの固定Botを基準にしたローカル評価です。外部のTierランクとの対応や、実力との統計的な相関は保証しません。現在の採点で直接使用しないクリティカル・ガード・ミスなども、評価の根拠として保存します。Botや共通KBを変更すると比較条件も変わります。

### 6.3 ELO更新

K＝32、期待勝率のスケール＝400です。

```text
勝者の期待勝率 Ew = 1 / (1 + 10^((敗者ELO - 勝者ELO) / 400))
変動量 Δ          = 32 × (1 - Ew) を小数第3位へ丸める
勝者の新ELO       = 勝者ELO + Δ
敗者の新ELO       = max(1400.000, 敗者ELO - Δ)
```

- 同じレートなら勝者＋16.000、敗者−16.000。
- 保存は0.001単位の整数で行い、小数3桁で表示します。
- 最低値は **1400.000**。最低値に達する場合だけ双方の増減はゼロサムになりません。
- **1800.000は初期レートの上限**であり、Ranked後のELO上限ではありません。
- 通常Bot・DuelはELO不変。認定は初期レート決定だけで、通常の勝敗ELO計算を行いません。
- RankedのFIGHTING開始後の切断は敗北として更新します。開始前中断・管理者強制停止・サーバー停止・内部異常では更新しません。

### 6.4 マッチング

- 選択したKitの認定3戦完了が必要です。
- 同じKit、かつELO差 **100.000以下** の2人を組み合わせます。境界100.000を含みます。
- 待機時間によるELO範囲の拡大はありません。
- 成立可能な古い組から探し、列の先頭に相手がいなくても、後方で成立する組を進めます。
- 対応する空きアリーナがなければQueueに残ります。

認定数・K・初期ELO範囲・最低ELO・マッチ差100は競技ルールとしてコード側に固定されており、現行の一般GUIやconfigで編集する項目ではありません。

<a id="duel"></a>
## 7. Duel・キルエフェクト

### 7.1 Duel

`/duel <player>` でKit選択、`/duel <player> <kit>` で直接招待できます。相手は同じPvPサーバーのオンラインプレイヤーです。送信・承諾時は双方がPvPロビーで、Queue未参加である必要があります。

- チャットのAccept / Deny、または `/duel accept <name>`・`/duel deny <name>` で応答。
- 認定不要、ELO変動なし。
- 招待期限60秒。1人につき送信済み招待1件。
- 保持中の招待送信から3秒未満の再送信は拒否。新しい招待は以前の送信招待を置き換えます。
- アリーナ不足で承諾できない場合は期限内に再承諾できます。ログアウトで関連招待を解除します。

### 7.2 Settings

PvPロビーのSettingsアイテム、または `/settings` から個人のキルエフェクトを選びます。

| 選択 | 表示 | 内容 |
| --- | --- | --- |
| 雷 | Lightning | 初期設定。視覚上の落雷と音 |
| 爆発 | Explosion | 爆発粒子と音 |
| レッドストーン | Redstone | 血に似た赤色粒子 |

勝者の選択を敗者の位置で再生し、Botが勝った場合は雷です。演出によるダメージ・火災・地形破壊は起こしません。選択は全Kit共通の個人設定として即時保存します。

Settingsで変更できるのはエフェクトです。終了後の60 tick待機は固定で、プレイヤーが待機秒数を編集する項目はありません。

<a id="results"></a>
## 8. 試合結果・統計の定義

### 8.1 閲覧UI

結果チャットのWinner / Loserの名前をクリックすると、その参加者の終了時インベントリを表示します。`/matchresult [名前またはUUID]` でも閲覧でき、無指定では両者の一覧を開きます。

個人画面は54枠です。収納27枠、ホットバー9枠、防具、3つの統計カテゴリを配置し、下段の左右矢印で相手へ切り替えます。個人画面に「両者の結果へ戻る」ボタンはありません。STARTING・FIGHTING中の閲覧は禁止し、ENDING中は閲覧できます。

| 統計アイテム | 説明文の項目 |
| --- | --- |
| Status | Remaining Health、Hunger |
| Combat Statistics | Hits、Criticals、Guards |
| Potion Statistics | Remaining Potions、Health Healed、Potion Accuracy、Missed Potions、Overheal、Opponent Health Healed |

表示ラベルは英語です。残り回復ポーション数は統計アイテムの個数でも表し、0本ならガラス瓶にします。スタック表示は最大64で、説明文には実数を記載します。Botの仮想回復所持数も結果に反映します。

### 8.2 指標の意味

| 指標 | 定義 |
| --- | --- |
| Remaining Health | 終了時HP。死亡決着の敗者は0。Boxingは敗者も終了時HPを表示 |
| Hunger | 終了時空腹度、最大20 |
| Hits | 有効な近接ヒット数。素振り・無効化された攻撃の回数ではない |
| Criticals | 落下中・非接地・非水中・非梯子・非盲目・非騎乗など、旧戦闘のクリティカル条件を満たす有効ヒット |
| Guards | 実際に近接被弾をガードした回数。最大19 |
| Remaining Potions | 残っている回復スプラッシュの本数 |
| Health Healed | 自分が投げた回復ポーションによる自己回復HPの合計 |
| Potion Accuracy | 自己回復HP合計÷（投げた回復ポーション数×8 HP）×100。最大100%、未使用0% |
| Missed Potions | 自己回復が8 HP未満だったポーションの本数。HPの不足合計ではない |
| Overheal | 自分に届いた回復量のうち、最大HPを超えて余ったHP |
| Opponent Health Healed | 自分が投げた回復ポーションにより、相手が回復したHP。相手の満タン超過分は含めない |

回復量はスプラッシュイベント時の強度と不足HPから算出します。

```text
届いた回復量 = floor(強度 × 8 + 0.5)
回復HP       = min(その時点の不足HP, 届いた回復量)
オーバーヒール = max(0, 届いた回復量 - 回復HP)
```

例えば2本投げて8 HPと4 HP回復した場合、精度は12÷16＝75%、ミス1本です。満タン付近で余らせた場合も自己回復が8 HP未満ならミスに含みます。試合終了時に未解決の投擲もミスへ加えます。相手の回復は自己精度の分子には足しません。

この回復統計は、イベント後の最終HP差を継続追跡する方式ではありません。他プラグインが後から回復量を変更すると、表示値と最終回復量が異なる可能性があります。

### 8.3 保存期間

通常の結果GUIは、**閲覧者ごとの最新1試合をメモリに保持する機能**です。ログアウト・再起動で消え、全試合を永続検索する履歴ブラウザではありません。認定の評価ファイルやELO台帳は別途永続保存します。

<a id="bots"></a>
## 9. 通常Bot・認定Bot

### 9.1 通常Botの画面遷移と設定範囲

Bot Fightまたは `/bot`・`/bot start`・`/bot settings` から **Kit選択→Bot Settings→Start** の順に進みます。Kit選択だけでは試合を始めません。設定画面からKit選択に戻れます。

難易度Easy / Normal / Hardは撤廃済みです。設定の既定値は従来のHard相当です。設定画面は54枠で、上からGeneral・Movement・Aim・Combat・Healing、下段に戻る・開始・全リセット・閉じるを配置します。

- 数値は左クリックで増加、右クリックで減少、Shiftで大きく変更、中央クリックで個別初期化。
- ON/OFFは左右クリックで切替。全リセットはShift＋左クリックでのみ実行。
- Boxingでは最大HP・Healing、ComboではHealingを無効表示し、クリックしても保存値を変えません。
- **設定は全プレイヤー・全Kitで共有**し、保存後の新しい通常Bot戦から適用します。既存Bot戦は開始時の設定を保持します。
- 通常Bot設定に管理者専用制限はなく、一般プレイヤーの設定操作も共有値を変更します。
- 認定Botは独立した固定設定です。通常Botを弱くして認定を有利にすることはできません。

Bot表示互換性（2026-09-19修正）: Bot生成後20tickでクライアントのプレイヤー情報を削除する処理を廃止しました。通常・認定Bot戦とHit Debug Roomでは、表示中は情報を保持し、終了・退室時に削除します。1.8.9以降でチャンク読み込みが遅れたり、追跡範囲外から再接近したりした際の、プロフィールを欠いた再出現を防ぎます。このため試合中・入室中はTAB欄にもBotが残ります。実装と検証の詳細は [BOT_VISIBILITY.md](BOT_VISIBILITY.md) を参照してください。

### 9.2 通常Botの設定一覧

下表のキーの先頭はすべて `bot.` です。運用設定は `movement.strafe-enabled=false` だけが下表の配布既定値と異なり、ほかの管理対象項目は同じです。

| 分類 | キー | 配布既定値 | GUI範囲・効果 |
| --- | --- | ---: | --- |
| General | `copy-player-skin` | true | 相手のスキンを使用 |
| General | `maximum-health` | 20 | 最大HP、1～200 |
| Movement | `movement.strafe-enabled` | true | 左右移動。運用設定はfalse |
| Movement | `movement.preferred-distance` | 2.65 | 維持を狙う距離、1～5 |
| Movement | `movement.retreat-distance` | 1.55 | 後退開始距離、0.5～preferred |
| Movement | `movement.strafe-input` | 0.828 | 横入力の強さ、0～1。速度そのものではない |
| Movement | `movement.strafe-switch-minimum-ticks` | 11 | 横移動の再選択最短間隔、2～100 tick |
| Movement | `movement.strafe-switch-maximum-ticks` | 20 | 横移動の再選択最長間隔、2～200 tick |
| Aim | `aim.maximum-yaw-change-per-tick` | 43.2 | 接近・回復時の水平旋回上限、1～180度/tick |
| Aim | `aim.maximum-pitch-change-per-tick` | 32.4 | 接近・回復時の上下旋回上限、1～180度/tick |
| Aim | `aim.prediction-ticks` | 1 | 接近時の移動先予測、0～5 tick |
| Aim | `aim.error-degrees` | 0.14 | 接近時の照準誤差、0～15度 |
| Combat | `combat.swing-lead-distance` | 1 | 実リーチより早く振り始める追加距離、0～3 |
| Combat | `combat.attack-range` | 3 | 攻撃試行の水平距離、1～6 |
| Combat | `combat.minimum-cps` | 16.8 | CPS下限、1～20 |
| Combat | `combat.maximum-cps` | 19.2 | CPS上限、1～20 |
| Combat | `combat.sprint-reset-ticks` | 1 | 命中後のW離し、0～10 tick |
| Healing | `healing.enabled` | true | NoDebuffで回復するか |
| Healing | `healing.health-threshold` | 12 | 回復開始HP、1～最大HP |
| Healing | `healing.cooldown-ticks` | 18 | 回復の再使用間隔、1～200 tick |
| Healing | `healing.potion-count` | 29 | 回復所持数、0～36 |
| Healing | `healing.retreat-before-throw-ticks` | 0 | 通常回復の最低逃走時間、0～100 tick |
| Healing | `healing.double-potion-health-threshold` | 6 | 2本回復のHP境界、1～回復開始HP |
| Healing | `healing.emergency-unanswered-hits` | 2 | 緊急回復とする反撃なし被弾数、1～10 |
| Healing | `healing.emergency-retreat-before-throw-ticks` | 0 | 緊急時最低逃走、0～通常逃走時間 |
| Healing | `healing.can-heal-opponent` | false | Botの回復で相手も回復させてよいか |

上下限の組はGUIで整合させます。CPSは `max(1, round(20 / cps))` tick間隔へ変換するため、小数の設定値どおりの実測クリック数を保証しません。既定範囲では概ね毎tickの試行です。

### 9.3 共通の移動・攻撃

- ZombieではなくWindSpigotの `EntityPlayer` NPCを使用します。
- 自発的なジャンプはしません。Speed IIを常時維持します。
- 標準では水平4ブロックから剣を振り、3ブロック以内でダメージを試行します。射線・上下差も確認し、最終命中はネイティブ戦闘処理に従います。
- 殴り合いの範囲では相手の**現在の目の位置へ正対**し、頭・胴体を揃えます。予測・照準誤差・旋回上限は近接正面追従には適用せず、遠方接近時に使います。
- 回復・食事・パール時は、その行動に必要な向きを優先します。
- 相手が背を向けた場合（相手からBotへの方位とのyaw差100度以上）、横移動を止めて直線追跡します。
- 相手の無敵時間中も設定間隔で攻撃を試し、攻撃頻度を余分に増やしません。
- ダッシュ等の減速条件を満たす攻撃試行で水平速度を0.6倍にし、ダッシュを解除します。無敵時間でダメージが入らない攻撃も対象です。二重減速は避けます。
- 実際の命中後に設定tickのW離しを行います。攻撃後には最大2 tickのガード斬りを行い、成功ガードは各参加者それぞれ19回までです。
- KBなどで自然に落下したときだけクリティカルを発生させます。ジャンプしてクリティカルを狙う行動はありません。
- クライアント相当の水平移動速度とサーバー側KB計算用速度を分け、受けたKBを不要に積み増ししません。被弾中は前入力を優先しますが、KBを直接消す機能ではありません。

### 9.4 認定Botの前後コンボ移動

認定は通常BotのGUI設定とは独立した、次の緩和済み戦闘値・移動制御を使用します。通常Botの既定の強さは変更しません。

| 項目 | 認定の固定仕様 |
| --- | --- |
| 攻撃頻度 | 10～13 CPS（旧16.8～19.2） |
| 攻撃リーチ | 2.8ブロック（旧3.0）。攻撃可能になる1ブロック手前から素振り |
| 接敵時の照準 | yaw最大32.4度/tick、pitch最大24.3度/tick、予測0.5tick、誤差0.8度。殴り合い時の正面向きは維持 |
| 回復再使用 | 24 tick（旧18）。HP閾値12・上限29本は変更なし |
| 横移動 | 有効、入力0.18。通常の配布既定0.828より小さい |
| 目標距離 | 2.65ブロック |
| 後退距離 | 2.35ブロック |
| 予測 | 相対水平速度から2 tick先、距離変化は±0.45まで |
| 後退解除 | 0.15ブロックの幅を設け、頻繁な前後切替を抑える |
| 有効命中後 | 2回連続命中でコンボ成立。その後8 tickは横入力0、1 tickのW離しと間合い維持 |
| 自身の被弾中 | 専用逃走を追加せず前入力で対抗 |

近すぎれば後退し、相手が離れれば前進して追撃する入力制御です。リーチ・ダメージ・KBを認定専用に増やす仕組みではありません。回復・食事・パール中は専用コンボ移動状態を解除します。

### 9.5 回復・消耗品

NoDebuffはHP12以下で回復を開始し、HP6以下なら最大2本を使います。反撃なし被弾2回または低HPを緊急回復の判断に使いますが、残数・再使用間隔は必要です。

1. 相手へ背中を向け、その方向へ前進して距離を取る。
2. 最低逃走時間は通常・緊急とも既定0。ただし近距離なら、4.5ブロック離れるか最大10 tickまで逃走する。設定最低時間がそれより長い場合はそちらを使う。
3. 逃走経路へ回復を投げ、直後は即座に相手へ向き直れる。緊急・空中では自分への即時スプラッシュを使う場合がある。

`can-heal-opponent=false` は距離の希望だけではなく、**Botが投げた回復IIの対戦相手への効果強度を0にします**。trueでは巻き込み回復を許可します。プレイヤー自身のポーションにこの制限は掛けません。

Boxingは回復・パールを使用しません。NoDebuff・ComboのBotは金ニンジンを食べず、NPCだけ満腹を維持します。人間プレイヤーの空腹・食事には影響しません。NoDebuffのSpeed II飲用と回復ポーション補充動作は維持します。Comboは回復スプラッシュ設定を使わず、支給された上位金リンゴ・予備防具を実際に消費します。

- 上位金リンゴ：再生V残り64 tick以下、またはHP8以下かつ吸収0で食事を開始。32 tickの食事後、80 tick再試行待ち。
- 金ニンジン：Botは使用しません。人間用Kitの支給内容は変更しません。
- 食事中は背を向けて移動し、食事の移動ペナルティを適用。
- 装備欠損・残耐久10以下で、収納内の予備装備と交換。

<a id="projectiles"></a>
## 10. ポーション・パール

### 10.1 スプラッシュの飛び方

`splash-potion.without-speed` と `splash-potion.with-speed-ii` を独立して設定します。Speed II以上は後者、Speedなし・Speed Iは前者です。

| キー | 効果 | 配布既定 | 運用：なし / II以上 |
| --- | --- | ---: | --- |
| `hitbox-size` | 投擲物の当たり判定の幅・高さ。有限の正数 | 1.0 | 1.0 / 1.0 |
| `x-speed` | X/Z両方の水平速度倍率。0以上 | 1.0 | 1.0 / 1.0 |
| `y-speed` | 垂直速度倍率。0以上 | 1.0 | 1.0 / 1.0 |
| `spawn-forward-offset` | 発射ベクトル方向への出現位置補正。上下方向も含む。−2～2 | 0 | 0 / 0.25 |
| `spawn-height-offset` | 垂直の出現位置補正。−2～2 | 0 | 0 / 0 |
| `self-collision-delay-ticks` | 投げ主との衝突待ち。整数0～4 | 0 | 2 / 2 |

速度倍率1.0は元の発射速度を保持します。自己衝突待ち0は次tickから本人に衝突可能、4はバニラ相当です。バニラの投擲物hitboxは0.25に対し、現在は1.0です。出現位置補正は生成直後の消滅を避けるため、次のスケジュールtickで現存する投擲物へ適用します。

プレイヤーの足元に必ず当てる・一定時間後に必ず自己回復させる機能ではありません。当たり判定・速度・開始位置・本人との衝突解禁を調整する機能です。WindSpigot側の `settings.lag-compensated-potions` は運用設定でtrueです。

### 10.2 プレイヤーのパール

- 通常Kitは `match.ender-pearl-cooldown-seconds` に従い、運用・既定16秒。設定許容は0～300秒。
- Comboだけ **8秒固定**。通常Kitの値と独立します。
- 使用時の元の発射を止め、**2 tick後の最新位置・yaw・pitch** で再発射します。元の発射速度の大きさを保持します。
- 同じ試合・同じワールドであることを確認し、退出後などに遅延投擲を実行しません。
- クールダウン中は右クリックを拒否し、発射イベントまで進んで消費された場合も1個返却します。
- 経験値レベルに残り秒数の切上げ、経験値バーに残時間比率を表示し、毎tick更新します。
- NoDebuff・Comboで落下扱いのパールダメージを許可します。防具・効果で最終ダメージは変わります。

### 10.3 Botのパール

- 自身が接地している場合だけ投げ、Boxingでは投げません。
- 開幕はクールダウン全量待ちます。運用設定ではNoDebuffは16秒、Comboは8秒後からです。通常BotのNoDebuffは試合のCD設定に従い、認定BotのNoDebuffは16秒固定です。
- 通常は相手の左右約1.65ブロック、相手が浮いた直後4 tick以内なら左右約3ブロックを狙います。左右は交互です。
- 相手の背後へ投げる旧分岐は削除済みです。
- 速度1.5・ばらつき0。目標地点に水平0.6ブロック以内まで近づくか、通り越す条件で飛翔を終え、遠方への行き過ぎを抑制します。
- 偽プレイヤーに通常クライアントのテレポート確認待ちを残さない専用処理を使い、キャンセル可能なBukkitテレポートイベントを経由します。
- 基礎5 HPの落下扱いダメージを適用します。防具などを反映した最終値は5 HPと限りません。

<a id="knockback"></a>
## 11. ノックバック・無敵時間・Combo設定

### 11.1 通常Kit

通常KitはWindSpigot側で管理します。プラグイン側の旧重複設定と `/practice combat` は撤廃済みです。

- 無敵時間の運用設定：`runtime/windspigot.yml` の `settings.hit-delay=18`。
- これはネイティブの最大無敵カウンタです。同強度以下のヒットを抑止する期間は約9 tick（20 TPSで約0.45秒）。大きい攻撃は差分ダメージを扱うため、単純に「18 tick間すべての攻撃を無効」とは解釈しません。
- 非同期KBの運用設定：`settings.async.knockback=false`。
- 通常KBは `runtime/knockback.yml`、現在選択されているプロファイルは **kohi** です。

| kohiプロパティ | 運用設定値 |
| --- | ---: |
| `stop-sprint` | true |
| `friction-horizontal` / `friction-vertical` | 2 / 3 |
| `horizontal` / `vertical` | 0.28 / 0.34 |
| `vertical-min` / `vertical-max` | 0.34 / 0.34 |
| `extra-horizontal` / `extra-vertical` | 0 / 0 |
| `wtap-extra-horizontal` / `wtap-extra-vertical` | 0.31 / 0 |
| `add-horizontal` / `add-vertical` | 0 / 0 |
| 釣竿・矢・パール・雪玉・卵の各水平 / 垂直 | 0.35 / 0.35 |

通常KBの垂直min/maxを同値にするのはKB速度の制限であり、プレイヤーを固定高度で空中停止させる機能ではありません。重力・接地処理は続きます。

### 11.2 Comboの独立設定

Comboでは両参加者・Botに専用の一時個人プロファイルを適用し、終了後は元の設定へ戻します。WindSpigotの選択中グローバルプロファイルやNoDebuff・Boxingには影響させません。

`/combokb set <property> <value>` は保存し、**開始前・進行中のCombo対人戦とBot戦へ即時反映**します。保存失敗時は元へ戻す処理を持ちます。

下表のキーは `no-damage-ticks` だけ `combo.`、ほかは `combo.knockback.` 配下です。コマンドには表の短いキー名を渡します。

| キー | 配布既定 | 運用設定 | 意味・範囲 |
| --- | ---: | ---: | --- |
| `no-damage-ticks` | 2 | 0 | 同強度ヒット再受付の目安間隔。整数0～100。内部カウンタは設定×2 |
| `stop-sprint` | true | true | KB処理のダッシュ停止設定 |
| `fall-height` | 3.0 | 3.2 | 最後の接地位置からの上昇上限。0～50、0で独自下降を無効 |
| `fall-speed` | 0.08 | 0.3 | 上限到達後の一定下降速度、0.04～0.5ブロック/tick |
| `friction-horizontal` | 2 | 5 | 既存水平運動の減衰係数、1～100 |
| `friction-vertical` | 2 | 10 | 既存垂直運動の減衰係数、1～100 |
| `horizontal` | 0.30 | 0.4 | 基本水平KB、0～4 |
| `vertical` | 0.10 | 0.25 | 基本垂直KB、0～4 |
| `vertical-min` | −1 | 0.25 | 垂直KB下限、−4～4 |
| `vertical-max` | 0.15 | 0.25 | 垂直KB上限、−4～4。min以上 |
| `extra-horizontal` | 0.10 | 0.1 | 通常ダッシュ系の水平追加、0～4 |
| `extra-vertical` | 0 | 0 | 通常ダッシュ系の垂直追加、−4～4 |
| `wtap-extra-horizontal` | 0.10 | 0.1 | W-tap系の水平追加、0～4 |
| `wtap-extra-vertical` | 0 | 0 | W-tap系の垂直追加、−4～4 |
| `add-horizontal` / `add-vertical` | 0 / 0 | 0 / 0 | 追加成分、各−4～4 |
| `projectiles.{rod,arrow,pearl,snowball,egg}.{horizontal,vertical}` | 各0.4 | 各0.4 | 投擲物別KB、各0～4 |

`fall-height` に達すると、着地まで追加の上向きKBを抑え、`fall-speed` の一定速度で降下します。自由落下のように重力で下降が加速する仕様ではありません。水平KBと攻撃ダメージは維持します。運用設定0.3なら20 TPS時に約6ブロック/秒、配布既定0.08なら約1.6ブロック/秒です。

```text
/combokb view
/combokb view all
/combokb set no-damage-ticks 0
/combokb set fall-height 3.2
/combokb set fall-speed 0.3
```

上記は構文例です。値の妥当性は現在のKB・試合ルールに依存し、すべての組合せでコンボ維持や誤検知回避を保証するものではありません。

<a id="anticheat"></a>
## 12. ChatterKB・ReachGuard

両機能とも**PoppyPractice内蔵**です。独立した別JARを追加する構成ではありません。原クライアントのprotocolを取得し、変換APIが失敗したクライアントを安易に1.8扱いしません。

### 12.1 ChatterKB Normalizer

短時間に重複したATTACKと移動フレームを調べ、チャタリングによる過剰な攻撃減速を補正します。単なるCPS制限や「クリックが速ければすべて無効」の機能ではありません。

| モード | 動作 |
| --- | --- |
| OFF | チャタリング分類・補正を停止 |
| DETECT_ONLY | 検知・補正推定の記録だけ。速度送信・重複ヒット取消なし |
| CHATTER_ONLY | ACTIVE状態の確定重複を対象に補正。通常攻撃による水平0.6減速は残す |
| STRICT_NORMALIZE | 確定チャタリング以外の減速対象攻撃にも補正を試行。安全なKB補正窓は必要 |

**運用設定・配布既定値はCHATTER_ONLY**。確定重複ヒット取消は `cancel-confirmed-duplicate-hits=true` のため有効です。STRICTでも全攻撃を取り消すわけではありません。

#### 検知設定

以下のキーの先頭は `chatter-kb.` です。表の値は運用設定と配布既定値が一致します。

| キー・グループ | 値 | 効果 |
| --- | --- | --- |
| `target.protocols` | [5,47] | 検知対象の原クライアント |
| `target.require-combat-correlation` | true | 戦闘ダメージとの相関を要求 |
| `detection.history-ms` | 3000 | 検知履歴の保持時間 |
| `detection.same-frame-gap-ms` | strong 5 / medium 12 / weak 20 | 同一フレーム内の重複間隔評価、ms |
| `detection.boundary-gap-ms` | strong 8 / weak 12 | フレーム境界をまたぐ重複評価、ms |
| `detection.boundary-detection` | enabled true / require-armed true | 境界検知を有効にし、既存の疑い状態を要求 |
| `detection.activation` | score 5.5 / min-bursts 2 / window-ms 2000 | ACTIVEへ移行する累積条件 |
| `detection.deactivation` | score 2 / no-burst-ms 5000 | 疑い解除条件 |
| `detection.score-decay-per-second` | 1.0 | スコアの自然減衰 |
| `detection.network-batch-penalty` | 0.5 | 束ね受信らしいケースの加点倍率 |
| `detection.cancel-confirmed-duplicate-hits` | true | 確定重複のヒットを取り消す |

状態はCLEAN / ARMED / ACTIVE / COOLINGです。同一ターゲット・再発なども評価し、境界検知には以前の同一フレーム証拠も使います。チャタリングを手動で起こしても、戦闘相関や累積条件を満たさなければ補正・ログが増えない場合があります。

#### 補正・安全・ログ設定

| キー・グループ | 値 | 効果 |
| --- | --- | --- |
| `compensation.max-client-frames` / `max-window-ms` | 4 / 250 | KB補正窓の最大フレーム・時間 |
| `compensation.min-horizontal-velocity` | 0.08 | 最低水平速度 |
| `compensation.correction-scale` | 0.96 | 補正の強さ |
| `compensation.max-corrections-per-window` / `min-correction-gap-ms` | 2 / 40 | 回数・連続送信の制限 |
| `compensation.max-direction-deviation-degrees` | 35 | 元KB方向から許容する角度差 |
| `compensation.ping-projection` / `max-ping-ms` | true / 220 | Pingを考慮。高Pingでは速度補正を見送る |
| `compensation.require-slowdown-eligible` | true | 攻撃減速の適格性を要求 |
| `compensation.knockback-enchant-support` | false | KBエンチャント追加対応を有効にしない |
| `physics.air-horizontal-drag` / `default-ground-slipperiness` | 0.91 / 0.60 | 推定用の空中抵抗・地上摩擦 |
| `physics.gravity` / `vertical-drag` | 0.08 / 0.98 | 推定用の垂直物理 |
| `physics.observation-blend-alpha` | 0.35 | 観測結果を推定へ混ぜる割合 |
| `safety.min-tps` / `skip-on-position-discontinuity` | 18 / true | 低TPS・位置不連続時に補正を避ける |
| `logging.enabled` / `include-attack-samples` | true / false | ファイル記録有効、全ATTACK常時記録はしない |
| `logging.debug-ring-size` / `async-queue-size` | 256 / 4096 | 診断履歴・書込み待ち容量 |
| `logging.rotate-mb` / `retain-files` | 32 / 7 | 32 MiBで分割、7ファイル保持 |

テレポート・リスポーン・ワールド変更・死亡で状態をリセットします。速度補正の見送りと重複ヒット取消は別経路なので、速度補正できなかったケースでも確定重複の取消が起こり得ます。

#### 無効化ヒットの確認

`/chatterkb debug <player> on` または `/practice debug on` が有効なら、無効化された攻撃を行った本人へ `Hit disabled` と理由をチャット表示します。ログ上のイベント名は `HIT_DISABLED` です。

`/chatterkb status [player]` で状態・スコア・protocol・補正窓・見送り理由・診断件数・ログ欠落数を確認できます。`export` は診断リングからCSVを出し、過去すべての履歴を取得するコマンドではありません。

### 12.2 ReachGuard

攻撃者の目と相手の当たり判定AABBとの最短距離を評価します。送信・確認済みの相手位置と攻撃者位置の履歴候補から、許容できる最小距離を採用します。中心間距離ではなく、完全な照準線検査でもありません。

| モード | 動作 |
| --- | --- |
| OBSERVE | 検知・証拠記録だけ。攻撃は無効化しない |
| PROTECT | 信頼できるリーチ超過・古い状態への攻撃・不正対象などを保護条件に従い取り消す |
| STRICT | 同じ安全性判定の下、STRICT用の閾値を使用 |

**運用設定・配布既定値はOBSERVE**です。したがって現在の保存設定では、リーチを検知してもこの機能による攻撃取消は行いません。

PROTECT・STRICTのHIGH/MEDIUM取消境界はすべて **3.000以上（境界を含む）**。両モードの閾値が同値なので、現在値では通常のリーチ取消境界に差がありません。低信頼・履歴不足・保護猶予・非対応protocol・極端な遅延等は未検証として通すため、「3ブロック以上なら例外なく全部取り消す」仕様ではありません。

以下のキーの先頭は `reachguard.`。値は運用設定と配布既定値が一致します。

| キー・グループ | 値 | 効果 |
| --- | --- | --- |
| `compatibility.packet-library` | NMS_DIRECT | 変換後のWindSpigotパケット経路を使用 |
| `reach.base-reach` / `flag-threshold` | 各3.00 | 基準距離・疑いの境界 |
| `reach.client-hitbox-expansion` / `geometry-epsilon` | 各0 | 追加の当たり判定拡張・幾何誤差なし |
| `reach.protect` / `reach.strict` | high/mediumとも3.00 | 信頼度別取消閾値 |
| `tracking.attacker-history-size` / `target-history-size` | 10 / 8 | 攻撃者・対象の履歴数 |
| `tracking.max-targets-per-viewer` / `history-retention-ms` | 64 / 1000 | 閲覧者ごとの追跡数・履歴時間 |
| `tracking.spawn-grace-ticks` / `teleport-grace-ticks` | 5 / 5 | 出現・転送後の判定猶予 |
| `tracking.respawn-grace-ticks` / `world-change-grace-ticks` | 10 / 10 | 復活・ワールド変更後の猶予 |
| `lag-compensation.sync-interval-ticks` / `max-rewind-ms` | 1 / 300 | 同期周期・巻戻しの上限 |
| `lag-compensation.stale-movement-distance` | 0.60 | 古い対象位置との移動距離判定 |
| `lag-compensation.extreme-ping-observe-threshold` / `maximum-jitter-ms` | 350 / 100 | 極端なPing・揺らぎへの配慮 |
| `server-health.minimum-tps-for-strict` / `maximum-tick-time-ms` | 18 / 250 | 負荷時に強制を避ける基準 |
| `server-health.low-tps-action` | OBSERVE | 低TPS時は監視へ低下 |
| `damage-event-guard.enabled` / `cancel-without-permit` | true / true | パケット許可とダメージイベントの照合 |
| `damage-event-guard.permit-expire-ms` | 150 | 攻撃許可の有効期間 |
| `violation.base` / `excess-multiplier` / `max-excess-addition` | 1 / 10 / 4 | 違反加点の基本値・超過倍率・超過分上限 |
| `violation.decay-per-second` | 0.20 | 違反値の自然減衰 |
| `violation.alert-vl` / `detailed-alert-vl` / `kick-vl` | 3 / 8 / 15 | 通知・詳細通知・処罰候補の境界 |
| `punishment.kick-enabled` | false | 自動Kickは無効 |
| `alerts.enabled` / `cooldown-ms` / `minimum-vl` | true / 500 / 3 | 管理者通知・間隔・最低違反値 |
| `logging.enabled` / `retention-days` / `async-queue-size` | true / 30 / 8192 | 証拠記録・保持日数・書込み容量 |
| `debug.enabled` / `target-player` / `log-valid-attacks` | false / 空 / false | 取消デバッグ・対象限定・正常攻撃の追加記録 |

DamageEventの許可ガードもOBSERVE・非対応protocol・パケット経路不調などでは攻撃を通す設計です。自動Kickは既定・運用とも無効です。検知通知と処罰を同一視しないでください。

ReachGuardの取消デバッグは `reachguard.debug.enabled` を有効にして再読込します。コンソールと通知権限を持つ管理者に `[ReachGuard DEBUG] hit disabled` を表示します。`/practice debug` のダメージ表示とは別設定です。

`reachguard.bypass` は既定falseで、OPだから自動的に検査対象外にはなりません。意図的な一時除外は `/reachguard exempt` を使用します。

<a id="debug"></a>
## 13. Ping・ダメージデバッグ・検証室

### 13.1 Ping

PvPバックエンド内のオンラインプレイヤー全員について、**試合外も含めて2 tickごと**にNMSのPing値を読み取り、キャッシュします。表示は0～9999 msへ制限します。

`/ping` で本人の現在値を表示します。更新周期2 tickはキャッシュの読取り周期であり、ネットワーク往復時間の新規測定を毎2 tick強制する意味ではありません。また、別プロセスの接続ロビーにいるプレイヤーまでPvP側で測定する機能ではありません。

### 13.2 人工遅延

管理者は `/practice ping <player> [milliseconds|off]` で、対象接続へ0～2000 msの人工RTTを追加できます。数値省略時は追加量を確認します。

- 表示値だけの変更ではなく、実際の送受信パケットを遅延します。
- 概ね指定値の半分ずつを双方向へ加え、順序を維持します。
- off時は既に待機しているパケットを順に排出してから解除します。
- 接続セッション単位で、ログアウト・停止で解除し、永続保存しません。
- Bot全体に100 msを追加する旧仕様は撤廃しています。

### 13.3 ダメージチャット

`/practice debug on|off` または `/practice debug damage on|off` で切り替えます。設定キーは `debug.damage-chat`、運用・既定falseです。変更は即時保存します。

有効時は対戦参加者のチャットへ、被害者名・最終ダメージHP・被弾前後HPを表示します。Bot戦では対戦プレイヤーへ表示します。0ダメージ・取消ダメージは表示しません。無効化された攻撃のログはChatterKB・ReachGuard側の設定を使います。

### 13.4 Hit Debug Room

管理者用 `/hitdebug` で専用室へ入れます。試合中は入れず、Queue中ならQueueを抜けて入室します。`/hitdebug reset` で再配置、`/hitdebug leave` または `/lobby` で退出します。

| ダミー | 動作 |
| --- | --- |
| PassiveDummy | 無抵抗、通常KBを受ける。初期位置から12ブロック超で復帰 |
| NoSprintGuard | 固定位置、KBを受けず範囲内へ通常攻撃 |
| SprintGuard | 固定位置、KBを受けず毎回ダッシュ攻撃 |
| PearlTester | 2地点間をパールで往復 |

| 設定 | 運用・配布既定 | 範囲 |
| --- | ---: | --- |
| `hit-debug-room.attack-bot.range` | 3.0 | 1～6ブロック |
| `hit-debug-room.attack-bot.interval-ticks` | 10 | 1～100 tick |
| `hit-debug-room.pearl-bot.cooldown-ticks` | 60 | 10～1200 tick。既定約3秒 |

ダミーのHP・防具耐久を継続復元します。プレイヤーも致死ダメージを防ぎ、次tickに全回復する実質無敵です。検証用の攻撃とKBを消さないため、すべての被ダメージイベントを取り消す方式ではありません。これらの無敵・防具修復は検証室専用で、通常Kitへ一律適用しません。

<a id="worlds"></a>
## 14. ワールド・アリーナ

### 14.1 PvPワールド

ワールド名は `practice`。アリーナ10個とPvPロビー、検証室をvoid環境へ配置します。

| 領域 | 床範囲・大きさ | 高さ・スポーン |
| --- | --- | --- |
| PvPロビー | X −30～30、Z 70～130、61×61 | 床Y3、スポーン(0.5,4,100.5) |
| アリーナn（1～10） | X＝1000n−50～1000n＋49、Z −75～74、100×150 | 床Y3、両スポーンY4 |
| 検証室 | X 11960～12040、Z −30～30、81×61 | 床Y3、入口(12000.5,4,22.5) |

アリーナはX方向へ **1000ブロック間隔の配置基準** で並びます。これは「アリーナの端から次の端まで1000ブロック空ける」実装ではありません。各アリーナの幅は100なので、床どうしの空きは900列です。

アリーナの両スポーンは `(1000n−0.5, 4, −55.5)` と `(1000n−0.5, 4, 55.5)`。向かい合うyawを設定します。

- 床はY0の岩盤、Y1～2の土、Y3の草。
- PvPロビーと通常アリーナは床の1ブロック外側を **高さ50ブロックのガラス壁（Y4～53）** で囲います。屋根はありません。
- 検証室のガラス壁は別仕様の高さ20です。
- 昼・晴天を使用します。
- 生成済みのマーカーと代表地点の床・壁を確認してレイアウトを維持します。外部へ手置きされた全ブロックを常時削除する清掃機能ではありません。

### 14.2 アリーナの共用

10アリーナはNoDebuff・Boxing・Combo、Ranked・Duel・Bot・認定で共用し、同じアリーナを同時に予約しません。通常試合は最大10組が同時使用できます。ENDINGの60 tickも予約に含みます。検証室は別枠です。

新形式は各アリーナに `kits: [nodebuff, boxing, combo]` を指定します。旧形式の `kit: nodebuff` も読めます。

旧生成名 `nodebuff_01`～`nodebuff_10` が初期座標のままの場合は、設定ファイルを上書きせず、読込み時に3Kit対応として扱います。カスタムアリーナは自動拡張せず、明示的な `kits` があればそちらを優先します。

### 14.3 接続ロビーのワールド

別サーバーのワールド `lobby` は65×65（X/Z −32～32）、床Y64、スポーンY65です。ガラス手すり・照明・アーチを生成し、外側は空間です。こちらはPvPアリーナの高さ50ガラス壁とは異なる建築です。落下・範囲外を検出して復帰させます。

<a id="commands"></a>
## 15. コマンド・権限

### 15.1 一般プレイヤー

| コマンド | 機能・場所 |
| --- | --- |
| `/queue join`・`/queue leave` | 選択KitのRanked参加・退出。認定条件あり |
| `/spawn`・`/lobby` | PvPロビーへ復帰。試合中は状態に応じて制限。検証室退出にも使用 |
| `/bot`・`/bot start`・`/bot settings` | 通常BotのKit選択→設定 |
| `/bot leave` | Bot戦を中断。`stop`も別名 |
| `/tier [nodebuff\|boxing\|combo]` | 認定のKit選択または直接開始。別名 `/tiertest`・`/placement` |
| `/elo [kit]` | Kit別ELO・認定状況を確認 |
| `/duel <player> [kit]` | 非レート対人戦の招待 |
| `/duel accept <name>`・`/duel deny <name>` | 招待への応答 |
| `/settings` | キルエフェクトの個人設定 |
| `/matchresult [player\|UUID]` | 最新試合の終了時インベントリ・統計 |
| `/ping` | 本人のPing |
| `/hub` | 接続ロビーへ移動 |
| `/pvp` | 接続ロビーからPvPへ移動 |

コマンドが存在しても、試合・Queue・編集中などの状態によって実行できない場合があります。

### 15.2 Practice管理

以下は `practice.admin` が必要で、既定はOPです。`hitdebug` はプレイヤー専用です。

| コマンド | 内容 |
| --- | --- |
| `/practice reload` | 設定・アリーナを再読込。対人・Bot試合があれば拒否 |
| `/practice forcestop <player>` | 対象を含む対人・Bot戦を強制終了 |
| `/practice debug` | 管理件数・状態を確認 |
| `/practice debug [damage] on\|off` | ダメージ表示の保存・即時変更 |
| `/practice potion` | 両スプラッシュプロファイルの現在設定 |
| `/practice potion <without-speed\|speed-ii> <property> <value>` | スプラッシュの保存・即時変更 |
| `/practice ping <player> [milliseconds\|off]` | 人工遅延の照会・変更 |
| `/combokb [view [all\|property]]` | Combo設定表示。allは投擲物も表示 |
| `/combokb set <property> <value>` | Combo設定を保存し進行中にも即時反映 |
| `/hitdebug [enter\|reset\|leave]` | 検証室への入退室・再配置 |
| `/tierreset <player\|UUID\|all> [nodebuff\|boxing\|combo\|all]` | 認定・ELOリセットのプレビュー |
| `/tierreset confirm <token>`・`/tierreset cancel` | 確認実行・取消 |

### 15.3 ChatterKB管理

権限は `chatterkb.admin.<subcommand>`、まとめて `chatterkb.admin.*`。既定はOPです。

| コマンド | 内容 |
| --- | --- |
| `/chatterkb status [player]` | 本人または指定オンラインプレイヤーの診断状態。コンソールでは対象必須 |
| `/chatterkb debug <player> on\|off` | 対象の診断リングと取消チャット |
| `/chatterkb mode <OFF\|DETECT_ONLY\|CHATTER_ONLY\|STRICT_NORMALIZE>` | 即時の一時モード変更。設定ファイルには保存しない |
| `/chatterkb reload` | 設定再読込・状態リセット |
| `/chatterkb reset <player>` | 検知状態だけをリセット |
| `/chatterkb export <player> [seconds]` | リング内の診断をCSV化。省略30秒 |

### 15.4 ReachGuard・ネットワーク管理

ReachGuard権限は `reachguard.status`、`inspect`、`alerts`、`mode`、`reload`、`exempt`、`resetvl`。`reachguard.admin` がまとめ、既定はOPです。

| コマンド | 内容 |
| --- | --- |
| `/reachguard status [player]` | 全体・対象のprotocol・RTT・VL・判定 |
| `/reachguard inspect <player>` | メモリ中の最近の証拠、最大8件 |
| `/reachguard alerts [on\|off]` | 実行者の通知切替。プレイヤー専用 |
| `/reachguard mode [observe\|protect\|strict]` | 表示または保存・即時変更 |
| `/reachguard exempt <player> <seconds>` | 1～86400秒の一時除外 |
| `/reachguard unexempt <player>` | 一時除外を解除 |
| `/reachguard resetvl <player>` | 違反値だけをリセット |
| `/reachguard reload` | 専用設定再読込。不正値なら旧設定を維持 |
| `/lobbybuild` | 接続ロビーの建築保護切替。`poppylobby.build`、既定OP |
| `/kb` | WindSpigot本体のKB管理。PoppyPracticeのコマンドではない |

`/chatterkb reset`・`/reachguard resetvl` は認定結果やELOを削除しません。認定削除は別の `/tierreset` です。

<a id="operations"></a>
## 16. 保存・認定リセット・運用

### 16.1 データの保存先

PoppyPracticeのデータ基準ディレクトリは `runtime/plugins/PoppyPractice/` です。

| 相対パス | 保存内容 |
| --- | --- |
| `config.yml` | ロビー・試合・Bot・Combo・投擲・検知設定・メッセージ |
| `arenas.yml` | アリーナとスポーン・対応Kit |
| `kit-layouts.yml` | UUID・Kit別の配置対応 |
| `ratings.yml` | 認定点数・ELO・処理済み試合ID・リセット後の旧ID保持 |
| `tier-assessments/<UUID>/<kit>/<match UUID>.yml` | 認定ごとの採点モデル・重み・結果・両者の生統計 |
| `cosmetic-preferences.yml` | 全Kit共通の個人キルエフェクト |
| `chatterkb/chatterkb-<時刻>.jsonl` | チャタリング検知・補正ログ |
| `chatterkb/export-<名前>-<時刻>.csv` | 診断リングのエクスポート |
| `reachguard-logs/reachguard-YYYY-MM-DD.jsonl` | 日別のリーチ証拠ログ |
| `backups/certification-reset-<識別子>/` | コマンドによる認定リセットのバックアップ |
| `certification-reset.pending` | 中断・成否不明なリセットの保護マーカー |

対人ELOは両者を1回の台帳保存で確定し、試合IDで二重更新を防ぎます。認定詳細の保存に失敗した場合は認定を加算せず、レート保存失敗を成功として表示しません。

レート・認定・個人設定の主要保存には一時ファイルからの置換を使用します。破損した台帳を無断で初期化せず、読込失敗として保護します。設定の復旧では元ファイルを退避してから、整合するバックアップを使います。

人工遅延・一時除外・通知のセッション状態・通常結果GUIは永続履歴ではありません。診断ログは有限の非同期キューを使い、混雑時は欠落数を記録してサーバー処理をブロックしない設計です。

### 16.2 認定結果を消すコマンド

```text
/tierreset PlayerName             # 1人の全Kitをプレビュー
/tierreset PlayerName nodebuff    # 1人のNoDebuffだけ
/tierreset all boxing             # 全員のBoxingだけ
/tierreset all                    # 全員の全Kit
/tierreset confirm 確認コード     # 同じ管理者が60秒以内に実行
/tierreset cancel                 # 自分の確認待ちを取消
```

**対象Kitの認定回数・点数・資格・ELOを消し、0/3へ戻します。ELOにはRankedで変動した分も含みます。** 選択していないプレイヤーやKitのELO、過去のランク戦相手のELOは巻き戻しません。Kit配置・エフェクト・Bot設定・KB・ワールドは削除しません。

安全条件と処理は次のとおりです。

1. 名前はオンラインまたは保存済みの既知プレイヤーから解決。曖昧なら完全UUIDを要求し、外部へ名前検索しない。
2. 初回は件数・ELO消去範囲のプレビューだけ。8文字の確認コードを発行し、同じ実行者が60秒以内に確認する。
3. 対象がQueue・STARTING・FIGHTING・ENDINGにいる場合は拒否する。Kit限定でも本人が別Kitで対戦中なら拒否する。
4. プレビュー後の新しい結果や台帳の外部編集をrevision・SHA-256で検出し、不一致なら再プレビューを要求する。
5. リセット前の `ratings.yml` 全体・対象の認定評価・操作情報とハッシュをバックアップする。
6. 対象評価を退避し台帳を更新。旧試合IDは保持して、古い終了処理が再送されても結果が復活しないようにする。
7. 成功後に対象へ再認定を通知。正常時は再起動不要。

保存失敗で旧台帳が無事なら退避評価を戻します。保存の成否が不明、復元失敗、途中停止などではpendingマーカーを残して保護し、マーカーが残る起動ではレート台帳の読込みも拒否します。**マーカーだけを先に消して続行しないでください。**

手動復旧ではPvPを通常停止し、現状を別途退避して、対応バックアップの `manifest.yml` とハッシュを確認します。全体台帳と指定評価ディレクトリを整合させて戻し、確認後にマーカーを解除して `run.bat` で起動します。全体台帳の復元は、リセット後の他プレイヤーの更新も巻き戻し得ます。自動Undoコマンドはありません。

停止中に使う旧リセットスクリプトと、現行コマンドでは対象範囲・バックアップ場所が異なります。日常の管理は本節と[認定・Ranked運用ガイド](RANKED.md)の現行コマンドを基準にしてください。

### 16.3 設定の反映タイミング

| 変更方法 | 保存 | 反映範囲・注意 |
| --- | --- | --- |
| 通常Bot GUI | する | 次に開始する通常Bot戦。全プレイヤー共有。認定は不変 |
| `/practice potion` | する | 以後の投擲へ即時 |
| `/combokb set` | する | 開始前・進行中の全Combo戦にも即時 |
| Settings | する | 個人の次のエフェクトへ |
| `/practice debug on/off` | する | ダメージチャットへ即時 |
| `/chatterkb mode` | しない | 一時変更。reload・再起動でファイル値へ |
| `/reachguard mode` | する | 設定検証後に即時再読込 |
| `/practice ping` | しない | 指定接続へ即時、切断で解除 |
| `/practice reload` | ファイルを読む | 試合中は拒否。全設定一括の完全トランザクションではない |
| WindSpigot設定の手編集 | ファイル上の変更 | 安全に停止・再起動して反映を確認 |

`ChatterKB logging.async-queue-size` は構築時に確保するため、サイズ変更は再起動で反映します。手編集とコマンド保存を同時に行うと上書き競合の可能性があるため、運用中の無計画な手編集は避けてください。

### 16.4 起動・停止・ビルド

サーバー起動・検証は **run.batを経由**します。

| 入口 | 用途 |
| --- | --- |
| `run-network.bat` | ネットワーク全体の起動・統合コンソール |
| `network/proxy/run.bat` | Velocity単体 |
| `network/lobby/run.bat` | 接続ロビー単体 |
| `runtime/run.bat` | PvP単体、最大メモリ4G |

ネットワークの統合コンソールでは `status`、`pvp list`、`lobby list`、`proxy velocity info`、`stopall` を使用できます。停止は正常な保存を伴う停止手順を使い、強制プロセス終了を通常手段にしません。

主なビルド入口は `scripts/build-plugin.ps1`、ロビーは `scripts/build-lobby-plugin.ps1`。PvP成果物は `target/PoppyPractice-0.1.0.jar` です。更新時は停止・既存JARと必要データの退避・JAR更新・run.batから起動・ログ確認の順に行います。

`setup-network.ps1` は固定アーティファクトのSHA-256を検証します。ネットワークの有効化・無効化スクリプトは全サーバー停止を前提にバックアップを作り、運用データを無断で置き換えない設計です。詳細は[NETWORK.md](NETWORK.md)を参照してください。

<a id="limitations"></a>
## 17. 旧仕様との相違・制約・検証状況

### 17.1 置き換え済みの要望

| 以前の案・仕様 | 現行 |
| --- | --- |
| Spigot・既定Java8で起動 | WindSpigot、バックエンド専用Java17 |
| Waterfall等で中継 | Velocityの独立ロビー構成 |
| 全Kit共通認定・レート | Kit別3戦・Kit別ELO |
| 認定不要の一般Queue | 現QueueはRanked。非レート対人はDuel |
| Bot難易度選択 | Kit選択→共有Bot設定GUI。認定は固定設定 |
| デバッグ用リーチ0・直進Botモード | 撤廃。通常Botの横移動ON/OFFと、別の管理者検証室 |
| Bot全体へ100 ms遅延 | 撤廃。対象プレイヤーへの人工遅延機能は残る |
| 4回反撃できなければ逃げる専用動作 | 撤廃。回復の緊急判定と認定の前後間合い制御は別機能 |
| 能動ジャンプ・ジャンプ回復 | なし。自然落下のクリティカルのみ |
| 回復前に常に1秒逃げる | 既定最低0 tick、近距離では4.5ブロックまたは最大10 tickの退避 |
| 相手の背後を狙うパール | 削除。横狙い・接地条件・行き過ぎ抑制 |
| 全アイテムの開幕3秒禁止 | なし。開始前に補充したポーションの個別使用ガード |
| 決着後すぐ帰還 | 自然決着は60 tick待機 |
| 統計を全部別アイテムで表示 | 英語のStatus / Combat / Potionの3カテゴリ |
| 個人結果から両者へ戻るボタン | 左右矢印で相手へ切替 |
| ReachGuardで既定取消 | 既定OBSERVE。PROTECT/STRICTへ変えた場合のみ条件付き取消 |
| Chatterの全面減速補正を既定にする | 既定CHATTER_ONLY、正常攻撃の減速は残す |

### 17.2 設定名と実装の注意

監査で次の項目が確認されています。今回の文書作成では設定削除・動作修正を行っていません。

- 運用configに残る `bot.combat.swing-range` は現在読みません。実効値は `attack-range + swing-lead-distance` です。
- `chatter-kb.safety.disable-on-teleport` は現在読まれず、転送時の状態リセットは常時行います。
- `chatter-kb.safety.disable-on-unsupported-protocol` と `chatter-kb.logging.include-corrections` は読み込まれますが、動作切替には参照されません。falseにしても非対応クライアント補正・補正ログ停止を保証しません。
- 新しいprotocol番号をconfigへ足すだけで、未検証版のアンチチート対応が完成するわけではありません。
- Pingの2 tick更新は取得キャッシュの周期、結果GUIは最新1試合の一時保存、Bot設定は共有です。この3点は特に誤解しやすい制約です。

### 17.3 検証済み範囲と未保証事項

直近のPoppyPracticeテスト記録は **966件、成功965件、失敗0件、エラー0件、スキップ1件**。スキップはWindowsのシンボリックリンク作成権限を要するケースです。今回の文書作成ではJavaテストを再実行せず、既存レポートを確認しています。

既存の隔離WindSpigot（127.0.0.1:25569、Java17、run.bat起動）では、認定・ELO・Duel・終了待機・認定リセット実コマンド・Bot正面追従・認定前後移動などの検証記録があります。検証ヘルパーやランダムな試験プレイヤーデータは本番機能ではありません。

ネットワークは代表protocolのステータス・認証要求、旧版および上限側の合成クライアントで接続・サーバー移動の確認記録があります。一方、全対応バージョンの実クライアントによる画面表示・全装備操作・実戦品質を網羅した保証ではありません。

Botの対人らしさ、認定点数と人間の実力との相関、全条件でのポーション・パール精度、厳格リーチ判定の誤検知率は実プレイで継続確認する事項です。

<a id="sources"></a>
## 18. 実装・設定ファイルの参照先

| 分野 | 主な参照先 |
| --- | --- |
| 全体初期化・登録 | [PracticePlugin.java](../src/main/java/com/poppy/practice/PracticePlugin.java)、[plugin.yml](../src/main/resources/plugin.yml) |
| 配布既定値 | [config.yml](../src/main/resources/config.yml) |
| 運用値 | `runtime/plugins/PoppyPractice/config.yml`、`runtime/windspigot.yml`、`runtime/knockback.yml` |
| Kit・配置保存 | [kit](../src/main/java/com/poppy/practice/kit)、[KitLayoutService.java](../src/main/java/com/poppy/practice/kit/KitLayoutService.java) |
| ロビー・Sidebar | [LobbyService.java](../src/main/java/com/poppy/practice/service/LobbyService.java)、[PracticeSidebar.java](../src/main/java/com/poppy/practice/service/PracticeSidebar.java) |
| 試合・結果 | [MatchService.java](../src/main/java/com/poppy/practice/service/MatchService.java)、[result](../src/main/java/com/poppy/practice/result) |
| 入力・アイテム制限 | [listener](../src/main/java/com/poppy/practice/listener)、[RefilledPotionUseGuard.java](../src/main/java/com/poppy/practice/listener/RefilledPotionUseGuard.java) |
| Bot・認定移動 | [bot](../src/main/java/com/poppy/practice/bot)、[BotCertificationMovement.java](../src/main/java/com/poppy/practice/bot/BotCertificationMovement.java) |
| 認定・ELO | [tier](../src/main/java/com/poppy/practice/tier)、[rating](../src/main/java/com/poppy/practice/rating)、[RANKED.md](RANKED.md) |
| Duel・エフェクト | [duel](../src/main/java/com/poppy/practice/duel)、[cosmetic](../src/main/java/com/poppy/practice/cosmetic) |
| Combo・アリーナ設定 | [ComboConfig.java](../src/main/java/com/poppy/practice/config/ComboConfig.java)、[ArenaKitPolicy.java](../src/main/java/com/poppy/practice/config/ArenaKitPolicy.java) |
| 認定リセット | [TierResetCommand.java](../src/main/java/com/poppy/practice/command/TierResetCommand.java)、[CertificationResetBackup.java](../src/main/java/com/poppy/practice/rating/CertificationResetBackup.java) |
| ChatterKB・ReachGuard | [chatter](../src/main/java/com/poppy/practice/chatter)、[reach](../src/main/java/com/poppy/practice/reach) |
| Ping・遅延 | [network](../src/main/java/com/poppy/practice/network) |
| ワールド生成 | [ArenaWorldLayoutService.java](../src/main/java/com/poppy/practice/service/ArenaWorldLayoutService.java)、[HitDebugRoomLayoutService.java](../src/main/java/com/poppy/practice/service/HitDebugRoomLayoutService.java) |
| 接続ロビー | [lobby-plugin](../lobby-plugin)、[LobbyLayout.java](../lobby-plugin/src/main/java/com/poppy/lobby/LobbyLayout.java) |
| ネットワーク構成・固定版 | [NETWORK.md](NETWORK.md)、[artifacts.json](../network-template/artifacts.json) |
| 運用・ビルド | [scripts](../scripts)、[run-network.bat](../run-network.bat) |

設定・実装が更新された場合は、運用値の表だけでなく、認定の比較条件・即時反映範囲・旧仕様との差分も合わせて改訂してください。
