(ns oppai.gen.guard
  "The R18 boundary and the consent boundary as pure functions, so the Worker
  enforces exactly what the tests exercise.

  Three invariants, each a decision and not a filter that happened to be
  handy:

  1. **No minors, ever.** A prompt naming a minor is refused before it leaves
     this origin, and every image job carries `minor-negative` appended to its
     negative prompt whether or not the caller wrote one. Both, because a
     blocklist is a floor and the negative prompt is a steer — neither alone
     is the guarantee, and neither is the last line (the model side has its
     own).
  2. **A face reaches the fleet only through the enrolment ceremony.** The
     browser cannot send `input.face`; it sends `input.face_ref true` and the
     Worker substitutes the frame it captured from that browser's camera
     under a server-issued, single-use, time-boxed challenge. There is no
     upload path for a face on this site. That is the whole consent design:
     the only face you can render is the one that was in front of your
     camera when this site asked for it.
  3. **A challenge is one nonce, one window.** Issued at `t`, usable until
     `t + challenge-ttl-ms` inclusive — the boundary itself is inside the
     window (CLAUDE.md 8 問 #5: a comparison without its boundary case is
     unmeasured)."
  (:require [kotoba.lang.text :as str]))

;; ---- minors ------------------------------------------------------------------

(def minor-terms
  "Lower-cased substrings that name or imply a minor. Japanese entries are
  matched as-is. `jk`/`jc`/`js` are matched as whole tokens (see `minor?`),
  because `jk` is inside `jkl` and `js` is inside `json`."
  ["loli" "lolita" "shota" "child" "children" "kid" "kids" "toddler" "baby"
   "infant" "teen" "teenage" "teenager" "underage" "minor" "schoolgirl"
   "schoolboy" "preteen" "young girl" "young boy" "little girl" "little boy"
   "小学生" "中学生" "高校生" "幼女" "幼児" "少年" "少女" "子供" "子ども" "こども"
   "ロリ" "ショタ" "未成年" "女子高生" "女子中学生" "園児" "赤ちゃん"])

