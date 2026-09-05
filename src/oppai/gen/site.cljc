(ns oppai.gen.site
  "Generates `public/index.html`: the DADS shell the browser bundle mounts
  into.

  jp-go-dds inlines the whole vendored stylesheet into one `<style>` — zero
  external requests is the design system's stated goal, and this app is a
  single document, so one inlined bundle beats several render-blocking links.

  DADS is light-only (upstream ships no dark palette and `jp-go-dds.page` pins
  `color-scheme: light`). There is no theme map here and no dark branch to
  keep in sync; do not add one.

  Regenerate with `clojure -M:local:generate-site`."
  (:require [css.core :as css]
            [oppai.gen.ui :as ui]
            [jp-go-dds.page :as dds-page]
            [jp-go-dds.tokens :as tokens]
            #?(:clj [clojure.edn :as edn])
            #?(:clj [clojure.java.io :as io])))

(def page-title "oppai.fans — R18 画像・動画生成（成人向け）")

(def page-description
  "oppai.fans は成人向け（R18）の画像・動画生成サービスです。18 歳未満の利用は禁止です。チャット・画像・動画を、自社運用の Mac mini フリート（murakumo）で生成します。")

(def age-gate-head
  "R18 declaration for the whole site: advisory rating + noindex for the app
  shell (the studios are gated, JS-only, and have nothing for a search
  engine to index anyway). The model LP pages carry their own head."
  [[:meta {:name "rating" :content "RTA-5042-1996-1401-1577-RTA"}]
   [:meta {:name "robots" :content "noindex, noarchive"}]])

(defn dds-css
  "The vendored DADS bundle. `jp-go-dds.page` is pure by design and will not
  read it, so reading it is ours to do."
  []
  #?(:clj (slurp (io/resource "jp_go_dds/dds.css"))
     :cljs (throw (ex-info "dds-css is JVM-only (the shell is generated at build time)" {}))))

(def default-bundle-path
  "Only used when no build manifest exists yet (a fresh clone running the
  tests before a release build). A deploy must never ship this: an unhashed
  path is precisely the one a stale cache entry can outlive."
  "/assets/main.js")

(defn bundle-path-from-manifest
  "shadow-cljs writes `public/assets/manifest.edn` describing what it actually
  emitted. Reading the name from there rather than assuming it is what keeps
  the hash in the HTML honest — the generator cannot drift from the build,
  because it is quoting it."
  [manifest-edn]
  (or (some->> manifest-edn
               (filter #(= :main (:module-id %)))
               first
               :output-name
               (str "/assets/"))
      default-bundle-path))

#?(:clj
   (defn bundle-path []
     (let [f (io/file "public" "assets" "manifest.edn")]
       (if (.exists f)
         (bundle-path-from-manifest (edn/read-string (slurp f)))
         default-bundle-path))))

