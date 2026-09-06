# oppai.fans (net-oppai-gen)

**成人向け（R18）の画像・動画生成サービス。** `oppai.fans` の Worker + SPA。
生成そのものは**自社運用の murakumo Mac mini フリート**（`api.murakumo.cloud` /
`generation.murakumo.cloud`）が担い、この repo は UI と capability token の
保持だけを持つ（外部の生成 API は経由しない）。

雛形は `network-awai/network-awai-apex`（awai.network）と同じ skin
（jp-go-dds = デジタル庁デザインシステム / DADS）で、**コードは共有していない**
— apex と同じ理由（`= 同じ見た目の別 surface`、実装は独立）。

## R18 境界（この repo 特有の不変条件）

- **age gate が全画面の前**。`oppai.gen.db/age-confirmed?` が false の間、
  スタジオ・モデル一覧・`/api/*` 呼び出し UI は一切描画されない
  （`localStorage["oppai-age-ok"] = "1"` で永続化）。
- 生成シェル（`public/index.html`）は `rating` meta（RTA ラベル）+
  `noindex, noarchive`。スタジオは JS-only なので検索対象にしない。
- 禁止コンテンツ（実在人物の肖像、未成年を想起させる内容、違法コンテンツ）
  は gate 画面と README で明示。**サーバ側の prompt 検査は murakumo 側
  （`cloud-murakumo` gateway）の管轄**で、この Worker は二重に持たない。

## どこへ何を投げるか — apex と同じ分岐（意図的）

| surface | 経路 | なぜ |
|---|---|---|
| 画像 | ブラウザ → 直接 `api.murakumo.cloud/v1/images/generations` | 無認証・CORS 全開。同期 60〜100 秒の GPU 処理を Cloudflare の 100 秒 subrequest 天井に通すと 524 になるので proxy しない |
| モデル一覧 | ブラウザ → 直接 `api.murakumo.cloud/infer/model-map` | 同上（読み取り専用）。ComfyUI の実機スキャン結果をそのまま出す |
| チャット | Worker `/api/chat` → `api.murakumo.cloud/v1/chat/completions` | ストリーミングなので天井に当たらない。実行タグ付けと濫用抑制を 1 箇所に置く |
| 動画 | Worker `/api/generation` → `generation.murakumo.cloud/api/v1/generation` | 署名付き capability token（`MURAKUMO_GENERATION_TOKEN`）が要る。鍵はブラウザに置かない |

## 開発

```bash
npm install
npm run dev          # shadow-cljs watch (http://localhost:8790)
npm test             # nbb（41 tests / 162 assertions、JVM を起こさない）
clojure -M:local:lint   # clj-kondo。ここだけまだ JVM（clj-kondo は nbb で走らない）
npm run build        # release build + index.html 生成（hash 済 bundle 名）
npm run deploy       # build → wrangler deploy (oppai.fans)
```

`npm test` / `npm run generate-site` は nbb で走る（オーナー判断 2026-09-06、
`clojure -M` を JVM-free な経路へ置換）。classpath は package.json に書いた
相対パスで、monorepo の sibling checkout（`../../kotoba-lang/*`）を指す ——
以前の `:local` alias と同じ前提。別の場所に置くなら
`OPPAI_RESOURCE_PATH` で dds.css の resource root を上書きする。

Secret（deploy 時に `wrangler secret put`。repo に置かない）:

- `MURAKUMO_GENERATION_TOKEN` — scope=generation の murakumo capability。
  無い場合、動画スタジオは 503 とその理由を正直に表示する。
- `OPPAI_CHAT_MODEL` / `OPPAI_VIDEO_MODEL`（任意）— 未設定時は
  `murakumo-main` alias / クライアント選択（ADR-2607173100、model id を焼かない）。

## レイアウト

```
wrangler.jsonc          oppai.fans custom domain、ASSETS binding
src/oppai/gen/
  site.cljc             静的シェル生成（DADS inline、R18 head、gate CSS）
  db.cljc               pure state 遷移（age gate 含む、JVM test 対象）
  events.cljc / subs.cljc  re-frame
  views.cljc            3 スタジオ + age gate
  fleet.cljc            model-map 解析 / リクエスト整形（model id は導出値のみ）
  net.cljs              browser I/O（上記の分岐表の実装）
  worker.cljs           /api/*（chat proxy / generation token proxy / health）
test/oppai/gen/         JVM tests（gate / db / fleet / chat / site）
scripts/                E2E・prod smoke
```