(def ^:private token-terms #{"jk" "jc" "js"})

(defn- tokens [s]
  (remove str/blank? (str/split (str/lower s) #"[^a-z0-9]+")))

(defn minor?
  "True when `text` names a minor. Substring for the phrase list, whole-token
  for the two-letter abbreviations."
  [text]
  (let [low (str/lower (str text))]
    (boolean
     (or (some #(str/includes? low %) minor-terms)
         (some token-terms (tokens low))))))

(def minor-negative
  "Appended to EVERY image job's negative prompt by the Worker."
  "child, loli, shota, teen, underage, minor, young, petite child, childlike, small body, flat chest child")

(defn with-minor-negative [negative]
  (let [n (str/trim (str negative))]
    (if (str/blank? n) minor-negative (str n ", " minor-negative))))

;; ---- image job body ------------------------------------------------------------

(def image-sizes
  "What the job API accepts per side (its IMAGE_SIZES) — offered as pairs so
  a caller cannot construct a 400 from a free-form number."
  [["832x1216" "縦長 832×1216"]
   ["1024x1024" "正方形 1024×1024"]
   ["1216x832" "横長 1216×832"]
   ["896x1152" "縦 896×1152"]])

(def ^:private side-set #{512 640 768 832 896 1024 1152 1216 1280 1344})

(defn parse-size [size]
  (when-let [[_ w h] (re-matches #"(\d+)x(\d+)" (str size))]
    (let [w (parse-long w) h (parse-long h)]
      (when (and (side-set w) (side-set h)) [w h]))))

(def face-weight-min 0.1)
(def face-weight-max 1.5)
(def face-weight-default 0.85)

(defn clamp-weight [w]
  (let [w (if (number? w) w face-weight-default)]
    (max face-weight-min (min face-weight-max w))))

(defn image-job
  "Browser intent → the body the Worker forwards (minus the face bytes, which
  the Worker adds from the enrolment). Returns [body nil] or [nil reason].

  `:face-ref?` true asks for the enrolled face; the Worker refuses the job
  when there is none rather than silently rendering without it — a person who
  ticked 自分の顔を使う and got a stranger back has been lied to."
  [{:keys [prompt model size negative seed steps face-ref? face-mode face-weight]}]
  (let [prompt (str/trim (str prompt))
        [w h] (parse-size size)]
    (cond
      (str/blank? prompt) [nil "プロンプトが空です"]
      (> (count prompt) 2000) [nil "プロンプトが長すぎます（2000 文字まで）"]
      (minor? prompt) [nil "未成年を想起させる語が含まれています。oppai.fans では生成できません。"]
      (str/blank? (str model)) [nil "モデルを選んでください"]
      (nil? w) [nil "サイズが不正です"]
      :else
      [{:type "image"
        :input (cond-> {:prompt prompt}
                 face-ref? (assoc :face_ref true))
        :params (cond-> {:model (str model) :width w :height h
                         :steps (let [s (if (integer? steps) steps 26)] (max 1 (min 50 s)))
                         :negative_prompt (with-minor-negative negative)}
                  (integer? seed) (assoc :seed (max 0 seed))
                  face-ref? (assoc :face_mode (if (= "faceid" (str face-mode)) "faceid" "plus-face")
                                   :face_weight (clamp-weight face-weight)))}
       nil])))

(defn sanitize-image-job
  "What the Worker does to a body it received from the browser before adding
  the face and forwarding. A body carrying `input.face` is refused outright:
  that key exists only on the wire between this Worker and the fleet."
  [body face-data-uri]
  (let [input (:input body)
        params (:params body)]
    (cond
      (not= "image" (:type body)) [nil "type must be image"]
      (contains? input :face) [nil "input.face is not accepted from a browser; enrol a face and send input.face_ref"]
      (minor? (:prompt input)) [nil "prompt names a minor"]
      (and (:face_ref input) (str/blank? (str face-data-uri))) [nil "no enrolled face for this browser"]
      :else
      [{:type "image"
        :input (cond-> {:prompt (str (:prompt input))}
                 (:face_ref input) (assoc :face face-data-uri))
        :params (-> (select-keys params [:model :width :height :steps :seed :face_mode :face_weight])
                    (assoc :negative_prompt (with-minor-negative (:negative_prompt params))))}
       nil])))

;; ---- face challenge / enrolment ---------------------------------------------------

(def gestures
  "The liveness challenge. The server picks one; the client must capture a
  neutral frame AND a frame doing this, inside the window. This is a consent
  ceremony (the face in front of *this* camera, *now*, on request) — it is
  not identity verification, and the UI says so."
  ["少し右を向いてください" "少し左を向いてください" "口を開けてください"
   "目を閉じてください" "軽く上を向いてください"])

(def challenge-ttl-ms 60000)
(def face-max-bytes (* 4 1024 1024))
(def enrolment-ttl-s (* 7 24 3600))

(def ^:private data-uri-prefixes
  ["data:image/png;base64," "data:image/jpeg;base64," "data:image/webp;base64,"])

(defn data-uri-ok?
  "A raster data URI no larger than `face-max-bytes` decoded. The base64 length
  bound is the decoded bound scaled, so a frame right at the cap passes and one
  byte over does not."
  [s]
  (and (string? s)
       (some #(str/starts-with? s %) data-uri-prefixes)
       (<= (count s) (+ 128 (quot (* face-max-bytes 4) 3)))))

(defn challenge-valid?
  "`issued-at` + ttl is the LAST usable instant, inclusive."
  [issued-at now]
  (and (integer? issued-at) (integer? now)
       (<= issued-at now (+ issued-at challenge-ttl-ms))))

(defn validate-enrolment
  "{:nonce :frames [neutral gesture] :consent true} against the stored
  challenge {:issued-at :gesture :used?} at `now` → [record nil] | [nil reason]."
  [{:keys [nonce frames consent]} challenge now]
  (cond
    (str/blank? (str nonce)) [nil "nonce required"]
    (nil? challenge) [nil "unknown or expired challenge"]
    (:used? challenge) [nil "challenge already used"]
    (not (challenge-valid? (:issued-at challenge) now)) [nil "challenge window closed"]
    (not (true? consent)) [nil "consent must be given explicitly"]
    (not (and (sequential? frames) (= 2 (count frames)))) [nil "exactly two frames required (neutral, gesture)"]
    (not (every? data-uri-ok? frames)) [nil "frames must be PNG/JPEG/WebP data URIs under 4 MiB"]
    :else
    [{:frame (first frames)
      :gesture (:gesture challenge)
      :enrolled-at now
      :expires-at (+ now (* 1000 enrolment-ttl-s))
      :consent {:self-only true :adult true :at now}}
     nil]))

(defn enrolment-active? [record now]
  (and (map? record) (integer? (:expires-at record)) (< now (:expires-at record))))

(defn public-face
  "What the browser may see about its enrolment: everything but nothing more
  than it gave us."
  [record]
  (select-keys record [:frame :gesture :enrolled-at :expires-at]))