(def layout-stylesheet
  "Layout plus the `oppai-*` primitives `oppai.gen.ui` had to build because a
  government design system ships no tab bar, avatar, spinner, chat bubble or
  media result frame.

  Colors are `--hig-*`, which `jp-go-dds.tokens` bridges onto DADS
  primitives — the workspace token contract, so there is no raw hex here.
  Sizes are literal: DADS publishes color, elevation and font-family tokens
  but no radius or type scale, and adding an `--oppai-*` scale would be a third
  token system nobody asked for."
  {:rules
   [[":root" {:--oppai-max "min(880px, 100%)"
              :--oppai-radius "8px"}]
    ["*" {:box-sizing :border-box}]
    ["body" {:margin 0 :min-height "100vh"
             :background "var(--hig-color-secondary-system-background)"
             :color "var(--hig-color-label)"
             :font-family "var(--hig-font-text)"}]
    ["button, textarea, input, select" {:font :inherit}]

    [".oppai-shell" {:display :grid
                    :grid-template-rows "auto minmax(0, 1fr) auto"
                    :min-height "100vh"}]

    ;; --- header + tabs -------------------------------------------------------
    [".oppai-header" {:display :flex :flex-wrap :wrap :align-items :center
                     :justify-content "space-between" :gap "12px"
                     :padding "12px 20px"
                     :padding-top "calc(12px + env(safe-area-inset-top, 0px))"
                     :background "var(--hig-color-system-background)"
                     :border-bottom "1px solid var(--hig-color-separator)"}]
    [".oppai-brand" {:display :inline-flex :flex-direction :column :align-items :flex-start
                     :gap "2px" :color "var(--hig-color-label)" :text-decoration :none}]
    [".oppai-brand-logo" {:display :block :width :auto :height "46px"}]
    [".oppai-brand-sub" {:font-size "0.75rem"
                        :padding-left "4px"
                        :color "var(--hig-color-secondary-label)"}]

    [".oppai-model-links" {:display :flex :gap "8px" :align-items :center}]
    [".oppai-model-link" {:display :inline-flex
                         :align-items :center
                         :gap "8px"
                         :min-height "44px"
                         :padding "0 14px"
                         :border "1px solid var(--hig-color-separator)"
                         :border-radius "999px"
                         :background "var(--hig-color-system-background)"
                         :color "var(--hig-color-label)"
                         :text-decoration :none
                         :font-weight 700}]
    [".oppai-model-link:hover" {:background "var(--hig-color-tertiary-system-fill)"}]
    [".oppai-model-link:focus-visible" {:outline "2px solid var(--hig-color-tint)"
                                       :outline-offset "2px"}]
    [".oppai-model-link small" {:font-size "0.6875rem"
                               :font-weight 500
                               :color "var(--hig-color-secondary-label)"}]

    ;; --- model banner -------------------------------------------------------
    [".oppai-home" {:min-height 0}]
    [".oppai-model-banner" {:width "min(1180px, calc(100% - 32px))"
                           :margin "24px auto 0"
                           :display :grid
                           :grid-template-columns "minmax(0, 1.08fr) minmax(300px, 0.92fr)"
                           :overflow :hidden
                           :border "1px solid var(--hig-color-separator)"
                           :border-radius "var(--oppai-radius)"
                           :background "var(--hig-color-system-background)"}]
    [".oppai-banner-copy" {:padding "clamp(24px, 4vw, 44px)"
                          :display :flex
                          :flex-direction :column
                          :justify-content :center}]
    [".oppai-banner-kicker" {:margin "0 0 10px"
                            :font-size "0.75rem"
                            :font-weight 700
                            :letter-spacing "0.12em"
                            :text-transform :uppercase
                            :color "var(--hig-palette-red)"}]
    [".oppai-banner-title" {:margin 0
                           :font-size "clamp(1.85rem, 4vw, 3.5rem)"
                           :font-weight 500
                           :line-height 1.22
                           :letter-spacing "-0.03em"}]
    [".oppai-banner-lead" {:margin "14px 0 0"
                          :max-width "36em"
                          :line-height 1.8
                          :color "var(--hig-color-secondary-label)"}]
    [".oppai-banner-models" {:display :grid
                            :grid-template-columns "1fr 1fr"
                            :gap "10px"
                            :margin-top "24px"}]
    [".oppai-banner-model" {:display :grid
                           :grid-template-columns "auto minmax(0, 1fr) auto"
                           :align-items :center
                           :gap "12px"
                           :min-height "74px"
                           :padding "12px 14px"
                           :border "1px solid var(--hig-color-separator)"
                           :background "var(--hig-color-secondary-system-background)"
                           :color "var(--hig-color-label)"
                           :text-decoration :none}]
    [".oppai-banner-model:hover" {:background "var(--hig-color-tertiary-system-fill)"}]
    [".oppai-banner-model:focus-visible" {:outline "2px solid var(--hig-color-tint)"
                                         :outline-offset "2px"}]
    [".oppai-banner-model-mark" {:display :grid
                                :place-items :center
                                :width "38px"
                                :height "38px"
                                :border "1px solid var(--hig-color-separator)"
                                :font-family :serif
                                :font-size "1.125rem"}]
    [".oppai-banner-model-copy" {:display :flex :flex-direction :column :gap "2px"}]
    [".oppai-banner-model-copy strong" {:font-size "1rem"}]
    [".oppai-banner-model-copy small" {:font-size "0.6875rem"
                                      :line-height 1.4
                                      :color "var(--hig-color-secondary-label)"}]
    [".oppai-banner-arrow" {:font-size "1.25rem" :color "var(--hig-color-tint)"}]
    [".oppai-banner-art" {:position :relative
                         :min-height "320px"
                         :background "var(--hig-color-secondary-system-background)"}]
    [".oppai-banner-art img" {:display :block
                             :width "100%"
                             :height "100%"
                             :min-height "320px"
                             :object-fit :cover}]
    [".oppai-banner-art-label" {:position :absolute
                               :right "14px"
                               :bottom "14px"
                               :padding "6px 9px"
                               :background "color-mix(in srgb, var(--hig-color-system-background) 88%, transparent)"
                               :font-size "0.6875rem"
                               :color "var(--hig-color-secondary-label)"}]
    [".oppai-studio-header" {:width "var(--oppai-max)"
                            :margin "32px auto 0"
                            :padding "0 16px"
                            :display :flex
                            :align-items :end
                            :justify-content "space-between"
                            :gap "16px"}]
    [".oppai-studio-heading" {:display :flex :flex-direction :column :gap "4px"}]
    [".oppai-studio-heading strong" {:font-size "1.125rem"}]
    [".oppai-studio-heading span" {:font-size "0.75rem"
                                  :color "var(--hig-color-secondary-label)"}]

    [".oppai-tabs" {:display :flex :gap "4px" :flex-wrap :wrap}]
    [".oppai-tab" {:appearance :none :cursor :pointer
                  :display :inline-flex :align-items :center :gap "6px"
                  :min-height "44px" :padding "0 16px"
                  :border "1px solid var(--hig-color-separator)"
                  :border-radius "999px"
                  :background "var(--hig-color-system-background)"
                  :color "var(--hig-color-label)"}]
    [".oppai-tab:hover" {:background "var(--hig-color-tertiary-system-fill)"}]
    [".oppai-tab.is-active" {:background "var(--hig-color-tint)"
                            :border-color "var(--hig-color-tint)"
                            :color "var(--color-neutral-white)"
                            :font-weight 700}]
    [".oppai-tab:focus-visible" {:outline "2px solid var(--hig-color-tint)"
                                :outline-offset "2px"}]

    ;; --- studios -------------------------------------------------------------
    [".oppai-main" {:min-height 0 :display :flex :justify-content :center
                   :padding "20px 16px"}]
    [".oppai-studio" {:width "var(--oppai-max)" :display :flex
                     :flex-direction :column :gap "16px" :min-height 0}]
    [".oppai-form" {:display :flex :flex-direction :column :gap "16px"
                   :padding "20px"
                   :background "var(--hig-color-system-background)"
                   :border "1px solid var(--hig-color-separator)"
                   :border-radius "var(--oppai-radius)"}]
    [".oppai-form-row" {:display :grid :gap "16px"
                       :grid-template-columns "repeat(auto-fit, minmax(220px, 1fr))"}]
    [".oppai-full" {:display :block :width "100%"}]
    [".oppai-full .dads-input-text__input, .oppai-full .dads-textarea__textarea, .oppai-full .dads-select__select"
     {:width "100%"}]
    [".oppai-actions" {:display :flex :flex-wrap :wrap :align-items :center :gap "12px"}]
    [".oppai-inline-status" {:display :inline-flex :align-items :center :gap "8px"
                            :font-size "0.8125rem"
                            :color "var(--hig-color-secondary-label)"}]
    [".oppai-note" {:margin 0 :font-size "0.8125rem"
                   :color "var(--hig-color-secondary-label)"}]
    [".oppai-note.is-warning" {:color "var(--hig-palette-orange)"}]

    ;; --- chat ----------------------------------------------------------------
    [".oppai-chat" {:flex 1}]
    [".oppai-thread" {:flex 1 :overflow :auto :min-height "40vh"
                     :display :flex :flex-direction :column :gap "4px"
                     :padding "4px 0"}]
    [".oppai-message" {:padding "8px 0"}]
    [".oppai-message-inner" {:display :grid
                            :grid-template-columns "36px minmax(0, 1fr)"
                            :gap "12px"}]
    [".oppai-role" {:font-size "0.75rem" :font-weight 700 :margin-bottom "4px"
                   :color "var(--hig-color-secondary-label)"}]
    [".oppai-avatar" {:display :inline-flex :align-items :center
                     :justify-content :center
                     :width "36px" :height "36px" :border-radius "50%"
                     :font-size "0.75rem" :font-weight 700
                     :background "var(--hig-color-tint)"
                     :color "var(--color-neutral-white)"}]
    [".oppai-avatar-user" {:background "var(--hig-color-secondary-system-fill)"
                          :color "var(--hig-color-label)"}]
    [".oppai-panel" {:border "1px solid var(--hig-color-separator)"
                    :border-radius "var(--oppai-radius)"
                    :background "var(--hig-color-system-background)"}]
    [".oppai-bubble" {:padding "12px 14px" :line-height 1.7}]
    [".oppai-bubble.is-user" {:background "var(--color-key-100)"
                             :border-color "var(--color-key-200)"}]
    [".oppai-message.is-error .oppai-bubble" {:border-color "var(--hig-palette-red)"}]
    [".oppai-plain" {:white-space :pre-wrap :word-wrap :break-word}]
    [".oppai-composer" {:display :grid
                       :grid-template-columns "minmax(0, 1fr) auto"
                       :gap "8px" :align-items :end}]
    [".oppai-composer-input" {:display :block :min-width 0}]
    [".oppai-composer-input .dads-textarea__textarea" {:width "100%" :max-height "180px"
                                                      :resize :none}]

    ;; --- results -------------------------------------------------------------
    [".oppai-result" {:margin 0 :display :flex :flex-direction :column :gap "8px"
                     :padding "16px"
                     :background "var(--hig-color-system-background)"
                     :border "1px solid var(--hig-color-separator)"
                     :border-radius "var(--oppai-radius)"}]
    [".oppai-result img, .oppai-video" {:width "100%" :height :auto
                                      :border-radius "var(--oppai-radius)"
                                      :background "var(--hig-color-tertiary-system-fill)"}]
    [".oppai-job" {:display :flex :align-items :center :gap "12px" :flex-wrap :wrap}]
    [".oppai-meter" {:display :flex :align-items :center :gap "8px" :flex 1
                    :min-width "180px"}]
    [".oppai-meter-bar" {:flex 1 :height "8px" :appearance :none :border 0
                        :border-radius "999px"
                        :background "var(--hig-color-tertiary-system-fill)"}]
    ["progress.oppai-meter-bar::-webkit-progress-bar"
     {:background "var(--hig-color-tertiary-system-fill)" :border-radius "999px"}]
    ["progress.oppai-meter-bar::-webkit-progress-value"
     {:background "var(--hig-color-tint)" :border-radius "999px"}]
    ["progress.oppai-meter-bar::-moz-progress-bar"
     {:background "var(--hig-color-tint)" :border-radius "999px"}]
    [".oppai-meter-value" {:font-size "0.75rem" :font-variant-numeric "tabular-nums"
                          :color "var(--hig-color-secondary-label)"}]

    ;; --- spinner -------------------------------------------------------------
    [".oppai-progress" {:display :inline-flex}]
    [".oppai-progress-spinner" {:display :block :width "18px" :height "18px"
                               :border "2px solid var(--hig-color-separator)"
                               :border-top-color "var(--hig-color-tint)"
                               :border-radius "50%"
                               :animation "oppai-spin 700ms linear infinite"}]

    ;; --- footer --------------------------------------------------------------
    [".oppai-footer" {:padding "20px"
                     :padding-bottom "calc(20px + env(safe-area-inset-bottom, 0px))"
                     :border-top "1px solid var(--hig-color-separator)"
                     :background "var(--hig-color-system-background)"
                     :font-size "0.8125rem"}]
    [".oppai-footer p" {:margin "0 0 4px"}]
    [".oppai-footer a" {:color "var(--hig-color-tint)"}]

    ;; --- age gate ------------------------------------------------------------
    [".oppai-gate" {:min-height "100vh"
                    :display :grid
                    :place-items :center
                    :padding "24px"
                    :background "var(--hig-color-secondary-system-background)"}]
    [".oppai-gate-card" {:max-width "520px"
                         :background "var(--hig-color-system-background)"
                         :border "1px solid var(--hig-color-separator)"
                         :border-radius "12px"
                         :padding "28px"}]
    [".oppai-gate-badge" {:display :inline-block
                          :margin "0 0 12px"
                          :padding "2px 10px"
                          :border "1px solid var(--hig-color-tint)"
                          :border-radius "999px"
                          :color "var(--hig-color-tint)"
                          :font-weight 700
                          :letter-spacing "0.08em"}]
    [".oppai-gate-title" {:margin "0 0 12px" :font-size "1.375rem"}]
    [".oppai-gate-lead" {:margin "0 0 8px" :font-size "0.9375rem"
                         :line-height 1.7}]
    [".oppai-gate-note" {:margin "0 0 20px" :font-size "0.8125rem"
                         :color "var(--hig-color-secondary-label)"
                         :line-height 1.7}]
    [".oppai-gate-actions" {:display :flex
                            :flex-direction :column
                            :gap "12px"
                            :align-items :flex-start}]]

   :keyframes
   [[:oppai-spin [[0 {:transform "rotate(0deg)"}] [100 {:transform "rotate(360deg)"}]]]]

   :media
   [["(max-width: 600px)"
     [[".oppai-header" {:flex-direction :column :align-items :flex-start}]
      [".oppai-model-banner" {:grid-template-columns "1fr" :margin-top "16px"}]
      [".oppai-banner-art" {:min-height "220px" :order -1}]
      [".oppai-banner-art img" {:min-height "220px"}]
      [".oppai-banner-models" {:grid-template-columns "1fr"}]
      [".oppai-studio-header" {:align-items :flex-start :flex-direction :column}]
      [".oppai-form" {:padding "16px"}]
      [".oppai-message-inner" {:grid-template-columns "28px minmax(0, 1fr)" :gap "8px"}]
      [".oppai-avatar" {:width "28px" :height "28px" :font-size "0.6875rem"}]]]

    ["(prefers-reduced-motion: reduce)"
     [[".oppai-progress-spinner" {:animation :none}]]]]})

