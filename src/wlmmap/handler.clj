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
            [hiccup.form :as f]
            [shoreleave.middleware.rpc :refer [defremote wrap-rpc]]
            [taoensso.tower :as tower
             :refer (with-locale with-tscope t *locale*)]))

(def trad
  {:dev-mode? true
   :fallback-locale :en
   :dictionary
   {:en {:main {:show "Show"
                :stop "Stop"
                :here "Here"
                :about "About"}
         :about {:a "About"
                 :b "This map has been developed during "
                 :c "It allows you to explore cultural heritage treasures of the world."
                 :d "Blue markers are for monuments with a photo."
                 :e "Red markers are for monuments without one."
                 :f "All the pictures are from "
                 :g ", available under a free license."
                 :h "The code behind this website is available from "
                 :i "I appreciate feedback and suggestions! "
                 :j "Drop me an email"}}
    :fr {:main {:show "Afficher"
                :stop "Stop"
                :here "Ici"
                :about "À propos"}
         :about {
                 :a "À propos"
                 :b "Cette carte a été développée pour "
                 :c "Elle permet d'explorer les monuments historiques du monde entier."
                 :d "Les points en rouge indiquent des monuments avec photo."
                 :e "Les points en bleu indiquent des monuments <b>sans</b> photo."
                 :f "Toutes les images viennent de "
                 :g ", disponibles sous licence libre."
                 :h "Le code derrière ce site web est disponible depuis "
                 :i "J'apprécie les remarques, critiques et suggestions! "
                 :j "Envoyez-moi un e-mail"
                 }}}})

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
      (clojure.string/replace #"\[\[([^]]+)\|[^]]+\]\]" "$1")
      (clojure.string/replace #"\[\[|\]\]|[\n\r]+|\{\{[^}]+\}\}" "")))

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
   ;; 3, ["be-bru" 	   "nl"]
   4, ["be-vlg" 	   "en"]
   5, ["be-vlg" 	   "fr"]
   6, ["be-vlg" 	   "nl"]
   7, ["be-wal" 	   "en"]
   8, ["be-wal" 	   "fr"]
   9, ["be-wal" 	   "nl"]
   10, ["bo" 	   "es"]
   ;; 11, ["by" 	   "be-x-old"]
   12, ["ca" 	   "en"]
   13, ["ca" 	   "fr"]
   14, ["ch" 	   "fr"]
   ;; 15, ["ch-old" 	   "de"]
   ;; 16, ["ch-old" 	   "en"]
   ;; 17, ["ch-old" 	   "it"]
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
   ;; 35, ["gb-nir" 	   "en"]
   36, ["gb-sct" 	   "en"]
   37, ["gb-wls" 	   "en"]
   ;; 38, ["gh" 	   "en"]
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
   ;; 54, ["pk" 	   "en"]
   55, ["pl" 	   "pl"]
   56, ["pt" 	   "pt"]
   57, ["ro" 	   "ro"]
   58, ["rs" 	   "sr"]
   59, ["ru" 	   "ru"]
   60, ["se-bbr" 	   "sv"]
   61, ["se-fornmin"  "sv"]
   ;; 62, ["se-ship" 	   "sv"]
   63, ["sk" 	   "de"]
   64, ["sk" 	   "sk"]
   65, ["th" 	   "th"]
   ;; 66, ["tn" 	   "fr"]
   67, ["ua" 	   "uk"]
   68, ["us" 	   "en"]
   69, ["us-ca" 	   "en"]
   ;; 70, ["uy" 	   "es"]
   71, ["ve" 	   "es"]
   72, ["za" 	   "en"]
   73, ["ad" 	   "ca"]
   74, ["hu" 	   "hu"]
   })

(def toolserver-url
  "http://toolserver.org/~erfgoed/api/api.php?action=search&format=json&limit=5000&props=lat|lon|name|registrant_url|id|image|lang|monument_article")
;;  "http://tools.wmflabs.org/heritage/api/api.php?action=search&format=json&limit=5000&props=lat|lon|name|registrant_url|id|image|lang|monument_article")
(def toolserver-bbox-format-url
;;  "http://tools.wmflabs.org/heritage/api/api.php?action=search&format=json&limit=5000&props=lat|lon|name|registrant_url|id|image|lang|monument_article&bbox=%s")
  "http://toolserver.org/~erfgoed/api/api.php?action=search&format=json&limit=5000&props=lat|lon|name|registrant_url|id|image|lang|monument_article&bbox=%s")
(def wm-thumbnail-format-url
  "<img src=\"https://commons.wikimedia.org/w/index.php?title=Special%%3AFilePath&file=%s&width=250\" />")
(def wm-img-format-url
  "<a href=\"http://commons.wikimedia.org/wiki/File:%s\" target=\"_blank\">%s</a>")
(def wp-link-format-url
  "<a href=\"http://%s.wikipedia.org/wiki/%s\" target=\"_blank\">%s</a>")
(def src-format-url
  "Source: <a href=\"%s\" target=\"_blank\">%s</a>")

