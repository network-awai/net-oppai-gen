(ns oppai.gen.views
  "The oppai.fans surface: three studios (チャット / 画像 / 動画) over one
  murakumo fleet, rendered in the デジタル庁デザインシステム through the single
  `oppai.gen.ui` seam. Presentation only — state lives in re-frame."
  (:require [kotoba.lang.text :as str]
            [oppai.gen.catalog :as catalog]
            [oppai.gen.db :as db]
            [oppai.gen.fleet :as fleet]
            [oppai.gen.guard :as guard]
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
         :loading [:p {:class "oppai-note"} "生成ノードの構成を確認しています…"]
         :fallback [:p {:class "oppai-note is-warning"}
                    (str "モデル一覧を取得できませんでした（" error
                         "）。既知のモデルを表示しています。")]
         [:p {:class "oppai-note"}
          "モデル一覧は生成ノードの ComfyUI が今この瞬間に持つチェックポイントです。"]))))

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
   (defn dds-checkbox [id label checked? disabled? on-change]
     [:label {:class "oppai-check" :for id}
      [:input {:id id :type "checkbox" :checked checked? :disabled disabled?
               :on-change on-change}]
      [:span label]]))

#?(:cljs
   (defn face-toggle
     "自分の顔を使う — offered only when the node can (a face mode is
     advertised); enabled only when this browser has an enrolment."
     [{:keys [face-ref? face-mode face-weight]} busy?]
     (let [available? @(rf/subscribe [:face-available?])
           enrolled? @(rf/subscribe [:face-enrolled?])
           modes @(rf/subscribe [:fleet-face-modes])]
       (when available?
         [:div {:class "oppai-face-toggle"}
          (dds-checkbox "image-face-ref" "自分の顔を使う（登録した顔で描く）"
                        (boolean face-ref?) (or busy? (not enrolled?))
                        #(rf/dispatch [:image/set :face-ref? (.. % -target -checked)]))
          (if enrolled?
            (when face-ref?
              [:div {:class "oppai-form-row"}
               (ui/field {:label "顔の効き" :for "image-face-weight"
                          :support "0.5 で雰囲気、0.85 で標準、1.2 でかなり寄せる。"}
                         [:input {:id "image-face-weight" :type "range"
                                  :min guard/face-weight-min :max guard/face-weight-max :step 0.05
                                  :value face-weight :disabled busy?
                                  :class "oppai-range"
                                  :on-change #(rf/dispatch [:image/set :face-weight
                                                            (js/parseFloat (.. % -target -value))])}])
               (when (> (count modes) 1)
                 (ui/field {:label "方式" :for "image-face-mode"
                            :support "plus-face は雰囲気重視、faceid は顔の同一性重視。"}
                           (ui/select {:id "image-face-mode" :value face-mode :disabled busy?
                                       :class "oppai-full"
                                       :on-change #(rf/dispatch [:image/set :face-mode (.. % -target -value)])}
                                      (mapv (fn [m] [m m]) modes))))])
            [:p {:class "oppai-note"}
             "顔はまだ登録されていません。"
             [:a {:href "#face"} "「自分の顔」で登録する"] "と使えます。"])]))))

