# Bento Home — 実装設計 v0.1

作成日: 2026-10-26（日本時間）  
前提: `plan.md` v0.3 の要求・方針を実装へ落とす文書。plan.md と矛盾する記述があれば plan.md を優先し、この文書を直す。  
状態: 段階5まで実装済み。実機確認は `docs/spike-notes.md`（段階1）、`docs/stage2-notes.md`〜`docs/stage5-notes.md`（段階2〜5）。運用手順は `docs/operations.md`。「実機で確認」とある項目は段階1の技術検証で結論を出す。

## 1. 確定した判断（2026-10-26）

| 項目 | 決定 | 理由・影響 |
|---|---|---|
| applicationId | `com.rou39.bentohome` | 作者名義 rou39 とリポジトリ名に合わせる |
| ウィジェットの配置粒度 | 位置は半マス（小マス単位）、サイズは1マス刻み（小マス×subdivisions） | 中央寄せ・半マスずらしを許しつつ、提供元のサイズ制約と整合しやすい |
| 長押しメニュー（ホームのアイコン） | ホームから削除／移動開始、アプリ情報／アンインストール、アプリショートカット（静的・動的）、外観の個別設定 | ショートカットは既定ホームのときだけ表示。ショートカットのホームへの固定は初版対象外 |
| アプリ一覧の形式 | グリッド。上部に検索欄と並び順切替 | ホームと同じアイコン描画を共有し、グレー表示・バッジの見え方を揃える |
| 設計文書の置き場 | 要求・計画は `plan.md`、実装設計は `docs/design.md`、技術検証の記録は `docs/spike-notes.md`（段階1で作成） | 1ファイルの肥大化を避ける |

## 2. 技術スタックと開発環境

### 固定する方針

- Kotlin、Jetpack Compose（Material3）、単一 Gradle モジュール `app`。Gradle Kotlin DSL と version catalog（`gradle/libs.versions.toml`）。
- 依存注入は手書き（`Application` が持つ `AppContainer`）。Hilt は入れない。個人用の単一モジュールで、生成コードとビルド時間を増やす利点が薄い。
- 永続化は Room（KSP）と Preferences DataStore。バックアップ書き出しは kotlinx.serialization の JSON。
- 非同期は coroutines / Flow。UI 状態は `ViewModel` の `StateFlow`。
- テストは JUnit4 + kotlinx-coroutines-test。純粋ロジック（配置・順位・通知集計・バックアップ変換）は JVM テスト。Room のマイグレーションは instrumented テスト。
- リリース APK に `INTERNET` を含めない。マージ後のマニフェスト（`app/build/intermediates/merged_manifest/release/`）を検査する Gradle タスクを用意し、ビルドのたびに確認する。

### このPCの現状（2026-10-26 確認）

| 項目 | 状態 | 対応 |
|---|---|---|
| JDK | 17.0.2（`C:\Java\jdk-17.0.2`、`JAVA_HOME` 設定済み） | 現行 AGP の最低要件は満たす。採用する AGP が JDK 21 を求める場合は追加導入 |
| Android SDK | `C:\Android`（`ANDROID_SDK_ROOT` 設定済み）。`platforms;android-37.0`、`build-tools;37.0.0`、platform-tools 37.0.1 を取得済み | compileSdk / targetSdk 37 で確定 |
| ビルド構成 | Gradle 9.8.2（wrapper）、AGP 9.4.1、Kotlin 2.4.21、Compose BOM 2026.09.00 | JDK 17 でデバッグ・リリースのビルドと単体テストが通ることを確認済み。版は `gradle/libs.versions.toml` で管理。Room / KSP は段階2で追加 |
| Android Studio | 未検出 | なくても Gradle CLI と adb でビルド・インストールできる。Compose プレビューやレイアウト検査が必要になれば導入 |
| 接続端末 | なし（`adb devices` 空） | Pixel 9a で USB デバッグを有効化して接続する |
| Git | 段階1の雛形で初回コミット済み | — |

## 3. アプリ構造

### Activity 構成