(defn render-css []
  (str "/* oppai.fans — layout on the デジタル庁デザインシステム */\n"
       (css/css layout-stylesheet)))

(def model-page-stylesheet
  "Washi-inspired editorial layer for the model LPs. DADS remains the
  component and token foundation; this only adds composition, paper texture,
  and the vertical display treatment that DADS intentionally does not own."
  {:rules
   [[".oppai-lp" {:min-height "100vh"
                  :position :relative
                  :overflow :hidden
                  :background "var(--hig-color-system-background)"
                  :color "var(--hig-color-label)"}]
    [".oppai-lp::before" {:content "\"\""
                         :position :fixed
                         :inset 0
                         :pointer-events :none
                         :z-index 0
                         :opacity 0.38
                         :background-image "repeating-linear-gradient(87deg, transparent 0, transparent 13px, color-mix(in srgb, var(--hig-color-separator) 22%, transparent) 14px, transparent 15px), repeating-linear-gradient(3deg, transparent 0, transparent 29px, color-mix(in srgb, var(--hig-color-separator) 16%, transparent) 30px, transparent 31px)"}]
    [".oppai-lp > *" {:position :relative :z-index 1}]
    [".oppai-lp-header" {:display :flex
                        :align-items :center
                        :justify-content "space-between"
                        :gap "20px"
                        :padding "20px clamp(20px, 5vw, 72px)"
                        :border-bottom "1px solid var(--hig-color-separator)"}]
    [".oppai-lp-brand" {:display :inline-flex
                       :align-items :center
                       :gap "12px"
                       :color "var(--hig-color-label)"
                       :text-decoration :none}]
    [".oppai-lp-brand-logo" {:display :block :width :auto :height "44px"}]
    [".oppai-lp-brand span" {:font-size "0.75rem"
                            :color "var(--hig-color-secondary-label)"}]
    [".oppai-lp-nav" {:display :flex :align-items :center :gap "20px" :flex-wrap :wrap}]
    [".oppai-lp-nav a" {:color "var(--hig-color-label)"
                       :text-underline-offset "5px"}]
    [".oppai-lp-nav a[aria-current=page]" {:font-weight 700
                                          :text-decoration-color "var(--hig-palette-red)"}]
    [".oppai-lp-hero" {:width "min(1180px, calc(100% - 40px))"
                      :margin "0 auto"
                      :min-height "min(760px, calc(100vh - 82px))"
                      :display :grid
                      :grid-template-columns "minmax(0, 1fr) minmax(120px, 220px)"
                      :align-items :center
                      :gap "clamp(36px, 8vw, 120px)"
                      :padding "clamp(72px, 12vw, 150px) 0"}]
    [".oppai-lp-copy" {:max-width "760px"}]
    [".oppai-lp-kicker" {:margin "0 0 20px"
                        :font-size "0.8125rem"
                        :letter-spacing "0.16em"
                        :text-transform :uppercase
                        :color "var(--hig-color-secondary-label)"}]
    [".oppai-lp-title" {:margin 0
                       :max-width "12em"
                       :font-family "var(--hig-font-text)"
                       :font-size "clamp(2.75rem, 7vw, 6.75rem)"
                       :font-weight 500
                       :line-height 1.08
                       :letter-spacing "-0.045em"}]
    [".oppai-lp-lead" {:max-width "38em"
                      :margin "32px 0 0"
                      :font-size "clamp(1.05rem, 2vw, 1.35rem)"
                      :line-height 2
                      :letter-spacing "0.025em"}]
    [".oppai-lp-actions" {:display :flex :flex-wrap :wrap :gap "12px"
                         :margin-top "36px"}]
    [".oppai-lp-mark" {:justify-self :end
                      :display :grid
                      :place-items :center
                      :min-height "470px"
                      :padding "28px 32px"
                      :border-left "1px solid var(--hig-color-separator)"
                      :border-right "1px solid var(--hig-color-separator)"
                      :writing-mode :vertical-rl
                      :font-size "clamp(3.5rem, 8vw, 7rem)"
                      :font-family "serif"
                      :font-weight 500
                      :letter-spacing "0.22em"}]
    [".oppai-lp-seal" {:position :absolute
                      :right "-14px"
                      :bottom "38px"
                      :display :grid
                      :place-items :center
                      :width "54px"
                      :height "54px"
                      :border "2px solid var(--hig-palette-red)"
                      :color "var(--hig-palette-red)"
                      :font-size "0.75rem"
                      :font-weight 700
                      :letter-spacing "0.12em"
                      :writing-mode :horizontal-tb
                      :transform "rotate(-4deg)"}]
    [".oppai-lp-art" {:width "min(1180px, calc(100% - 40px))"
                     :margin "0 auto clamp(72px, 10vw, 128px)"
                     :padding 0}]
    [".oppai-lp-art-frame" {:position :relative
                           :padding "clamp(8px, 1.2vw, 14px)"
                           :border "1px solid var(--hig-color-separator)"
                           :background "var(--hig-color-system-background)"}]
    [".oppai-lp-art img" {:display :block
                         :width "100%"
                         :height :auto
                         :aspect-ratio "4 / 3"
                         :object-fit :cover}]
    [".oppai-lp-art figcaption" {:display :flex
                                :justify-content "space-between"
                                :gap "20px"
                                :margin-top "12px"
                                :font-size "0.75rem"
                                :letter-spacing "0.04em"
                                :color "var(--hig-color-secondary-label)"}]
    [".oppai-lp-band" {:border-top "1px solid var(--hig-color-separator)"
                      :background "color-mix(in srgb, var(--hig-color-secondary-system-background) 68%, transparent)"}]
    [".oppai-lp-section" {:width "min(1080px, calc(100% - 40px))"
                         :margin "0 auto"
                         :padding "clamp(64px, 9vw, 112px) 0"}]
    [".oppai-lp-section-head" {:display :grid
                              :grid-template-columns "minmax(180px, 0.7fr) minmax(0, 1.3fr)"
                              :gap "40px"
                              :margin-bottom "44px"}]
    [".oppai-lp-section h2" {:margin 0
                            :font-size "clamp(1.75rem, 3vw, 2.75rem)"
                            :font-weight 500
                            :line-height 1.35}]
    [".oppai-lp-section-intro" {:margin 0 :line-height 1.9
                               :color "var(--hig-color-secondary-label)"}]
    [".oppai-lp-grid" {:display :grid
                      :grid-template-columns "repeat(3, minmax(0, 1fr))"
                      :border-top "1px solid var(--hig-color-separator)"
                      :border-bottom "1px solid var(--hig-color-separator)"}]
    [".oppai-lp-feature" {:padding "32px 28px"
                         :border-right "1px solid var(--hig-color-separator)"}]
    [".oppai-lp-feature:last-child" {:border-right 0}]
    [".oppai-lp-feature-no" {:display :block :margin-bottom "28px"
                            :font-family "var(--hig-font-mono)"
                            :font-size "0.75rem"
                            :color "var(--hig-palette-red)"}]
    [".oppai-lp-feature h3" {:margin "0 0 12px" :font-size "1.25rem"}]
    [".oppai-lp-feature p" {:margin 0 :line-height 1.8
                           :color "var(--hig-color-secondary-label)"}]
    [".oppai-lp-status" {:display :grid
                        :grid-template-columns "minmax(0, 0.8fr) minmax(0, 1.2fr)"
                        :gap "clamp(36px, 8vw, 96px)"
                        :align-items :start}]
    [".oppai-lp-status-note" {:padding "28px"
                             :border "1px solid var(--hig-color-separator)"
                             :background "var(--hig-color-system-background)"}]
    [".oppai-lp-status-note p" {:margin "16px 0 0" :line-height 1.8}]
    [".oppai-lp-specs" {:margin 0}]
    [".oppai-lp-specs div" {:display :grid
                           :grid-template-columns "minmax(120px, 0.7fr) minmax(0, 1.3fr)"
                           :gap "20px"
                           :padding "18px 0"
                           :border-bottom "1px solid var(--hig-color-separator)"}]
    [".oppai-lp-specs dt" {:color "var(--hig-color-secondary-label)"}]
    [".oppai-lp-specs dd" {:margin 0 :font-weight 600 :word-break :break-word}]
    [".oppai-lp-route" {:display :grid
                       :grid-template-columns "repeat(4, minmax(0, 1fr))"
                       :gap "1px"
                       :background "var(--hig-color-separator)"
                       :border "1px solid var(--hig-color-separator)"}]
    [".oppai-lp-route-step" {:min-height "150px"
                            :padding "24px"
                            :background "var(--hig-color-system-background)"}]
    [".oppai-lp-route-step strong" {:display :block :margin-bottom "12px"}]
    [".oppai-lp-route-step span" {:font-size "0.875rem"
                                 :line-height 1.7
                                 :color "var(--hig-color-secondary-label)"}]
    [".oppai-lp-footer" {:display :flex
                        :align-items :center
                        :justify-content "space-between"
                        :gap "24px"
                        :padding "36px clamp(20px, 5vw, 72px)"
                        :border-top "1px solid var(--hig-color-separator)"
                        :font-size "0.8125rem"
                        :color "var(--hig-color-secondary-label)"}]
    [".oppai-model-links" {:display :flex :gap "12px" :align-items :center}]
    [".oppai-model-links a" {:color "var(--hig-color-label)"
                            :font-size "0.8125rem"
                            :text-underline-offset "4px"}]]
   :media
   [["(max-width: 760px)"
     [[".oppai-lp-header" {:align-items :flex-start :flex-direction :column}]
      [".oppai-lp-brand span" {:display :none}]
      [".oppai-lp-hero" {:grid-template-columns "1fr"
                        :gap "48px"
                        :min-height :auto
                        :padding "72px 0"}]
      [".oppai-lp-mark" {:justify-self :start
                        :min-height :auto
                        :width "100%"
                        :writing-mode :horizontal-tb
                        :border-left 0
                        :border-right 0
                        :border-top "1px solid var(--hig-color-separator)"
                        :border-bottom "1px solid var(--hig-color-separator)"
                        :padding "28px 0"
                        :place-items :start
                        :letter-spacing "0.12em"}]
      [".oppai-lp-seal" {:right "8px" :bottom "18px"}]
      [".oppai-lp-section-head, .oppai-lp-status" {:grid-template-columns "1fr" :gap "24px"}]
      [".oppai-lp-grid" {:grid-template-columns "1fr"}]
      [".oppai-lp-feature" {:border-right 0
                           :border-bottom "1px solid var(--hig-color-separator)"}]
      [".oppai-lp-feature:last-child" {:border-bottom 0}]
      [".oppai-lp-route" {:grid-template-columns "1fr 1fr"}]
      [".oppai-lp-footer" {:align-items :flex-start :flex-direction :column}]]]
    ["(max-width: 440px)"
     [[".oppai-lp-route" {:grid-template-columns "1fr"}]
      [".oppai-lp-title" {:font-size "2.75rem"}]]]
    ["(prefers-reduced-motion: reduce)"
     [[".oppai-lp-seal" {:transform :none}]]]]})

