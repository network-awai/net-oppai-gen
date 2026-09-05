(ns oppai.gen.worker
  "The oppai.fans Worker.

  It exists to hold exactly one thing the browser must not: the murakumo
  generation capability token. Everything else it does is routing.

  What deliberately does NOT pass through here:

  - **Image generation.** `api.murakumo.cloud/v1/images/generations` is
    unauthenticated and CORS-open, and a render is a measured 60–100 s of
    synchronous GPU work. Cloudflare terminates a non-streaming Worker
    subrequest at 100 s with an HTML 524, so proxying it would turn slow
    successes into unfixable timeouts. The browser calls it directly.
  - **The fleet model map.** Same reasoning minus the timing: read-only,
    unauthenticated, CORS-open. A proxy would add a hop and nothing else.

  What does pass through:

  - `/api/chat` — streamed, so no 100 s ceiling, and one place to bound abuse.
  - `/api/generation` + `/api/generation/jobs/:id` — token-gated upstream.

  Privacy: no prompt, completion or artifact is stored or logged here. This
  Worker is a pipe."
  (:require [clojure.string :as str]
            [goog.object :as gobj]))

(def ^:private chat-upstream "https://api.murakumo.cloud/v1/chat/completions")

(def ^:private generation-upstream
  "The generation API and the token that opens it are host-specific: the same
  token answers 401 on `murakumo.cloud`, which is a different Worker with a
  different secret. Jobs are also polled here, NOT at the `statusUrl` the API
  returns — that URL points at `murakumo.cloud` and would 401."
  "https://generation.murakumo.cloud/api/v1/generation")

(def ^:private default-model "murakumo-main")

(def ^:private max-messages 40)
(def ^:private max-chars 24000)
(def ^:private max-output-tokens 2048)

(defn- env-str [env k]
  (let [v (gobj/get env k)]
    (when (and (string? v) (not (str/blank? v))) (str/trim v))))

(defn- chat-model [env]
  (or (env-str env "OPPAI_CHAT_MODEL") default-model))

(defn- video-model [env]
  (env-str env "OPPAI_VIDEO_MODEL"))

(defn- json-response [body status]
  (js/Response. (js/JSON.stringify (clj->js body))
                #js {:status status
                     :headers #js {"content-type" "application/json"
                                   "cache-control" "no-store"}}))

(defn- error-response [type message status]
  (json-response {:error {:type type :message message}} status))

(defn- same-origin?
  "Requests must come from this site. There is no cross-origin use case: the
  browser bundle is served from the same Worker, and an open proxy in front of
  a paid GPU fleet is an invitation."
  [request]
  (let [origin (.get (.-headers request) "origin")
        host (.-host (js/URL. (.-url request)))]
    (or (nil? origin)
        (try (= host (.-host (js/URL. origin))) (catch :default _ false)))))

;; ---- chat -------------------------------------------------------------------

(defn- sanitize-chat
  "Take only the fields our own client sends, clamp them, and force the model.
  Never forwards a caller-supplied model id or arbitrary upstream parameters."
  [raw model]
  (let [messages (gobj/get raw "messages")]
    (cond
      (not (array? messages)) [nil "messages must be an array"]
      (zero? (alength messages)) [nil "messages must not be empty"]
      (> (alength messages) max-messages) [nil (str "too many messages (max " max-messages ")")]
      (> (reduce (fn [acc m] (+ acc (count (str (gobj/get m "content"))))) 0 messages)
         max-chars)
      [nil (str "conversation too long (max " max-chars " characters)")]
      :else
      [#js {:model model
            :messages messages
            :stream (boolean (gobj/get raw "stream"))
            :max_tokens (min (or (gobj/get raw "max_tokens") max-output-tokens)
                             max-output-tokens)
            :temperature (let [t (gobj/get raw "temperature")]
                           (if (number? t) (max 0 (min 2 t)) 0.7))
            ;; Load-bearing: without it the hybrid-thinking fleet main spends
            ;; the whole budget on reasoning and returns empty content.
            :chat_template_kwargs #js {:enable_thinking false}}
       nil])))

(defn- proxy-chat! [request env]
  (-> (.json request)
      (.then (fn [raw]
               (let [[body err] (sanitize-chat raw (chat-model env))]
                 (if err
                   (error-response "invalid_request" err 400)
                   (-> (js/fetch chat-upstream
                                 #js {:method "POST"
                                      :headers #js {"content-type" "application/json"
                                                    ;; Tags the run for fleet
                                                    ;; demand accounting.
                                                    "x-murakumo-app" "oppai-fans"}
                                      :body (js/JSON.stringify body)})
                       (.then (fn [resp]
                                (let [headers (js/Headers.)]
                                  (.set headers "content-type"
                                        (or (.get (.-headers resp) "content-type")
                                            "application/json"))
                                  (.set headers "cache-control" "no-store")
                                  (js/Response. (.-body resp)
                                                #js {:status (.-status resp)
                                                     :headers headers})))))))))
      (.catch (fn [e] (error-response "upstream_error" (str e) 502)))))