| Activity | 役割 | マニフェスト属性 |
|---|---|---|
| `HomeActivity` | HOME 入口。ワークスペース、アプリ一覧（オーバーレイ）、編集モードを1つの Compose ツリーで持つ | `MAIN` + `HOME` + `DEFAULT`。`launchMode="singleTask"`、`stateNotNeeded="true"`、`excludeFromRecents="true"`、`screenOrientation="portrait"`（初版は縦固定）、テーマで `windowShowWallpaper=true` |
| `SettingsActivity` | 設定。ホームの長押しメニューと、他ランチャーからも開けるよう `LAUNCHER` カテゴリーを持つ | ラベルは「Bento Home 設定」 |
| 外部 | ウィジェットの許可・設定画面、アプリ情報、ロール要求 | `ActivityResultLauncher` で呼び、結果を `WidgetHostController` に戻す |

HOME キーで `HomeActivity` が前面になるとき、`singleTask` により上に積まれた Activity（設定・ウィジェット設定画面）は閉じられ、`onNewIntent` が届く。ここで編集モード・ドラッグ・アプリ一覧・取り消し待ちを解除し、設定された「ホームページ」へ戻す。

### 既定ホームの扱い

- 起動時に `RoleManager.isRoleHeld(ROLE_HOME)` を確認し、未保持ならホーム上部に案内を出す。タップで `createRequestRoleIntent(ROLE_HOME)`。
- 元のホームへ戻す導線は設定画面に置く（`Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS` を開くだけ）。

### 戻る操作

- `enableOnBackInvokedCallback=true` で予測型バックに対応する。
- ホームでの戻る: ドラッグ中はキャンセル、アプリ一覧が開いていれば閉じる、編集モードなら解除、それ以外は何もしない。

### エッジ・ツー・エッジ

- システムバーは透過。配置計算は `WindowInsets.systemBars` と `displayCutout` を除いた領域で行う。ジェスチャーナビゲーション領域と下部ドックの重なりを実機で確認する。

## 4. 状態の所有と流れ

```
Room / DataStore --Flow--> Repository --> ViewModel(StateFlow<UiState>) --> Compose
      ^                                        |
      +---------- 書き込み（LayoutEngine の結果）<----+
```

- 配置の正本は Room。`WorkspaceViewModel` は Room の Flow を購読して `WorkspaceUiState`（ページ、項目、ドック、編集モード、ドラッグ状態、取り消し待ち）を組み立てる。
- 配置の変更は `LayoutEngine`（純粋関数）で新しい配置を計算し、成功したときだけ Room に書く。衝突は拒否して元に戻す（自動で押し出さない）。
- 取り消しは1段階、メモリ内。直前の操作の前の配置全体を保持し、次の操作で破棄する。プロセス終了で消える。スナックバーが消えたあとも、背景の長押しメニューから取り消せる。
- ウィジェットの削除では、ID をすぐには解放しない。次の操作で取り消せなくなった時点か、画面を閉じた時点で `deleteAppWidgetId` する。取り消し中にプロセスが終わった場合は、次の起動時にホスト側にあって配置に無い ID をまとめて解放する。
- 保存は配置全体を1トランザクションで置き換える。書き込みはアプリスコープで行い、画面を閉じても途中で捨てない。

## 5. LayoutEngine（純粋 Kotlin、テスト対象）

### グリッド定義

- `GridSpec(baseColumns, baseRows, subdivisions=2)`。小マス数は `cols = baseColumns×sub`、`rows = baseRows×sub`。
- 小マスの画面寸法は `使用可能幅 / cols`、`使用可能高さ / rows`。使用可能領域はシステムバー・カットアウト・ドック・ページインジケーターを除く。
- 配置座標はすべて小マス単位。ピクセル値は描画時にのみ計算する。

### 占有範囲（footprint）

| 種別 | spanX, spanY | 位置の自由度 |
|---|---|---|
| アイコン | sub × sub（初期 2×2） | 任意の小マス |
| ウィジェット | sub の倍数（1マス刻み） | 任意の小マス |

