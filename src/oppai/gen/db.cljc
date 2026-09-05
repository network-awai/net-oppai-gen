(ns oppai.gen.db
  "Portable app state: shape, defaults, and the pure transitions. Everything
  here runs identically on the JVM, which is what the tests drive."
  (:require [clojure.string :as str]
            [oppai.gen.fleet :as fleet]))

(def tabs
  [{:id :chat  :label "チャット" :icon "◇"}
   {:id :image :label "画像"     :icon "▣"}
   {:id :douga :label "動画"     :icon "▶"}])

(def welcome
  (str "oppai.fans へようこそ。\n\n"
       "チャット・画像・動画のすべてを、Mac mini で動く murakumo フリートが生成します。"
       "外部の生成 API は経由しません。"))

(defn now-ms []
  #?(:cljs (.now js/Date) :clj (System/currentTimeMillis)))

(defn new-id [prefix]
  (str prefix "-" (now-ms) "-" (rand-int 1000000)))

;; ---- age gate ---------------------------------------------------------------

(def age-gate-key "oppai-age-ok")

(defn age-confirmed?
  "Pure predicate over app db. The persisted fact lives in localStorage
  (`oppai.gen.app` reads it at init); the db mirrors it so views stay a
  pure function of state. R18 surface: everything behind this gate."
  [db]
  (true? (:age-confirmed? db)))

(defn confirm-age [db]
  (assoc db :age-confirmed? true))

(defn initial-db []
  {:age-confirmed? false
   :tab :chat
   :fleet {:status :loading :image [] :video fleet/video-models :text nil}
   :chat {:messages [{:id (new-id "m") :role :assistant :content welcome}]
          :draft ""
          :streaming? false
          :streaming-id nil
          :stream-token 0
          :error nil}
   :image {:prompt "" :model nil :size "1024x1024" :negative ""
           :status :idle :b64 nil :started-at nil :elapsed-ms nil :error nil}
   :douga {:prompt "" :model (fleet/default-video-model) :size "768x448"
           :frames 49 :status :idle :job nil :error nil}})

;; ---- fleet ------------------------------------------------------------------

(defn fleet-loaded
  "Fold a parsed model map into the db and pick a default image model if the
  user has not chosen one. The video list is NOT taken from the map — douga
  runs on the generation job API, which keeps its own allowlist."
  [db parsed]
  (let [image (fleet/image-models parsed)]
    (-> db
        (assoc :fleet {:status :ready
                       :image image
                       :video fleet/video-models
                       :text (:text parsed)
                       :ts (:ts parsed)})
        (update-in [:image :model] #(or % (:model-id (first image)))))))

(defn fleet-failed [db err]
  (let [image fleet/fallback-image-models]
    (-> db
        (assoc :fleet {:status :fallback :image image
                       :video fleet/video-models :error (str err)})
        (update-in [:image :model] #(or % (:model-id (first image)))))))

;; ---- chat -------------------------------------------------------------------

(defn chat-messages-for-api [db]
  (->> (get-in db [:chat :messages])
       (remove #(str/blank? (str (:content %))))
       (mapv (fn [m] {:role (name (:role m)) :content (str (:content m))}))))

(defn begin-chat
  "Consume the draft, append the user turn and an empty streaming assistant
  turn. Returns [db assistant-id] — the id is how every later delta finds its
  message without searching by position."
  [db]
  (let [draft (str/trim (str (get-in db [:chat :draft])))]
    (if (str/blank? draft)
      [db nil]
      (let [uid (new-id "m")
            aid (new-id "m")]
        [(-> db
             (update-in [:chat :messages] conj
                        {:id uid :role :user :content draft}
                        {:id aid :role :assistant :content "" :streaming? true})
             (assoc-in [:chat :draft] "")
             (assoc-in [:chat :error] nil)
             (assoc-in [:chat :streaming?] true)
             (assoc-in [:chat :streaming-id] aid)
             (update-in [:chat :stream-token] inc))
         aid]))))

(defn- update-message [db id f]
  (update-in db [:chat :messages]
             (fn [ms] (mapv #(if (= id (:id %)) (f %) %) ms))))

(defn chat-delta [db id text]
  (update-message db id #(update % :content str text)))

(defn chat-done [db id]
  (-> db
      (update-message id #(dissoc % :streaming?))
      (assoc-in [:chat :streaming?] false)
      (assoc-in [:chat :streaming-id] nil)))

(defn chat-failed [db id message]
  (-> db
      (update-message id (fn [m] (-> m (dissoc :streaming?) (assoc :error? true))))
      (assoc-in [:chat :streaming?] false)
      (assoc-in [:chat :streaming-id] nil)
      (assoc-in [:chat :error] message)))

(defn chat-stopped [db id]
  (-> db
      (update-message id (fn [m] (-> m (dissoc :streaming?) (assoc :stopped? true))))
      (assoc-in [:chat :streaming?] false)
      (assoc-in [:chat :streaming-id] nil)
      (update-in [:chat :stream-token] inc)))

;; ---- image ------------------------------------------------------------------

(defn image-ready?
  "A generation run costs a real GPU minute on a real machine, so the button
  is only live when there is something to generate and nothing in flight."
  [db]
  (boolean
   (and (not= :running (get-in db [:image :status]))
        (seq (str/trim (str (get-in db [:image :prompt]))))
        (seq (str (get-in db [:image :model]))))))

(defn begin-image [db]
  (-> db
      (assoc-in [:image :status] :running)
      (assoc-in [:image :error] nil)
      (assoc-in [:image :b64] nil)
      (assoc-in [:image :elapsed-ms] nil)
      (assoc-in [:image :started-at] (now-ms))))

(defn image-done [db b64]
  (let [started (get-in db [:image :started-at])]
    (-> db
        (assoc-in [:image :status] :done)
        (assoc-in [:image :b64] b64)
        (assoc-in [:image :elapsed-ms] (when started (- (now-ms) started))))))

(defn image-failed [db message]
  (-> db
      (assoc-in [:image :status] :failed)
      (assoc-in [:image :error] message)))

;; ---- douga ------------------------------------------------------------------

(defn douga-ready? [db]
  (boolean
   (and (not= :running (get-in db [:douga :status]))
        (seq (str/trim (str (get-in db [:douga :prompt])))))))

(defn begin-douga [db]
  (-> db
      (assoc-in [:douga :status] :running)
      (assoc-in [:douga :error] nil)
      (assoc-in [:douga :job] nil)))

(defn douga-job [db job]
  (let [job (fleet/job-status job)]
    (cond-> (assoc-in db [:douga :job] job)
      (fleet/terminal? job)
      (assoc-in [:douga :status] (if (= :done (:job/status job)) :done :failed))

      (and (fleet/terminal? job) (:job/error job))
      (assoc-in [:douga :error] (:job/error job)))))

(defn douga-failed [db message]
  (-> db
      (assoc-in [:douga :status] :failed)
      (assoc-in [:douga :error] message)))
