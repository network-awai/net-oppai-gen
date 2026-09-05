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
            [clojure.string :as str]
            [promesa.core :as p]))

(def port 8793)
(def base (str "http://127.0.0.1:" port))
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
          page (.newPage ctx)
          errs (atom [])
          _ (watch-errors page errs)
          _ (.goto page base)
          _ (.waitForSelector page ".oppai-tabs")
          body-bg (.evaluate page "getComputedStyle(document.body).backgroundColor")
          tabs (.evaluate page "document.querySelectorAll('.oppai-tab').length")
          dads (.evaluate page "document.querySelectorAll('[class*=\"dads-\"]').length")
          _ (.screenshot page #js {:path (path/join shot-dir (str "oppai-chat-" scheme ".png"))})]
    (check! (str scheme "/3 studios in the tab bar") (= 3 tabs) (str tabs))
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
          page (.newPage ctx)
          errs (atom [])
          _ (watch-errors page errs)
          _ (.goto page base)
          _ (.click page "text=画像")
          _ (.waitForSelector page "#image-model")
          ;; the fleet fetch is a real network call to api.murakumo.cloud
          _ (.waitForFunction page "document.querySelectorAll('#image-model option').length > 0"
                              nil #js {:timeout 20000})
          options (.evaluate page "Array.from(document.querySelectorAll('#image-model option')).map(o => o.value)")
          selected (.evaluate page "document.querySelector('#image-model').value")
          note (.evaluate page "document.querySelector('.oppai-note').textContent")
          submit-disabled (.evaluate page
                                     "Array.from(document.querySelectorAll('button')).find(b => b.textContent.includes('画像を生成')).disabled")
          _ (.fill page "#image-prompt" "a quiet japanese garden")
          submit-enabled (.evaluate page
                                    "!Array.from(document.querySelectorAll('button')).find(b => b.textContent.includes('画像を生成')).disabled")
          _ (.screenshot page #js {:path (path/join shot-dir "oppai-image.png")})]
    (check! "image picker is populated" (pos? (alength options)) (str (js->clj options)))
    (check! "image picker has a selected model" (seq (str selected)) (str selected))
    (check! "image models came from the live fleet scan"
            (str/includes? (str note) "実機スキャン")
            (str note))
    ;; A GPU minute must not be one stray click away from an empty prompt.
    (check! "generate is disabled with an empty prompt" submit-disabled (str submit-disabled))
    (check! "generate enables once there is a prompt" submit-enabled (str submit-enabled))
    (check! "image studio zero uncaught errors" (empty? @errs) (str/join " | " (take 3 @errs)))
    (.close ctx)))

(defn douga-studio-scenario [browser]
  (p/let [ctx (.newContext browser #js {:viewport #js {:width 1280 :height 900}})
          page (.newPage ctx)
          errs (atom [])
          _ (watch-errors page errs)
          _ (.goto page base)
          _ (.click page "text=動画")
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

(defn mobile-scenario [browser]
  (p/let [ctx (.newContext browser #js {:viewport #js {:width 320 :height 740}})
          page (.newPage ctx)
          _ (.goto page base)
          _ (.waitForSelector page ".oppai-tabs")
          scroll-w (.evaluate page "document.documentElement.scrollWidth")
          _ (.click page "text=画像")
          _ (.waitForSelector page "#image-prompt")
          scroll-w2 (.evaluate page "document.documentElement.scrollWidth")
          _ (.screenshot page #js {:path (path/join shot-dir "oppai-320.png")})]
    (check! "320px no horizontal scroll" (and (= scroll-w 320) (= scroll-w2 320))
            (str scroll-w "/" scroll-w2))
    (.close ctx)))

(defn -main []
  (fs/mkdirSync shot-dir #js {:recursive true})
  (let [srv (serve-public!)]
    (-> (p/let [browser (.launch chromium #js {:channel "chromium" :headless true})
                _ (shell-scenario browser "light")
                _ (shell-scenario browser "dark")
                _ (image-studio-scenario browser)
                _ (douga-studio-scenario browser)
                _ (mobile-scenario browser)]
          (.close browser))
        (p/catch (fn [e] (check! "harness completed" false (str e))))
        (p/finally (fn []
                     (.close srv)
                     (let [fails (remove :ok @results)]
                       (println "----")
                       (println (count @results) "checks," (count fails) "failures")
                       (when (seq fails) (js/process.exit 1))))))))

(-main)