(defn- make-monuments-list [monuments start]
  (map (fn [m cnt]
         (when (and (not (nil? (:lat m)))
                    (not (nil? (:lon m)))
                    (not (or (nil? (:name m)) (= "" (:name m)))))
           (let [reg (:registrant_url m)
                 id (:id m)
                 nam (cleanup-name (:name m))
                 imc (:image m)
                 img (codec/url-encode imc)
                 lng (:lang m)
                 emb (format wm-thumbnail-format-url img)
                 ilk (format wm-img-format-url img emb)
                 art (:monument_article m)
                 arl (format wp-link-format-url lng (codec/url-encode art) art)
                 src (format src-format-url reg id)
                 all (str "<h3>" nam "</h3>"
                          (when (not (= "" imc)) (str ilk "<br/>"))
                          (when (not (= "" art)) (str arl "<br/>"))
                          (when (not (= "" reg)) src))]
             (list cnt (list (:lat m) (:lon m)) (= "" imc) all))))
       monuments
       (range (if (empty? start) 0 (Integer/parseInt start)) 100000)))

(defn- make-monuments-list-from-toolserver [map-bounds-string]
  (make-monuments-list
   (:monuments
    (json/read-str (slurp (format toolserver-bbox-format-url map-bounds-string))
                   :key-fn keyword))
   "0"))

(defremote get-markers-toolserver [map-bounds-string]
  (make-monuments-list-from-toolserver map-bounds-string))

