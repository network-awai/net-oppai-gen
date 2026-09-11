# oppai.fans (fans-oppai)

**成人向け（R18）の画像・動画生成サービス — civitai / tensorhub 型のモデル
カタログと、本人の顔だけを使える顔参照つき。** `oppai.fans` の Worker + SPA。
生成そのものは**自社運用の murakumo フリート**（`generation.murakumo.cloud`
→ gad の ComfyUI）が担い、この repo は UI と capability token と
顔登録の儀式だけを持つ（外部の生成 API は経由しない）。

6 つの view（`#image` `#douga` `#face` `#models` `#works` `#chat`）を 1 文書・
1 バンドルで持つ single-page app（ADR-2608080100）。view は `oppai.gen.route`
の表が正で、nav はそこから生成される。

## 顔参照（faceswap ではない — 設計上の決定、2026-09-11）

「任意の写真の顔を載せる」機能は**作らない**。非同意の性的ディープフェイクの
製造装置になるので、これは機能の欠落ではなく境界である。代わりに:

- **登録できる顔は、いまカメラの前にいる本人の顔だけ。** ファイル入力は無い。
  サーバが出す 60 秒・single-use の nonce と、向き/表情のお題（liveness）に
  合わせて 2 枚撮り、明示的な同意にチェックして登録する（`oppai.gen.guard`）。
  これは**同意の儀式であって本人確認ではない** — UI もそう言う。
- 登録はこのブラウザにだけ紐づく（HttpOnly cookie → KV、7 日で消える、
  いつでも削除できる）。
- **ブラウザは `input.face` を送れない。** `input.face_ref true` を送り、Worker
  が登録済みの frame を差し替えて fleet に渡す。`input.face` を持ち込んだ
  request は 400。したがってこのサイト経由で fleet に届く顔は、儀式を通った
  ものだけ。
- fleet 側は pixel の貼り付けではなく **IP-Adapter（PLUS FACE / FaceID v2）
  による identity 条件付け**（`kotoba-lang/murakumo` の generation API、
  `type: image` + `input.face`）。生成物には `face-reference:<mode>` の
  capability が付く。

## R18 境界（サーバ側）

- **未成年を名指す語**（`guard/minor-terms`、日英・略語）を含む prompt は
  Worker が 400 で拒否する。合わせて**全 image job の negative prompt に
  `guard/minor-negative` を必ず追記**する。カタログの preset は全て成人を
  明示し、テストがそれを検査する。
- fleet 側（murakumo gateway）の検査は独立に存在し、ここは二重に持たない
  という従来の決定は、この 2 点（拒否 + negative）を足した上で据え置き。

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
| 画像 | Worker `/api/generation`（`type: image`、job）→ `generation.murakumo.cloud/api/v1/generation` | **2026-09-11 に browser 直の同期 `api.murakumo.cloud/v1/images/generations` から移した。** 同期 60〜100 秒は Worker の 100 秒天井に当たるが、job（submit → poll → artifact）には天井が無い。同じノードの同じ ComfyUI で、顔参照を運べる唯一の経路（無認証の公開 endpoint に顔を載せない） |
| モデル一覧 | Worker `/api/image-models` → `…/v1/generation/image-models` | **そのノードの** ComfyUI が今持つチェックポイント。fleet 全体の `/infer/model-map` は別の mini のディスクを報告し、gad に無いモデルを選ばせて 502 になっていた |
| 顔登録 | Worker `/api/face/challenge` `/api/face/enroll` `/api/face`（GET/DELETE） | 上記。KV `OPPAI_KV` |
| チャット | Worker `/api/chat` → `api.murakumo.cloud/v1/chat/completions` | ストリーミングなので天井に当たらない。実行タグ付けと濫用抑制を 1 箇所に置く |
| 動画 | Worker `/api/generation`（`type: video`）→ 同上 | 署名付き capability token（`MURAKUMO_GENERATION_TOKEN`）が要る。鍵はブラウザに置かない。`input.image` に data URI を渡すと i2v（作品棚の「動画にする」） |

「作品」（`#works`）は**この端末の localStorage** にある棚で、公開ギャラリーでは
ない。公開・共有・アカウント（SIWE + Passkey）・クレジット（x402/USDC）は
次の段（superproject ADR-2609111130 の gap 表）。

## studio bot — 11 台を 24 時間回す生成 bot（2026-09-11、ADR-2609117000）

`bots/oppai-studio.edn` が bot の **profile**（どの lane をどのノードで、どの語彙で、
どこまで明示的に、何を絶対に描かないか）。`oppai.gen.bot` が pure な半分
（validate / compose / assign / quality / graph）、`scripts/oppai_studio_tick.cljk`
が I/O の半分（ssh → ノードの ComfyUI loopback、mlx-audio CLI、gad の generation API
loopback、receipt ledger）。**モデルは呼ばない** —— prompt は seeded PRNG で bank から
組み、`oppai.gen.guard/minor?` と profile の `:forbidden` を、他人が書いた prompt と
同じ扱いで通す。adult marker を持たない prompt は組めない（throw）。

