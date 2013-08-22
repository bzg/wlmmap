(ns wlmmap.handler
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [cemerick.friend :as friend]
            (cemerick.friend [workflows :as workflows]
                             [credentials :as creds])
            [cemerick.friend.credentials :refer (hash-bcrypt)]
            [taoensso.carmine :as car]
            [ring.util.codec :as codec]
            [ring.util.response :as resp]
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
         (str/join
          "\n"
          (for [m monuments
                :let [article (:monument_article m)]
                :when (if (= witharticle "1") (not (= "" article)) (= "" article))]
            (if (and (:lat m) (:lon m))
              (format fmt-string (:id m)
                      (str (if (not (= "" (:monument_article m)))
                             (str "<a href=\\\"http://" (:lang m) ".wikipedia.org/wiki/"
                                  (codec/url-encode (:monument_article m))
                                  "\\\">"
                                  (cleanup-name (:name m))
                                  "</a>")
                             (cleanup-name (:name m)))
                           "<br/>"
                           (str "<img src=\\\"https://commons.wikimedia.org/w/index.php?title=Special%3AFilePath&file=" 
                                (codec/url-encode (:image m))
                                "&width=250\\\" />"
                                "<br/>")
                           "<a href=\\\"http://commons.wikimedia.org/wiki/File:"
                           (codec/url-encode (:image m)) "\\\">"
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

(def users (atom {"bzg" {:username "bzg"
                         :password (hash-bcrypt "tintin")
                         :roles #{::user}}}))

(def lang-pairs
{
73, ["ad" 	"ca"]
0, ["aq" 	"en"]
1, ["ar" 	"es"]
2, ["at" 	"de"]
3, ["be-bru" 	"nl"]
4, ["be-vlg" 	"en"]
5, ["be-vlg" 	"fr"]
6, ["be-vlg" 	"nl"]
7, ["be-wal" 	"en"]
8, ["be-wal" 	"fr"]
9, ["be-wal" 	"nl"]
10, ["bo" 	"es"]
11, ["by" 	"be-x-old"]
12, ["ca" 	"en"]
13, ["ca" 	"fr"]
14, ["ch" 	"fr"]
15, ["ch-old" 	"de"]
16, ["ch-old" 	"en"]
17, ["ch-old" 	"it"]
18, ["cl" 	"es"]
19, ["co" 	"es"]
20, ["cz" 	"cs"]
21, ["de-by" 	"de"]
22, ["de-he" 	"de"]
23, ["de-nrw" 	"de"]
24, ["de-nrw-bm" 	"de"]
25, ["de-nrw-k" 	"de"]
26, ["dk-bygning" 	"da"]
27, ["dk-fortids" 	"da"]
28, ["ee" 	"et"]
29, ["es" 	"ca"]
30, ["es" 	"es"]
31, ["es" 	"gl"]
32, ["fr" 	"ca"]
33, ["fr" 	"fr"]
34, ["gb-eng" 	"en"]
35, ["gb-nir" 	"en"]
36, ["gb-sct" 	"en"]
37, ["gb-wls" 	"en"]
38, ["gh" 	"en"]
39, ["ie" 	"en"]
40, ["il" 	"he"]
41, ["in" 	"en"]
42, ["it" 	"it"]
43, ["it-88" 	"ca"]
44, ["it-bz" 	"de"]
45, ["ke" 	"en"]
46, ["lu" 	"lb"]
47, ["mt" 	"de"]
48, ["mx" 	"es"]
49, ["nl" 	"nl"]
50, ["nl-gem" 	"nl"]
51, ["no" 	"no"]
52, ["pa" 	"es"]
53, ["ph" 	"en"]
54, ["pk" 	"en"]
55, ["pl" 	"pl"]
56, ["pt" 	"pt"]
57, ["ro" 	"ro"]
58, ["rs" 	"sr"]
59, ["ru" 	"ru"]
60, ["se-bbr" 	"sv"]
61, ["se-fornmin" 	"sv"]
62, ["se-ship" 	"sv"]
63, ["sk" 	"de"]
64, ["sk" 	"sk"]
65, ["th" 	"th"]
66, ["tn" 	"fr"]
67, ["ua" 	"uk"]
68, ["us" 	"en"]
69, ["us-ca" 	"en"]
70, ["uy" 	"es"]
71, ["ve" 	"es"]
72, ["za" 	"en"]
})

(defn- storemons
  "interface to select which lang/country to store"
  [req]
  (h/html5
   [:h1 "Select lang and country to store"]
   [:table
    [:tr
     [:td {:style "width: 100px;"} "#"]
     [:td {:style "width: 100px;"} "Lang"]
     [:td {:style "width: 100px;"} "Country"]
     [:td {:style "width: 200px;"} "Last updated"]
     [:td {:style "width: 300px;"} "Continue from"]
     ]]
   (doall (map
           #(let [fval (first (val %))
                  lval (last (val %))
                  hset (str "h" fval lval)
                  cont (wcar* (car/hget hset "continue"))
                  updt (wcar* (car/hget hset "updated"))]
              (h/html5
               [:form {:method "POST" :action "/storemons0"}
                [:table
                 [:tr 
                  [:td {:style "width: 100px;"}
                   (wcar* (car/hget (str "h" fval lval) "size"))]
                  [:td {:style "width: 100px;"}
                   lval [:input {:type "hidden" :name "lang" :value lval}]]
                  [:td {:style "width: 100px;"}
                   fval [:input {:type "hidden" :name "country" :value fval}]]
                  [:td {:style "width: 200px;"} updt]
                  [:td {:style "width: 300px;"}
                   cont [:input {:type "hidden" :name "cont" :value cont}]]
                  [:td [:input {:type "submit" :value "Go"}]]]]]))
           lang-pairs))))

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
        (let [card (wcar* (car/scard set))]
          (wcar* (car/hmset (str "h" set) "size" card
                            "updated" (java.util.Date.)
                            "continue" next))
          (h/html5
           [:h1 (format "Store monuments for country %s and lang %s into \"%s\""
                        (:country params) (:lang params) set)]
           [:form {:method "POST" :action "/storemons0" :class "main"}
            [:h2 "Current continuation"] (:cont params)
            [:h2 "Done"] card
            [:h2 "Next"]
            [:input {:type "hidden" :name "country" :value (:country params)}]
            [:input {:type "hidden" :name "lang" :value (:lang params)}]
            [:input {:type "text-area" :name "cont" :value next}]
            [:input {:type "submit" :value "go"}]])))))

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
                 #(creds/bcrypt-credential-fn @users %)
                 )]}))

(def login-form
  [:div {:class "row"}
   [:div {:class "columns small-12"}
    [:h1 "Login (for existing users)"]
    [:div {:class "row"}
     [:form {:method "POST" :action "login" :class "columns small-4"}
      [:div "Username: " [:input {:type "text" :name "username"}]]
      [:div "Password: " [:input {:type "password" :name "password"}]]
      [:div [:input {:type "submit" :class "button" :value "Login"}]]]]]])

(defroutes app-routes 
  (GET "/" {params :params} (index params))
  (POST "/" {params :params} (index params))
  (GET "/storemons" req (if-let [identity (friend/identity req)] (storemons req) "Doh!"))
  (POST "/storemons0" {params :params} (storemons0 params))
  (GET "/login" req (h/html5 login-form))
  (GET "/logout" req (friend/logout* (resp/redirect (str (:context req) "/"))))
  (route/resources "/")
  (route/not-found "Not found"))

(def app (middleware/app-handler
          [(wrap-friend app-routes)]
          :middleware []
          :access-rules []))

(def war-handler (middleware/war-handler app))
