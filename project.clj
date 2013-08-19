(defproject
  wlmmap "0.0.1"
  :url "http://github.com/bzg/wlmmap"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies
  [[org.clojure/clojure "1.5.1"]
   [lib-noir "0.6.6"]
   [com.cemerick/friend "0.1.5"]
   [compojure "1.1.5"]
   [cheshire "5.2.0"]
   [com.taoensso/carmine "2.0.0"]
   [ring-server "0.2.8"]
   [com.taoensso/tower "2.0.0-beta1"]
   [com.draines/postal "1.10.4"]]
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
  [[lein-ring "0.8.5"]]
  :description "Wlmmap: Wiki Loves Monuments Map"
  :min-lein-version "2.0.0")
