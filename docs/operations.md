# Bento Home — 運用手順

本人の端末で使い続けるための手順。ストア公開は扱わない（plan.md §8）。

## ビルドと署名

| 種類 | パッケージ | 署名 | 用途 |
|---|---|---|---|
| デバッグ版 | `com.rou39.bentohome.debug` | Android SDK のデバッグ鍵 | 開発・実機確認 |
| リリース版 | `com.rou39.bentohome` | リリース鍵（下記） | 普段使い |

```
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # app/build/outputs/apk/release/app-release.apk
```

リリースビルドは、マージ後のマニフェストに INTERNET 権限が無いことを毎回検査する（`checkNoInternetPermission`）。

### リリース鍵

- 鍵: `C:\Users\kuronekorou39\.bento-home\release.jks`（PKCS12、別名 `bento-home`、RSA 4096、有効期限100年）
- パスワード: プロジェクト直下の `keystore.properties` と、鍵と同じフォルダの `keystore.properties` に記載
- `keystore.properties` と `*.jks` は `.gitignore` 済みで、リポジトリには入らない
- 証明書 SHA-256: `11e3f57fd12d6542107bec1b0e5c25a790e1fa40d36ba00b78be56aca42e0e72`

**鍵とパスワードは別の場所にも控えること。** 失うと、リリース版を同じパッケージのまま更新できなくなる。その場合はアンインストールして入れ直すしかなく、配置・設定は事前のバックアップから戻すことになる。

`keystore.properties` が無い環境では、リリース版は未署名でビルドされる。

## インストールと更新

```
adb install app/build/outputs/apk/release/app-release.apk      # 初回
adb install -r app/build/outputs/apk/release/app-release.apk   # 更新（同じ鍵・versionCode を上げる）
```

- 更新するときは `app/build.gradle.kts` の `versionCode` を上げる。
- インストール・更新のたびに Norton 360 が警告を出す。除外設定に入れておくと煩わしくない。
- 更新の直後にホームキーを押すと、ホームアプリの選択画面が出ることがある。Bento Home を「常時」で選び直す。
- 実験版と安定版の APK は、日付とバージョンを付けて手元に残しておく。

## 元のホームへ戻す

次のどちらかで戻せる。

1. Bento Home の設定画面の「既定のホームアプリを変更（元のホームへ戻す）」から Pixel Launcher を選ぶ
2. OS の設定 → アプリ → デフォルトのアプリ → ホームアプリ

Bento Home が起動できない状態でも、PC から戻せる。

```
adb shell cmd role add-role-holder android.app.role.HOME com.google.android.apps.nexuslauncher
```

## システムモニターの権限

詳細は `docs/monitor.md`。どれも任意で、無ければ表示できる情報が減るだけ。

### adb で一度だけ付与する（再起動後も残る）

```
adb shell appops set com.rou39.bentohome GET_USAGE_STATS allow
adb shell pm grant com.rou39.bentohome android.permission.DUMP
```

デバッグ版はパッケージ名を `com.rou39.bentohome.debug` にする。使用状況へのアクセスは OS の設定（特別なアプリアクセス → 使用状況へのアクセス）からも許可できる。

### Shizuku（全プロセスの CPU・メモリ、端末の CPU 使用率）

- Shizuku 13.6.0 を GitHub のリリースから入れた（署名者 `CN=Rikka`、証明書 SHA-256 `268b5590…db30`）。Play ストア版でもよい。
- 端末の再起動のたびに起動し直す。端末だけで行うなら、Shizuku アプリの「ワイヤレスデバッグ」から起動する。PC がつながっていれば次のコマンドでも起動できる。

```
adb shell "$(adb shell pm path moe.shizuku.privileged.api | sed 's/package://;s/base.apk//')lib/arm64/libshizuku.so"
```

- 初回はモニター画面の「Shizuku を許可する」から許可する。許可した後は「Shizuku でまとめて許可する」で、上の2つの権限も付与できる。
- Shizuku を後から起動したときに Bento が気づかない場合は、Bento を強制停止して開き直す。
- 計測用のプロセスはモニター画面を表示している間だけ動く。

## バックアップと復元

- 設定画面 → バックアップ → 「書き出す」で、配置・設定・外観の個別設定を JSON に保存する。保存先はシステムのファイル選択で選ぶ。
- 「使用履歴も含める」をオンにしたときだけ、起動回数と最終起動を含める。
- ウィジェット ID は端末ごとに違うので書き出さない。読み込むと「タップしてウィジェットを追加し直す」と表示されるので、タップして許可・設定をやり直す。
- 読み込み前に、ページ数・アプリ数・追加し直しが必要なウィジェット数・この端末に無いアプリ数を表示する。
- 配置の置き換えはホームの「元に戻す」で取り消せる。設定・外観の個別設定・使用履歴は取り消しの対象外。
- OS のクラウドバックアップと端末間転送は無効にしている（`allowBackup="false"` とデータ抽出ルール）。

## データの削除

- 使用履歴だけ: 設定画面 →「使用履歴を削除」
- すべて: OS の設定でストレージを消去するか、アンインストールする

## 通信について

Bento Home 自身は INTERNET 権限を持たず、通信しない。システムモニターの「Bento」タブで、権限の有無と端末の起動後に送受信したバイト数を確認できる。起動したアプリ、ウィジェットの提供元、OS のバックアップ、ユーザーが選んだクラウドの保存先は、それぞれ独自に通信することがある。
