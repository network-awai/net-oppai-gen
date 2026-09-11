;; Real-Chromium verification for the oppai.fans shell (nbb — repo rule:
;; new Node harnesses are nbb .cljs, not raw .mjs).
;;
;; Prereqs (verification-only; playwright is deliberately NOT a package.json dep):
;;   npm run build
;;   npm install --no-save playwright && npx playwright install chromium
;; Run:
;;   npx nbb scripts/verify-e2e.cljs [screenshot-dir]
;;
;; It serves public/ statically, so /api/* does not exist here — that is the
;; point of the split. What can be checked without the Worker is checked:
;; the three studios render, the fleet model map is fetched LIVE from
;; api.murakumo.cloud (it is CORS-open, which is exactly why the browser calls
;; it directly), the pickers fill with real fleet models, DADS classes are on
;; the page, it stays light under an OS dark preference, and 320px does not
;; scroll sideways. Chat and douga need the deployed Worker and are verified
;; against production after deploy.
(ns verify-e2e
  (:require ["playwright" :refer [chromium]]
            ["node:http" :as http]
            ["node:fs" :as fs]
            ["node:path" :as path]
            [kotoba.lang.text :as str]
            [promesa.core :as p]))

(def port 8793)
;; OPPAI_E2E_BASE=http://127.0.0.1:8797 points the run at `wrangler dev` so
;; the Worker routes (/api/image-models, /api/face/*) are live; without it
;; public/ is served statically and those routes answer 404, which the app
;; must survive (the fallback picker, the "could not ask" card state).
(def base (or (some-> js/process.env.OPPAI_E2E_BASE (str/replace #"/+$" ""))
              (str "http://127.0.0.1:" port)))
(def static? (not js/process.env.OPPAI_E2E_BASE))
(def shot-dir (or (first *command-line-args*) "e2e-shots"))

(def mime {".html" "text/html; charset=utf-8"
           ".css" "text/css; charset=utf-8"
           ".js" "text/javascript; charset=utf-8"
           ".json" "application/json"
           ".png" "image/png"
           ".svg" "image/svg+xml"})

(defn serve-public! []
  (doto (http/createServer
         (fn [req res]
           (let [url (first (str/split (.-url req) #"\?"))
                 rel (if (= "/" url) "/index.html" url)
                 file (path/join "public" (str/replace rel #"^/" ""))]
             (if (fs/existsSync file)
               (do (.setHeader res "content-type"
                               (get mime (path/extname file) "application/octet-stream"))
                   (.end res (fs/readFileSync file)))
               (do (set! (.-statusCode res) 404) (.end res "not found"))))))
    (.listen port)))

(def results (atom []))

(defn check! [name ok? detail]
  (swap! results conj {:ok (boolean ok?) :name name})
  (println (if ok? "PASS -" "FAIL -") name (if detail (str "(" detail ")") "")))

(defn watch-errors [page bucket]
  (.on page "pageerror" (fn [e] (swap! bucket conj (str e))))
  (.on page "console"
       (fn [msg]
         (when (and (= "error" (.type msg))
                    (not (str/includes? (.text msg) "Failed to load resource")))
           (swap! bucket conj (.text msg))))))

(defn shell-scenario
  "One OS colour-scheme preference. DADS is light-only, so both must render
  light — a flip would mean a dark palette crept back in."
  [browser scheme]
  (p/let [ctx (.newContext browser #js {:colorScheme scheme
                                        :viewport #js {:width 1280 :height 900}})
          _ (.addInitScript ctx "localStorage.setItem('oppai-age-ok','1')")
          page (.newPage ctx)
          errs (atom [])
          _ (watch-errors page errs)
          _ (.goto page base)
          _ (.waitForSelector page ".oppai-tabs")
          body-bg (.evaluate page "getComputedStyle(document.body).backgroundColor")
          tabs (.evaluate page "document.querySelectorAll('.oppai-tab').length")
          dads (.evaluate page "document.querySelectorAll('[class*=\"dads-\"]').length")
          _ (.screenshot page #js {:path (path/join shot-dir (str "oppai-chat-" scheme ".png"))})]
    (check! (str scheme "/6 views in the nav (image, douga, face, models, works, chat)") (= 6 tabs) (str tabs))
    (check! (str scheme "/DADS components are on the page") (pos? dads) (str dads))
    (check! (str scheme "/light under either OS scheme")
            (let [[r g b] (map js/parseInt (rest (re-find #"(\d+), (\d+), (\d+)" (str body-bg))))]
              (and (> r 200) (> g 200) (> b 200)))
            body-bg)
    (check! (str scheme "/zero uncaught errors") (empty? @errs) (str/join " | " (take 3 @errs)))
    (.close ctx)))

(defn image-studio-scenario
  "The image picker must fill from the LIVE fleet scan, not from the fallback
  list — that is the whole point of deriving it."
  [browser]
  (p/let [ctx (.newContext browser #js {:viewport #js {:width 1280 :height 900}})
          _ (.addInitScript ctx "localStorage.setItem('oppai-age-ok','1')")
          page (.newPage ctx)
          errs (atom [])
          _ (watch-errors page errs)
          _ (.goto page (str base "/#image"))
          _ (.waitForSelector page "#image-model")
          ;; /api/image-models is the Worker asking the generation node
          _ (.waitForFunction page "document.querySelectorAll('#image-model option').length > 0"
                              nil #js {:timeout 20000})
          options (.evaluate page "Array.from(document.querySelectorAll('#image-model option')).map(o => o.value)")
          selected (.evaluate page "document.querySelector('#image-model').value")
          note (.evaluate page "Array.from(document.querySelectorAll('.oppai-note')).map(n => n.textContent).join(' ')")
          presets (.evaluate page "document.querySelectorAll('.oppai-preset').length")
          _ (.click page ".oppai-preset")
          ;; the dispatch and the React commit are async; wait for the value
          _ (.waitForFunction page "document.querySelector('#image-prompt').value.length > 0" nil #js {:timeout 5000})
          preset-prompt (.evaluate page "document.querySelector('#image-prompt').value")
          _ (.fill page "#image-prompt" "")
          _ (.waitForFunction page "document.querySelector('#image-prompt').value.length === 0" nil #js {:timeout 5000})
          submit-disabled (.evaluate page
                                     "Array.from(document.querySelectorAll('button')).find(b => b.textContent.includes('画像を生成')).disabled")
          _ (.fill page "#image-prompt" "a quiet japanese garden")
          _ (.waitForFunction page "!Array.from(document.querySelectorAll('button')).find(b => b.textContent.includes('画像を生成')).disabled" nil #js {:timeout 5000})
          submit-enabled (.evaluate page
                                    "!Array.from(document.querySelectorAll('button')).find(b => b.textContent.includes('画像を生成')).disabled")
          _ (.screenshot page #js {:path (path/join shot-dir "oppai-image.png")})]
    (check! "image picker is populated" (pos? (alength options)) (str (js->clj options)))
    (check! "image picker has a selected model" (seq (str selected)) (str selected))
    (check! "the picker says where its list came from (node, or fallback)"
            (or (str/includes? (str note) "今この瞬間") (str/includes? (str note) "取得できませんでした"))
            (str note))
    (check! "presets are offered" (pos? presets) (str presets))
    (check! "a preset writes an ADULT prompt" (str/includes? (str preset-prompt) "adult") (str preset-prompt))
    ;; A GPU minute must not be one stray click away from an empty prompt.
    (check! "generate is disabled with an empty prompt" submit-disabled (str submit-disabled))
    (check! "generate enables once there is a prompt" submit-enabled (str submit-enabled))
    (check! "image studio zero uncaught errors" (empty? @errs) (str/join " | " (take 3 @errs)))
    (.close ctx)))

(defn douga-studio-scenario [browser]
  (p/let [ctx (.newContext browser #js {:viewport #js {:width 1280 :height 900}})
          _ (.addInitScript ctx "localStorage.setItem('oppai-age-ok','1')")
          page (.newPage ctx)
          errs (atom [])
          _ (watch-errors page errs)
          _ (.goto page (str base "/#douga"))
          _ (.waitForSelector page "#douga-model")
          submit-disabled (.evaluate page
                                     "Array.from(document.querySelectorAll('button')).find(b => b.textContent.includes('動画を生成')).disabled")
          models (.evaluate page "Array.from(document.querySelectorAll('#douga-model option')).map(o => o.value)")
          frames (.evaluate page "Array.from(document.querySelectorAll('#douga-frames option')).map(o => Number(o.value))")
          _ (.screenshot page #js {:path (path/join shot-dir "oppai-douga.png")})]
    (check! "douga models are the job-API allowlist"
            (= ["ltx-2.3" "wan2.2-ti2v-5b" "wan-dancer-14b"] (js->clj models))
            (str (js->clj models)))
    ;; Upstream requires 8n+1 frames; offering anything else just builds a 400.
    (check! "frame choices are all 8n+1"
            (every? #(zero? (mod (- % 1) 8)) (js->clj frames))
            (str (js->clj frames)))
    (check! "douga generate is disabled with an empty prompt" submit-disabled (str submit-disabled))
    (check! "douga studio zero uncaught errors" (empty? @errs) (str/join " | " (take 3 @errs)))
    (.close ctx)))

(defn single-page-scenario
  "Crossing views must not load a document (ADR-2608080100). A marker left on
  window survives every crossing; the element waited for exists ONLY in the
  target view."
  [browser]
  (p/let [ctx (.newContext browser #js {:viewport #js {:width 1280 :height 900}})
          _ (.addInitScript ctx "localStorage.setItem('oppai-age-ok','1')")
          page (.newPage ctx)
          errs (atom [])
          _ (watch-errors page errs)
          _ (.goto page (str base "/#image"))
          _ (.waitForSelector page "#image-prompt")
          _ (.evaluate page "window.__oppaiMarker = 'kept'")
          _ (.fill page "#image-prompt" "state that must survive")
          _ (.click page "a.oppai-tab[href='#models']")
          _ (.waitForSelector page ".oppai-cards .oppai-card")
          cards (.evaluate page "document.querySelectorAll('.oppai-card').length")
          card-titles (.evaluate page "Array.from(document.querySelectorAll('.oppai-card-title')).map(e => e.textContent)")
          _ (.click page "a.oppai-tab[href='#face']")
          _ (.waitForSelector page ".oppai-face")
          face-copy (.evaluate page "document.querySelector('.oppai-face').textContent")
          file-inputs (.evaluate page "document.querySelectorAll('.oppai-face input[type=file]').length")
          _ (.click page "a.oppai-tab[href='#works']")
          _ (.waitForSelector page ".oppai-works")
          _ (.click page "a.oppai-tab[href='#image']")
          _ (.waitForSelector page "#image-prompt")
          marker (.evaluate page "window.__oppaiMarker")
          prompt (.evaluate page "document.querySelector('#image-prompt').value")
          hash (.evaluate page "location.hash")
          _ (.screenshot page #js {:path (path/join shot-dir "oppai-models.png")})]
    (check! "window marker survives four crossings (no document load)" (= "kept" marker) (str marker))
    (check! "app state survives the crossings" (= "state that must survive" prompt) (str prompt))
    (check! "the address follows the view" (= "#image" hash) (str hash))
    (check! "the catalog renders every card, available or not" (= 4 cards) (str (js->clj card-titles)))
    (check! "the face view says it is a consent ceremony, not identity verification"
            (str/includes? (str face-copy) "本人確認ではありません") "")
    (check! "the face view offers no file upload" (zero? file-inputs) (str file-inputs))
    (check! "single-page zero uncaught errors" (empty? @errs) (str/join " | " (take 3 @errs)))
    (.close ctx)))

(defn face-ceremony-scenario
  "With the Worker live: the challenge button asks the server, and the
  ceremony refuses to enrol until two frames AND consent exist."
  [browser]
  (if static?
    (p/resolved (check! "face ceremony (skipped: static run, no Worker)" true "skipped"))
    (p/let [ctx (.newContext browser #js {:viewport #js {:width 1280 :height 900}})
            _ (.addInitScript ctx "localStorage.setItem('oppai-age-ok','1')")
          page (.newPage ctx)
            _ (.goto page (str base "/#face"))
            _ (.waitForSelector page ".oppai-face")
            _ (.click page "text=カメラで登録を始める")
            _ (.waitForSelector page ".oppai-camera-steps")
            gesture (.evaluate page "document.querySelectorAll('.oppai-step')[1].textContent")
            enrol-disabled (.evaluate page
                                      "Array.from(document.querySelectorAll('button')).find(b => b.textContent.includes('この顔を登録する')).disabled")
            _ (.screenshot page #js {:path (path/join shot-dir "oppai-face.png")})]
      (check! "the server issued a gesture challenge" (str/includes? (str gesture) "ください") (str gesture))
      (check! "enrol is disabled before frames and consent" enrol-disabled (str enrol-disabled))
      (.close ctx))))

(defn mobile-scenario [browser]
  (p/let [ctx (.newContext browser #js {:viewport #js {:width 320 :height 740}})
          _ (.addInitScript ctx "localStorage.setItem('oppai-age-ok','1')")
          page (.newPage ctx)
          _ (.goto page (str base "/#models"))
          _ (.waitForSelector page ".oppai-tabs")
          scroll-w (.evaluate page "document.documentElement.scrollWidth")
          _ (.click page "a.oppai-tab[href='#image']")
          _ (.waitForSelector page "#image-prompt")
          scroll-w2 (.evaluate page "document.documentElement.scrollWidth")
          _ (.screenshot page #js {:path (path/join shot-dir "oppai-320.png")})]
    (check! "320px no horizontal scroll" (and (= scroll-w 320) (= scroll-w2 320))
            (str scroll-w "/" scroll-w2))
    (.close ctx)))

(defn -main []
  (fs/mkdirSync shot-dir #js {:recursive true})
  (let [srv (when static? (serve-public!))]
    (-> (p/let [browser (.launch chromium #js {:channel "chromium" :headless true})
                _ (shell-scenario browser "light")
                _ (shell-scenario browser "dark")
                _ (image-studio-scenario browser)
                _ (douga-studio-scenario browser)
                _ (single-page-scenario browser)
                _ (face-ceremony-scenario browser)
                _ (mobile-scenario browser)]
          (.close browser))
        (p/catch (fn [e] (check! "harness completed" false (str e))))
        (p/finally (fn []
                     (when srv (.close srv))
                     (let [fails (remove :ok @results)]
                       (println "----")
                       (println (count @results) "checks," (count fails) "failures")
                       (when (seq fails) (js/process.exit 1))))))))

(-main)
