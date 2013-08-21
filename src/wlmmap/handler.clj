(ns wlmmap.handler
  (:require [clojure.data.json :as json]
            [clojure.string]
            [taoensso.carmine :as car]
            [ring.util.codec :as r]
            [noir.util.middleware :as middleware]
            [compojure.core :as compojure :refer (GET POST defroutes)]
            (compojure [handler :as handler]
                       [route :as route])
            [hiccup.page :as h]
            [hiccup.element :as e]))

(defn init 
  "Called when the application starts."
  []
  (str "init"))

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

(defn- cleanup-name [n]
  (-> n
      (clojure.string/replace #"[\n\r]" "")
      (clojure.string/replace #"\"" "\\\\\"")))

(defn- build-js [monuments witharticle]
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
        a%smarker.bindPopup(a%slegend,{minWidth:270});
        markers.addLayer(a%smarker);\n"]
    (str init
         (clojure.string/join
          "\n"
          (for [m monuments
                :let [article (:monument_article m)]
                :when (if (= witharticle "1") (not (= "" article)) (= "" article))]
            (if (and (:lat m) (:lon m))
              (format fmt-string (:id m)
                      (str (if (not (= "" (:monument_article m)))
                             (str "<a href=\\\"http://" (:lang m) ".wikipedia.org/wiki/"
                                  (r/url-encode (:monument_article m))
                                  "\\\">"
                                  (cleanup-name (:name m))
                                  "</a>")
                             (cleanup-name (:name m)))
                           "<br/>"
                           (str "<img src=\\\"https://commons.wikimedia.org/w/index.php?title=Special%3AFilePath&file=" 
                                (r/url-encode (:image m))
                                "&width=250\\\" />"
                                "<br/>")
                           "<a href=\\\"http://commons.wikimedia.org/wiki/File:"
                           (r/url-encode (:image m)) "\\\">"
                           (:image m)
                           "</a><br/>")
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
    "With article:" 
    [:input {:type "hidden" :name "witharticle" :value "0"}]
    [:input (if (= (:witharticle params) "1")
              {:type "checkbox" :name "witharticle" :value "1" :checked "checked"}
              {:type "checkbox" :name "witharticle" :value "1"})]
    "&nbsp;"
    "Max:" [:input {:type "text-area" :name "limit" :value (:limit params)}]
    "&nbsp;"
    "Wikipedia (2 letters):" [:input {:type "text-area" :name "srcountry" :value (:srcountry params)}]
    [:input {:type "submit" :value "Go"}]]

   [:div {:id "map"}]
   (build-js (seq
              (:monuments
               (json/read-str (slurp (build-tsreq params))
                              :key-fn keyword)))
             (:witharticle params))))

(def server1-conn
  {:pool {} :spec {:uri (System/getenv "REDISTOGO_URL")}})

(defmacro wcar* [& body]
  `(car/wcar server1-conn ~@body))

(defn- storemons
  "interface to select which lang/country to store"
  []
  (h/html5
   [:h1 "Select lang and country to store"]
   [:form {:method "POST" :action "/storemons0"}
    "Lang:" [:input {:type "text-area" :name "lang" :value "fr"}]
    "Country:" [:input {:type "text-area" :name "country" :value "fr"}]
    [:input {:type "submit" :value "Go"}]]))

(defn- storemons0
  "store all monuments from a request"
  [params]
  (let [cntry (:country params)
        lang (:lang params)
        req (str "http://toolserver.org/~erfgoed/api/api.php?action=search&format=json&limit=5000"
                 "&srcountry=" cntry "&srlang=" lang
                 (when (not (= (:cont params) ""))
                   (str "&srcontinue=" (:cont params))))
        set (str cntry lang)
        res (json/read-str (slurp req) :key-fn keyword)
        next (or (:srcontinue (:continue res)) "")]
    (do (doall (map
                #(wcar* (do (car/set (:id %) (str %))
                            (car/sadd set (:id %))
                            (when (not (= (:monument_article %) ""))
                              (car/sadd (str set "ar") (:id %)))
                            (when (not (= (:image %) ""))
                              (car/sadd (str set "im") (:id %)))))
                (:monuments res)))
        (h/html5
         [:h1 (format "Store monuments for country %s and lang %s into \"%s\""
                      (:country params) (:lang params) set)]
         [:form {:method "POST" :action "/storemons0" :class "main"}
          [:h2 "Current continuation"] (:cont params)
          [:h2 "Done"] (wcar* (car/scard set))
          [:h2 "Next"]
          [:input {:type "hidden" :name "country" :value (:country params)}]
          [:input {:type "hidden" :name "lang" :value (:lang params)}]
          [:input {:type "text-area" :name "cont" :value next}]
          [:input {:type "submit" :value "go"}]]))))

(defroutes app-routes 
  (GET "/" {params :params} (index params))
  (POST "/" {params :params} (index params))
  (GET "/storemons" [] (storemons))
  (POST "/storemons0" {params :params} (storemons0 params))
  (route/resources "/")
  (route/not-found "Not found"))

(def app (middleware/app-handler
          [app-routes]
          :middleware []
          :access-rules []))

(def war-handler (middleware/war-handler app))
