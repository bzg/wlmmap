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
            [hiccup.element :as e]
            [shoreleave.middleware.rpc :refer [defremote wrap-rpc]]))

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
      (clojure.string/replace #"\[\[|\]\]|[\n\r]" "")
      ;; (clojure.string/replace #"\"" "\\\\\"")
      ))

(def server1-conn
  {:pool {} :spec {:uri (System/getenv "OPENREDIS_URL")}})

(defmacro wcar* [& body]
  `(car/wcar server1-conn ~@body))

(def admin
  (atom {"bzg" {:username "bzg"
                :password (hash-bcrypt (System/getenv "backendpwd"))
                :roles #{::user}}}))

(def lang-pairs
{
0, ["aq" 	   "en"]
1, ["ar" 	   "es"]
2, ["at" 	   "de"]
3, ["be-bru" 	   "nl"]
4, ["be-vlg" 	   "en"]
5, ["be-vlg" 	   "fr"]
6, ["be-vlg" 	   "nl"]
7, ["be-wal" 	   "en"]
8, ["be-wal" 	   "fr"]
9, ["be-wal" 	   "nl"]
10, ["bo" 	   "es"]
11, ["by" 	   "be-x-old"]
12, ["ca" 	   "en"]
13, ["ca" 	   "fr"]
14, ["ch" 	   "fr"]
15, ["ch-old" 	   "de"]
16, ["ch-old" 	   "en"]
17, ["ch-old" 	   "it"]
18, ["cl" 	   "es"]
19, ["co" 	   "es"]
20, ["cz" 	   "cs"]
21, ["de-by" 	   "de"]
22, ["de-he" 	   "de"]
23, ["de-nrw" 	   "de"]
24, ["de-nrw-bm"   "de"]
25, ["de-nrw-k"    "de"]
26, ["dk-bygning"  "da"]
27, ["dk-fortids"  "da"]
28, ["ee" 	   "et"]
29, ["es" 	   "ca"]
30, ["es" 	   "es"]
31, ["es" 	   "gl"]
32, ["fr" 	   "ca"]
33, ["fr" 	   "fr"]
34, ["gb-eng" 	   "en"]
35, ["gb-nir" 	   "en"]
36, ["gb-sct" 	   "en"]
37, ["gb-wls" 	   "en"]
38, ["gh" 	   "en"]
39, ["ie" 	   "en"]
40, ["il" 	   "he"]
41, ["in" 	   "en"]
42, ["it" 	   "it"]
43, ["it-88" 	   "ca"]
44, ["it-bz" 	   "de"]
45, ["ke" 	   "en"]
46, ["lu" 	   "lb"]
47, ["mt" 	   "de"]
48, ["mx" 	   "es"]
49, ["nl" 	   "nl"]
50, ["nl-gem" 	   "nl"]
51, ["no" 	   "no"]
52, ["pa" 	   "es"]
53, ["ph" 	   "en"]
54, ["pk" 	   "en"]
55, ["pl" 	   "pl"]
56, ["pt" 	   "pt"]
57, ["ro" 	   "ro"]
58, ["rs" 	   "sr"]
59, ["ru" 	   "ru"]
60, ["se-bbr" 	   "sv"]
61, ["se-fornmin"  "sv"]
62, ["se-ship" 	   "sv"]
63, ["sk" 	   "de"]
64, ["sk" 	   "sk"]
65, ["th" 	   "th"]
66, ["tn" 	   "fr"]
67, ["ua" 	   "uk"]
68, ["us" 	   "en"]
69, ["us-ca" 	   "en"]
70, ["uy" 	   "es"]
71, ["ve" 	   "es"]
72, ["za" 	   "en"]
73, ["ad" 	   "ca"]
})

(def toolserver-url "http://toolserver.org/~erfgoed/api/api.php?action=search&format=json&limit=5000")

(defn- backend
  "interface to select which lang/country to store"
  []
  (h/html5
   [:head (h/include-css "/css/admin.css")]
   [:body
    [:h1 "Select the lang and the country of monuments to store"]
    [:table {:style "width: 80%;"}
     [:tr
      [:td {:style "width: 100px;"} "#"]
      [:td {:style "width: 100px;"} "Lang"]
      [:td {:style "width: 100px;"} "Country"]
      [:td {:style "width: 200px;"} "Last updated"]
      [:td {:style "width: 300px;"} "Continue from"]]
     (doall
      (map
       #(let [fval (first (val %))
              lval (last (val %))
              col (odd? (key %))
              stats (str "s" fval lval)
              cont (wcar* (car/hget stats "continue"))
              updt (wcar* (car/hget stats "updated"))
              size (wcar* (car/hget (str "s" fval lval) "size"))]
          [:form {:method "POST" :action "/process"}
           [:tr {:style (str "background-color: " (if col "white" "white"))}
            [:td {:style "width: 100px;"} size]
            [:td {:style "width: 100px;"} lval
             [:input {:type "hidden" :name "lang" :value lval}]]
            [:td {:style "width: 100px;"}
             fval [:input {:type "hidden" :name "country" :value fval}]]
            [:td {:style "width: 200px;"} updt]
            [:td {:style "width: 300px;"} cont
             [:input {:type "hidden" :name "cont" :value cont}]]
            [:td [:input {:type "submit" :value "Go"}]]]])
       lang-pairs))]]))

(defn- process
  "Connect to the toolserver and store monuments in the redis database"
  [params]
  (let [cntry (:country params)
        lang (:lang params)
        req (str toolserver-url "&srcountry=" cntry "&srlang=" lang
                 (when (not (= (:cont params) ""))
                   (str "&srcontinue=" (:cont params))))
        rset (str cntry lang)
        res (json/read-str (slurp req) :key-fn keyword)
        next (or (:srcontinue (:continue res)) "")]
    (doseq [m (:monuments res)]
      (when (and (not (nil? (:lat m)))
                 (not (nil? (:lon m)))
                 (not (= "" (:name m)))
                 (not (= "" (:image m))))
        (let [reg (:registrant_url m)
              id (:id m)
              nam (cleanup-name (:name m))
              imc (:image m)
              img (codec/url-encode imc)
              lng (:lang m)
              emb (str "<img src=\"https://commons.wikimedia.org/w/index.php?title=Special%3AFilePath&file=" img "&width=250\" />")
              ilk (when (not (= imc "")) (str "<a href=\"http://commons.wikimedia.org/wiki/File:" img "\" target=\"_blank\">" emb "</a>"))
              art (:monument_article m)
              lnk "<a href=\"http://%s.wikipedia.org/wiki/%s\" target=\"_blank\">%s</a>"
              arl (format lnk lng (codec/url-encode art) art)
              src (format "Source: <a href=\"%s\" target=\"_blank\">%s</a>" reg id)
              all (str "<h3>" nam "</h3>"
                                      (when (not (= "" imc)) (str ilk "<br/>"))
                                      (when (not (= "" art)) (str arl "<br/>"))
                                      (when (not (= "" reg)) src))]
          (wcar* (car/hset rset (:id m) (list (list (:lat m) (:lon m)) all))))))
    (let [card (count (wcar* (car/hvals rset)))]
      (wcar* (car/hmset (str "s" rset)
                        "size" card
                        "updated" (java.util.Date.)
                        "continue" next))
      (h/html5
       [:head (h/include-css "/css/admin.css")]
       [:body
        [:h1 (format "Store monuments for country %s and lang %s into \"%s\""
                     (:country params) (:lang params) rset)]
        [:form {:method "POST" :action "/process" :class "main"}
         [:h2 "Current continuation"] (:cont params)
         [:h2 "Done"] card
         [:h2 "Next"]
         [:input {:type "hidden" :name "country" :value (:country params)}]
         [:input {:type "hidden" :name "lang" :value (:lang params)}]
         [:input {:type "text-area" :name "cont" :value next}]
         [:input {:type "submit" :value "go"}]]
        "Back to <a href=\"/backend\">backend</a>"]))))

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
                 #(creds/bcrypt-credential-fn @admin %))]}))

(defn- login-form []
  (h/html5
   [:head (h/include-css "/css/admin.css")]
   [:body
    [:div {:class "row"}
     [:div {:class "columns small-12"}
      [:h1 "Admin login"]
      [:div {:class "row"}
       [:form {:method "POST" :action "login"}
        [:div "Username: " [:input {:type "text" :name "username"}]]
        [:div "Password: " [:input {:type "password" :name "password"}]]
        [:div [:input {:type "submit" :class "button" :value "Login"}]]]]]]]))

(def db (atom "frfr"))

(defremote get-markers []
  (wcar* (car/hvals @db)))

(defn- index [params]
  (h/html5
   [:head
    (h/include-css "/css/mapbox.css")
    "<!--[if lt IE 8]>"
    (h/include-css "/css/mapbox.ie.css")
    "<![endif]-->"
    (h/include-css "/css/generic.css")]
   [:body
    (h/include-css "/css/MarkerCluster.css")
    (h/include-css "/css/MarkerCluster.Default.css")
    (h/include-js "/js/ArrayLikeIsArray.js")
    (h/include-js "/js/mapbox.js")
    "<!--[if lt IE 8]>"
    (h/include-css "/css/MarkerCluster.Default.ie.css")
    "<![endif]-->"
    (h/include-js "/js/leaflet.markercluster.js")
    "<div id=\"map\"></div>"
    (h/include-js "/js/main.js")]))

;; FIXME remote req from admin?
(defroutes app-routes 
  (GET "/" {params :params} (index params))
  (POST "/" {params :params} (index params))
  (GET "/backend" req (if-let [identity (friend/identity req)] (backend) "Doh!"))
  (POST "/process" {params :params} (process params))
  (GET "/login" [] (login-form))
  (GET "/logout" req (friend/logout* (resp/redirect (str (:context req) "/"))))
  (route/resources "/")
  (route/not-found "Not found"))

(def app (middleware/app-handler
          [(wrap-rpc (wrap-friend app-routes))]
          :middleware []
          :access-rules []))

(def war-handler (middleware/war-handler app))
