# Survival初回デプロイの起動判定不良（2026-09-21）

対象: GitHub Actions `35517403222`、Paper 26.3 build 26。

## 確認した原因

- コンテナの`/tmp`は`noexec`。Paper同梱のJNAがここへ展開した共有ライブラリをロードできず、OSHIがERRORを出力した。
- Survivalプラグインの有効化と`Done (71.225s)!`は記録されていたが、readinessは起動エラーを保持して正常判定しなかった。
- 240秒の起動期限で`Readiness failed: survival`となり、更新処理が旧3サービス構成へロールバックした。
- 旧リリースのcurrent、Practice/Lobby/Proxyのhealthy、DB接続受付、transaction.jsonの削除、入口の再開を確認した。
- Actionsに表示されたNumPy推奨メッセージはIAP転送性能の警告で、この失敗原因ではない。

## 修正方針

1. Paper同梱JNAのネイティブライブラリを、検証済みイメージの読み取り専用領域へ配置する。
2. `/tmp`と制御ソケット領域の`noexec`、非root、read-only、capability削除を維持する。
3. PRのCIで同じ制限下のJNA/OSHI実ロードを検査する（EULAの自動同意なし）。
4. 本番デプロイでは既存の同意ファイルをそのまま利用し、隔離環境で実際のPaper起動・ヘルスチェック・通常停止を先に検査する。
5. 起動異常時は最初のエラーもreadinessへ表示し、ホスト処理の失敗時はActionsへjournalを表示する。

起動エラーを一律に無視したり、起動期限を延長して回避する変更ではない。
新しいMinecraft/Paperバージョンへの更新、プレイヤーデータの変更、公開ポートや権限の追加は含めない。
