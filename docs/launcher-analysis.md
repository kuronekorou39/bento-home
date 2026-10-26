# ランチャーの比較と Bento Home の方向性

作成日: 2026-10-26（日本時間）  
状態: 検討用の分析。機能の採否は未決定。

市販ランチャーの情報は、2026-10-26 に Web で調べた。紹介記事の評価は筆者の意見で、計測ではない。機能の記述は公式ヘルプ・変更履歴・報道による。最新版で確かめていないものは、その旨を書いた。

## 1. タスクマネージャー的な表示はできるか

結論として、端末全体の情報と、アプリごとの「使用時間」「通信量」は表示できる。Windows のタスクマネージャーのような、アプリごとのリアルタイムの CPU・メモリ使用率は、一般のアプリには表示できない。これは Kotlin かどうかではなく、Android の権限の制限による。

### Pixel 9a / Android 17 での確認

Bento Home と同じ権限（`run-as com.rou39.bentohome.debug`）で読めるかを試した。

| 情報 | 読み出し先 | 結果 |
|---|---|---|
| 端末全体のメモリ | `/proc/meminfo` | 読める |
| CPU の周波数 | `/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq` | 読める |
| 端末全体の CPU 使用率 | `/proc/stat` | 拒否 |
| 負荷平均 | `/proc/loadavg` | 拒否 |
| 他のアプリのプロセス情報 | `/proc/<pid>/stat` | 拒否 |
| プロセス一覧 | `ps -A` | 自分のプロセスしか出ない |
| 通信量の生カウンタ | `/proc/net/dev` | 拒否 |
| 温度センサー | `/sys/class/thermal/...` | 拒否 |
| 電池の電流 | `/sys/class/power_supply/...` | 拒否 |

### API で取れるもの（公式 API。実機での動作は未確認）

| 情報 | API | 必要な許可 |
|---|---|---|
| 端末のメモリ（空き・合計・逼迫） | `ActivityManager.getMemoryInfo` | 不要 |
| 端末全体の通信速度 | `TrafficStats.getTotalRxBytes` などの差分 | 不要 |
| 電池の残量・充電・温度・電流 | `BatteryManager`、`ACTION_BATTERY_CHANGED` | 不要 |
| 発熱による制限の状態 | `PowerManager.getCurrentThermalStatus`、`getThermalHeadroom` | 不要 |
| ストレージの空き | `StatFs` | 不要 |
| アプリごとの使用時間・最近使ったアプリ | `UsageStatsManager` | 使用状況へのアクセス（ユーザーが設定で許可） |
| アプリごとの通信量（時間単位の集計） | `NetworkStatsManager` | 使用状況へのアクセス |
| アプリごとのストレージ使用量 | `StorageStatsManager` | 使用状況へのアクセス |
| 実行中のプロセス一覧 | `ActivityManager.getRunningAppProcesses` | 他のアプリの分は取れない（Android 5.0 以降） |
| 他のアプリのメモリ・CPU | — | 取れない |
| アプリの強制終了 | — | できない（バックグラウンド終了の要求は効果が薄い） |

### 抜け道と、その代償

Shizuku を使うと、ADB の権限でアプリを動かせる。`dumpsys meminfo` や `dumpsys cpuinfo` 相当の、アプリごとの情報も取れるようになる。ただし再起動のたびにワイヤレスデバッグでの起動が必要で、強い権限をアプリに渡すことになる。root はさらに強い。どちらも任意の拡張にとどめるのが妥当。

### Bento Home で作るなら

- 端末全体のグラフ（メモリ、通信速度、電池・温度、発熱の状態、CPU 周波数）は作れる。記録はホームが表示されている間だけにして、常駐で計測しない。段階5で待機中の描画を0にした方針と揃えるため。
- アプリ一覧に、使用時間・通信量・ストレージ量の列を付けられる。使用状況へのアクセスを許可したときだけにする（plan.md §4 の任意機能の延長）。
- Bento Home 自身の負荷（CPU、描画フレーム、メモリ）は正確に測れる。ウィジェットの描画負荷も、Bento Home の中で起きるので測れる。段階5で Duolingo のアニメーションを突き止めた方法を、そのまま機能にできる。
- 通信量の計測に INTERNET 権限はいらない。「通信しない」方針は保てる。

## 2. 主なランチャーの比較

