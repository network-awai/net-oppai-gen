(ns oppai.gen.route
  "Views as data, addressed by fragment (ADR-2608080100: one document, one
  bundle, one mount; moving between screens changes state, not location).

  The nav is generated from `views`, so a view added here is a view in the
  nav — there is no second list to forget. `#models` / `#face` / … are the
  addresses; an unknown or empty fragment is the default studio."
  (:require [kotoba.lang.text :as str]))

(def views
  [{:id :image  :label "画像"     :icon "▣" :fragment "image"}
   {:id :douga  :label "動画"     :icon "▶" :fragment "douga"}
   {:id :face   :label "自分の顔" :icon "◉" :fragment "face"}
   {:id :models :label "モデル"   :icon "◈" :fragment "models"}
   {:id :works  :label "作品"     :icon "▤" :fragment "works"}
   {:id :chat   :label "チャット" :icon "◇" :fragment "chat"}])

(def default-view :image)

(defn fragment->view [fragment]
  (let [f (str/replace (str fragment) #"^#" "")]
    (or (some #(when (= f (:fragment %)) (:id %)) views)
        default-view)))

(defn view->fragment [view]
  (str "#" (or (some #(when (= view (:id %)) (:fragment %)) views)
               (:fragment (first (filter #(= default-view (:id %)) views))))))

(defn view? [x] (boolean (some #(= x (:id %)) views)))
