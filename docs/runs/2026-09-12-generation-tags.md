# run — 生成タグの着地（shinshi 式タグを生成物に付ける）— 2026-09-12

owner 指示: 「https://oppai.fans/ で shinshi.club と同じような 生成タグも入れて生成して」
（shinshi.club と同じく、プロンプトを組んだ同じ語彙選択を生成タグとして生成物に添える）。

## 着地済み（origin/main に実在）

| commit | 内容 |
|---|---|
| `285ada9` | bot: 全ての compose が `:tags` を返す（bank 選択と同じ語彙。`:adult-tags` レーンは adult ピックが先頭、`:natural` レーンは `:adult-markers` から）。receipt・DRY 出力も載せる。境界検査（minor / forbidden / adult marker）をプロンプトと同じ `allowed?` でタグにも適用 |
| `7340de1` | main ↔ `payments/dark-checkout` 統合。producer bot（時間 1 リクエスト・`public-examples-v1` 同意・ネットワーク別無料枠）、フィードバック（D1 `migrations/0001_feedback.sql`）、free-video 経路、作品タグチップ表示、discovery/detail 遷移を壊さず取り込む。conflict 11 本は perl 多行正則で theirs（dark-checkout）10 本 / ours（run_tests.cljk 結合）1 本で解決、残骸 marker 0 本を確認 |
| `219abd1` | ビルド経路の修正。main の `dabcc41` が書いた `amu compile --target wasm32-browser app worker` は**一度も緑になっていない**（現行 amu CLI は entry ファイル + `--source-path` を取り、ディレクトリを拒否する — 実測: `source input must use .kotoba, .cljk, or .cljc`）。本番を建ててきた `shadow-cljs release app worker` を build/dev だけ復元。kbb は cljk / generate-site / test / e2e / smoke / bot:tick の全経路で維持 |
| `b8b56ae` | 上記 3 本の origin/main への着地（`gh api repos/network-awai/fans-oppai/merges`、force なし） |

## 検証（固定）

- **テスト**: `npm test` 96 テスト / 1956 assert 全緑（マージ前 93/1928 → dark-checkout 分 +3 テスト）。新規タグ検査 6 本: 全 bank エントリが非空 `:tags` / compose されたジョブが選択由来のタグを持つ / タグがプロンプトの選択から来る / タグだけが境界を破っても compose が拒否（理由 literal `"composed tags fail the boundary"` を pin）/ voice・SVD はタグなし。
- **本番ビルド**: app 123 files（52 compiled, 警告 0）/ worker 49 files（6 compiled, 警告 0）/ index.html 生成。
- **デプロイ**: 共有 checkout（新 main FF 同期）から `npm run deploy`。Worker Version `a5db8e1c-5814-4486-8c0b-0237f5bacc4d`。deploy guard は origin/main 包含で通過。
- **ライブ実測**: `https://oppai.fans/assets/main.77B97FC9.js` に `producer-20260912T04Z`（窓辺のニット・ポートレート、tags `["portrait" "sweater" "indoors" "window_light"]`、sha256 `202c79aa…`）と `"window_light"` が配信されている。タグチップ表示（`.oppai-work-tags`）も同 bundle。
- **実機 bot**: 共有 checkout 同期後の最初の tick（05:11Z）から全レーンの送信ジョブに `:tags` が付き、完了 receipt も `:tags [...]` を載せる（05:05Z 以前に compose された古いジョブは正当に `nil`）。`~/.itonami/oppai-studio/receipts.edn` 実測。

## 残存 gap（優先順）

1. **amu compile のモジュール指定（高）**: `dabcc41` のビルド書き換えは fans-oppai で実測失敗（entry ファイルが必要）。正しい amu 呼び出し（`.cljk` → wasm32-browser app/worker）を別 repo・別タスクで測って着地するまで、この repo の build は shadow-cljs に依存する。`219abd1` の commit message に開いている旨を記載済み。
2. **06Z producer 生成（中）**: 05Z リクエストは 1 POST のみ実施。upstream murakumo が `503 preview_unavailable`（"The model could not finish … no payment was taken"、request_id `439eaa66-244b-415c-be49-748732166054`、response.json 保存）。producer_io 規約「POST を再試行しない」に従い再試行していない。receipt は検査後に `failed` へ確定済み（証跡保存、次の時間枠を塞がない）。**次の最初の 1 コマンド**（UTC の次の時間枠で）:
   ```
   cd orgs/network-awai/fans-oppai && kbb --backend sci --classpath scripts \
     scripts/producer_tick.cljk ~/.local/state/oppai-producer/pending-06Z-request.json
   ```
   request 内容: animagine-xl-4.0、雪の夜の灯台守（成人 29 歳・完全に服着用・rating:general）、`publication_consent public-examples-v1`。`generated` なら PNG を目視レビュー → `public/img/producer-20260912T06Z.png` としてカタログ先頭に `:tags` 付きで公開（entry 形は `src/oppai/gen/catalog.cljk` の `producer-20260912T04Z` が実例）→ branch push + merges API で着地 → 共有 checkout 同期して `npm run deploy`。失敗・skip なら再試行せず報告する。
3. **コア配信は成立済み**: タグ付き作品は既にライブ（04Z）。06Z は追加の 1 件であり、失敗しても上記の達成を覆さない。

## cleanup 状態（この run の終了時点）

- worktree `orgs/network-awai/_wt-fans-oppai-tags` と branch `bot-generation-tags`（local + remote）は着地確認後に削除。untracked は node_modules / .cljk-build / ビルド成果物 / `workspace/`（タグ語彙の分類スクラッチ — 本体は `bots/oppai-studio.edn` に commit 済み）で、全て捨ててよい。
- stash: 本セッションは使用していない。
