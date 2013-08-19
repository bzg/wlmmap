(ns wlmmap.handler
  (:require [noir.util.middleware :as middleware]
            [compojure.core :as compojure :refer (GET POST defroutes)]
            (compojure [handler :as handler]
                       [route :as route])
            [hiccup.page :as h]
            [hiccup.element :as e]))

(defn init 
  "Called when the application starts."
  [] (str "init"))

(defn destroy
  "Called when the application shuts down."
  []
  (str "Destroy"))

(defn- index [params]
  (h/html5
   (h/include-css "/css/generic.css")
   (h/include-css "/css/MarkerCluster.Default.css")
   (h/include-css "/css/MarkerCluster.Default.ie.css")
   (h/include-css "/css/MarkerCluster.css")
   (h/include-css "http://cdn.leafletjs.com/leaflet-0.6.4/leaflet.css")
   (h/include-js "http://cdn.leafletjs.com/leaflet-0.6.4/leaflet.js")
   (if (= (:lang params) "fr")
     (h/include-js "/js/fr_fr_noimg.js")
     (h/include-js "/js/mon.js"))
   (h/include-js "/js/leaflet.markercluster.js")
   ;; [:h1 "WLM Map (work in progress)"]
   [:div {:id "map"}]
   (h/include-js "/js/map.js")))

(defroutes app-routes 
  (GET "/" {params :params} (index params))
  (route/resources "/")
  (route/not-found "Not found"))

(def app (middleware/app-handler
          [app-routes]
          :middleware []
          :access-rules []))

(def war-handler (middleware/war-handler app))