- アイコンの画像とラベルは footprint の内側に収める。ラベルは footprint 幅で省略表示し、はみ出しで隣と重ねない。アイコン画像サイズの設定値は footprint からラベル高を引いた範囲に丸める。
- 衝突判定は footprint の矩形重なりのみ。これにより「ラベル・タップ領域を含めた衝突」は footprint 同士の判定で保証される。

### 操作 API（案）

```
canPlace(ws, pageId, rect, ignoreItemId?) : Boolean
place(ws, item) : Result<Workspace, Conflict>
move(ws, itemId, pageId, cellX, cellY) : Result<Workspace, Conflict>
resize(ws, itemId, spanX, spanY, bounds) : Result<Workspace, Conflict>
remove(ws, itemId) : Workspace
changeGrid(ws, newSpec) : GridChangePreview   // 収まらない項目一覧を返し、確定は別呼び出し
firstFreeCell(ws, pageId, spanX, spanY) : Cell?  // ウィジェット追加・復元時の自動配置
```

`Workspace` は不変データ。`Result` 失敗時は呼び出し側が元の状態を表示し続けるだけでよい。

### ウィジェットのサイズ算出

- 提供元の `targetCellWidth/Height`（API 31+）は「1マス（base cell）」の数として解釈し、`×sub` で小マス数にする。未指定なら `minWidth/minHeight` を小マス寸法で割り上げ、sub の倍数に丸める。
- 下限は `minResizeWidth/Height`、上限は `maxResizeWidth/Height`（API 31+、0 なら無制限）を小マス寸法で換算する。`resizeMode` が許す方向だけハンドルを出す。
- リサイズはメニューの「サイズ変更」で枠を出し、辺のつまみで1マス刻みに変える。提供元の `resizeMode` が許す辺だけつまみを出し、`minResize`・`maxResize` を1マスの寸法で換算した範囲に収める。置けない大きさは赤で示し、指を離したときに置ける大きさなら確定する。
- 配置・リサイズ・グリッド変更・インセット変化のたびに `AppWidgetHostView.updateAppWidgetSize(null, listOf(SizeF(wDp, hDp)))` を呼ぶ。初版は縦固定なので1サイズでよい。

## 6. ワークスペースの描画と操作

- ページは `HorizontalPager`。各ページは自作 `Layout` で、小マス座標から絶対配置する。
- 壁紙はウィンドウ背景で表示し、ページのスクロール量に応じて `WallpaperManager.setWallpaperOffsets` を呼ぶ（設定でオフ可能）。
- ドックはページの外側に固定。ドロップ先として同じ衝突規則を使う（ドックは1行、スロット単位）。

### 描画の負荷

- ページャーのページ数とキーのラムダは `remember` で固定する。作り直すとページャーが状態を書き換え、ホーム画面が毎フレーム再コンポーズされる（`docs/stage5-notes.md`）。
- 見えていないページのウィジェットと、アプリ一覧の下のウィジェットは View を `INVISIBLE` にする。提供元のアニメーションが止まり、待機中の描画が無くなる。

### 読み上げ

- アイコンは自前のジェスチャー処理なので、`semantics` で「開く」「メニューを開く」の操作を明示し、子要素をまとめて1つにする。
- ラベルを表示しているときは画像に説明を付けず、名前を二重に読まない。バッジは「通知 N 件」、グレー表示は状態として「しばらく使っていません」を読む。
- 背景の長押しと上スワイプの代わりに、ホームに「ホームのメニュー」「すべてのアプリ」のカスタム操作を付ける。ページ表示は「ページ x / n」と読む。

### ジェスチャーの優先順位

| 入力 | 動作 |
|---|---|
| タップ（アイコン） | 起動 |
| 長押し（アイコン・ウィジェット） | 触覚フィードバック、メニュー表示。指を動かしたらドラッグへ移行 |
| 横スワイプ（空き領域・アイコン上） | ページ送り |
| 上スワイプ（空き領域） | アプリ一覧 |
| 長押し（空き領域） | ウィジェット追加・壁紙・設定のメニュー |
| ウィジェット内のタッチ | 原則そのままウィジェットへ渡す |