| ランチャー | 方針 | 固有の機能 | 長所 | 短所・リスク | 2026年の状況 |
|---|---|---|---|---|---|
| Pixel Launcher | 端末標準。Google のサービスと一体 | 最近使ったアプリ画面との統合、ジェスチャー、検索バー、At a Glance、App Bubbles | OS と完全に一体。動きが滑らか | 配置の自由度が低い。アイコンパック非対応。Google の機能が前提 | Android 17 で検索バーのカスタマイズ、At a Glance の削除、ラベルの非表示が入った |
| Nova Launcher | 何でも設定できる定番 | 細かいグリッド、ジェスチャー操作、アイコンパック、引き出しのタブ、バックアップ | 自由度と実績 | 2025年に開発が事実上停止。2026年1月に Instabridge が買収し、広告とトラッカーの SDK が入った。有料版でも報告あり | 開発は再開したが、広告・データ収集・AI アシスタントの方向 |
| Niagara | 片手で使う縦のアルファベット一覧 | 波のような一覧、通知のインライン表示、Usage Breaker（使いすぎ対策）、Theme Studio | 広告なし。大画面でも片手で届く | グリッドで自由に置く使い方には合わない。主要機能は Pro | 2026年4月に Theme Studio のベータ |
| Smart Launcher 6 | 自動整理 | アプリの自動カテゴリ分け、壁紙に合わせた配色、ウィジェットのスタック | 手間がかからない | 機能が多く重め。課金あり | 2026年の更新内容は確認できず |
| Lawnchair | Pixel 風をオープンソースで | Launcher3 ベース、引き出しのフォルダー | 無料・オープンソース。Pixel に近い見た目 | 長くベータのまま。新しい Android への追従が遅れがち | 15 はベータ、16 は開発中。17 対応は未確認 |
| Kvaesitso | 検索が中心 | 端末内とウェブの横断検索、電卓・単位換算、タグで分類、プラグイン | 無料・オープンソース。アイコンを探さない | F-Droid 経由が中心。更新の頻度に不満の声 | 2026年9月に 1.41.0 |
| ミニマル系（Olauncher、Before、Ratio、Escape など） | 使いすぎを減らす | 文字だけのホーム、通知の振り分け、使用時間の報告、アプリの非表示 | 集中しやすい | 見た目と機能を意図的に削っている。ウィジェットは基本なし | 各アプリで差が大きい |
| Microsoft Launcher | Microsoft のサービスとの連携 | PC との連携、フィード | Office 系と連携 | 2025年以降の状況は確認できず | 不明 |

### すべてのサードパーティ製ランチャーに共通する制約

- 最近使ったアプリ画面とジェスチャーは、端末標準のランチャー（QuickStep）が持っている。サードパーティには API が開かれていない。Android 10 以降の問題で、2024年に Nova・Niagara・Smart Launcher・Lawnchair の開発者が共同で Google に改善を求めたが、大きくは変わっていない。
- Pixel の最近使ったアプリ画面の「選択」など、一部の機能はサードパーティでは使えない。
- Android 17 で何か変わったかを書いた情報は見つからなかった。Bento Home の実機確認（`docs/spike-notes.md`）でも、ホームキーとアプリ起動は問題なかった。

## 3. Bento Home の今の立ち位置

### すでに持っている強み

| 強み | 中身 | 他との違い |
|---|---|---|
| 通信しないことを確かめられる | INTERNET 権限なし。リリースビルドで毎回検査 | Nova は2026年に広告とトラッカーを入れた。「通信しない」を検査で保証するランチャーは見当たらない |
| 半マス単位の自由配置 | 中央寄せ・半マスずらし。重ね置きはしない | Nova の細かいグリッドに近い自由度を、衝突判定付きで持つ |
| 正直なバッジ | 現在の通知件数。重複とまとめ通知を除く | 未読数を装わない |
| 使っていないアプリの見える化 | 自分のランチャー内の起動記録だけで判定。理由を表示 | ミニマル系の使用時間対策に近いが、ホームの自由配置と両立 |
| ウィジェットの扱い | 横スワイプでの誤起動を防ぐ。見えないときは止める | 段階5で、待機中の描画を0にした |
| 自分で把握できる | 設計・検証の記録が全部ある | 本人用としての最大の利点 |

### 足りないもの（他のランチャーが標準で持つもの）

フォルダー、アイコンパック、ジェスチャー操作（ダブルタップで画面オフなど）、ウィジェットのスタック、ホームからの検索の強化、仕事用プロファイル・プライベートスペースへの対応。

## 4. どこで優位に立つか

本人用のランチャーなので、「優位性」は自分にとっての使いやすさと安心のこと。公開を考える場合は「信頼できる」ことが一番の差になる。Nova が広告とデータ収集の方向へ動いた2026年は、その差が出やすい。

### 方針: 「静かで、見える」ランチャー

1. **静か**: 通信しない。広告・トラッカー・AI を入れない。待機中に電池を使わない。勝手に並べ替えない。
2. **見える**: 端末とアプリの状態、ランチャー自身の振る舞いを、ユーザーが確かめられる。

他のランチャーは「便利さ」か「ミニマル」のどちらかに寄っている。「見える」を軸にした製品は見当たらない。タスクマネージャー的な表示への関心とも一致する。

### 機能の候補

