# run — oppai-scheduler story bot（tags.csv → 1h ストーリー投稿）— 2026-09-12

owner 指示: 「tags.csv のキーワードをベースに oppai.fans でキャラクターを
ストーリーベースに投稿する bot profile を作って、1h 単位で、murakumo.cloud を
使って生成」→「基本的に全て採用」→「実際に生成して、測定・最適化 loop を稼働」。

## 着地済み（Hermes profile `oppai-scheduler`、実測稼働）

正本は repo ではなく Hermes profile 側（測定 loop は repo に常駐しない方針:

- `~/.hermes/profiles/oppai-scheduler/scripts/story_tick.py` — 1 story =
  全レーン 1 job（real-mix / real-cn / anime / video-ltx）を seeded LCG
  （`job-seed` と同じ式）で決定論 compose、最も空いている node に ComfyUI
  `/prompt` で提出。boundary（guard/minor-terms + profile `:forbidden` と
  同じ語彙、単語 whole-token・句 substring）を提出前にローカル検査し、
  adult marker（`:adult-tags`）を必ず先頭に載せる。
- `scripts/sched_evidence.py` — receipts 48h の wall median/p90 per
  (lane, node)、queue 深度、zero-receipt hour（tick 停止検出）を実測し
  `MEASURE<TAB>key<TAB>value` 行と append-only ledger
  （`workspace/ledger.jsonl`）に出す。verdict は script が決定論的に出す。
- cron 2 本（profile-scope）: `oppai-story-hourly`（毎時 :00 evidence）+
  `oppai-story-submit`（毎時 :10 実提出、job `55e5ac578dda`）。手動 fire
  1 回 completed を実測。`SOUL.md` は propose-only・台帳手編集禁止・
  cap 超え禁止を明記。

## 実測（2026-09-12 14:30 UTC 前後）

- receipts 3,953 件（48h）、pass 3,950 / failed 3。実効 **164.7 works/h**。
- wall（median/p90, s）: real-mix benjamin 353/430、simeon 352/407、
  real-cn dan 351/410、joseph 350/410、anime zebulun 367/736、
  video-ltx asher 347/391・naphtali 349/407・issachar 361/718、
  eros gad 315/667。
- queue: 9 node 中 7 が常時 running=1（飽和）。TTS の judah/levi は
  ComfyUI 無し（CLI per job）なので queue probe 対象外。
- **06:00–16:00 UTC に receipt 欠落 10 時間**（zero-receipt hour 検出）。
  現在の cap=1 で 165 works/h → 欠落復活だけで +1,650 works/日。
- story 実提出 1 周成功（real-mix→simeon、real-cn→dan、anime→zebulun、
  video-ltx→naphtali、全て submitted、zebulun の queue で story prompt
  実走を確認）。2 周目は画像レーンが Studio bot 占有で skipped、
  video-ltx→issachar のみ提出成功（正しい cap 挙動）。

## 着地（この run で main に載せるもの）

- `docs/data/accepted_tags.txt` — tags.csv 213 語を gate 本体
  （`guard/minor?` + `forbidden-hit`）で分類した採用 202 語
  （2026-09-12 実測。worktree `_wt-fans-oppai-tags` は前 run closing で
  削除済みのため、Desktop と /tmp のミラーから復元）。
- `docs/data/dropped_tags.txt` — 棄却 11 語（minor 3: childbirth / loli /
  shota、forbidden 8: bestiality / incest / rape / reverse-rape / scat /
  student / tentacle / torture）。**`tentacle` は単数形が profile
  `:forbidden` に当たり、`tentacles`（複数形）は whole-token で通る** —
  語彙側の non-consent 文脈拒否と形態論の差で、採用語は全てこの gate
  実測に基づく。
- story bank の本体（`bots/oppai-studio.edn` への `:story` bank 追加）は
  **この run では未着地**。story_tick.py が profile EDN を読まず語彙を
  自前で持つ現状は、bank 正本が 2 箇所に存在する状態（下記 gap 1）。

## 残存 gap（優先順）

1. **story 語彙の正本一元化（高）**: story_tick.py の STORY_* bank を
   `bots/oppai-studio.edn` の `:bot/banks`（または `:story` key）に移し、
   script は EDN を読むだけにする。現状の 2 箇所は drift の温床。
   検証は `npm test`（validate-profile が bank を boundary で洗う）。
2. **story job の receipts 統合（中）**: story_tick.py は ComfyUI に直接
   提出するため Studio bot の pending.edn を経ず、receipt が stories の
   台帳（`workspace/story-ledger.jsonl`）にしか乗らない。Studio tick と
   pending 形式を共有するか、story_tick 自体が history を拾って receipt
   を書くか、どちらかに寄せる。
3. **欠落 10h の原因（中）**: 06:00–16:00 UTC の tick 停止は未調査。
   oppai-scheduler の zero_receipt_hour 検出が翌日以降も見張る。
4. **06Z producer 生成（前 run から引き継ぎ、中）**: 前回 run doc
   （docs/runs/2026-09-12-generation-tags.md gap 2）のまま。

## cleanup 状態

- 本 run は repo 側に worktree / branch を作っていない（profile 側が
  本体）。`docs/data/` の 2 ファイルのみ staging 済み。
- stash: 未使用。