アイコンのタップ・長押し・ドラッグは1つの `awaitEachGesture` で扱う。長押しが成立したら指を離すまでのイベントを消費し、離したときにタップとして起動しない（`docs/stage2-notes.md`）。

ウィジェット（`AppWidgetHostView`）上の操作は Compose と View の相互運用になる。`AndroidView` を包む `Box` の `pointerInput` で `PointerEventPass.Initial` を使い、消費せずに動きを監視する。長押しが成立したとき、またはタッチスロップを超えて横方向が優勢になったときは、以降のイベントを消費する。消費すると相互運用層が View に `ACTION_CANCEL` を送る。縦方向はウィジェット内部のスクロールに渡す。段階1の実機検証で、横スワイプを取らないと View がタップと判定して提供元を開くことを確認し、この方式で解消した（`docs/spike-notes.md`）。View ラッパーへの切り替えは不要。消費したイベントはページャーにも届かないので、ウィジェット上の横ドラッグはホームが `PagerState.dispatchRawDelta` で直接ページを動かす。移動量はウィジェット自身の移動に影響されないよう、ルート座標で測る。縦スクロールするリスト型ウィジェットとの干渉は未検証。

### ドラッグ

- ドラッグ中は元の位置を空けたまま、半透明のゴーストを指に追従させる。ドロップ候補の小マスを強調し、衝突する場合は赤系で示してドロップを拒否する。
- 画面端で一定時間止まるとページ送り。ページ末尾では新規ページを作る。空ページは編集モード終了時に末尾側から削除する。
- ドロップ確定で `LayoutEngine.move` → Room 書き込み → 取り消しスナップショット更新。

## 7. WidgetHostController

- `AppWidgetHost(context, HOST_ID)` はアプリスコープで1つ。`HomeActivity.onStart` で `startListening()`、`onStop` で `stopListening()`。
- 追加の流れ:
  1. 自作のピッカーで `AppWidgetManager.getInstalledProvidersForProfile(user)` を提供元ごとに一覧表示（プレビュー画像、ラベル、推奨サイズ）。
  2. `allocateAppWidgetId()` で仮 ID。
  3. `bindAppWidgetIdIfAllowed(id, provider, options)` が false なら `ACTION_APPWIDGET_BIND` を結果付きで開く。
  4. `info.configure != null` なら `startAppWidgetConfigureActivityForResult`。
  5. 置き場所を決める（ドラッグで置く。置けなければ `firstFreeCell`、全ページで無ければ新規ページ）。Room に保存して確定。
  6. いずれかで失敗・取消なら `deleteAppWidgetId(id)`。
- View は ID ごとに `host.createView(...)` を1回だけ作り、`WidgetHostController` 内の `Map<Int, AppWidgetHostView>` に保持する。Compose 側は `key(widgetId) { AndroidView(factory = { controller.viewFor(id) }) }` とし、再コンポーズで作り直さない。項目削除の確定時と `HomeActivity.onDestroy` で解放する。
- `onProvidersChanged` で提供元情報を更新し、`onAppWidgetRemoved(id)` で該当項目を Room から外す。
- 起動時に `host.appWidgetIds` と Room の `widgetId` を突き合わせる。ホスト側に無い ID は「再追加が必要」のプレースホルダーとして同じ footprint に表示し、タップで追加の流れをやり直す。黙って消さない。

## 8. AppCatalog

- `LauncherApps.getActivityList(null, user)` で起動対象を取得し、`AppKey(componentName, profileSerial)` をキーにする。初版はメインプロファイルのみ扱うが、キーには最初から serial を含める。
- `LauncherApps.Callback` でインストール・更新・削除・利用可否変化を受け、カタログの Flow を更新し、`IconRepository` の該当キャッシュを無効化する。
- アプリが削除されたらホーム・ドックから該当項目を外す。利用不可（一時的）では残す。
- 起動は `startMainActivity(component, user, sourceBounds, options)`。例外なく呼べたときだけ `UsageRepository.recordLaunch` を呼ぶ。
- ショートカット: `hasShortcutHostPermission()` が true のときだけ `getShortcuts` で静的・動的を取得し、長押しメニューの上部に最大4件表示。静的なものを先に、それぞれ rank 順。`startShortcut` で起動する。使用頻度への加算は段階4で入れる。

