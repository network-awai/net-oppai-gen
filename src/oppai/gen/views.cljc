(ns oppai.gen.views
  "The oppai.fans surface: three studios (チャット / 画像 / 動画) over one
  murakumo fleet, rendered in the デジタル庁デザインシステム through the single
  `oppai.gen.ui` seam. Presentation only — state lives in re-frame."
  (:require [clojure.string :as str]
            [oppai.gen.db :as db]
            [oppai.gen.fleet :as fleet]
            [oppai.gen.ui :as ui]
            #?(:cljs [oppai.gen.net :as net])
            #?(:cljs [re-frame.core :as rf])
            #?(:cljs [reagent.core :as r])))

;; ---- shared -----------------------------------------------------------------

(defn- error-banner [message]
  (when (seq (str message))
    (ui/notification {:type :error :heading "エラー" :class "oppai-error"}
                     [:p (str message)])))

#?(:cljs
   (defn fleet-note
     "Where the model list came from. A picker that silently falls back to a
     hardcoded list while the fleet is unreachable is lying by omission."
     []
     (let [{:keys [status error]} @(rf/subscribe [:fleet])]
       (case status
         :loading [:p {:class "oppai-note"} "フリートの構成を確認しています…"]
         :fallback [:p {:class "oppai-note is-warning"}
                    (str "フリート一覧を取得できませんでした（" error
                         "）。既知のモデルを表示しています。")]
         [:p {:class "oppai-note"}
          "モデル一覧は murakumo フリートの実機スキャン結果です。"]))))

;; ---- chat -------------------------------------------------------------------

#?(:cljs
   (defn chat-message [{:keys [id role content streaming? error? stopped?]}]
     ^{:key id}
     [:div {:class (str "oppai-message " (name role)
                        (when error? " is-error"))}
      [:div {:class "oppai-message-inner"}
       (ui/avatar (if (= role :user) "あ" "op")
                  {:class (if (= role :user) "oppai-avatar-user" "oppai-avatar-bot")})
       [:div {:class "oppai-message-body"}
        [:div {:class "oppai-role"} (if (= role :user) "あなた" "oppai")]
        (ui/panel
         (list
          ^{:key :text} [:div {:class "oppai-plain"} content]
          (when (and streaming? (str/blank? (str content)))
            ^{:key :spin} (ui/progress {:label "生成中"}))
          (when stopped? ^{:key :stop} [:div {:class "oppai-note"} "停止しました"]))
         {:class (str "oppai-bubble" (when (= role :user) " is-user"))})]]]))

