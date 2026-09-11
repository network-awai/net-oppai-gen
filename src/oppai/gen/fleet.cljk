(ns oppai.gen.fleet
  "What the murakumo fleet can actually do right now, as data.

  Every model name here is *derived*, never typed. `GET
  https://api.murakumo.cloud/infer/model-map` is a live ComfyUI
  `/object_info/CheckpointLoaderSimple` scan of each Mac mini, so it reports
  the checkpoints that are on disk on a node this minute — which is the only
  honest answer to 'which image models can I pick?'. The repo-wide rule is the
  same one that governs the text side: model capability turns over fast, so a
  concrete model id must never be hardcoded as a default (ADR-2607173100).

  The fallback catalogue below exists for one reason: if the map is
  unreachable the picker must still offer something rather than render empty.
  It is explicitly marked `:fallback? true` so the UI can say so instead of
  pretending it knows."
  (:require [kotoba.lang.text :as str]))

(def model-map-url "https://api.murakumo.cloud/infer/model-map")
(def image-url "https://api.murakumo.cloud/v1/images/generations")
(def chat-url "https://api.murakumo.cloud/v1/chat/completions")
(def generation-url "https://generation.murakumo.cloud/api/v1/generation")

(def chat-model
  "The fleet-main ALIAS, not a concrete id. murakumo resolves it server-side,
  so switching the fleet's main model is one KV entry upstream and needs no
  deploy here (ADR-2607173100)."
  "murakumo-main")