| 優先 | 機能 | 中身 | 実現性 | 他にあるか |
|---|---|---|---|---|
| 高 | システムモニター | メモリ、通信速度、電池・温度、発熱の状態、CPU 周波数のグラフ。ホーム表示中だけ記録 | 標準 API で可。許可不要 | 専用アプリはあるが、ランチャーに統合されたものは少ない |
| 高 | 透明性のページ | Bento Home の権限、保存しているデータ、通信しないことの検査結果、自身の CPU・描画負荷を表示 | 可 | 見当たらない |
| 高 | ウィジェットの負荷計測 | どのウィジェットが描画・CPU を使っているかを表示し、止める・別ページへ移すを提案 | 可。Bento Home の中で描画するので測れる | 見当たらない |
| 中 | アプリの棚卸し | 使っていない日数、使用時間、通信量、ストレージ量で一覧を並べ、アンインストールへの導線を出す | 使用状況へのアクセスが必要 | 一部のミニマル系・ファイル管理アプリにある |
| 中 | ウィジェットのスタック | 同じ場所に複数のウィジェットを重ね、スワイプで切り替える | 可 | Smart Launcher、Pixel（一部） |
| 中 | フォルダー・アイコンパック | 他のランチャーと同じ水準にする | 可（段階6の予定） | ほぼ全部 |
| 低 | 端末内検索の強化 | アプリ・ショートカット・設定項目・電卓。ウェブ検索はしない | 可。連絡先などは許可が必要 | Kvaesitso、Niagara（Sesame 連携） |
| 低 | Shizuku 連携 | 許可した場合だけ、アプリごとのメモリ・CPU を表示 | 可。セットアップが重い | ほぼ無い |

### 入れないもの

- ウェブ検索、AI アシスタント、クラウド同期、広告、分析ツール。方針の「静か」と両立しない。
- 自動の並べ替え・自動のカテゴリ分け。plan.md の「手で配置したアイコンは自動で並べ替えない」を守る。頻度順は一覧と、別に有効にした領域に限る。
- 通知の内容を使う機能。件数だけを扱う方針を変えない。

### 次の一歩の案

1. システムモニターと透明性のページを、ホームの1ページまたはアプリ一覧の上部に試作する。表示中だけ1秒ごとに記録し、5分程度のグラフにする。
2. ウィジェットの負荷計測を、段階5で使った描画フレームと CPU 時間の測り方で作る。
3. フォルダー・アイコンパックを、段階6として順に足す。

## 参考資料

- [Nova Launcher is basically dead (9to5Google, 2025-09)](https://9to5google.com/2025/09/08/nova-launcher-shutting-down/)
- [Nova Launcher has a new lease on life, and that probably means ads are coming (9to5Google, 2026-01)](https://9to5google.com/2026/01/20/nova-launcher-new-owners-ads/)
- [Nova Launcher ads and trackers (Android Authority)](https://www.androidauthority.com/nova-launcher-ads-trackers-3637496)
- [Nova Launcher gets a new owner and ads too (Android Headlines, 2026-01)](https://www.androidheadlines.com/2026/01/nova-launcher-gets-a-new-owner-and-ads-too.html)
- [Nova Launcher Privacy Policy](https://novalauncher.com/privacy)
- [Android 17 stable rollout features (Android Authority)](https://www.androidauthority.com/android-17-stable-rollout-features-3675016/)
- [Android 17 Beta 1 Pixel Launcher UI changes (Android Central)](https://www.androidcentral.com/apps-software/android-os/android-17-beta-1-pixel-launcher-ui-changes)
- [Niagara Pro Features (公式ヘルプ)](https://help.niagaralauncher.app/article/40-niagara-pro-features)
- [Niagara Launcher Studio Update beta (TuttoAndroid, 2026-04)](https://www.tuttoandroid.net/news/2026/04/16/niagara-launcher-studio-update-beta-aprile-2026-novita-1149207/)
- [Smart Launcher 6 changelog (APKMirror)](https://www.apkmirror.com/?p=12957115)
- [Lawnchair 15 Beta 1 (公式ブログ)](https://lawnchair.app/blog/lawnchair-15-beta-1/)
- [Kvaesitso (F-Droid)](https://f-droid.org/en/packages/de.mm20.launcher2.release/)
- [Best minimalist Android launchers 2026 (Android Authority)](https://www.androidauthority.com/best-minimalist-android-launchers-2026-3709772/)
- [Android treats third-party launchers poorly, but this survey could help (Android Authority)](https://www.androidauthority.com/android-third-party-launchers-issues-survey-3481961/)
- [It's been five years since Google broke gestures in custom launchers (Android Police)](https://www.androidpolice.com/5-years-later-custom-launchers-are-still-a-mess-with-gesture-navigation/)
- [Lockdown AM.getRunningAppProcesses API (AOSP)](https://android.googlesource.com/platform/frameworks/base/+/aaa0fee)
- [NetworkStatsManager (Android Developers)](https://developer.android.com/reference/android/app/usage/NetworkStatsManager)
- [ActivityManager (Android Developers)](https://developer.android.com/reference/android/app/ActivityManager)
