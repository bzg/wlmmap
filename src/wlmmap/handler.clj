(ns wlmmap.handler
  (:require [ring.util.codec :as codec]
            [ring.util.response :as resp]
            [cheshire.core :as json]
            [noir.util.middleware :as middleware]
            [noir.session :as session]
            [cemerick.friend :as friend]
            (cemerick.friend [workflows :as workflows]
                             [credentials :as creds])
            [cemerick.friend.credentials :refer (hash-bcrypt)]
            [compojure.core :as compojure :refer
             (GET POST defroutes)]
            (compojure [handler :as handler]
                       [route :as route])
            [hiccup.page :as h]
            [hiccup.element :as e]
            [taoensso.carmine :as car]))

(defn init 
  "Called when the application starts."
  [] (str "init"))

(defn destroy
  "Called when the application shuts down."
  []
  (str "Destroy"))

(defn wrap-friend [handler]
  "Wrap friend authentication around handler."
  (friend/authenticate
   handler
   {:allow-anon? true
    :workflows [(workflows/interactive-form
                 :allow-anon? true
                 :login-uri "/login"
                 :default-landing-uri "/login"
                 :credential-fn
                 (partial creds/bcrypt-credential-fn load-user))]}))

(defn- index [req]
  (h/html5
   (h/include-css "/css/generic.css")
   (h/include-css "/css/MarkerCluster.Default.css")
   (h/include-css "/css/MarkerCluster.Default.ie.css")
   (h/include-css "/css/MarkerCluster.css")
   (h/include-css "http://cdn.leafletjs.com/leaflet-0.6.4/leaflet.css")
   (h/include-js "http://cdn.leafletjs.com/leaflet-0.6.4/leaflet.js")
   (h/include-js "/js/mon.js")
   (h/include-js "/js/leaflet.markercluster.js")
   ;; [:h1 "WLM Map (work in progress)"]
   [:div {:id "map"}]
   (h/include-js "/js/map.js")))

(defroutes app-routes 
  (GET "/" req (index req))
  (route/resources "/")
  (route/not-found "Not found"))

(def app (middleware/app-handler
          [(wrap-friend app-routes)]
           :middleware []
           :access-rules []))

(def war-handler (middleware/war-handler app))
