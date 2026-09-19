# Botが見えなくなる問題への対処

更新: 2026-09-19。ソース修正と本番への反映は別作業です。

## 原因と変更点

従来はBot生成時に `ADD_PLAYER` を送り、20tick後に `REMOVE_PLAYER` を送ってTAB欄から除いていました。
この情報はTABだけでなく、1.8以降のプレイヤーエンティティの生成にも必要です。
WindSpigotの通常のエンティティ追跡は、再生成時にBotのプロフィールを追加し直しません。

そのため、チャンクの送信が初回生成から1秒以上遅れる場合や、追跡距離（現運用では48ブロック）の外へ離れてから戻る場合に、プロフィールがない状態で `NamedEntitySpawn` が届く経路がありました。

- 通常Bot・認定Bot: 20tick後の情報削除を廃止し、NPCを除去するまで保持します。
- Hit Debug Room: 同じ遅延削除を廃止し、退室またはNPC除去時に情報を削除します。
- 通常のNPC除去は、エンティティ破棄 → プロフィール削除の順です。
- 副作用として、見ているBotはTAB欄にも表示されます。TABのみを隠すためのバージョン固有処理は追加していません。
- Botの戦闘・移動・KB・スキン設定は変更しません。

## 検証

単体テスト `BotNpcPlayerInfoTest` は5件で、プロフィール追加、終了時のパケット順序、二重削除、オフライン/不在の閲覧者の後始末を確認します。
以前の対戦履歴等の未コミット変更を含む作業ツリーの全体ビルドは1,101件、失敗0・エラー0・スキップ22（この実行では実DBの19件を未実行、Windows symlink関連3件）です。

隔離WindSpigotは必ず `tmp/reachguard-smoke-runtime/run.bat` から起動し、127.0.0.1:25569だけで検証します。
実運用の認証設定・DB・公開サーバーは変更しません。
試験コードは `tmp/bot-visibility-smoke/` に置き、protocol47（Minecraft 1.8.x）でプレイヤー情報と出現・破棄パケットを照合します。

修正前のJARでは、生成約1秒後にADD → SPAWN、約2秒後にREMOVE、遠方へ移動して戻った約6秒後にプロフィールのないSPAWNを再現しました。
修正版では60tick経過後もプロフィールがあり、同じ遠方移動・再接近によるSPAWNでも情報が存在しました。終了時のDESTROY → REMOVEの順と、プロフィールが残らないことも確認しました。
これはパケットの整合性試験であり、全バージョンの実クライアントの描画を網羅した試験ではありません。

## 本番反映の注意

今回の作業ツリーには、別途実装した対戦履歴スキーマV2とWeb同期の未コミット変更があり、このBot修正コミットには含めません。
これらの反映承認なしに、Bot修正を理由としてその全体JARを本番へ配置してはいけません。
Bot修正だけ反映する場合は、既存の本番JARから対象のBotクラス3系統だけを差し替えた修正専用JARを検証して使用します。
適用前に既存JARをバックアップし、稼働中なら正常停止してから配置し、起動は `runtime/run.bat` を使います。

### 今回の反映記録

ユーザー承認によりBot表示修正だけを `runtime/plugins/PoppyPractice.jar` へ配置しました。**サーバーは起動していません。**
元JARは `runtime/backups/bot-visibility-20260919-233854/` に保管しています。
修正専用JARのSHA-256は `0989C20CD99CDD5C2492884F28912149266E6EA38C448B01BB4448B5FBCDD7D4` です。
3クラス系統以外の1,409エントリーは元JARと全て同一で、対戦履歴V2・Web同期を追加していません。
このJARも隔離環境の同じ再表示試験に合格し、試験サーバーは正常停止・元の設定とJARへ復元済みです。

参考: [WindSpigotのEntityTrackerEntry](https://github.com/Wind-Development/WindSpigot/blob/master/WindSpigot-Server/src/main/java/net/minecraft/server/EntityTrackerEntry.java)、[ViaVersionの1.19.3向けプレイヤー情報変換](https://github.com/ViaVersion/ViaVersion/blob/master/common/src/main/java/com/viaversion/viaversion/protocols/v1_19_1to1_19_3/rewriter/EntityPacketRewriter1_19_3.java)。
