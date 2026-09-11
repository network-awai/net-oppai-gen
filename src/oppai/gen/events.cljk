(ns oppai.gen.events
  "re-frame events. Every db transition delegates to `oppai.gen.db` so the
  logic is testable without a browser; the handlers here only wire effects."
  (:require [oppai.gen.db :as db]
            #?(:cljs [oppai.gen.net :as net])
            #?(:cljs [re-frame.core :as rf])))

#?(:cljs
   (do
     (rf/reg-event-fx
      :app/initialize
      ;; R18 surface: the age gate is the first thing the app resolves. The
      ;; persisted answer lives in localStorage under `db/age-gate-key`; a
      ;; fresh visitor sees the gate, a returning one goes straight in.
      (fn [_ _]
        (let [confirmed (= js/localStorage.getItem db/age-gate-key "1")]
          {:db (cond-> (db/initial-db)
                 confirmed (db/confirm-age))
           :fx (when confirmed [[:fleet/fetch nil]])})))

     (rf/reg-event-fx
      :age/confirm
      (fn [{:keys [db]} _]
        (js/localStorage.setItem db/age-gate-key "1")
        {:db (db/confirm-age db)
         :fx [[:fleet/fetch nil]]}))

     (rf/reg-event-db :ui/tab (fn [db [_ tab]] (assoc db :tab tab)))

     ;; ---- fleet -------------------------------------------------------------

     (rf/reg-fx :fleet/fetch
                (fn [_]
                  (net/fetch-fleet!
                   {:on-ok #(rf/dispatch [:fleet/loaded %])
                    :on-error #(rf/dispatch [:fleet/failed %])})))

     (rf/reg-event-db :fleet/loaded (fn [db [_ parsed]] (db/fleet-loaded db parsed)))
     (rf/reg-event-db :fleet/failed (fn [db [_ err]] (db/fleet-failed db err)))

     ;; ---- chat --------------------------------------------------------------

     (rf/reg-event-db :chat/draft-set (fn [db [_ v]] (assoc-in db [:chat :draft] v)))

     (rf/reg-event-fx
      :chat/submit
      (fn [{:keys [db]} _]
        (let [[db' id] (db/begin-chat db)]
          (if-not id
            {:db db'}
            ;; db' — the db AFTER the user turn was appended. Taking the
            ;; messages from the pre-submit db sends a conversation whose last
            ;; (and, on the first turn, only) message is the assistant
            ;; welcome, which upstream rejects with a 400. Found live: every
            ;; first message in production failed. The empty streaming
            ;; placeholder db' also carries is filtered out by
            ;; chat-messages-for-api, so db' is the correct source.
            {:db db'
             :fx [[:chat/stream {:messages (db/chat-messages-for-api db')
                                 :id id
                                 :token (get-in db' [:chat :stream-token])}]]}))))

     (rf/reg-fx
      :chat/stream
      (fn [{:keys [messages id token]}]
        (net/stream-chat!
         {:messages messages
          ;; The token guards against a late frame from a stopped or
          ;; superseded run landing in the current message. Without it,
          ;; pressing Stop and sending again interleaves two replies.
          :on-delta (fn [text] (rf/dispatch [:chat/delta id token text]))
          :on-done (fn [] (rf/dispatch [:chat/done id token]))
          :on-error (fn [err] (rf/dispatch [:chat/failed id token err]))})))

     (rf/reg-event-db
      :chat/delta
      (fn [db [_ id token text]]
        (if (= token (get-in db [:chat :stream-token]))
          (db/chat-delta db id text)
          db)))

     (rf/reg-event-db
      :chat/done
      (fn [db [_ id token]]
        (if (= token (get-in db [:chat :stream-token])) (db/chat-done db id) db)))

     (rf/reg-event-db
      :chat/failed
      (fn [db [_ id token err]]
        (if (= token (get-in db [:chat :stream-token]))
          (db/chat-failed db id (str err))
          db)))

     (rf/reg-event-fx
      :chat/stop
      (fn [{:keys [db]} _]
        (let [id (get-in db [:chat :streaming-id])]
          (cond-> {:db (if id (db/chat-stopped db id) db)}
            true (assoc :fx [[:chat/abort nil]])))))

     (rf/reg-fx :chat/abort (fn [_] (net/abort-chat!)))

     ;; ---- image -------------------------------------------------------------

     (rf/reg-event-db :image/set (fn [db [_ k v]] (assoc-in db [:image k] v)))

     (rf/reg-event-fx
      :image/submit
      (fn [{:keys [db]} _]
        (if-not (db/image-ready? db)
          {:db db}
          {:db (db/begin-image db)
           :fx [[:image/generate (select-keys (:image db)
                                              [:prompt :model :size :negative])]]})))

     (rf/reg-fx
      :image/generate
      (fn [params]
        (net/generate-image!
         (assoc params
                :on-ok #(rf/dispatch [:image/done %])
                :on-error #(rf/dispatch [:image/failed %])))))

     (rf/reg-event-db :image/done (fn [db [_ b64]] (db/image-done db b64)))
     (rf/reg-event-db :image/failed (fn [db [_ err]] (db/image-failed db (str err))))

     ;; ---- douga -------------------------------------------------------------

     (rf/reg-event-db :douga/set (fn [db [_ k v]] (assoc-in db [:douga k] v)))

     (rf/reg-event-fx
      :douga/submit
      (fn [{:keys [db]} _]
        (if-not (db/douga-ready? db)
          {:db db}
          {:db (db/begin-douga db)
           :fx [[:douga/generate (select-keys (:douga db)
                                              [:prompt :model :size :frames])]]})))

     (rf/reg-fx
      :douga/generate
      (fn [params]
        (net/submit-douga!
         (assoc params
                :on-ok #(rf/dispatch [:douga/job %])
                :on-error #(rf/dispatch [:douga/failed %])))))

     (rf/reg-event-fx
      :douga/job
      (fn [{:keys [db]} [_ job]]
        (let [db' (db/douga-job db job)
              status (get-in db' [:douga :job :job/status])]
          (cond-> {:db db'}
            ;; Keep polling until the job reaches a terminal state. A video
            ;; run is 2+ minutes of real GPU time, so this is a slow poll, not
            ;; a spin.
            (contains? #{:queued :running} status)
            (assoc :fx [[:douga/poll (get-in db' [:douga :job :job/id])]])))))

     (rf/reg-fx
      :douga/poll
      (fn [job-id]
        (net/poll-douga!
         {:job-id job-id
          :on-ok #(rf/dispatch [:douga/job %])
          :on-error #(rf/dispatch [:douga/failed %])})))

     (rf/reg-event-db :douga/failed (fn [db [_ err]] (db/douga-failed db (str err))))))