(def image-kinds
  "`model-kind` values in the fleet map that mean 'text-to-image checkpoint'."
  #{"image"})

(def video-kinds
  "`model-kind` values that mean 'video'. `ltx-video` and `video` are both
  used upstream — LTX is reported under its own family name."
  #{"video" "ltx-video"})

(def fallback-image-models
  "Used only when the live map cannot be read. Sourced from the fleet registry
  (kotoba-lang/murakumo `infer.edn`), not invented."
  [{:model-id "animagine-xl-4.0" :label "Animagine XL 4.0" :fallback? true}
   {:model-id "wai-illustrious-sdxl-v150" :label "WAI Illustrious SDXL v1.5" :fallback? true}])

(def video-models
  "Douga models live on the generation job API (gad), which keeps its own
  allowlist — they are NOT in the Mac mini `model-map`, so unlike the image
  list this one cannot be derived from a live scan. Measured timings are from
  `cloud-murakumo/resources/video-engine-comparison.edn`."
  [{:model-id "ltx-2.3"
    :label "LTX 2.3"
    :hint "既定・実測 約140秒/クリップ。音声も出せます"
    :default? true}
   {:model-id "wan2.2-ti2v-5b"
    :label "Wan 2.2 TI2V 5B"
    :hint "実測 約135秒。画像を渡すと参照付き i2v になります"}
   {:model-id "wan-dancer-14b"
    :label "Wan Dancer 14B"
    :hint "ダンス i2v。重みの取得待ちで失敗することがあります"}])

(defn- humanize
  "`animagine-xl-4.0` -> `Animagine XL 4.0`-ish. A label, not an identity —
  `:model-id` stays the wire value."
  [id]
  (->> (str/split (str id) #"[-_]")
       (map (fn [part]
              (cond
                (re-matches #"(?i)xl|sdxl|svd|ltx|vae|ti2v" part) (str/upper part)
                (re-matches #"[0-9].*" part) part
                :else (str/capitalize part))))
       (str/join " ")))

(defn parse-model-map
  "Fleet map JSON (already keywordized) -> what the pickers need.

  Nodes are folded per model: the same checkpoint sits on several minis, and a
  user picking a model is not picking a machine. `:queue` is summed and
  `:nodes` kept so the UI can show where the work would land."
  [m]
  (let [media (or (:media m) [])
        by-kind (fn [kinds]
                  (->> media
                       (filter #(contains? kinds (str (:model-kind %))))
                       (group-by :model-id)
                       (map (fn [[id entries]]
                              {:model-id id
                               :label (humanize id)
                               :nodes (vec (sort (map :node entries)))
                               :checkpoint (:checkpoint (first entries))
                               :queue (reduce + 0 (keep :queue entries))
                               ;; `family-guess` means the node carries a
                               ;; checkpoint whose exact id upstream could not
                               ;; confirm — surface it rather than smoothing it
                               ;; over, because it is how a "registered as
                               ;; 0.9.1, actually 0.9.6" drift stays visible.
                               :exact? (= "exact" (:match (first entries)))}))
                       (sort-by :model-id)
                       vec))]
    {:image (by-kind image-kinds)
     :video (by-kind video-kinds)
     :text (:text m)
     :ts (:ts m)}))

(defn image-models
  "Live image models, or the marked fallback when the map gave us none."
  [parsed]
  (let [live (:image parsed)]
    (if (seq live) live fallback-image-models)))

(defn default-image-model [parsed]
  (:model-id (first (image-models parsed))))

(defn default-video-model []
  (:model-id (or (first (filter :default? video-models)) (first video-models))))

;; ---- image request ----------------------------------------------------------

(def image-sizes
  "SDXL-friendly sizes. The gateway parses `WxH` and defaults to 832x1216."
  [["1024x1024" "正方形 1024×1024"]
   ["832x1216" "縦長 832×1216"]
   ["1216x832" "横長 1216×832"]
   ["768x768" "小さめ 768×768"]])

(defn image-request
  "Body for `POST /v1/images/generations`.

  `model` goes on the wire as the bare model id; the gateway appends
  `.safetensors` itself. Sending the extension used to surface as a misleading
  'node became unreachable mid-render', so we deliberately do not."
  [{:keys [prompt model size negative seed]}]
  (cond-> {:prompt (str/trim (or prompt ""))
           :n 1
           :size (or size "1024x1024")}
    (seq model) (assoc :model model)
    (seq negative) (assoc :negative negative)
    (some? seed) (assoc :seed seed)))

(defn image-b64
  "The gateway returns base64, not a URL: `{:data [{:b64_json …}]}`."
  [resp]
  (some-> resp :data first :b64_json))

;; ---- video (douga) request --------------------------------------------------

(def video-frame-choices
  "`frames` must be 8n+1 in 9..121 upstream — offering free-form numbers would
  only let the user construct a 400."
  [[49 "約2秒（49コマ）"]
   [73 "約3秒（73コマ）"]
   [97 "約4秒（97コマ）"]
   [121 "約5秒（121コマ）"]])

(def video-sizes
  [["768x448" "横長 768×448（既定）"]
   ["512x512" "正方形 512×512"]
   ["448x768" "縦長 448×768"]])

(defn- parse-size [size fallback-w fallback-h]
  (if-let [[_ w h] (re-matches #"(\d+)x(\d+)" (str size))]
    [(parse-long w) (parse-long h)]
    [fallback-w fallback-h]))

(defn video-request
  "Body for `POST /api/v1/generation` with `type: \"video\"`.

  Upstream validates: prompt 1–2000 chars, width/height multiples of 32 in
  256–1280, frames 8n+1 in 9–121. We shape the request so those hold rather
  than letting the user discover them as a 400."
  [{:keys [prompt model size frames seed image]}]
  (let [[w h] (parse-size size 768 448)]
    {:type "video"
     :model (or model (default-video-model))
     :input (cond-> {:prompt (str/trim (or prompt ""))}
              (seq image) (assoc :image image))
     :params (cond-> {:width w :height h
                      :frames (or frames 49)}
               (some? seed) (assoc :seed seed))}))

(defn job-status
  "Normalize a job envelope, and replace every upstream URL with one that
  actually works from a browser.

  Both URLs the API hands back point at `murakumo.cloud`, which answers 401
  for these tokens — it is a different Worker with a different secret
  (measured: the artifact URL in a completed job returns 401, while the same
  path on `generation.murakumo.cloud` returns the mp4). And even the correct
  host needs the Bearer token, which the browser must not hold.

  So neither URL survives: the status URL is dropped (poll the host the job
  was submitted to), and the artifact URL becomes a same-origin path our own
  Worker proxies. Rendering the upstream URL would give the user a `<video>`
  that silently fails to load."
  [job]
  (let [id (or (:jobId job) (:job-id job) (:id job))
        artifacts (vec (or (:artifacts job) []))]
    {:job/id id
     :job/status (keyword (or (:status job) "queued"))
     :job/progress (or (:progress job) 0)
     :job/output-kind (or (:outputKind job) (:output-kind job))
     :job/artifacts artifacts
     :job/artifact-url (when (and (seq (str id)) (seq artifacts))
                         (str "/api/generation/jobs/" id "/artifact"))
     :job/error (or (:error job) (:message job))}))

(defn terminal? [job]
  (contains? #{:done :failed :cancelled} (:job/status job)))
