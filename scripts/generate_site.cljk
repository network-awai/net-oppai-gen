(ns generate-site
  "`npm run generate-site` — writes public/index.html and the model LPs.

  JVM-free replacement for the old `clojure -M:local:generate-site`
  (owner decision 2026-09-06). Same generator: `oppai.gen.site/generate!`."
  (:require [node-io]
            [oppai.gen.site :as site]))

(node-io/install!)

(when-not (seq (site/dds-css))
  (println "ERROR: jp_go_dds/dds.css read as empty — refusing to write a page with no stylesheet.")
  (js/process.exit 2))

(site/generate!)