(defn render-model-css []
  (str (render-css) "\n/* model LP — washi editorial layer */\n"
       (css/css model-page-stylesheet)))

(def model-pages
  {:basho
   {:slug "basho"
    :name "Basho"
    :kanji "芭蕉"
    :kicker "Japanese language model · in development"
    :title "日本語の間を、応答に。"
    :description "Basho は、文脈の余白や語調まで丁寧に扱う、日本語中心の生成モデルとして oppai.fans が開発しています。"
    :lead "言葉をただ速く返すのではなく、何を言わないか、どの距離で語るかまで読む。Basho は、日本語の文章・対話・道具利用のためのモデルを目指しています。"
    :base "Qwen/Qwen3.8-Flash-Next"
    :protocol "OpenAI-compatible /v1/chat/completions"
    :hosting "Baseten · scale-to-zero"
    :state "開発中 · 推論未公開"
    :image "/img/basho-washi.webp"
    :image-alt "生成した和紙の上に、言葉の間を思わせる墨と藍の軌跡が広がる抽象画"
    :image-caption "Basho — 言葉と余白のための生成ビジュアル"
    :features [["余白" "文脈を読む" "敬語、含意、主語の省略。日本語が言葉の外側に置く情報まで評価します。"]
               ["構造" "形を崩さない" "長文、要約、JSON、道具利用を、日本語品質と両立できるよう検証します。"]
               ["根拠" "測って公開する" "日本語評価、安全性、速度、価格、データ方針を揃えてから提供します。"]]}
   :hokusai
   {:slug "hokusai"
    :name "Hokusai"
    :kanji "北斎"
    :kicker "Japanese audiovisual model · in development"
    :title "音と映像に、気配を宿す。"
    :description "Hokusai は、日本語の演出意図から音と映像を組み立てる、非同期の映像生成モデルとして oppai.fans が開発しています。"
    :lead "構図だけでなく、時間、音、静けさ、場の気配まで一つの生成へ。Hokusai は、日本語の指示から映像と音響を編むモデルを目指しています。"
    :base "MiniMaxAI/MiniMax-H3"
    :protocol "OpenRouter-compatible /api/v1/videos"
    :hosting "Baseten async · scale-to-zero"
    :state "API入口稼働 · モデル推論未公開"
    :image "/img/hokusai-washi.webp"
    :image-alt "生成した和紙の上を、音と時間の流れを思わせる藍と墨の線が横切る抽象画"
    :image-caption "Hokusai — 音と映像のための生成ビジュアル"
    :features [["構図" "流れをつくる" "一枚の見栄えではなく、カットの連続性と時間の設計を評価します。"]
               ["音景" "音まで一つに" "映像と音声を別工程にせず、同じ演出意図から扱う構成です。"]
               ["非同期" "待ち時間を正直に" "submit、poll、content download のジョブ形式で長い生成を安全に扱います。"]]}})

