(ns node-io
  "Node fs hooks for `oppai.gen.site`, which names no host module of its own.

  `site.cljc` reads two things a JVM would ask the classpath and `java.io`
  for — the vendored `jp_go_dds/dds.css` and the shadow-cljs build manifest —
  and writes the generated pages. Under nbb there is no classpath, so the
  resource roots are named here.

  `OPPAI_RESOURCE_PATH` overrides the roots (`:`-separated). The default is
  the monorepo sibling checkout, which is exactly what the deps.edn `:local`
  alias pointed at, so a bare clone is no worse off than it was before."
  (:require ["fs" :as fs]
            ["path" :as path]
            [kotoba.lang.text :as str]
            [oppai.gen.site :as site]))

(def resource-roots
  (let [env (some-> (aget js/process.env "OPPAI_RESOURCE_PATH") str/trim not-empty)]
    (if env
      (str/split env #":")
      ["../../kotoba-lang/jp-go-digital-design-system/resources"])))

(defn- slurp-if-present [p]
  (when (fs/existsSync p) (fs/readFileSync p "utf8")))

(defn read-resource [rel]
  (some #(slurp-if-present (path/join % rel)) resource-roots))

(defn install! []
  (site/install-io!
   {:resource read-resource
    :file     slurp-if-present
    :write!   (fn [rel text]
                (fs/mkdirSync (path/dirname rel) #js {:recursive true})
                (fs/writeFileSync rel text))}))
