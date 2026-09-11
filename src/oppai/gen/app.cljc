(ns oppai.gen.app
  (:require [oppai.gen.events]
            [oppai.gen.subs]
            [oppai.gen.views :as views]
            #?(:cljs [re-frame.core :as rf])
            #?(:cljs [reagent.core :as r])
            #?(:cljs [reagent.dom.client :as rdom])))

#?(:cljs (defonce root (atom nil)))

#?(:cljs
   (defn mount! []
     (when-let [el (.getElementById js/document "app")]
       (reset! root (rdom/create-root el))
       (.render @root (r/as-element [views/app])))))

#?(:cljs
   (defn init []
     (rf/dispatch-sync [:app/initialize])
     (.addEventListener js/window "hashchange"
                        (fn [_] (rf/dispatch [:ui/fragment js/location.hash])))
     (mount!)))

#?(:clj (defn init [] nil))