## 9. 通知バッジ

- `BadgeListenerService : NotificationListenerService`。マニフェストで `BIND_NOTIFICATION_LISTENER_SERVICE` 権限とインテントフィルターを宣言。
- 有効状態は `NotificationManager.isNotificationListenerAccessGranted` で判定。設定画面から `ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS` を開く。
- 集計は純粋関数 `BadgeAggregator` に分離し、`StatusBarNotification` を `NotificationFact(key, packageName, userSerial, groupKey, isGroupSummary, flags, canShowBadge)` に写してから渡す。本文・タイトル・送信者は写さない。
- 規則:
  - `key` で重複排除。同じキーの更新は置換。
  - 同じ `groupKey` に子通知があればまとめ通知（`isGroupSummary`）は数えない。子が無いまとめ通知は1件として数える。
  - `FLAG_ONGOING_EVENT`、`FLAG_FOREGROUND_SERVICE` を持つ通知は初版では除外。`Ranking.canShowBadge()` が false のものも除外。
  - 結果は `Map<BadgeKey(packageName, userSerial), Int>`。
- `onListenerConnected` で `activeNotifications` と `currentRanking` から全再構築。`onListenerDisconnected` と権限撤回時は「不明」（null）にして古い数を残さない。
- `BadgeRepository` はアプリスコープの `StateFlow<BadgeState>`（`Disabled` / `Unknown` / `Counts(map)`）。ホームと一覧は `AppKey.componentName.packageName` と `profileSerial` で引く。
- 表示は「なし／点／数値」。数値は 99 を超えたら `99+`。アプリ単位の `badgeMode` が既定値を上書きする。

## 10. 使用頻度とグレー表示

- Room v3: `usage_daily(component, profileSerial, epochDay, count)`、`usage_summary(component, profileSerial, observedSince, lastLaunchAt)`。
- `recordLaunch`: 起動に成功したときだけ呼ぶ。ホーム・ドック・一覧のタップと、アプリショートカットの起動を数える。同じアプリの起動が5秒以内に続いたら1回とみなす。日付は端末のローカル日。
- 保持は60日。アプリのプロセス起動時に古い行を削除する。
- カタログに現れたアプリは、`usage_summary` に観測開始だけを記録する（既にある行は変えない）。アンインストールされたアプリは記録を消し、入れ直したら観測をやり直す。
- 設定の「使用履歴を削除」は、日別件数と最終起動を消し、全アプリの観測開始を今の時刻にする。直後に全アプリを「未使用」と判定しないため。
- 純粋ロジックは `UsagePolicy` にまとめた（設計時の RankingPolicy・GrayPolicy）。
- `score(dailyCounts, today) = Σ count_d × 0.5^(age_d / 7)`（過去28日）。同点は `lastLaunchAt` 降順、次に名前。「頻度集計から除外」したアプリは頻度0・最終起動なしとして扱う。
- 最近開いた順は `lastLaunchAt` 降順。一度も開いていない・除外したアプリは後ろに名前順。
- 一覧の並び順は設定（名前順・よく使う順・最近開いた順）に保存する。一覧を開いた時点か切り替えた時点の記録で順番を決め、開いている間は動かさない。
- グレー表示の対象画面（ホーム・一覧）と日数（7・14・30・60日、初期値30日）は設定で選ぶ。長押しメニューに理由を出す。
- `grayReason(usage, now, grayDays, keepColor, hasBadge)`:
  - `keepColor` または `hasBadge` なら false。
  - `lastLaunchAt != null` なら `now - lastLaunchAt >= grayDays`。
  - `lastLaunchAt == null` なら `now - observedSince >= grayDays`。
  - `observedSince` が無い（記録方式切替直後など）なら false。
