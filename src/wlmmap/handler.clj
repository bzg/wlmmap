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

(defn- cleanup-name [n]
  (-> n
      (clojure.string/replace #"^\[\[|\]\]" "")
      (clojure.string/replace #"[\n\r]" "")
      (clojure.string/replace #"\"" "\\\\\"")))

(def server1-conn
  {:pool {} :spec {:uri (System/getenv "OPENREDIS_URL")}})

(defmacro wcar* [& body]
  `(car/wcar server1-conn ~@body))

(defn- generate-markers
  "return a string with the javascript code to generate addressPoints."
  [params]
  (let [mons (take 50000 (wcar* (car/smembers "frfrim")))
        all (remove #(nil? %)
                    (for [m mons]
                      (let [res (read-string (wcar* (car/get m)))
                            lat (:lat res)
                            lon (:lon res)
                            reg (:registrant_url res)
                            id (:id res)
                            nam (cleanup-name (:name res))
                            imc (:image res)
                            img (codec/url-encode imc)
                            lng (:lang res)
                            emb (str "<img src=\\\"https://commons.wikimedia.org/w/index.php?title=Special%3AFilePath&file=" 
                                     img "&width=250\\\" />")
                            ilk (if (not (= imc ""))
                                  (str "<a href=\\\"http://commons.wikimedia.org/wiki/File:"
                                       img "\\\">" emb "</a>") "")
                            art (:monument_article res)
                            arl (format "<a href=\\\"http://%s.wikipedia.org/wiki/%s\\\">%s</a>" lng art art)
                            src (format "Source: <a target=\\\"blank\\\" href=\\\"%s\\\">%s</a>" reg id)]
                        (when (and lat lon nam)
                          (format "[%s,%s,\"%s\",\"%s\"]" lat lon nam
                                  (str "<h3>" nam "</h3>"
                                       ilk "<br/>" (if art (str arl "<br/>") "")
                                       src "<br/>"))))))]
    (str "var addressPoints = [" (str/join "," all) "];")))

(defn- index [params]
  (h/html5
   [:head
    (h/include-css "/css/generic.css" "/css/mapbox.css")
    "<!--[if lt IE 8]>"
    (h/include-css "/css/mapbox.ie.css")
    "<![endif]-->"
    (h/include-js "/js/mapbox.js")]
   
   [:body
    (h/include-css "/css/generic.css" "/css/Control.MiniMap.css")
    (h/include-css "/css/MarkerCluster.Default.css")
    "<!--[if lt IE 8]>"
    (h/include-css "/css/MarkerCluster.Default.ie.css")
    "<![endif]-->"
    (h/include-js "/js/leaflet.markercluster.js")
    (h/include-js "/js/Control.MiniMap.js")    

    "<script type='text/javascript'>"
    
    (generate-markers params)
    
    "</script>\n"
    
    "
<!-- <div class='corner'> -->
<!-- <span>...</span> -->
<!-- </div> -->

<div id='map'></div>

<script type='text/javascript'>
    var map = L.mapbox.map('map', 'examples.map-uci7ul8p')
        .setView([48, 1.2], 3)
        .on('ready', function() {
        new L.Control.MiniMap(L.mapbox.tileLayer('examples.map-uci7ul8p'))
          .addTo(map);
      });

    // centering
    map.markerLayer.on('click', function(e) {
        map.panTo(e.layer.getLatLng());
    });

    var markers = new L.MarkerClusterGroup();

    for (var i = 0; i < addressPoints.length; i++) {
        var a = addressPoints[i];
        var title = a[2];
        var popup = a[3];
        var marker = L.marker(new L.LatLng(a[0], a[1]), {
            icon: L.mapbox.marker.icon({'marker-symbol': 'post', 'marker-color': '0044FF'}),
            title: title
        });
        marker.bindPopup(popup,{minWidth:270});
        markers.addLayer(marker);
    }

    map.addLayer(markers);
</script>
"
    ]))

   ;; [:form {:method "POST" :action "/" :class "main"}
   ;;  "With images:" 
   ;;  [:input {:type "hidden" :name "srwithimage" :value "0"}]
   ;;  [:input (if (= (:srwithimage params) "1")
   ;;            {:type "checkbox" :name "srwithimage" :value "1" :checked "checked"}
   ;;            {:type "checkbox" :name "srwithimage" :value "1"})]
   ;;  "&nbsp;"
   ;;  "With article:" 
   ;;  [:input {:type "hidden" :name "witharticle" :value "0"}]
   ;;  [:input (if (= (:witharticle params) "1")
   ;;            {:type "checkbox" :name "witharticle" :value "1" :checked "checked"}
   ;;            {:type "checkbox" :name "witharticle" :value "1"})]
   ;;  "&nbsp;"
   ;;  "Max:" [:input {:type "text-area" :name "limit" :value (:limit params)}]
   ;;  "&nbsp;"
   ;;  "Wikipedia (2 letters):" [:input {:type "text-area" :name "srcountry" :value (:srcountry params)}]
   ;;  [:input {:type "submit" :value "Go"}]]

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