| lane | model | nodes | kind |
|---|---|---|---|
| `:real-mix` | waiREALMIX_v11 | benjamin, simeon | txt2img（SDXL、写実） |
| `:real-cn` | waiREALCN_v150 | dan, joseph | txt2img（SDXL、写実・アジア系） |
| `:anime` | waiIllustriousSDXL_v150 | zebulun | txt2img（Danbooru タグ） |
| `:video-ltx` | ltxv-2b-0.9.6-distilled | naphtali, issachar, asher | t2v 3 s |
| `:voice` | Qwen3-TTS 1.7B (mlx-audio, ono_anna) | judah, levi | 日本語の短い台詞 |
| `:eros-h3` | 10Eros-Max（MiniMax-H3 finetune） | gad | ref2va 動画、generation API 経由 |

fleet 側の宣言は `kotoba-lang/murakumo` の `fleet.edn` `:node/serves`（同日、16 GiB mini
10 台から text 推論を退避）。lane ↔ node の対応は
`test/oppai/gen/bot_test.cljk` の `placement-matches-fleet-declaration` が pin する。

```bash
npm run bot:tick -- --dry-run          # 何を投げるかだけ（fleet に触らない）
npm run bot:tick -- --only benjamin    # 1 ノードだけ
npm run bot:tick                       # 1 tick（collect → submit → receipts）
npm run bot:tick -- --report           # ledger の集計
```

状態は `~/.itonami/oppai-studio/`（`receipts.edn` は append-only の測定列）。
loop は `deploy/network.awai.bot.oppai-studio.plist`（5 分毎、install 手順は plist 内）。
生成物は**候補**で、`catalog/public-works` への昇格は人が行う（`:promote :manual`）。

実測 2026-09-11（初回 tick）: SDXL 832×1216 / 26 steps は 16 GiB M4 で **165 s/枚**
（cold、`Unloaded partially` を含む）。Qwen3-TTS は 4.4 s の台詞を 8 s（cold load 込み）。
LTX 704×480×73f は 5 分台。**SVD-XT は 16 GiB では 1 step 215〜280 s**（576×1024×24f、
UNet が常駐できない）で 1 clip 1.5 時間 —— asher は同日 LTX に切り替えた。
**ここに書いた値は当日の値**であって定数ではない —— `--report` が今日の値を持つ。

## 開発

**source は `.cljk` が正本**（2026-09-11、adr-2609111500-cljk-rename-all-clojure-source、
元の拡張子は `cljk-origin.edn`）。nbb も shadow-cljs もまだ `.cljk` を namespace として
解決できない（loader は root#3093 の提案段階）ので、`npm run cljk`
（`scripts/cljk_mirror.cljk`、単一ファイル）が `.cljk-build/` に元拡張子の派生 tree を
書き、test / build はそこを classpath にする。**派生 tree は編集も commit もしない**
（gitignore）。origin の記録が無い `.cljk` は拒否される（推測しない）。loader が着地したら
mirror・`.cljk-build`・deps.edn の `:paths` を元に戻す。

```bash
npm install
npm run dev          # shadow-cljs watch (http://localhost:8790)
npm test             # nbb（64 tests / 371 assertions、JVM を起こさない）
npm run e2e          # 実 Chromium。OPPAI_E2E_BASE=http://127.0.0.1:8797 で wrangler dev の Worker 経路も検査
clojure -M:local:lint   # clj-kondo。ここだけまだ JVM（clj-kondo は nbb で走らない）
npm run build        # release build + index.html 生成（hash 済 bundle 名）
npm run deploy       # build → wrangler deploy (oppai.fans)
```

`npm test` / `npm run generate-site` は nbb で走る（オーナー判断 2026-09-06、
`clojure -M` を JVM-free な経路へ置換）。classpath は package.json に書いた
相対パスで、monorepo の sibling checkout（`../../kotoba-lang/*`、`text` を
含む — nbb は deps.edn を読まないので、`kotoba.lang.text` へ移った日から
2026-09-11 まで `npm test` は main で赤だった）を指す ——
以前の `:local` alias と同じ前提。別の場所に置くなら
`OPPAI_RESOURCE_PATH` で dds.css の resource root を上書きする。

Secret（deploy 時に `wrangler secret put`。repo に置かない）:

- `MURAKUMO_GENERATION_TOKEN` — scope=generation の murakumo capability。
  無い場合、画像・動画スタジオは 503 とその理由を正直に表示する。
- KV binding `OPPAI_KV`（wrangler.jsonc）— 顔登録と challenge nonce。無い
  場合、顔登録は 503 とその理由を表示し、他は動く。
