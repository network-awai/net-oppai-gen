(ns oppai.gen.net
  "Browser-side I/O. The routing decision per surface is deliberate and
  differs, so it is written down here rather than inferred from the code:

  - **Fleet map and image generation go straight to `api.murakumo.cloud`.**
    Those routes are unauthenticated and send `Access-Control-Allow-Origin:
    *`, so there is nothing for a proxy to add — and one thing it would take
    away: an image render is a measured 60–100 s of synchronous GPU work,
    while Cloudflare cuts a non-streaming Worker subrequest at 100 s and
    returns an HTML 524. Proxying image generation would convert a slow
    success into an unfixable timeout. The browser has no such limit.

  - **Chat goes through our own `/api/chat`.** It streams, so the 100 s
    ceiling does not apply, and routing it through the Worker gives the app
    one place to tag runs and to bound abuse later.

  - **Douga goes through our own `/api/generation`.** That API is gated by a
    signed capability token which the browser must never hold."
  (:require [oppai.gen.chat :as chat]
            [oppai.gen.fleet :as fleet]
            [clojure.string :as str]))

(defonce ^:private chat-controller (atom nil))

(defn- json-body [x] (js/JSON.stringify (clj->js x)))

(defn- ->clj [x] (js->clj x :keywordize-keys true))

(defn- error-text
  "A message a person can act on. `resp.statusText` alone is noise; the body
  usually carries the real reason, so it is preferred when present."
  [status body]
  (let [detail (or (get-in body [:error :message])
                   (:error body)
                   (:message body))]
    (if (seq (str detail))
      (str detail " (HTTP " status ")")
      (str "リクエストが失敗しました (HTTP " status ")"))))

(defn- fetch-json!
  [url {:keys [method body on-ok on-error]}]
  (-> (js/fetch url (clj->js (cond-> {:method (or method "GET")}
                               body (assoc :headers {"content-type" "application/json"}
                                           :body (json-body body)))))
      (.then (fn [resp]
               (-> (.text resp)
                   (.then (fn [text]
                            (let [parsed (try (->clj (js/JSON.parse text))
                                              (catch :default _ nil))]
                              (if (.-ok resp)
                                (on-ok parsed)
                                (on-error (error-text (.-status resp) parsed)))))))))
      (.catch (fn [e] (on-error (str "ネットワークに到達できません: " e))))))

;; ---- fleet ------------------------------------------------------------------

(defn fetch-fleet! [{:keys [on-ok on-error]}]
  (fetch-json! fleet/model-map-url
               {:on-ok #(on-ok (fleet/parse-model-map %))
                :on-error on-error}))

;; ---- chat -------------------------------------------------------------------

(defn abort-chat! []
  (when-let [c @chat-controller]
    (.abort c)
    (reset! chat-controller nil)))

(defn stream-chat!
  "POST /api/chat and feed SSE deltas out one at a time.

  The reasoning-trace strip runs on the accumulated text, not per delta: a
  `<think>` tag can straddle two network chunks, and stripping per chunk would
  leak the halves."
  [{:keys [messages on-delta on-done on-error]}]
  (abort-chat!)
  (let [controller (js/AbortController.)
        _ (reset! chat-controller controller)
        emitted (atom "")
        raw (atom "")
        push! (fn []
                ;; Emit only the newly-visible suffix so the caller can append.
                (let [visible (chat/strip-think @raw)]
                  (when (> (count visible) (count @emitted))
                    (on-delta (subs visible (count @emitted)))
                    (reset! emitted visible))))]
    (-> (js/fetch "/api/chat"
                  #js {:method "POST"
                       :headers #js {"content-type" "application/json"}
                       :signal (.-signal controller)
                       :body (json-body (chat/request-body {:messages messages
                                                            :stream? true}))})
        (.then
         (fn [resp]
           (if-not (.-ok resp)
             (-> (.text resp)
                 (.then (fn [t]
                          (on-error (error-text (.-status resp)
                                                (try (->clj (js/JSON.parse t))
                                                     (catch :default _ nil)))))))
             (let [reader (.getReader (.-body resp))
                   decoder (js/TextDecoder.)
                   buffer (atom "")]
               (letfn [(pump []
                         (-> (.read reader)
                             (.then
                              (fn [chunk]
                                (if (.-done chunk)
                                  (do (push!) (reset! chat-controller nil) (on-done))
                                  (do
                                    (swap! buffer str (.decode decoder (.-value chunk)
                                                               #js {:stream true}))
                                    (let [[lines remainder] (chat/split-frames @buffer)]
                                      (reset! buffer remainder)
                                      (doseq [line lines]
                                        (let [payload (chat/sse-payload line)]
                                          (cond
                                            (nil? payload) nil
                                            (= chat/done payload) nil
                                            :else
                                            (when-let [d (some-> (try (->clj (js/JSON.parse payload))
                                                                      (catch :default _ nil))
                                                                 chat/delta-from-chunk)]
                                              (swap! raw str d))))))
                                    (push!)
                                    (pump)))))))]
                 (pump))))))
        (.catch (fn [e]
                  (reset! chat-controller nil)
                  ;; An abort is the user pressing Stop, not a failure.
                  (when-not (= "AbortError" (.-name e))
                    (on-error (str "チャットに失敗しました: " e))))))))

;; ---- image ------------------------------------------------------------------

(defn generate-image!
  [{:keys [prompt model size negative on-ok on-error]}]
  (fetch-json! fleet/image-url
               {:method "POST"
                :body (fleet/image-request {:prompt prompt :model model
                                            :size size :negative negative})
                :on-ok (fn [resp]
                         (if-let [b64 (fleet/image-b64 resp)]
                           (on-ok b64)
                           (on-error "生成は成功しましたが画像が返りませんでした")))
                :on-error on-error}))

;; ---- douga ------------------------------------------------------------------

(defn submit-douga!
  [{:keys [prompt model size frames on-ok on-error]}]
  (fetch-json! "/api/generation"
               {:method "POST"
                :body (fleet/video-request {:prompt prompt :model model
                                            :size size :frames frames})
                :on-ok on-ok
                :on-error on-error}))

(def ^:private poll-interval-ms
  "A video run is minutes of GPU time (LTX measured ~140 s/clip), so polling
  faster buys nothing and just multiplies requests."
  4000)

(defn poll-douga!
  [{:keys [job-id on-ok on-error]}]
  (when (seq (str job-id))
    (js/setTimeout
     (fn []
       (fetch-json! (str "/api/generation/jobs/" (js/encodeURIComponent job-id))
                    {:on-ok on-ok :on-error on-error}))
     poll-interval-ms)))

(defn data-uri [b64] (str "data:image/png;base64," (str/trim (str b64))))
