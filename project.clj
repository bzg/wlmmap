(defproject
  wlmmap "0.0.7"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :repositories {"sonatype-oss-public"
                 "https://oss.sonatype.org/content/groups/public/"}
  :dependencies
  [[org.clojure/clojure "1.5.1"]
   [org.clojure/clojurescript "0.0-1859"]
   ;; [org.clojure/clojurescript "0.0-1877"]
   [org.clojure/core.async "0.1.0-SNAPSHOT"]
   [lib-noir "0.6.8"]
   [midje "1.5.1"]
   [com.cemerick/friend "0.1.5"]
   [net.drib/blade "0.1.0"]
   [compojure "1.1.5"]
   [com.taoensso/carmine "2.2.0"]
   [org.clojure/data.json "0.2.2"]
   [ring-server "0.2.8"]
   [shoreleave/shoreleave-remote-ring "0.3.0"]
   [shoreleave/shoreleave-remote "0.3.0"]
   [domina "1.0.2-SNAPSHOT"]
   [com.taoensso/tower "2.0.0-beta5"]]
  :ring
  {:handler wlmmap.handler/war-handler,
   :init wlmmap.handler/init,
   :destroy wlmmap.handler/destroy}
  :profiles
  {:production
   {:ring
    {:open-browser? false, :stacktraces? false, :auto-reload? false}},
   :dev
   {:dependencies [[ring-mock "0.1.5"] [ring "1.2.0"]]}}
  :url "http://wlmmap.herokuapp.com"
  :plugins
  [[lein-cljsbuild "0.3.2"]
   [lein-ring "0.8.5"]]
  :cljsbuild {:builds
              [{:source-paths ["src/cljs"]
                :compiler
                {:output-to "resources/public/js/main.js"
                 :optimizations :simple
                 :pretty-print false}}]}
  :java-agents [[com.newrelic.agent.java/newrelic-agent "2.21.4"]]
  :description "Wlmmap: Wiki Loves Monuments Map"
  :min-lein-version "2.0.0")