- `OPPAI_CHAT_MODEL` / `OPPAI_VIDEO_MODEL`（任意）— 未設定時は
  `murakumo-main` alias / クライアント選択（ADR-2607173100、model id を焼かない）。

## レイアウト

```
wrangler.jsonc          oppai.fans custom domain、ASSETS binding
src/oppai/gen/
  site.cljc             静的シェル生成（DADS inline、R18 head、gate CSS）
  route.cljc            view 表（nav と fragment の正本）
  catalog.cljc          モデルカード・preset（編集物。搭載状況は実行時に fleet から）
  guard.cljc            R18 境界 + 同意儀式の pure 関数（Worker が enforce、test が検査）
  db.cljc               pure state 遷移（age gate / face / works 含む）
  events.cljc / subs.cljc  re-frame
  views.cljc            6 view + age gate
  fleet.cljc            ノードの model 一覧解析 / リクエスト整形（model id は導出値のみ）
  net.cljs              browser I/O（カメラ・localStorage 含む）
  worker.cljs           /api/*（generation token proxy / image-models / face / chat / health）
test/oppai/gen/         nbb tests（gate / db / fleet / chat / site / guard / catalog / route）
scripts/                E2E・prod smoke
```

## Payments and dark default (2026-09-11)

The HTML itself selects dark, including browser color-scheme/theme-color,
so OS light mode and JavaScript startup no longer cause a light first paint.
The `#payments` view reads `/api/payments`. That endpoint fetches Murakumo's
live x402 offer, accepts only the Base-mainnet USDC v2 chat offer facilitated
by `https://x402.nexus`, and links to the existing Murakumo hosted checkout.
Wallet approval, settlement and the inference result remain on that checkout.
Discovery failure hides the checkout link; it never fabricates a price.

This is a hosted **text inference payment** integration, not image-credit
fulfillment. A settled text request must never increment an image balance.
No funds were transferred during verification.

Top-up investigation: Murakumo's `/api/store/checkout` responds, and its
Stripe webhook maps paid credit SKUs through `metadata.did` to
`/itonami/topup`. The generation gateway currently bills our service identity
`oppai-fans`; this site has no authenticated purchaser account or verified
mapping from checkout DID to that identity. The public x402 offer contains
chat/infer-memory resources, not image generation or a credit top-up SKU.
Before enabling image top-ups, establish the account mapping and authenticated
balance lookup, then verify webhook fulfillment and payment-event deduplication
against the same ledger used by generation. Do not credit on a redirect or
expose the shared generation token in the browser.

## Default free generation (2026-09-11)

`#image` now defaults to the owned-fleet anonymous Animagine XL 4.0 Preview.
No account, payment, or operator generation token is required. The local
`POST /api/free/image` validates explicit `public-examples-v1` consent and a
1–2000-character prompt, then forwards to the public Murakumo Preview API.
The upstream retains quotas and concurrency admission: currently 768×768,
5 attempts per network/UTC day and a shared capacity cap. Busy/limit failures
never fall back to a paid request. Free mode does not accept face references,
model overrides, negative-prompt or size settings; those remain in the
explicit credit-backed mode.

The donated commons document at `api.murakumo.cloud/v1/commons` describes the
reserved **text inference** floor. Images use the separately bounded owned
Preview lane; no reserved image-slot guarantee is claimed.

Live anonymous image generation returned price 0 in 84.577 seconds, artifact
`2b12b571-12dc-4770-bd08-ddd25716d539`. The PNG was hash-checked and visually
reviewed (garden, no people/private data) before inclusion as our curated
`public/img/free-garden.png` sample. Upstream review status remains pending;
this site's independent publication does not change Murakumo's moderation.
Prompts and private user-library entries are not added to the public gallery.

### waiREALMIX_v11 rollout

The free selector and canonical response handling also support waiREALMIX_v11.
Upstream registration is merged in cloud-murakumo PR #196 (main 14aa413).
Its focused Worker test confirms the exact checkpoint, zero price, remaining
quota, and pending artifact review. A real 768×768 image was generated by the
public owned gateway and visually reviewed; SHA256:
905e709c37335471a735ce48a9189957f617fe478be2ca43e3612cc32b39082b.

Live rollout completed using the existing Cloudflare OAuth session. The same
D1 deployment mutex was acquired atomically and released by its holder; the
clean-current-main deployment guard was retained. No lease signing secret
or login change was needed.

- Murakumo version: `8171920f-1971-404a-a8d0-bcfe067c4cb0` (main `6b5b281`).
- oppai.fans version: `d9d085dd-4c43-4380-9931-5d5d95ddea7b`.
- Live Preview model listing includes waiREALMIX_v11 with zero price.
- A full browser submission through oppai.fans displayed a WAI garden image
  in 28 seconds, with the canonical model name and free label.
- Public sample SHA256 matches the reviewed image above.
- Frontend: 69 tests / 389 assertions passed; both builds passed. Upstream
  free Preview and signed-credit integration tests passed on deployed main.
