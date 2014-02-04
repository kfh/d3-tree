  (defproject d3-tree "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [compojure "1.1.6"]
                 [org.clojure/clojurescript "0.0-2138"]
                 [net.drib/mrhyde "0.5.3"]
                 [net.drib/strokes "0.5.1"]
                 [ring/ring-json "0.2.0"]
                 [clj-http "0.7.8"]
                 [me.raynes/laser "1.0.0"]]
  :plugins [[lein-ring "0.8.10"]
            [lein-cljsbuild "1.0.1"]]
  :cljsbuild {
    :builds [{
      :source-paths ["src-cljs"]
      :compiler {
        :output-to "resources/public/js/tree.js"
        :optimizations :whitespace
        :pretty-print false}}]}
  :ring {:handler d3-tree.web.handler/app
         :init d3-tree.web.handler/lein-ring-init}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring-mock "0.1.5"]]}})
