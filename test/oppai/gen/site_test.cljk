(ns oppai.gen.site-test
  (:require [clojure.test :refer [deftest is]]
            [kotoba.lang.text :as str]
            [oppai.gen.site :as site]
            [oppai.gen.ui :as ui]))

(deftest the-shell-is-a-dads-document
  (let [html (site/render-html)]
    (is (str/starts-with? html "<!DOCTYPE html>"))
    (is (str/includes? html "<html lang=\"ja\">"))
    (is (str/includes? html "<div id=\"app\"></div>"))
    (is (re-find #"<script src=\"/assets/main[^\"]*\.js\">" html))
    ;; jp-go-dds inlines the bundle — a <link> would mean the port did not
    ;; actually happen.
    (is (not (str/includes? html "rel=\"stylesheet\"")))
    (is (str/includes? html ".dads-button"))
    ;; light-only, in both the meta and the CSS
    (is (str/includes? html "name=\"color-scheme\" content=\"light\""))
    (is (str/includes? html "color-scheme: light"))
    ;; the --hig-* bridge rides along
    (is (str/includes? html "--hig-color-tint: var(--color-key-900)"))))

(deftest the-shell-quotes-the-bundle-the-build-emitted
  ;; The HTML must name the hashed file shadow-cljs actually wrote, not a
  ;; guess. An unhashed `/assets/main.js` is the path a stale CDN entry can
  ;; outlive — the sibling apex shipped a fresh index.html next to a
  ;; 30-day-old bundle and the new stylesheet landed on the old app.
  (is (= "/assets/main.ABC12345.js"
         (site/bundle-path-from-manifest
          [{:module-id :main :name :main :output-name "main.ABC12345.js"}])))
  (is (= site/default-bundle-path (site/bundle-path-from-manifest []))
      "no manifest yet (fresh clone, tests before a release build) -> fallback")
  (let [html (site/render-html (site/dds-css) "/assets/main.ABC12345.js")]
    (is (str/includes? html "<script src=\"/assets/main.ABC12345.js\">"))
    (is (not (str/includes? html "/assets/main.js\">")))))

(deftest layout-css-is-token-only
  (let [css (site/render-css)]
    (is (zero? (site/hex-color-count css))
        "app CSS must be token-only — DADS has no theme map to put a hex in")
    (is (str/includes? css "var(--hig-"))
    (is (not (str/includes? css "prefers-color-scheme"))
        "DADS is light-only: no hand-maintained second palette")))

(deftest every-oppai-primitive-has-a-rule
  ;; These are the components DADS does not ship, so nothing else styles them.
  ;; A missing rule renders as an unstyled span and is easy to miss.
  (let [css (site/render-css)]
    (doseq [sel [".oppai-shell" ".oppai-header" ".oppai-tabs" ".oppai-tab"
                 ".oppai-brand-logo"
                 ".oppai-model-banner" ".oppai-banner-model" ".oppai-banner-art"
                 ".oppai-studio-header"
                 ".oppai-studio" ".oppai-form" ".oppai-thread" ".oppai-avatar"
                 ".oppai-panel" ".oppai-bubble" ".oppai-composer" ".oppai-result"
                 ".oppai-meter" ".oppai-progress" ".oppai-note" ".oppai-footer"]]
      (is (str/includes? css sel) (str "no rule for " sel)))))

(deftest generation-is-deterministic
  (is (= (site/render-html) (site/render-html))))

(deftest model-pages-keep-dads-and-tell-the-truth
  (doseq [[id model] site/model-pages]
    (let [html (site/render-model-html model)]
      (is (str/includes? html ".dads-button") (str id " keeps DADS"))
      (is (str/includes? html "oppai-lp") (str id " has the editorial layer"))
      (is (str/includes? html (:base model)) (str id " attributes its base"))
      (is (str/includes? html (:image model)) (str id " includes its generated art"))
      (is (str/includes? html (:image-alt model)) (str id " describes its generated art"))
      (is (str/includes? html "/img/oppai-network-logo.png") (str id " includes the brand logo"))
      (is (str/includes? html "開発中") (str id " is not presented as live"))
      (is (str/includes? html "推論未公開") (str id " discloses inference state"))
      (is (str/includes? html "https://oppai.fans/og/basho-hokusai.png"))
      (is (not (str/includes? html "今すぐ利用可能"))))))

(deftest model-page-css-is-token-only-and-washi-inspired
  (let [css (site/render-model-css)]
    (is (zero? (site/hex-color-count css)))
    (is (str/includes? css "repeating-linear-gradient"))
    (is (str/includes? css "writing-mode: vertical-rl"))
    (is (str/includes? css "var(--hig-"))))

(deftest button-keeps-both-the-dads-class-and-the-callers
  ;; jp-go-dds merges its own :class after the caller's :attrs, so a class
  ;; passed through :attrs is dropped silently. The seam appends instead.
  (let [cls (get-in (ui/button "送信" {:class "oppai-send"}) [1 :class])]
    (is (str/includes? cls "dads-button"))
    (is (str/includes? cls "oppai-send"))))

(deftest button-emphasis-maps-onto-the-three-dads-levels
  (is (= "solid-fill" (get-in (ui/button "x" {:variant :primary}) [1 :data-type])))
  (is (= "outline" (get-in (ui/button "x" {:variant :secondary}) [1 :data-type])))
  (is (= "text" (get-in (ui/button "x" {:variant :ghost}) [1 :data-type])))
  (is (= "outline" (get-in (ui/button "x") [1 :data-type]))))

(deftest select-marks-the-current-value-selected
  ;; jp-go-dds documents a real incident: omit :value and the FIRST option
  ;; silently renders selected while looking correct.
  (let [node (ui/select {:value "b"} [["a" "A"] ["b" "B"]])
        selected (->> (tree-seq coll? seq node)
                      (filter #(and (vector? %) (= :option (first %))
                                    (:selected (second %))))
                      first)]
    (is (= "b" (:value (second selected))))))

(deftest model-option-surfaces-what-the-fleet-said
  (let [[value label] (ui/model-option {:model-id "ltxv-2b-0.9.1"
                                        :label "LTX 2b 0.9.1"
                                        :nodes ["issachar" "naphtali"]
                                        :queue 3 :exact? false})]
    (is (= "ltxv-2b-0.9.1" value) "the wire value stays the model id")
    ;; A picker that hides queue depth invites the user to pick the busy node.
    (is (str/includes? label "issachar/naphtali"))
    (is (str/includes? label "待ち 3"))
    (is (str/includes? label "推定") "an unconfirmed id is labelled, not smoothed over")))

(deftest tab-bar-marks-the-current-surface-for-assistive-tech
  ;; Links, not buttons: a view is an address (ADR-2608080100), so the nav
  ;; is real anchors to the fragment and the back button works.
  (let [node (ui/tab-bar {:items [{:id :chat :label "チャット" :icon "◇" :fragment "chat"}
                                  {:id :image :label "画像" :icon "▣" :fragment "image"}]
                          :active :image})
        links (->> (tree-seq coll? seq node)
                   (filter #(and (vector? %) (= :a (first %)))))]
    (is (= 2 (count links)))
    (is (= ["#chat" "#image"] (mapv #(:href (second %)) links)))
    (is (= [nil "page"] (mapv #(:aria-current (second %)) links)))))