(defn model-social-head [{:keys [slug name description]}]
  (let [url (str "https://oppai.fans/" slug "/")
        image "https://oppai.fans/og/basho-hokusai.png"
        title (str name " — 日本語の感性を、生成モデルへ。")]
    [[:link {:rel "canonical" :href url}]
     [:meta {:property "og:type" :content "website"}]
     [:meta {:property "og:site_name" :content "oppai.fans"}]
     [:meta {:property "og:title" :content title}]
     [:meta {:property "og:description" :content description}]
     [:meta {:property "og:url" :content url}]
     [:meta {:property "og:image" :content image}]
     [:meta {:property "og:image:width" :content "1200"}]
     [:meta {:property "og:image:height" :content "630"}]
     [:meta {:property "og:image:alt" :content "Basho / Hokusai — 日本語の感性を、生成モデルへ。"}]
     [:meta {:name "twitter:card" :content "summary_large_image"}]
     [:meta {:name "twitter:title" :content title}]
     [:meta {:name "twitter:description" :content description}]
     [:meta {:name "twitter:image" :content image}]]))

(defn model-page-body [{:keys [slug name kanji kicker title lead base protocol hosting state
                               image image-alt image-caption features]}]
  [:div {:class "oppai-lp"}
   [:header {:class "oppai-lp-header"}
    [:a {:class "oppai-lp-brand" :href "/"}
     [:img {:class "oppai-lp-brand-logo"
            :src "/img/oppai-network-logo.png"
            :alt "oppai.fans"
            :width 410 :height 128}]
     [:span "日本語の感性を、生成モデルへ。"]]
    [:nav {:class "oppai-lp-nav" :aria-label "モデル"}
     [:a {:href "/basho/" :aria-current (when (= slug "basho") "page")} "Basho"]
     [:a {:href "/hokusai/" :aria-current (when (= slug "hokusai") "page")} "Hokusai"]
     [:a {:href "/"} "生成スタジオ"]]]
   [:main
    [:section {:class "oppai-lp-hero"}
     [:div {:class "oppai-lp-copy"}
      [:p {:class "oppai-lp-kicker"} kicker]
      [:h1 {:class "oppai-lp-title"} title]
      [:p {:class "oppai-lp-lead"} lead]
      [:div {:class "oppai-lp-actions"}
       (ui/button "生成スタジオを見る" {:variant :primary :href "/"})
       (ui/button (str (if (= slug "basho") "Hokusai" "Basho") " も見る")
                  {:variant :secondary
                   :href (if (= slug "basho") "/hokusai/" "/basho/")})]]
     [:div {:class "oppai-lp-mark" :aria-label (str name "、" kanji)}
      kanji
      [:span {:class "oppai-lp-seal" :aria-hidden "true"} "oppai"]]]

    [:figure {:class "oppai-lp-art"}
     [:div {:class "oppai-lp-art-frame"}
      [:img {:src image
             :alt image-alt
             :width 1448
             :height 1086
             :loading "eager"
             :fetchpriority "high"}]]
     [:figcaption
      [:span image-caption]
      [:span "Generative artwork · oppai.fans"]]]

    [:section {:class "oppai-lp-band"}
     [:div {:class "oppai-lp-section"}
      [:div {:class "oppai-lp-section-head"}
       [:h2 "日本語から、設計する。"]
       [:p {:class "oppai-lp-section-intro"}
        "翻訳後の日本語ではなく、日本語の文脈を出発点にする。独自データセットでの fine-tune と評価を経て、根拠のある日本語モデルとして公開します。"]]
      (into [:div {:class "oppai-lp-grid"}]
            (map-indexed
             (fn [idx [eyebrow heading copy]]
               [:article {:class "oppai-lp-feature"}
                [:span {:class "oppai-lp-feature-no"}
                (str (if (< (inc idx) 10) "0" "") (inc idx) " · " eyebrow)]
                [:h3 heading]
                [:p copy]])
             features))]]

    [:section {:class "oppai-lp-section"}
     [:div {:class "oppai-lp-status"}
      [:div
       [:p {:class "oppai-lp-kicker"} "Current status"]
       [:h2 "公開前だからこそ、正直に。"]]
      [:div
       [:div {:class "oppai-lp-status-note"}
        (ui/chip "開発中" {:color "yellow" :style "filled-1"})
        [:p "APIの入口は先に整えていますが、fine-tune 済みの immutable checkpoint と推論 backend はまだ公開していません。別モデルの名前替えは行いません。"]]
       [:dl {:class "oppai-lp-specs"}
        [:div [:dt "Base"] [:dd base]]
        [:div [:dt "API"] [:dd protocol]]
        [:div [:dt "Hosting"] [:dd hosting]]
        [:div [:dt "State"] [:dd state]]]]]]

    [:section {:class "oppai-lp-band"}
     [:div {:class "oppai-lp-section"}
      [:div {:class "oppai-lp-section-head"}
       [:h2 "日本から、世界のAPIへ。"]
       [:p {:class "oppai-lp-section-intro"}
        "開発・提供・基盤を分け、同じモデル revision を確認してから公開します。OpenRouter への掲載は、推論・価格・容量・データ方針の検証後です。"]]
      [:div {:class "oppai-lp-route" :aria-label "提供経路"}
       [:div {:class "oppai-lp-route-step"} [:strong "01 · oppai.fans"] [:span "モデル開発と日本語評価"]]
       [:div {:class "oppai-lp-route-step"} [:strong "02 · murakumo.cloud"] [:span "認証、routing、metering"]]
       [:div {:class "oppai-lp-route-step"} [:strong "03 · Baseten"] [:span "scale-to-zero 推論基盤"]]
       [:div {:class "oppai-lp-route-step"} [:strong "04 · OpenRouter"] [:span "審査後の公開API"]]]]]]
   [:footer {:class "oppai-lp-footer"}
    [:span (str "© 2026 oppai.fans · " name)]
    [:span "Designed on jp-go-dds · Inference by murakumo.cloud"]]])

