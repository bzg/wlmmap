(ns wlmmap.handler
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [wlmmap.vars :refer :all]
            [wlmmap.i18n :refer :all]
            [ring.util.codec :as codec]
            [ring.util.response :as resp]
            [noir.util.middleware :as middleware]
            [ring.middleware.reload :refer :all]
            [org.httpkit.server :refer :all]
            [compojure.core :as compojure :refer (GET POST defroutes)]
            (compojure [handler :as handler]
                       [route :as route])
            [hiccup.page :as h]
            [hiccup.element :as e]
            [hiccup.form :as f]
            [shoreleave.middleware.rpc :refer [defremote wrap-rpc]]
            [taoensso.tower :as tower
             :refer (with-locale with-tscope t *locale*)]))

(defn- cleanup-name [n]
  (-> n
      (clojure.string/replace #"\[\[([^]]+)\|[^]]+\]\]" "$1")
      (clojure.string/replace #"\[\[|\]\]|[\n\r]+|\{\{[^}]+\}\}" "")))

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
                          (if (empty? imc)
                            (str "[No image]<br/>")
                            (str ilk "<br/>"))
                          (when (seq art) (str arl "<br/>"))
                          (when (seq reg) src))]
             (list cnt (list (:lat m) (:lon m)) (empty? imc) all))))
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
  (if (.exists (clojure.java.io/file
                (str "resources/public/cldr/" lang "/languages.json")))
    (let [languages-json
          (json/read-str
           (slurp (str "resources/public/cldr/" lang "/languages.json"))
           :key-fn keyword)
          languages
          (get-in languages-json
                  [:main (keyword lang) :localeDisplayNames :languages])
          territories-json
          (json/read-str
           (slurp (str "resources/public/cldr/" lang "/territories.json"))
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

(defn- get-selected-db [dbl lng]
  (let [rm1 "%s/%s" rm2 ".+/%s"
        f (fn [rm] (second
                    (first (filter #(re-find
                                     (re-pattern
                                      (format rm lng lng)) (get % 1)) dbl))))]
    (or (f rm1) (f rm2))))

(defn- index [lang]
  (let [lng (or lang "en")
        dbl (db-options-localized lng)]
    (h/html5
     [:head
      [:title "Panoramap.org - cultural heritage monuments"]
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
      [:div {:id "top-left"}
       [:form [:select {:id "db"} (f/select-options dbl (get-selected-db dbl lng))]]
       (e/link-to {:id "sm"} "#" (tower/t (keyword lng) trad :main/show))
       (e/link-to {:id "stop" :accesskey "!"} "#" (tower/t (keyword lng) trad :main/stop))
       (e/link-to {:id "showhere" :accesskey "?" :title (tower/t (keyword lng) trad :main/showhere)} "#" "...")
       (f/text-field {:id "per" :size 12} "per")]
      [:div {:id "top-right"}
       (e/link-to {:id "links"} (str "/" lng "/links")
                  (tower/t (keyword lng) trad :main/links))
       (e/link-to {:id "about"} (str "/" lng "/about")
                  (tower/t (keyword lng) trad :main/about))
       (e/link-to {:id "roadmap"} (str "/" lng "/roadmap")
                  (tower/t (keyword lng) trad :main/roadmap))
       (h/include-js "/js/tt.js")]
      [:div {:id "bottom-right"} "Panoramap.org -- "
       (e/link-to "https://bzg.fr" "bzg.fr")]
      (h/include-js "/js/main.js")])))

(defn- links [lang]
  (h/html5
   [:head
    [:title "Panoramap - explore cultural heritage"]
    (h/include-css "/css/about.css")
    (h/include-js "/js/gg.js")]
   [:body
    [:p (tower/t (keyword lang) trad :links/intro)]
    [:ul
     (doall (for [[name raw] (db-options-localized lang)]
              [:li (e/link-to (str "https://www.panoramap.org/" lang "/"
                                   (clojure.string/replace raw #"/" "")) name)]))]]))

(defn- roadmap [lang]
  (h/html5
   [:head
    [:title (str "Panoramap - " (tower/t (keyword lang) trad :main/roadmap))]
    (h/include-css "/css/about.css")
    (h/include-js "/js/gg.js")]
   [:body
    [:h1 (tower/t (keyword lang) trad :main/roadmap)]
    [:h2 "wlmmap v0.0.9"]
    [:ul
     [:li "Allow users to create lists when logged in"]]
    [:h2 "wlmmap v0.0.8"]
    [:ul
     [:li (str "Add missing countries (")
      (e/link-to "mailto:bzg@bzg.fr" "please send me an email")
      ")"]
     [:li "Merge entries from the same countries (e.g. for DE)"]
     [:li "Implement basic login"]
     [:li "Enhance backend to make it easier to sync"]
     ]
    [:h2 "wlmmap v0.0.7 (Current)"]
    [:ul
     [:li "Display WLM monuments on a map"]]]))

(defn- about [lang & msg]
  (h/html5
   [:head
    [:title "Panoramap - explore cultural heritage"]
    (h/include-css "/css/about.css")
    (h/include-js "/js/gg.js")]
   [:body
    (when msg [:div [:h1 "Not found"] [:p msg]])
    [:h1 (tower/t (keyword lang) trad :about/a)]
    [:p (tower/t (keyword lang) trad :about/b)
     (e/link-to {:target "_blank"}
                "http://www.wikilovesmonuments.org"
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
    [:p "-- " (e/link-to {:target "_blank"} "https://bzg.fr" "bzg")]]))

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
  (GET "/" [] (index "en"))
  (GET "/:lang/:lat/:lon/:zoom" [lang] (index lang))
  (GET "/:lang/links" [lang] (links lang))
  (GET "/:lang/about" [lang]  (about lang))
  (GET "/:lang/" [lang] (index lang))
  (GET "/:lang/roadmap" [lang] (roadmap lang))
  (route/resources "/")
  (route/not-found (about "en" "Sorry, the page you're looking for could not be found.")))

(def app (wrap-reload (middleware/app-handler [(wrap-rpc app-routes)])))

(defn -main [& args]
  (run-server #'app {:port 8888}))