- `observedSince` はカタログがそのアプリを初めて観測した日時。アプリ更新では変えない。
- グレー表示は描画時に `ColorMatrix` で彩度を0にし、濃さを 70% にする。キャッシュ画像は加工しない。ラベルとタップ領域は変えない。

## 11. IconRepository

- `LruCache<IconKey, Bitmap>`、上限は `ActivityManager.memoryClass` の 1/8 程度（バイト単位）。`IconKey = AppKey + sizePx + shape`。
- 形は「端末の標準・円・角丸四角・四角」。端末の標準は OS のマスクをそのまま使う。それ以外は、adaptive icon の前景・背景の層を各辺 1/4 はみ出して描き、形のパスで切り抜く。旧式アイコンは白い背景に余白付きで載せる。
- パッケージ変更、形状・サイズ設定の変更でキーが変わるか無効化される。
- 読み込みは `Dispatchers.IO`、未ロード中はプレースホルダー。アプリ一覧の高速スクロールで詰まらないよう、画面に出る分だけ要求する。

## 12. 永続化とバックアップ

### Room（schema v1）

`exportSchema = true` でスキーマ JSON（`app/schemas/`）をリポジトリに入れ、マイグレーションテストの基準にする。

- v1 は `workspace_page`、`workspace_item`、`dock_item` の3表。v2 で `app_appearance`（keepColor・excludeFromRanking・badgeMode）を自動マイグレーションで足した。`usage_daily`・`usage_summary` は段階4で足す。
- `app_appearance` は plan.md の iconOverride・labelOverride を持たない。アイコンの差し替えは段階6の拡張で検討する。
- マイグレーションの instrumented テストは、実機の配置を消さないよう保留している。`connectedAndroidTest` は終了時にアプリを削除するため。エミュレーターを用意したら追加する。
- plan.md の `pinned` 列は使い道が無いので持たない。ページの表示順の列名は、SQL の予約語を避けて `position` とする。
- `workspace_item.type` は `ICON` / `WIDGET`。`WIDGET` は `widgetId`、`widgetProvider`（ComponentName 文字列）、`profileSerial` を持つ。

### DataStore

- 外観・機能設定を Preferences DataStore に置き、型付きの `LauncherSettings` に変換して公開する。
- 項目: 配置ロック、ドック表示、列数（3〜6）、行数（4〜8）、ドックの枠数（3〜6）、アイコンの大きさ（40〜72dp）と形、ラベルの表示と文字サイズ、バッジの表示方式。
- マス目とドック枠の変更は設定画面で「適用」したときだけ書く。適用前に現在の配置から、移る項目・縮むウィジェット・ドックから外れるアプリの数を計算して見せる。
- ホームは設定の変化を受けて `LayoutEngine.changeGrid`・`shrinkDock` で配置を組み直す。項目は削除せず、取り消せる。取り消したときは設定も配置に合わせて書き戻す。

### バックアップ

- 書き出し形式は JSON（kotlinx.serialization）: `{ format, schemaVersion, exportedAt, appVersion, settings, pages, items, dock, appearances, usage? }`。`items` のウィジェットは `widgetId` を含めず、`widgetProvider` と footprint だけを書く。使用履歴は既定で含めない（オプション）。未知の項目は無視し、形式名が違うファイルと新しすぎる版は読まない。
- `ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT` で保存先を選ぶ。
- 読み込み時はプレビュー（ページ数・項目数・再追加が必要なウィジェット数・この端末に無いアプリ数）を見せてから置き換える。この端末に無いアプリは置かない。
- ウィジェットは `widgetId = -1` の「再追加が必要」な項目として復元する。タップすると提供元を探して追加の流れをやり直し、同じ項目の ID を置き換える。
- ホームが動いていれば復元をホームの ViewModel に渡し、配置の変更として取り消せるようにする。設定・外観の個別設定・使用履歴は置き換えるだけで、取り消しの対象外。
- マニフェストは `allowBackup="false"`。OS のクラウドバックアップ・端末間転送の対象外にする。

## 13. アプリ一覧

