(ns oppai.gen.ui
  "The app's ONE seam onto the design system: jp-go-dds, the デジタル庁
  デザインシステム (DADS) cljc port.

  Same rule as everywhere else in this workspace — only this namespace
  requires the design system, so swapping skins is one file. The gftd.ai apex
  has a sibling of this file (ADR-2607310100); they are deliberately *not*
  shared as a library yet. Two consumers is not a pattern, and a premature
  `apex-ui` package would have to guess which of the two owns the vocabulary.
  If a third appears, extract then.

  DADS ships the vocabulary of a form and a document: button, heading,
  accordion, input, textarea, select, checkbox, table, chip, divider,
  notification-banner. It ships no tab bar, no avatar, no spinner, no chat
  bubble, no media grid — a government service page needs none of those. Those
  are built here from tokens and prefixed `oppai-*`, beside the upstream
  `dads-*` vocabulary rather than inside it (the same discipline jp-go-dds
  itself follows with `dds-ext-*`).

  Colors are `var(--hig-*)`, which `jp-go-dds.tokens` re-defines on top of
  DADS primitives — the workspace-wide token contract, so no raw hex here."
  (:require [kotoba.lang.text :as str]
            [jp-go-dds.core :as dds]))

;; ---- hiccup plumbing --------------------------------------------------------

(defn on-click [hiccup f] (update hiccup 1 assoc :on-click f))

(defn with-class [hiccup extra]
  (update hiccup 1 update :class (fn [c] (if (seq c) (str c " " extra) extra))))

;; ---- DADS-backed --------------------------------------------------------------

(def ^:private variant->dads
  {:primary :solid-fill :secondary :outline :ghost :text})

(defn button
  "DADS button.

  The class handling is not incidental: jp-go-dds merges its own `class` AFTER
  the caller's `:attrs`, on purpose, so a consumer cannot break component
  identity — which also means a `:class` passed through `:attrs` is silently
  dropped. So `:attrs` carries only non-class attributes and our class is
  appended afterwards; `dads-button` survives and ours rides alongside."
  ([label] (button label nil nil))
  ([label opts] (button label opts nil))
  ([label {:keys [variant size class title disabled type href aria-label]} on-click-fn]
   (let [node (dds/button
               label
               (cond-> {:type (get variant->dads variant :outline)
                        :size (or size "md")
                        :submit? (= "submit" type)
                        :disabled disabled
                        :aria-label aria-label}
                 href (assoc :href href)
                 title (assoc :attrs {:title title})))]
     (cond-> node
       (seq class) (with-class class)
       on-click-fn (on-click on-click-fn)))))

(defn heading
  ([level text] (heading level text nil))
  ([level text {:keys [size class]}]
   (cond-> (dds/heading level text (cond-> {} size (assoc :size size)))
     (seq class) (with-class class))))

(defn chip
  ([label] (chip label nil))
  ([label {:keys [class color style]}]
   (cond-> (dds/chip-label label {:color (or color "blue") :style (or style "text")})
     (seq class) (with-class class))))

(defn field
  "DADS form-control-label wrapping a control. This is the component the whole
  design system is built around — a labelled input with support text and a
  required marker — so the studios use it for every control rather than
  scattering bare `<label>`s."
  [{:keys [label for support requirement required?]} control]
  (dds/form-field {:label label :for for :support support
                   :requirement requirement :required? required?}
                  control))

(defn text-input
  [{:keys [class] :as opts}]
  (cond-> (dds/input-text (dissoc opts :class))
    (seq class) (with-class class)))

(defn text-area
  [{:keys [class] :as opts}]
  (cond-> (dds/textarea (dissoc opts :class))
    (seq class) (with-class class)))

(defn select
  "DADS select. options: [[value label] …].

  `:value` MUST be passed when the control has a current value — jp-go-dds
  documents, from a real incident, that omitting it renders the FIRST option
  as selected while looking entirely correct."
  [{:keys [on-change class] :as opts} options]
  (cond-> (dds/select (-> opts
                          (dissoc :class :on-change)
                          (assoc :attrs (cond-> {} on-change (assoc :on-change on-change))))
                      options)
    (seq class) (with-class class)))

(defn notification
  "DADS notification-banner. The design system has an opinion about how a
  service tells someone that something failed, and it is not red text."
  [{:keys [type heading class]} & body]
  (cond-> (apply dds/notification-banner
                 {:type (or type :error) :heading heading :heading-level 2}
                 body)
    (seq class) (with-class class)))

(defn divider [] (dds/divider))
(defn card
  ([body] (card body nil))
  ([body {:keys [class]}]
   (cond-> (dds/card body) (seq class) (with-class class))))

;; ---- primitives DADS does not ship (oppai-*) --------------------------------

(defn tab-bar
  "Top-level surface switch. items: [{:id :label :icon :fragment}] — the
  route table. Real links to the fragment, so a view is an address a person
  can copy and the back button works; `hashchange` feeds the state
  (ADR-2608080100)."
  [{:keys [items active on-select]}]
  (into [:nav {:class "oppai-tabs" :aria-label "モード"}]
        (map (fn [{:keys [id label icon fragment]}]
               (cond-> [:a {:href (str "#" fragment)
                            :class (str "oppai-tab" (when (= id active) " is-active"))
                            :aria-current (when (= id active) "page")}
                        [:span {:class "oppai-tab-icon" :aria-hidden "true"} icon]
                        [:span label]]
                 on-select (on-click #(on-select id))))
             items)))

(defn avatar
  ([content] (avatar content nil))
  ([content {:keys [class]}]
   [:span {:class (str "oppai-avatar" (when (seq class) (str " " class)))
           :aria-hidden "true"}
    content]))

(defn progress
  "Indeterminate activity indicator. `:label` reaches assistive tech — a bare
  spinner announces nothing."
  ([] (progress nil))
  ([{:keys [label class]}]
   [:span {:class (str "oppai-progress" (when (seq class) (str " " class)))
           :role "status" :aria-label (or label "処理中")}
    [:span {:class "oppai-progress-spinner" :aria-hidden "true"}]]))

(defn meter
  "Determinate job progress, 0–100. A real `<progress>`: the platform already
  has this element and it is announced correctly for free."
  [{:keys [value label]}]
  [:div {:class "oppai-meter"}
   [:progress {:class "oppai-meter-bar" :max 100 :value (or value 0)
               :aria-label (or label "進捗")}]
   [:span {:class "oppai-meter-value"} (str (int (or value 0)) "%")]])

(defn panel
  ([body] (panel body nil))
  ([body {:keys [class]}]
   [:div {:class (str "oppai-panel" (when (seq class) (str " " class)))} body]))

;; ---- formatting -------------------------------------------------------------

(defn model-option
  "[value label] for a select, with the fleet's own hints folded into the
  label — which node holds it, whether it is queued, whether the id is a
  guess. A picker that hides queue depth invites the user to pick the busy one."
  [{:keys [model-id label nodes queue exact? fallback? hint]}]
  [model-id
   (str (or label model-id)
        (when (seq nodes) (str " · " (str/join "/" nodes)))
        (when (and queue (pos? queue)) (str " · 待ち " queue))
        (when (false? exact?) " · 推定")
        (when fallback? " · 一覧取得不可")
        (when (seq hint) (str " · " hint)))])