#?(:cljs
   (defn preset-row []
     [:div {:class "oppai-presets" :role "group" :aria-label "プリセット"}
      (for [{:keys [id label]} catalog/presets]
        ^{:key id}
        (ui/button label {:variant :ghost :size "sm" :class "oppai-preset"}
                   #(rf/dispatch [:image/preset id])))]))

#?(:cljs
   (defn image-studio []
     (let [{:keys [prompt model size negative status job elapsed-ms error] :as image}
           @(rf/subscribe [:image])
           models @(rf/subscribe [:fleet-image-models])
           ready? @(rf/subscribe [:image-ready?])
           busy? (= :running status)
           card (catalog/card-by-id model)
           set-str (fn [k] (fn [e] (rf/dispatch [:image/set k (.. e -target -value)])))]
       [:section {:class "oppai-studio oppai-image" :aria-label "画像生成"}
        [:div {:class "oppai-form"}
         (preset-row)
         (ui/field {:label "プロンプト" :for "image-prompt"
                    :support (if (= :tags (:prompt-style card))
                               "このモデルは Danbooru 風のタグ列（1girl, adult, …）で書きます。"
                               "生成したい画像を日本語または英語で書いてください。")
                    :requirement "必須" :required? true}
                   (ui/text-area {:id "image-prompt" :rows 3 :value prompt
                                  :disabled busy?
                                  :class "oppai-full"
                                  :on-change (set-str :prompt)}))
         [:div {:class "oppai-form-row"}
          (ui/field {:label "モデル" :for "image-model"
                     :support (or (:tagline card) "生成ノードに実際に載っているチェックポイントです。")}
                    (ui/select {:id "image-model" :value model :disabled busy?
                                :class "oppai-full"
                                :on-change (set-str :model)}
                               (mapv (fn [m] (let [c (catalog/card-by-id (:model-id m))]
                                               [(:model-id m) (or (:name c) (:label m))]))
                                     models)))
          (ui/field {:label "サイズ" :for "image-size"}
                    (ui/select {:id "image-size" :value size :disabled busy?
                                :class "oppai-full"
                                :on-change (set-str :size)}
                               guard/image-sizes))]
         (ui/field {:label "除外したい要素" :for "image-negative"
                    :support "任意。未成年を除外する語はサーバ側で常に追加されます。"}
                   (ui/text-input {:id "image-negative" :value negative :disabled busy?
                                   :class "oppai-full"
                                   :on-change (set-str :negative)}))
         (face-toggle image busy?)
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
             [:span (if (= :queued (:job/status job)) "順番待ち…" "1〜2分ほどかかります（初回はモデル読込で最大 6 分）")]])]
         (fleet-note)]
        (when (and job busy?)
          [:div {:class "oppai-job"}
           (ui/meter {:value (:job/progress job) :label "生成の進捗"})
           [:span {:class "oppai-note"} (str "状態: " (name (:job/status job)))]])
        (error-banner error)
        (when-let [url (and (= :done status) (:job/artifact-url job))]
          [:figure {:class "oppai-result"}
           [:img {:src url :alt (str "生成された画像: " prompt)}]
           [:figcaption {:class "oppai-note"}
            (str (or (:name card) model)
                 (when elapsed-ms (str " · " (int (/ elapsed-ms 1000)) " 秒"))
                 (when (:face-ref? image) " · 自分の顔"))
            " · "
            [:a {:href url :download "oppai-image.png"} "PNG を保存"]
            " · "
            (ui/button "この画像から動画" {:variant :ghost :size "sm"}
                       #(rf/dispatch [:douga/from-work (first @(rf/subscribe [:works]))]))]])])))

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
         (when-let [img (:image (deref (rf/subscribe [:douga])))]
           [:div {:class "oppai-keyframe"}
            [:img {:src img :alt "動画の元になる画像" :class "oppai-keyframe-img"}]
            [:div
             [:p {:class "oppai-note"} "この画像を最初のコマにして動かします（i2v）。"]
             (ui/button "画像を外す" {:variant :ghost :size "sm"} #(rf/dispatch [:douga/clear-image]))]])
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

;; ---- models (catalog) --------------------------------------------------------

#?(:cljs
   (defn model-card [{:keys [id name base style rating tagline hint available? face-friendly?]}]
     ^{:key id}
     [:article {:class (str "oppai-card" (when (false? available?) " is-unavailable"))}
      [:div {:class "oppai-card-head"}
       [:h3 {:class "oppai-card-title"} name]
       (ui/chip (catalog/rating-labels rating)
                {:color (case rating :explicit "magenta" :suggestive "orange" "blue")})]
      [:p {:class "oppai-card-meta"} (str base " · " (catalog/styles style))]
      [:p {:class "oppai-card-lead"} tagline]
      [:p {:class "oppai-note"} hint (when face-friendly? " 顔参照向き。")]
      [:div {:class "oppai-card-actions"}
       (case available?
         true (ui/button "このモデルで生成" {:variant :primary :size "sm"}
                         #(rf/dispatch [:image/choose-model id]))
         false [:span {:class "oppai-note is-warning"} "このノードには未搭載"]
         [:span {:class "oppai-note"} "搭載状況を確認中…"])]]))

#?(:cljs
   (defn models-view []
     (let [models @(rf/subscribe [:fleet-image-models])
           {:keys [status]} @(rf/subscribe [:fleet])
           live (when (= :ready status) (map :model-id models))
           cards (catalog/with-availability live)]
       [:section {:class "oppai-studio oppai-models" :aria-label "モデル"}
        [:p {:class "oppai-lead"}
         "生成ノード（murakumo / gad）が今持っているチェックポイント。R18 の到達度と書き方が違うので、目的で選んでください。"]
        [:div {:class "oppai-cards"} (map model-card cards)]
        (fleet-note)])))

;; ---- face (consent ceremony) --------------------------------------------------

#?(:cljs
   (defn camera-box
     "Live front camera + capture. The two captures (neutral, gesture) are the
     enrolment; there is no file input here on purpose."
     []
     (let [video (atom nil)
           err (r/atom nil)]
       (r/create-class
        {:component-did-mount
         (fn [_] (when-let [v @video]
                   (net/camera-start! v {:on-ok (fn [_]) :on-error #(reset! err %)})))
         :component-will-unmount (fn [_] (net/camera-stop!))
         :reagent-render
         (fn []
           (let [{:keys [challenge capture]} @(rf/subscribe [:face])
                 n (count capture)]
             [:div {:class "oppai-camera"}
              [:video {:ref #(reset! video %) :autoPlay true :playsInline true :muted true
                       :class "oppai-camera-video" :aria-label "カメラ"}]
              (when @err (error-banner @err))
              [:div {:class "oppai-camera-steps"}
               [:p {:class (str "oppai-step" (when (>= n 1) " is-done"))}
                "1. 正面を向いて撮影"]
               [:p {:class (str "oppai-step" (when (>= n 2) " is-done"))}
                (str "2. " (:gesture challenge) " — そのまま撮影")]]
              [:div {:class "oppai-actions"}
               (ui/button (case n 0 "正面で撮影" 1 "指示どおりに撮影" "撮り直す")
                          {:variant :primary}
                          #(when-let [v @video]
                             (if (>= n 2)
                               (rf/dispatch [:face/reset-capture])
                               (rf/dispatch [:face/captured (net/capture-frame v)]))))]
              (when (seq capture)
                [:div {:class "oppai-captures"}
                 (for [[i c] (map-indexed vector capture)]
                   ^{:key i} [:img {:src c :alt (str "撮影 " (inc i)) :class "oppai-capture"}])])]))}))))

#?(:cljs
   (defn face-view []
     (let [{:keys [status record challenge consent busy? error]} @(rf/subscribe [:face])
           enrolled? @(rf/subscribe [:face-enrolled?])
           ready? @(rf/subscribe [:face-enrol-ready?])
           available? @(rf/subscribe [:face-available?])]
       [:section {:class "oppai-studio oppai-face" :aria-label "自分の顔"}
        [:p {:class "oppai-lead"}
         "登録できるのは、いま、このカメラの前にいる本人の顔だけです。写真のアップロードはできません。"]
        (ui/notification {:type :info-1 :heading "これは同意の儀式であって本人確認ではありません" :class "oppai-face-note"}
                         [:p "サーバが出す 60 秒のお題（向き・表情）に合わせて 2 枚撮り、同意にチェックして登録します。"
                          "登録した顔はこのブラウザにだけ紐づき、7 日で消えます。いつでも削除できます。"
                          "第三者の顔を使うことは規約で禁止し、生成物の責任は登録者にあります。"])
        (when-not available?
          [:p {:class "oppai-note is-warning"} "生成ノードが顔参照を提供していないため、いまは登録しても使えません。"])
        (error-banner error)
        (cond
          enrolled?
          [:div {:class "oppai-face-enrolled"}
           [:img {:src (:frame record) :alt "登録済みの顔" :class "oppai-capture"}]
           [:div
            [:p (str "登録済み · 有効期限 " (.toLocaleString (js/Date. (:expires-at record)) "ja-JP"))]
            [:p {:class "oppai-note"} "画像スタジオの「自分の顔を使う」で使えます。"]
            [:div {:class "oppai-actions"}
             (ui/button "画像スタジオへ" {:variant :primary :href "#image"})
             (ui/button (if busy? "削除中…" "登録を削除") {:variant :secondary :disabled busy?}
                        #(rf/dispatch [:face/delete]))]]]

          (nil? challenge)
          [:div {:class "oppai-actions"}
           (ui/button "カメラで登録を始める" {:variant :primary :disabled (= :unknown status)}
                      #(rf/dispatch [:face/challenge]))]

          :else
          [:div
           [camera-box]
           (dds-checkbox "face-consent"
                         "私は 18 歳以上の本人であり、自分の顔だけを登録し、生成物の責任を負います"
                         (boolean consent) busy?
                         #(rf/dispatch [:face/consent (.. % -target -checked)]))
           [:div {:class "oppai-actions"}
            (ui/button (if busy? "登録中…" "この顔を登録する")
                       (cond-> {:variant :primary} (not ready?) (assoc :disabled true))
                       #(rf/dispatch [:face/enrol]))
            (ui/button "やめる" {:variant :ghost} #(rf/dispatch [:face/status {:status "none"}]))]])])))

;; ---- works (this browser's shelf) -------------------------------------------

#?(:cljs
   (defn work-card [{:keys [id kind prompt model face? at url] :as work}]
     ^{:key id}
     [:article {:class "oppai-work"}
      (if (= :douga kind)
        [:video {:src url :controls true :playsInline true :class "oppai-work-media"}]
        [:img {:src url :alt prompt :loading "lazy" :class "oppai-work-media"}])
      [:div {:class "oppai-work-body"}
       [:p {:class "oppai-work-prompt"} prompt]
       [:p {:class "oppai-note"}
        (str (or (:name (catalog/card-by-id model)) model)
             (when face? " · 自分の顔")
             " · " (.toLocaleString (js/Date. at) "ja-JP"))]
       [:div {:class "oppai-actions"}
        [:a {:href url :download (str "oppai-" (name kind) ".") :class "oppai-link"} "保存"]
        (when (= :image kind)
          (ui/button "動画にする" {:variant :ghost :size "sm"} #(rf/dispatch [:douga/from-work work])))
        (ui/button "棚から外す" {:variant :ghost :size "sm"} #(rf/dispatch [:works/remove id]))]]]))

#?(:cljs
   (defn works-view []
     (let [works @(rf/subscribe [:works])]
       [:section {:class "oppai-studio oppai-works" :aria-label "作品"}
        [:p {:class "oppai-lead"}
         "この端末で生成した作品の棚です。この端末の中にだけ記録され、公開ギャラリーではありません（公開・共有は次の段で）。"]
        (if (seq works)
          [:div {:class "oppai-cards"} (map work-card works)]
          [:p {:class "oppai-note"} "まだ作品がありません。" [:a {:href "#image"} "画像スタジオ"] "から。"])])))

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
        "実在人物の肖像（自分の顔をカメラで登録した場合を除く）、未成年を想起させる内容、違法コンテンツの生成は禁止です。"]
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
           (ui/tab-bar {:items db/tabs :active tab})]
          [:div {:class "oppai-main"}
           (case tab
             :image [image-studio]
             :douga [douga-studio]
             :face [face-view]
             :models [models-view]
             :works [works-view]
             [chat-studio])]]]
        [:footer {:class "oppai-footer"}
         [:p "チャット・画像・動画のすべてを、自社運用の Mac mini フリート（murakumo）が生成します。"]
         [:p {:class "oppai-note"}
          "デザイン: デジタル庁デザインシステム (DADS, MIT) · "
          [:a {:href "https://murakumo.cloud"} "murakumo.cloud"]]]]))))