(def db-options
  (atom (sort (map #(let [[_ [cntry lng]] %] (str cntry " / " lng)) lang-pairs))))

(defn- db-options-localized [lang]
  (if (.exists (clojure.java.io/file (str "resources/public/cldr/" lang "/languages.json")))
    (let [languages-json
          (json/read-str (slurp (str "resources/public/cldr/" lang "/languages.json"))
                         :key-fn keyword)
          languages
          (get-in languages-json
                  [:main (keyword lang) :localeDisplayNames :languages])
          territories-json
          (json/read-str (slurp (str "resources/public/cldr/" lang "/territories.json"))
                         :key-fn keyword)
          countries
          (get-in territories-json
                  [:main (keyword lang) :localeDisplayNames :territories])]
      (sort (map #(let [[_ [cntry lng]] %
                        cplx (re-seq #"([^-]+)-(.+)" cntry)]
                    (if cplx
                      (let [[_ bare suffix] (first cplx)]
                        (vector (str ((keyword (clojure.string/upper-case bare)) countries)
                                     " (" suffix ") / " ((keyword lng) languages))
                                (str cntry "/" lng)))
                      (vector (str ((keyword (clojure.string/upper-case cntry)) countries)
                                   " / " ((keyword lng) languages))
                              (str cntry "/" lng))))
                 lang-pairs)))
    @db-options))

(defremote get-markers [db]
  (wcar* (car/hkeys db)))

(defremote get-marker [db id]
  (wcar* (car/hget db id)))

(defremote get-center [db]
  (wcar* (car/hget (str "s" db) "rep")))

(defn- index [lang & db]
  (let [lng (or lang "en")]
    (h/html5
     [:head
      (h/include-css "/css/mapbox.css")
      "<!--[if lt IE 8]>"
      (h/include-css "/css/mapbox.ie.css")
      "<![endif]-->"
      (h/include-css "/css/generic.css")
      (h/include-js "/js/gg.js")]
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
      [:div {:class "corner"}
       [:form
        (f/drop-down {:id "db"} "db" (db-options-localized lng))
        [:p
         (e/link-to {:id "sm" :style "color:green"} "#"
                    (tower/t (keyword lng) trad :main/show))
         (e/link-to {:id "stop" :style "color:red"} "#"
                    (tower/t (keyword lng) trad :main/stop))
         (e/link-to {:id "showhere" :style "color:yellow"} "#" "....")]
        "</p>"
        [:p (f/text-field {:id "per" :size 12 :style "text-align: right; background-color: black; border: 0px; color: white;"} "per")]
        [:p (e/link-to {:id "about" :style "font-size:90%;"} "/about"
                       (tower/t (keyword lng) trad :main/about))]]]
      (h/include-js "/js/main.js")])))

(defn- backend
  "interface to select which lang/country to store"
  [params]
  (h/html5
   [:head
    (h/include-css "/css/admin.css")
    (h/include-js "/js/gg.js")]
   [:body
    [:h1 "Select the lang and the country of monuments to store"]
    [:table {:style "width: 100%;"}
     [:tr
      [:td {:style "width: 200px;"} "#"]
      [:td {:style "width: 300px;"} "rep (im)"]
      [:td {:style "width: 100px;"} "Country"]
      [:td {:style "width: 100px;"} "Lang"]
      [:td {:style "width: 500px;"} "Last updated"]
      [:td {:style "width: 200px;"} "Continue from"]
      [:td {:style "width: 100px;"} "Add more"]
      [:td {:style "width: 100px;"} "Delete"]]
     (do
       (if (not (= "" (:del params)))
         (wcar* (car/del (:del params) (str "s" (:del params)))))
       (map
        #(let [fval (first (val %))
               lval (last (val %))
               stats (str "s" fval lval)
               rep (str (wcar* (car/hget stats "rep")))
               cont (wcar* (car/hget stats "continue"))
               updt (wcar* (car/hget stats "updated"))
               size (wcar* (car/hget stats "size"))]
           [:tr {:style (str "background-color: white")}
            [:form {:method "POST" :action "/process"}
             [:td {:style "width: 100px;"} size
              [:input {:type "hidden" :name "size" :value size}]]
             [:td {:style "width: 100px;"} rep]
             [:td {:style "width: 100px;"} fval
              [:input {:type "hidden" :name "country" :value fval}]]
             [:td {:style "width: 100px;"} lval
              [:input {:type "hidden" :name "srlang" :value lval}]]
             [:td {:style "width: 200px;"} updt]
             [:td {:style "width: 300px;"} cont
              [:input {:type "hidden" :name "cont" :value cont}]]
             [:td [:input {:type "submit" :value "Go"}]]]
            [:form {:method "POST" :action "/backend"}
             [:td {:style "width: 100px;"}
              [:input {:type "hidden" :name "del" :value (str fval lval)}]
              [:input {:type "submit" :value "Delete"}]]]])
        lang-pairs))]]))

(defn- process
  "Connect to the toolserver and store results in the database."
  [params]
  (let [cntry (:country params)
        srlang (:srlang params)
        req (str toolserver-url "&srcountry=" cntry "&srlang=" srlang
                 (when (not (= "" (:cont params)))
                   (str "&srcontinue=" (:cont params))))
        rset (str cntry srlang)
        res (json/read-str (slurp req) :key-fn keyword)
        next (or (:srcontinue (:continue res)) "")]
    (doseq [l (make-monuments-list (:monuments res) (:size params))]
      (wcar* (car/hset rset (first l) (rest l))))
    (let [all (wcar* (car/hvals rset))
          size (count all)
          rep (first all)
          llat (first (first rep))
          llon (last (first rep))]
      (wcar* (car/hmset (str "s" rset)
                        "rep" (list llat llon)
                        "size" size
                        "updated" (java.util.Date.)
                        "continue" next))
      (h/html5
       [:head (h/include-css "/css/admin.css")]
       [:body
        [:h1 (format "Store monuments for country %s and lang %s into \"%s\""
                     (:country params) (:srlang params) rset)]
        [:form {:method "POST" :action "/process" :class "main"}
         [:h2 (str "Continuated from " (:cont params))]
         [:input {:type "hidden" :name "size" :value size}]
         [:h2 (str "Done so far: " size)]
         [:h2 "Next"]
         [:input {:type "hidden" :name "country" :value (:country params)}]
         [:input {:type "hidden" :name "srlang" :value (:srlang params)}]
         [:input {:type "text-area" :name "cont" :value next}]
         [:input {:type "submit" :value "go"}]]
        "<br/><p>Back to <a href=\"/backend\">backend</a></p>"]))))

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

(defn- about [& msg]
  (h/html5
   [:head
    (h/include-css "/css/about.css")
    (h/include-js "/js/gg.js")]
   [:body
    (when msg [:div [:h1 "Not found"] [:p msg]])
    [:h1 (tower/t :en trad :about/a)]
    [:p (tower/t :en trad :about/b)
     (e/link-to {:target "_blank"}
                "http://www.wikilovesmonuments.org/"
                "Wiki Loves Monuments 2013") "."]
    [:p (tower/t :en trad :about/c)]
    [:p (tower/t :en trad :about/d)]
    [:p (tower/t :en trad :about/e)]
    [:p (tower/t :en trad :about/f)
     (e/link-to {:target "_blank"} 
                "https://commons.wikimedia.org"
                "Wikimedia Commons")
     (tower/t :en trad :about/g)]
    [:p (tower/t :en trad :about/h)
     (e/link-to {:target "_blank"} "https://github.com/bzg/wlmmap" "github") "."]
    [:p (tower/t :en trad :about/i)
     (e/link-to "mailto:bzg@bzg.fr?subject=[panoramap]"
                (tower/t :en trad :about/j))]
    [:p "-- " (e/link-to {:target "_blank"} "http://bzg.fr" "bzg")]]))

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

(defroutes app-routes 
  (GET "/" [] (index nil))
  (GET "/:lang/" [lang] (index lang))
  (GET "/:lang/:db" [lang db] (index lang db))
  (GET "/backend" req (if-let [identity (friend/identity req)] (backend req) "Doh!"))
  (POST "/backend" {params :params} (backend params))
  (POST "/process" {params :params} (process params))
  (GET "/about" []  (about))
  (GET "/login" [] (login-form))
  (GET "/logout" req (friend/logout* (resp/redirect (str (:context req) "/"))))
  (route/resources "/")
  (route/not-found (about "Sorry, the page you're looking for could not be found.")))

(def app (middleware/app-handler
          [(wrap-friend (wrap-rpc app-routes))]))

(def war-handler (middleware/war-handler app))