(defn render-model-html [model]
  (dds-page/->page
   {:title (str (:name model) " — 日本語の感性を、生成モデルへ。 | oppai.fans")
    :lang "ja"
    :description (:description model)
    :css (dds-css)
    :app-css (str tokens/skin-css "\n" (render-model-css))
    :head (model-social-head model)}
   (model-page-body model)))

(defn render-html
  "The bundle src comes from the build manifest, so the HTML always points at
  the hash that was actually emitted."
  ([] (render-html (dds-css) #?(:clj (bundle-path) :cljs default-bundle-path)))
  ([css-text] (render-html css-text #?(:clj (bundle-path) :cljs default-bundle-path)))
  ([css-text bundle]
   (dds-page/->page
    {:title page-title
     :lang "ja"
     :description page-description
     :css css-text
     :app-css (str tokens/skin-css "\n" (render-css))
     :head age-gate-head}
    [:div {:id "app"}]
    [:script {:src bundle}])))

(defn hex-color-count
  "Raw hex literals in a CSS string. Under DADS there is no theme map, so the
  vendored stylesheet is the only legitimate home for a color literal."
  [css-str]
  (count (re-seq #"#[0-9a-fA-F]{3,8}\b" css-str)))

#?(:clj
   (defn generate! []
     (let [bundle (bundle-path)]
       ;; Loud, because shipping the unhashed fallback is the failure this
       ;; whole mechanism exists to prevent. `npm run deploy` builds first.
       (when (= default-bundle-path bundle)
         (println "WARNING: no public/assets/manifest.edn — falling back to"
                  default-bundle-path
                  "\n         run the release build BEFORE generating the shell,"
                  "or the deploy ships a cache-vulnerable bundle path."))
       (io/make-parents (io/file "public" "index.html"))
       (spit (io/file "public" "index.html") (str (render-html (dds-css) bundle) "\n"))
       (doseq [[_ model] model-pages]
         (let [target (io/file "public" (:slug model) "index.html")]
           (io/make-parents target)
           (spit target (str (render-model-html model) "\n"))
           (println "wrote" (.getPath target) "(DADS + washi LP)")))
       (println "wrote public/index.html (DADS inlined, bundle" bundle ")"))))

#?(:clj (defn -main [& _args] (generate!)))