;; ---- generation (douga) -----------------------------------------------------

(defn- with-token
  "Run `f` with the generation capability token, or answer 503 honestly.

  A deployment without the secret is a real state — say so, rather than
  letting the user watch a spinner that can never finish."
  [env f]
  (if-let [token (env-str env "MURAKUMO_GENERATION_TOKEN")]
    (f token)
    (js/Promise.resolve
     (error-response "not_configured"
                     "動画生成はこのデプロイでは未設定です (MURAKUMO_GENERATION_TOKEN)"
                     503))))

(defn- forward!
  [url token {:keys [method body]}]
  (-> (js/fetch url (clj->js (cond-> {:method (or method "GET")
                                      :headers {"authorization" (str "Bearer " token)
                                                "content-type" "application/json"}}
                               body (assoc :body body))))
      (.then (fn [resp]
               (-> (.text resp)
                   (.then (fn [text]
                            (js/Response. text
                                          #js {:status (.-status resp)
                                               :headers #js {"content-type" "application/json"
                                                             "cache-control" "no-store"}}))))))
      (.catch (fn [e] (error-response "upstream_error" (str e) 502)))))

(defn- submit-generation! [request env]
  (with-token env
    (fn [token]
      (-> (.json request)
          (.then (fn [raw]
                   (when-let [model (video-model env)]
                     (gobj/set raw "model" model))
                   (forward! generation-upstream token
                             {:method "POST"
                              :body (js/JSON.stringify raw)})))
          (.catch (fn [e] (error-response "invalid_request" (str e) 400)))))))

(defn- job-status! [job-id env]
  (with-token env
    (fn [token]
      (forward! (str generation-upstream "/jobs/" (js/encodeURIComponent job-id))
                token
                {}))))

(defn- job-artifact!
  "Stream the finished artifact through.

  The URL the job envelope carries points at `murakumo.cloud` and answers 401
  (different Worker, different secret); the working host still needs the
  Bearer token, which the browser must not hold. So the bytes come through
  here. Streamed rather than buffered — an mp4 has no business sitting in
  Worker memory — and cached immutably, because a finished artifact is
  content-addressed and never changes."
  [job-id env]
  (with-token env
    (fn [token]
      (-> (js/fetch (str generation-upstream "/jobs/" (js/encodeURIComponent job-id)
                         "/artifact")
                    #js {:headers #js {"authorization" (str "Bearer " token)}})
          (.then (fn [resp]
                   (let [headers (js/Headers.)]
                     (.set headers "content-type"
                           (or (.get (.-headers resp) "content-type")
                               "application/octet-stream"))
                     (.set headers "cache-control"
                           (if (.-ok resp) "public, max-age=31536000, immutable" "no-store"))
                     (when-let [h (.get (.-headers resp) "x-content-sha256")]
                       (.set headers "x-content-sha256" h))
                     (js/Response. (.-body resp)
                                   #js {:status (.-status resp) :headers headers}))))
          (.catch (fn [e] (error-response "upstream_error" (str e) 502)))))))

;; ---- health -----------------------------------------------------------------

(defn- health [env]
  (json-response
   {:ok true
    :service "oppai-fans"
    :chat {:upstream chat-upstream :model (chat-model env)}
    :image {:direct-from-browser true
            :upstream "https://api.murakumo.cloud/v1/images/generations"}
    :douga {:configured (boolean (env-str env "MURAKUMO_GENERATION_TOKEN"))
            :model (or (video-model env) "client-selected")
            :upstream generation-upstream}}
   200))

;; ---- routing ----------------------------------------------------------------

(def ^:private job-path #"^/api/generation/jobs/([A-Za-z0-9_-]{1,128})$")
(def ^:private artifact-path #"^/api/generation/jobs/([A-Za-z0-9_-]{1,128})/artifact$")

(defn fetch-handler [request env _ctx]
  (let [url (js/URL. (.-url request))
        path (.-pathname url)
        method (.-method request)]
    (cond
      (and (= "/api/health" path) (= "GET" method))
      (health env)

      (str/starts-with? path "/api/")
      (if-not (same-origin? request)
        (error-response "forbidden_origin" "this endpoint serves oppai.fans only" 403)
        (cond
          (= "/api/chat" path)
          (if (= "POST" method)
            (proxy-chat! request env)
            (error-response "method_not_allowed" "POST only" 405))

          (= "/api/generation" path)
          (if (= "POST" method)
            (submit-generation! request env)
            (error-response "method_not_allowed" "POST only" 405))

          (re-matches job-path path)
          (if (= "GET" method)
            (job-status! (second (re-matches job-path path)) env)
            (error-response "method_not_allowed" "GET only" 405))

          (re-matches artifact-path path)
          (if (= "GET" method)
            (job-artifact! (second (re-matches artifact-path path)) env)
            (error-response "method_not_allowed" "GET only" 405))

          :else
          (error-response "not_found" "no such endpoint" 404)))

      :else
      (.fetch (gobj/get env "ASSETS") request))))

(def default (clj->js {:fetch fetch-handler}))