- `HomeActivity` 内の全画面オーバーレイ。上スワイプまたはボタンで開く。
- 上部: 検索欄（自動でフォーカスしない）、並び順の切替（名前／頻度／最近）。
- 本体: ホームと同じアイコン描画のグリッド。グレー表示・バッジを適用。
- 一覧からアプリを起動しても一覧は閉じない。ホーム操作で戻ったときに閉じる。
- 一覧を開いた時点で並び順を確定し、開いている間は再ソートしない。インストール・削除は反映するが位置は末尾追加・該当削除のみ。
- 長押しで「ホームへ追加（ドラッグ）」「アプリ情報」「アンインストール」「外観の個別設定」。

## 14. パッケージ構成（`com.rou39.bentohome`）

```
app/           BentoApplication, AppContainer
home/          HomeActivity, WorkspaceScreen, DrawerOverlay, EditMode, Drag
layout/        GridSpec, Workspace, LayoutEngine, Conflict        <- 純粋 Kotlin
catalog/       AppKey, AppCatalog, LauncherAppsSource
widget/        WidgetHostController, WidgetPicker, WidgetSurface
badge/         BadgeListenerService, BadgeAggregator, BadgeRepository
usage/         UsageRepository, RankingPolicy, GrayPolicy        <- Policy は純粋 Kotlin
icon/          IconRepository, IconShaper
data/          Room database, DAO, DataStore, Settings
settings/      SettingsActivity と各画面
backup/        BackupModel, Exporter, Importer
```

## 15. テスト方針

| 対象 | 種別 | 主な観点 |
|---|---|---|
| `LayoutEngine` | JVM | 半マス配置の衝突、中央寄せ、ウィジェットの span 丸め、グリッド変更プレビュー、`firstFreeCell` |
| `BadgeAggregator` | JVM | 重複排除、グループ集計、常駐の除外、ランキングの尊重、全再構築 |
| `RankingPolicy` / `GrayPolicy` | JVM | 減衰計算、同点処理、履歴不足・観測開始直後の非該当 |
| `BackupModel` | JVM | 往復変換、未知フィールドの無視、`widgetId` を含めないこと |
| Room | instrumented | スキーマ v1 の生成、以降のマイグレーション |
| 実機 | 手動 | plan.md §9 の表に従い `docs/spike-notes.md` へ記録 |

## 16. 段階1（技術検証）で作るもの

同じ `app` モジュールに作り、段階2以降はそのまま育てる。

1. 雛形: Gradle 設定、`HomeActivity`（HOME インテント、壁紙表示、ロール要求案内）、`SettingsActivity`（空）。
2. アプリ起動: `AppCatalog` の最小版で全アプリを縦に並べ、タップで起動、起動回数をログ。
3. ウィジェット: ピッカー → 許可 → 設定 → 画面下部へ1つ固定表示。Duolingo / GitHub で追加・タップ・更新・削除・再起動を確認。ウィジェット上の横スワイプと長押し検出の挙動を記録。
4. 通知バッジ: `BadgeListenerService` と `BadgeAggregator`。パッケージ名と件数だけをログに出し、X / Discord / Gmail で追加・更新・削除・グループ化を確認。
5. SDK: platform 37 の取得可否、AGP / Kotlin / Compose BOM の組み合わせを固定し `libs.versions.toml` に記録。

完了条件は plan.md §7 段階1 のとおり。結果と制限は `docs/spike-notes.md` に書き、この文書の「実機で確認」項目を更新する。

## 17. 未確定・リスク

- ウィジェット上のジェスチャー調停（§6）。横スワイプと長押しは段階1で解決済み。リスト型ウィジェットの縦スクロールとの干渉が残る。
- SDK 37 の提供状況。未提供なら 36 で開始する。
- Android 17 のメモリ制限とウィジェット更新時の画像制限。ホスト側の対策は View 参照の解放と `IconRepository` の上限。提供元起因の不具合は記録して対応範囲外とする。
- ウィジェット内タップからの起動（IntentSender 制限）。標準の `AppWidgetHostView` 経路を使い、追加のハンドリングを入れない。
- 縦固定の妥当性。横向き利用を求めるなら、グリッドを向きごとに持つ設計変更が必要になる。
