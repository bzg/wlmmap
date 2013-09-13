(ns wlmmap.handler
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [cemerick.friend :as friend]
            (cemerick.friend [workflows :as workflows]
                             [credentials :as creds])
            [taoensso.carmine :as car]
            [wlmmap.vars :refer :all]
            [wlmmap.i18n :refer :all]
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
        [:p
         (e/link-to {:id "links" :style "font-size:90%;"} (str "/" lng "/links")
                    (tower/t (keyword lng) trad :main/links))
         (e/link-to {:id "about" :style "font-size:90%;"} (str "/" lng "/about")
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

(defn- links [lang]
  (h/html5
   [:head
    (h/include-css "/css/about.css")
    (h/include-js "/js/gg.js")]
   [:body
    [:p (tower/t (keyword lang) trad :links/intro)]
    [:ul
     (doall (for [[name raw] (db-options-localized lang)]
              [:li (e/link-to (str "http://www.panoramap.org/" lang "/"
                                   (clojure.string/replace raw #"/" "")) name)]))]]))

(defn- about [lang & msg]
  (h/html5
   [:head
    (h/include-css "/css/about.css")
    (h/include-js "/js/gg.js")]
   [:body
    (when msg [:div [:h1 "Not found"] [:p msg]])
    [:h1 (tower/t (keyword lang) trad :about/a)]
    [:p (tower/t (keyword lang) trad :about/b)
     (e/link-to {:target "_blank"}
                "http://www.wikilovesmonuments.org/"
                "Wiki Loves Monuments 2013") "."]
    [:p (tower/t (keyword lang) trad :about/c)]
    [:p (tower/t (keyword lang) trad :about/d)]
    [:p (tower/t (keyword lang) trad :about/e)]
    [:p (tower/t (keyword lang) trad :about/f)
     (e/link-to {:target "_blank"}
                "https://commons.wikimedia.org"
                "Wikimedia Commons")
     (tower/t (keyword lang) trad :about/g)]
    [:p (tower/t (keyword lang) trad :about/h)
     (e/link-to {:target "_blank"} "https://github.com/bzg/wlmmap" "github") "."]
    [:p (tower/t (keyword lang) trad :about/i)
     (e/link-to "mailto:bzg@bzg.fr?subject=[panoramap]"
                (tower/t (keyword lang) trad :about/j))]
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
  (GET "/:lang/links" [lang] (links lang))
  (GET "/:lang/about" [lang]  (about lang))
  (GET "/:lang/" [lang] (index lang))
  (GET "/:lang/:db" [lang db] (index lang db))
  (GET "/backend" req (if-let [identity (friend/identity req)] (backend req) "Doh!"))
  (POST "/backend" {params :params} (backend params))
  (POST "/process" {params :params} (process params))
  (GET "/login" [] (login-form))
  (GET "/logout" req (friend/logout* (resp/redirect (str (:context req) "/"))))
  (route/resources "/")
  (route/not-found (about "en" "Sorry, the page you're looking for could not be found.")))

(def app (middleware/app-handler
          [(wrap-friend (wrap-rpc app-routes))]))

(def war-handler (middleware/war-handler app))