#?(:cljs
   (defn chat-studio []
     (let [el (atom nil)]
       (r/create-class
        {:component-did-update
         (fn [_] (when-let [n @el] (set! (.-scrollTop n) (.-scrollHeight n))))
         :reagent-render
         (fn []
           (let [messages @(rf/subscribe [:chat-messages])
                 draft @(rf/subscribe [:chat-draft])
                 streaming? @(rf/subscribe [:chat-streaming?])
                 error @(rf/subscribe [:chat-error])
                 text @(rf/subscribe [:fleet-text])
                 can-send? (and (not streaming?) (seq (str/trim (or draft ""))))]
             [:section {:class "oppai-studio oppai-chat" :aria-label "チャット"}
              [:div {:class "oppai-thread" :ref #(reset! el %) :aria-live "polite"}
               (map chat-message messages)]
              (error-banner error)
              [:form {:class "oppai-composer"
                      :on-submit (fn [e]
                                   (.preventDefault e)
                                   (when can-send? (rf/dispatch [:chat/submit])))}
               (ui/text-area
                {:value draft
                 :rows 1
                 :placeholder "メッセージを入力…"
                 :aria-label "メッセージ"
                 :class "oppai-composer-input"
                 :disabled streaming?
                 ;; dispatch-sync keeps the controlled input in step with the
                 ;; keystroke: under React 18 createRoot an async dispatch plus
                 ;; RAF-batched re-render reverts the DOM to a stale value and
                 ;; drops fast typing.
                 :on-change #(rf/dispatch-sync [:chat/draft-set (.. % -target -value)])
                 :on-key-down (fn [e]
                                (when (and (= "Enter" (.-key e))
                                           (not (.-shiftKey e))
                                           (not (.-isComposing e)))
                                  (.preventDefault e)
                                  (when can-send? (rf/dispatch [:chat/submit]))))})
               (if streaming?
                 (ui/button "停止" {:variant :secondary :class "oppai-send"}
                            #(rf/dispatch [:chat/stop]))
                 (ui/button "送信"
                            (cond-> {:variant :primary :type "submit" :class "oppai-send"}
                              (not can-send?) (assoc :disabled true))))]
              [:p {:class "oppai-note"}
               "Enter で送信 · Shift+Enter で改行"
               (when-let [m (:model-id text)] (str " · モデル " m))]]))}))))

;; ---- image ------------------------------------------------------------------

#?(:cljs
   (defn image-studio []
     (let [{:keys [prompt model size negative status b64 elapsed-ms error]}
           @(rf/subscribe [:image])
           models @(rf/subscribe [:fleet-image-models])
           busy? (= :running status)
           ;; A run occupies a real GPU for over a minute, so the control is
           ;; live only when there is actually something to generate.
           ready? (and (not busy?)
                       (seq (str/trim (str prompt)))
                       (seq (str model)))
           set-str (fn [k] (fn [e] (rf/dispatch [:image/set k (.. e -target -value)])))]
       [:section {:class "oppai-studio oppai-image" :aria-label "画像生成"}
        [:div {:class "oppai-form"}
         (ui/field {:label "プロンプト" :for "image-prompt"
                    :support "生成したい画像を日本語または英語で書いてください。"
                    :requirement "必須" :required? true}
                   (ui/text-area {:id "image-prompt" :rows 3 :value prompt
                                  :disabled busy?
                                  :class "oppai-full"
                                  :on-change (set-str :prompt)}))
         [:div {:class "oppai-form-row"}
          (ui/field {:label "モデル" :for "image-model"
                     :support "murakumo Mac mini フリートに実際に載っているチェックポイントです。"}
                    (ui/select {:id "image-model" :value model :disabled busy?
                                :class "oppai-full"
                                :on-change (set-str :model)}
                               (mapv ui/model-option models)))
          (ui/field {:label "サイズ" :for "image-size"}
                    (ui/select {:id "image-size" :value size :disabled busy?
                                :class "oppai-full"
                                :on-change (set-str :size)}
                               fleet/image-sizes))]
         (ui/field {:label "除外したい要素" :for "image-negative"
                    :support "任意。含めたくない要素をカンマ区切りで。"}
                   (ui/text-input {:id "image-negative" :value negative :disabled busy?
                                   :class "oppai-full"
                                   :on-change (set-str :negative)}))
         [:div {:class "oppai-actions"}
          (ui/button (if busy? "生成中…" "画像を生成")
                     (cond-> {:variant :primary}
                       (not ready?) (assoc :disabled true))
                     #(rf/dispatch [:image/submit]))
          ;; Not decoration: a run occupies a real GPU for over a minute and
          ;; the page looks frozen otherwise.
          (when busy?
            [:span {:class "oppai-inline-status"}
             (ui/progress {:label "生成中"})
             [:span "1〜2分ほどかかります"]])]
         (fleet-note)]
        (error-banner error)
        (when b64
          [:figure {:class "oppai-result"}
           [:img {:src (net/data-uri b64) :alt (str "生成された画像: " prompt)}]
           [:figcaption {:class "oppai-note"}
            (str model
                 (when elapsed-ms (str " · " (int (/ elapsed-ms 1000)) " 秒")))
            " · "
            [:a {:href (net/data-uri b64) :download "oppai-image.png"} "PNG を保存"]]])])))

;; ---- douga ------------------------------------------------------------------

#?(:cljs
   (defn douga-studio []
     (let [{:keys [prompt model size frames status job error]} @(rf/subscribe [:douga])
           models @(rf/subscribe [:fleet-video-models])
           busy? (= :running status)
           ready? (and (not busy?) (seq (str/trim (str prompt))))
           set-str (fn [k] (fn [e] (rf/dispatch [:douga/set k (.. e -target -value)])))
           set-int (fn [k] (fn [e] (rf/dispatch [:douga/set k (parse-long (.. e -target -value))])))]
       [:section {:class "oppai-studio oppai-douga" :aria-label "動画生成"}
        [:div {:class "oppai-form"}
         (ui/field {:label "プロンプト" :for "douga-prompt"
                    :support "動きを含めて書くと結果が安定します。"
                    :requirement "必須" :required? true}
                   (ui/text-area {:id "douga-prompt" :rows 3 :value prompt
                                  :disabled busy? :class "oppai-full"
                                  :on-change (set-str :prompt)}))
         [:div {:class "oppai-form-row"}
          (ui/field {:label "モデル" :for "douga-model"
                     :support "動画は生成ジョブ API 側のモデルです（画像とは別のノード）。"}
                    (ui/select {:id "douga-model" :value model :disabled busy?
                                :class "oppai-full" :on-change (set-str :model)}
                               (mapv ui/model-option models)))
          (ui/field {:label "サイズ" :for "douga-size"}
                    (ui/select {:id "douga-size" :value size :disabled busy?
                                :class "oppai-full" :on-change (set-str :size)}
                               fleet/video-sizes))
          (ui/field {:label "長さ" :for "douga-frames"}
                    (ui/select {:id "douga-frames" :value frames :disabled busy?
                                :class "oppai-full" :on-change (set-int :frames)}
                               fleet/video-frame-choices))]
         [:div {:class "oppai-actions"}
          (ui/button (if busy? "生成中…" "動画を生成")
                     (cond-> {:variant :primary} (not ready?) (assoc :disabled true))
                     #(rf/dispatch [:douga/submit]))
          (when busy?
            [:span {:class "oppai-inline-status"}
             (ui/progress {:label "生成中"})
             [:span "2〜3分ほどかかります"]])]]
        (when job
          [:div {:class "oppai-job"}
           (ui/meter {:value (:job/progress job) :label "生成の進捗"})
           [:span {:class "oppai-note"}
            (str "状態: " (name (:job/status job)))]])
        (error-banner error)
        (when-let [url (:job/artifact-url job)]
          [:figure {:class "oppai-result"}
           [:video {:src url :controls true :playsInline true :class "oppai-video"}]
           [:figcaption {:class "oppai-note"}
            [:a {:href url :download "oppai-douga.mp4"} "MP4 を保存"]]])])))

;; ---- shell ------------------------------------------------------------------

;; ---- age gate ---------------------------------------------------------------

#?(:cljs
   (defn age-gate []
     ;; R18 landing state. Until confirmed, this replaces the whole shell —
     ;; no studio, no model links, nothing to interact with behind the modal.
     [:div {:class "oppai-gate"}
      [:div {:class "oppai-gate-card"}
       [:p {:class "oppai-gate-badge"} "R18"]
       [:h1 {:class "oppai-gate-title"} "oppai.fans"]
       [:p {:class "oppai-gate-lead"}
        "このサイトは成人向け（R18）の画像・動画生成サービスです。"
        "18 歳未満の方、および未成年者の利用を禁止します。"]
       [:p {:class "oppai-gate-note"}
        "生成には自社運用の murakumo フリート（Mac mini）を使用します。"
        "実在人物の肖像、未成年を想起させる内容、違法コンテンツの生成は禁止です。"]
       [:div {:class "oppai-gate-actions"}
        [ui/button "18 歳以上です — 入口へ"
                   {:variant :primary}
                   #(rf/dispatch [:age/confirm])]
        [:a {:class "oppai-gate-leave" :href "https://oppai.network"}
         "18 歳未満 — 離れる"]]]]))

#?(:cljs
   (defn app []
     (if-not @(rf/subscribe [:age-confirmed?])
       [age-gate]
       (let [tab @(rf/subscribe [:tab])]
       [:div {:class "oppai-shell"}
        [:header {:class "oppai-header"}
         [:a {:class "oppai-brand" :href "/" :aria-label "oppai.fans トップ"}
          [:img {:class "oppai-brand-logo"
                 :src "/img/oppai-network-logo.png"
                 :alt "oppai.fans"
                 :width 410 :height 128}]
          [:span {:class "oppai-brand-sub"} "murakumo フリートで生成する"]]
         [:nav {:class "oppai-model-links" :aria-label "モデル"}
          [:a {:class "oppai-model-link" :href "/basho/"}
           [:span "Basho"] [:small "芭蕉"]]
          [:a {:class "oppai-model-link" :href "/hokusai/"}
           [:span "Hokusai"] [:small "北斎"]]]]
        [:main {:class "oppai-home"}
         [:section {:class "oppai-model-banner" :aria-labelledby "oppai-model-banner-title"}
          [:div {:class "oppai-banner-copy"}
           [:p {:class "oppai-banner-kicker"} "Models by oppai.fans"]
           [:h1 {:class "oppai-banner-title" :id "oppai-model-banner-title"}
            "日本語の感性を、生成モデルへ。"]
           [:p {:class "oppai-banner-lead"}
            "言葉の余白を読む Basho。音と映像に気配を宿す Hokusai。日本語を出発点に、oppai.fans が開発しています。"]
           [:div {:class "oppai-banner-models"}
            [:a {:class "oppai-banner-model" :href "/basho/"}
             [:span {:class "oppai-banner-model-mark" :aria-hidden "true"} "芭"]
             [:span {:class "oppai-banner-model-copy"}
              [:strong "Basho"]
              [:small "言葉・対話 · Qwen3.8 Flash Next based"]]
             [:span {:class "oppai-banner-arrow" :aria-hidden "true"} "→"]]
            [:a {:class "oppai-banner-model" :href "/hokusai/"}
             [:span {:class "oppai-banner-model-mark" :aria-hidden "true"} "北"]
             [:span {:class "oppai-banner-model-copy"}
              [:strong "Hokusai"]
              [:small "音・映像 · MiniMax H3 based"]]
             [:span {:class "oppai-banner-arrow" :aria-hidden "true"} "→"]]]]
          [:div {:class "oppai-banner-art"}
           [:img {:src "/img/oppai-models-banner.webp"
                  :alt "和紙の上で、静かな墨の軌跡と動く藍の流れが一つにつながる生成作品"
                  :width 1774 :height 591
                  :loading "eager" :fetchpriority "high"}]
           [:span {:class "oppai-banner-art-label"} "Generative artwork · oppai.fans"]]]
         [:section {:aria-labelledby "oppai-studio-title"}
          [:div {:class "oppai-studio-header"}
           [:div {:class "oppai-studio-heading"}
            [:strong {:id "oppai-studio-title"} "生成スタジオ"]
            [:span "現在利用可能な murakumo フリート"]]
           (ui/tab-bar {:items db/tabs :active tab
                        :on-select #(rf/dispatch [:ui/tab %])})]
          [:div {:class "oppai-main"}
           (case tab
             :image [image-studio]
             :douga [douga-studio]
             [chat-studio])]]]
        [:footer {:class "oppai-footer"}
         [:p "チャット・画像・動画のすべてを、自社運用の Mac mini フリート（murakumo）が生成します。"]
         [:p {:class "oppai-note"}
          "デザイン: デジタル庁デザインシステム (DADS, MIT) · "
          [:a {:href "https://murakumo.cloud"} "murakumo.cloud"]]]]))))
