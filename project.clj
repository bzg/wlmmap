(defproject
  wlmmap "0.1.2"
  :url "https://github.com/bzg/wlmmap"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :repositories {"sonatype-oss-public"
                 "https://oss.sonatype.org/content/groups/public/"}
  :dependencies
  [[org.clojure/clojure "1.6.0"]
   [org.clojure/clojurescript "0.0-1859"]
   [org.clojure/core.async "0.1.0-SNAPSHOT"]
   [lib-noir "0.8.9"]
   [net.drib/blade "0.1.0"]
   [compojure "1.1.9"]
   [http-kit "2.1.19"]
   [org.clojure/data.json "0.2.5"]
   [ring-server "0.3.1"]
   [shoreleave/shoreleave-remote-ring "0.3.0"]
   [shoreleave/shoreleave-remote "0.3.0"]
   [domina "1.0.2"]
   [com.taoensso/tower "3.0.1"]]
  :plugins
  [[lein-cljsbuild "0.3.2"]
   [lein-ring "0.8.5"]]
  :cljsbuild {:builds
              [{:source-paths ["src/cljs"]
                :compiler
                {:output-to "resources/public/js/main.js"
                 :optimizations :simple
                 :pretty-print false}}]}
  :description "Wlmmap: Wiki Loves Monuments Map"
  :min-lein-version "2.0.0"
  :main wlmmap.handler)
