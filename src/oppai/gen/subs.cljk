(ns oppai.gen.subs
  (:require [oppai.gen.db :as db]
            #?(:cljs [re-frame.core :as rf])))

#?(:cljs
   (do
     (rf/reg-sub :tab (fn [db _] (:tab db)))
     (rf/reg-sub :age-confirmed? (fn [db _] (db/age-confirmed? db)))
     (rf/reg-sub :fleet (fn [db _] (:fleet db)))
     (rf/reg-sub :fleet-image-models (fn [db _] (get-in db [:fleet :image])))
     (rf/reg-sub :fleet-video-models (fn [db _] (get-in db [:fleet :video])))
     (rf/reg-sub :fleet-text (fn [db _] (get-in db [:fleet :text])))
     (rf/reg-sub :chat (fn [db _] (:chat db)))
     (rf/reg-sub :chat-messages (fn [db _] (get-in db [:chat :messages])))
     (rf/reg-sub :chat-draft (fn [db _] (get-in db [:chat :draft])))
     (rf/reg-sub :chat-streaming? (fn [db _] (get-in db [:chat :streaming?])))
     (rf/reg-sub :chat-error (fn [db _] (get-in db [:chat :error])))
     (rf/reg-sub :image (fn [db _] (:image db)))
     (rf/reg-sub :douga (fn [db _] (:douga db)))))
