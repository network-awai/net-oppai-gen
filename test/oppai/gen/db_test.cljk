(ns oppai.gen.db-test
  (:require [clojure.test :refer [deftest is testing]]
            [oppai.gen.db :as db]
            [oppai.gen.fleet :as fleet]
            [oppai.gen.fleet-test :as ft]))

(deftest chat-turn-appends-a-user-turn-and-a-streaming-placeholder
  (let [d (assoc-in (db/initial-db) [:chat :draft] "  hello  ")
        [d' id] (db/begin-chat d)
        ms (get-in d' [:chat :messages])]
    (is (some? id))
    (is (= :user (:role (nth ms (- (count ms) 2)))))
    (is (= "hello" (:content (nth ms (- (count ms) 2)))) "the draft is trimmed")
    (is (true? (:streaming? (last ms))))
    (is (= "" (get-in d' [:chat :draft])))))

(deftest an-empty-draft-is-not-a-turn
  (let [[d' id] (db/begin-chat (assoc-in (db/initial-db) [:chat :draft] "   "))]
    (is (nil? id))
    (is (false? (get-in d' [:chat :streaming?])))))

(deftest deltas-accumulate-onto-the-right-message
  (let [[d id] (db/begin-chat (assoc-in (db/initial-db) [:chat :draft] "q"))
        d (-> d (db/chat-delta id "he") (db/chat-delta id "llo") (db/chat-done id))
        m (last (get-in d [:chat :messages]))]
    (is (= "hello" (:content m)))
    (is (nil? (:streaming? m)))
    (is (false? (get-in d [:chat :streaming?])))))

(deftest stopping-bumps-the-stream-token
  (testing "so a late frame from the stopped run cannot land in the next one"
    (let [[d id] (db/begin-chat (assoc-in (db/initial-db) [:chat :draft] "q"))
          before (get-in d [:chat :stream-token])
          d (db/chat-stopped d id)]
      (is (= (inc before) (get-in d [:chat :stream-token])))
      (is (true? (:stopped? (last (get-in d [:chat :messages]))))))))

(deftest fleet-load-picks-a-default-image-model-but-does-not-override-a-choice
  (let [parsed (fleet/parse-model-map ft/sample-map)]
    (is (= "animagine-xl-4.0" (get-in (db/fleet-loaded (db/initial-db) parsed)
                                      [:image :model])))
    (let [chosen (assoc-in (db/initial-db) [:image :model] "wai-illustrious-sdxl-v150")]
      (is (= "wai-illustrious-sdxl-v150"
             (get-in (db/fleet-loaded chosen parsed) [:image :model]))))))

(deftest an-unreachable-fleet-still-yields-a-usable-picker
  (let [d (db/fleet-failed (db/initial-db) "boom")]
    (is (= :fallback (get-in d [:fleet :status])))
    (is (seq (get-in d [:fleet :image])))
    (is (some? (get-in d [:image :model])))))

(deftest generation-is-gated-on-having-something-to-generate
  ;; A run costs a real GPU minute, so the button must not be live for an
  ;; empty prompt or while one is already in flight.
  (let [d (db/fleet-loaded (db/initial-db) (fleet/parse-model-map ft/sample-map))]
    (is (false? (db/image-ready? d)))
    (let [d (assoc-in d [:image :prompt] "a garden")]
      (is (true? (db/image-ready? d)))
      (is (false? (db/image-ready? (db/begin-image d)))))))

(deftest image-run-records-how-long-it-took
  (let [d (-> (db/initial-db)
              (assoc-in [:image :prompt] "x")
              db/begin-image
              (db/image-done "AAA"))]
    (is (= :done (get-in d [:image :status])))
    (is (= "AAA" (get-in d [:image :b64])))
    (is (number? (get-in d [:image :elapsed-ms])))))

(deftest a-terminal-job-ends-the-douga-run
  (let [d (db/begin-douga (assoc-in (db/initial-db) [:douga :prompt] "wave"))]
    (is (= :running (get-in (db/douga-job d {:jobId "j" :status "running" :progress 10})
                            [:douga :status])))
    (let [done (db/douga-job d {:jobId "j" :status "done" :progress 100
                                :artifacts [{:url "https://example.invalid/a.mp4"}]})]
      (is (= :done (get-in done [:douga :status])))
      ;; Same-origin, because the upstream URL is unusable from a browser
      ;; (wrong host -> 401; right host -> needs the Bearer token).
      (is (= "/api/generation/jobs/j/artifact"
             (get-in done [:douga :job :job/artifact-url]))))
    (let [failed (db/douga-job d {:jobId "j" :status "failed" :error "out of memory"})]
      (is (= :failed (get-in failed [:douga :status])))
      (is (= "out of memory" (get-in failed [:douga :error]))))))

(deftest messages-for-api-drop-the-empty-placeholder
  (let [[d _] (db/begin-chat (assoc-in (db/initial-db) [:chat :draft] "q"))
        api (db/chat-messages-for-api d)]
    (is (every? #(seq (:content %)) api))
    (is (every? string? (map :role api)) "roles go on the wire as strings")))

(deftest the-post-submit-db-is-what-goes-on-the-wire
  ;; Regression, found in production: taking the messages from the PRE-submit
  ;; db sends a conversation whose last (and, on the first turn, only) message
  ;; is the assistant welcome. Upstream answers 400 and every first message
  ;; failed. The post-submit db is the one that ends with the user's turn.
  (let [pre (assoc-in (db/initial-db) [:chat :draft] "こんにちは")
        [post _] (db/begin-chat pre)]
    (is (= "assistant" (:role (last (db/chat-messages-for-api pre))))
        "the pre-submit db ends on the assistant welcome — never send this")
    (is (= "user" (:role (last (db/chat-messages-for-api post)))))
    (is (= "こんにちは" (:content (last (db/chat-messages-for-api post)))))))
