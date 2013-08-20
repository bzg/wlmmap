(ns wlmmap.handler
  (:require [clojure.data.json :as json]
            [clojure.string]
            [ring.util.codec :as r]
            [noir.util.middleware :as middleware]
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

(defn- build-tsreq [params]
  (if (empty? params)
    (str "http://toolserver.org/~erfgoed/api/api.php?action=search&format=json&srlang=fr&srcountry=fr&limit=1000")
    (str "http://toolserver.org/~erfgoed/api/api.php?action=search&format=json&"
         "limit=" (or (:limit params) 1000)
         (if (:srlang params) (str "&srlang=" (:srlang params)))
         (if (= (:srwithimage params) "1")
           "&srwithimage=1&srwithoutimage=0"
           "&srwithimage=0&srwithoutimage=1")
         "&srcountry=" (or (:srcountry params) "fr"))))

(defn- build-js [monuments]
  (let [init
        "<script type='text/javascript'>
var map = L.map('map').setView([48, 1.2], 2);
L.tileLayer('http://{s}.tile.cloudmade.com/3068c9a9c9b648cb910837cf3c5fce10/997/256/{z}/{x}/{y}.png', {
    attribution: 'Map data &copy; <a href=\"http://openstreetmap.org\">OpenStreetMap</a> contributors, <a href=\"http://creativecommons.org/licenses/by-sa/2.0/\">CC-BY-SA</a>, Imagery © <a href=\"http://cloudmade.com\">CloudMade</a>',
    maxZoom: 18
}).addTo(map);
var markers = L.markerClusterGroup();\n"
        end
        "map.addLayer(markers);
</script>"
        fmt-string
        "var a%slegend = \"%s\";
        var a%smarker = L.marker(new L.LatLng(%s, %s), { title: a%slegend });
        a%smarker.bindPopup(a%slegend);
        markers.addLayer(a%smarker);\n"]
    (str init
         (clojure.string/join
          "\n"
          (for [m monuments]
            (if (and (:lat m) (:lon m))
              (format fmt-string (:id m)
                      (str (clojure.string/replace (:name m) #"[\n\r]" "")
                           "<br/>"
                           "<a href=\\\"http://commons.wikimedia.org/wiki/File:" (r/url-encode (:image m)) "\\\">"(:image m)"</a><br/>")
                      (:id m) (:lat m) (:lon m) (:id m) (:id m) (:id m) (:id m)))))
         end)))

(defn- index [params]
  (h/html5
   (h/include-css "/css/generic.css"
                  "/css/MarkerCluster.Default.css"
                  "/css/MarkerCluster.Default.ie.css"
                  "/css/MarkerCluster.css"
                  "http://cdn.leafletjs.com/leaflet-0.6.4/leaflet.css")
   (h/include-js "http://cdn.leafletjs.com/leaflet-0.6.4/leaflet.js"
                 "/js/leaflet.markercluster.js")
   [:form {:method "POST" :action "/" :class "main"}
    "With images:" 
    [:input {:type "hidden" :name "srwithimage" :value "0"}]
    [:input (if (= (:srwithimage params) "1")
              {:type "checkbox" :name "srwithimage" :value "1" :checked "checked"}
              {:type "checkbox" :name "srwithimage" :value "1"})]
    "&nbsp;"
    "Max:" [:input {:type "text-area" :name "limit" :value (:limit params)}]
    "&nbsp;"
    "Wikipedia (2 letters):" [:input {:type "text-area" :name "srcountry" :value (:srcountry params)}]
    [:input {:type "submit" :value "Go"}]]

   [:div {:id "map"}]
   (build-js (seq
              (:monuments
               (json/read-str (slurp (build-tsreq params))
                              :key-fn keyword))))))

(defroutes app-routes 
  (GET "/" {params :params} (index params))
  (POST "/" {params :params} (index params))
  (route/resources "/")
  (route/not-found "Not found"))

(def app (middleware/app-handler
          [app-routes]
          :middleware []
          :access-rules []))

(def war-handler (middleware/war-handler app))
