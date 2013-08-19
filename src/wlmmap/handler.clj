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

(def server1-conn
  {:pool {} :spec {:uri (System/getenv "REDISTOGO_URL")}})

(defmacro wcar* [& body]
  `(car/wcar server1-conn ~@body))

(defn get-username-uid [username]
  (wcar* (car/get (str "user:" username ":uid"))))

(defn get-uid-pass [uid]
  (wcar* (car/get (str "uid:" uid ":password"))))

(defn set-user [username password]
  (wcar* (car/incr "global:uid"))
  (let [guid (wcar* (car/get "global:uid"))]
    (wcar* (car/set (str "uid:" guid ":username") username))
    (wcar* (car/set (str "uid:" guid ":password") password))
    (wcar* (car/set (str "user:" username ":uid") guid))
    (wcar* (car/rpush "users" guid)))
  (session/put!
   :cred {:username username :password (hash-bcrypt password) :roles #{::users}})
  (h/html5
   (h/include-css "/css/generic.css")
   [:h1 "Thanks!"]
   [:div [:p (str "Thanks for registering " username "!")]
         [:p "You can now <a href=\"/login\">login</a>."]]))
  ;; (resp/redirect "/")

(defn set-project
  "set a project."
  [pname repro grass nonpr]
  (wcar* (car/incr "global:pid"))
  (let [pid (wcar* (car/get "global:pid"))
        uname (:username (session/get :cred))
        uid (get-username-uid uname)]
    (wcar* (car/hset (str "pid:" pid) "name" pname))
    (wcar* (car/rpush "timeline" pid))
    (when (= repro "on") (wcar* (car/rpush "repro" pid)))
    (when (= grass "on") (wcar* (car/rpush "grass" pid)))
    (when (= nonpr "on") (wcar* (car/rpush "nonpr" pid)))
    (h/html5
     (h/include-css "/css/generic.css")
     [:h1 "Thanks for adding a new project!"]
     [:div
      [:p (when (= repro "on") (str "The project <i>" pname "</i> by " uname " is <b>reproducible</b>."))]

      [:p (when (= repro "on") (str "The project <i>" pname "</i> by " uname " is <b>reproducible</b>."))]
      [:p (when (= grass "on") (str "The project <i>" pname "</i> by " uname " is a <b>grassroot</b>."))]
      [:p (when (= nonpr "on") (str "The project <i>" pname "</i> by " uname " is <b>non-profit</b>."))]
      [:p (e/link-to "/" "Back to the homepage")]])))

(defn load-user [username]
 (let [uid (get-username-uid username)
       password (get-uid-pass uid)]
   (session/put!
    :cred {:username username :password (hash-bcrypt password) :roles #{::users}})
   {:username username :password (hash-bcrypt password) :roles #{::users}}))

(derive ::admin ::user)

(def login-form
  [:div {:class "row"}
   [:div {:class "columns small-12"}
    [:h1 "Login (for existing users)"]
    [:div {:class "row"}
     [:form {:method "POST" :action "login" :class "columns small-4"}
      [:div "Username: " [:input {:type "text" :name "username"}]]
      [:div "Password: " [:input {:type "password" :name "password"}]]
      [:div [:input {:type "submit" :class "button" :value "Login"}]]]]]])

(def uregister-form
  [:div {:class "row"}
   [:div {:class "columns small-12"}
    [:h1 "Register a new user"]
    [:div {:class "row"}
     [:form {:method "POST" :action "uregistration" :class "columns small-4"}
      [:div "Username: " [:input {:type "text" :name "username"}]]
      [:div "Password: " [:input {:type "password" :name "password"}]]
      [:div [:input {:type "submit" :class "button" :value "Register"}]]]]]])

(def pregister-form
  [:div
   [:h1 "Register a new project"]
   [:form {:method "POST" :action "pregistration"}
    [:p "Project name: " [:input {:type "text" :name "pname"}]]
    [:p "Non profit? " [:input {:type "checkbox" :name "nonpr"}]]
    [:p "Reproducible? " [:input {:type "checkbox" :name "repro"}]]
    [:p "Grassroots? " [:input {:type "checkbox" :name "grass"}]]
    [:div {:id "map"}]
    [:p "Latitude: " [:input {:type "text" :name "lat" :id "lat" }]]
    [:p "Longitude: " [:input {:type "text" :name "lng" :id "lng" }]]
    [:p [:input {:type "submit" :class "button" :value "Submit new project"}]]]])

(defn home "The home page"
  [req]
  {:body req}) ;; {:uri (System/getenv "redistogo_url")})})

(defn- display-uid1 []
  (str (session/get :cred)))

(defn- register
  "Register a new account."
  []
  (h/html5
   (h/include-css "/css/generic.css")
   uregister-form))

(defn- pregister
  "Register a new project."
  []
  (h/html5
   (h/include-css "/css/generic.css")
   (h/include-css "http://cdn.leafletjs.com/leaflet-0.6.4/leaflet.css")
   (h/include-js "http://cdn.leafletjs.com/leaflet-0.6.4/leaflet.js")
   pregister-form
   (h/include-js "/js/map.js")))

(defn- create-new-user [{:keys [username password]}]
  (set-user username password))

(defn- create-new-project [{:keys [pname repro grass nonpr]}]
  (set-project pname repro grass nonpr))

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
  (let [logged?
        (if-let [identity (friend/identity req)]
          true false)]
    (h/html5
     (h/include-css "/css/generic.css")
     [:h1 "Welcome to Move Commons"]
     (when logged? [:p "Logged in."])
     (when (not logged?)
       [:div
        [:p (e/link-to "/register" "Register")]
        [:p (e/link-to "/login" "Login")]])
     (when logged?
       [:div
        [:p (e/link-to "/submit" "Submit project")]
        [:p (e/link-to "/logout" "Logout")]]))))


(defroutes app-routes 

  (GET "/" req (index req))
  (GET "/redis" [] (str {:uri (System/getenv "REDISTOGO_URL")}))
  (GET "/register" [] (register))
  (POST "/uregistration" {params :params} (create-new-user params))

  ;; (GET "/submit" [] (pregister))
  (GET "/submit"
       req (if-let [identity (friend/identity req)]
             (pregister)
             (str "<h1>Ooops</h1><p>Sorry but you can't register a new project.</p>"
                  "<p>Please <a href=\"/register\">register</a> or <a href=\"/login\">login</a> first.</p>")))
  (POST "/pregistration" {params :params} (create-new-project params))

  (GET "/uid" [] (display-uid1))
  (GET "/login" req (h/html5 
                     (h/include-css "/css/generic.css")
                     login-form))
  (GET "/check" req
       (if-let [identity (friend/identity req)]
         (apply str "Logged in, with these roles: "
                (-> identity friend/current-authentication :roles))
         "You are an anonymous user."))

  (GET "/logout" req (friend/logout* (resp/redirect (str (:context req) "/"))))
  (route/resources "/")
  (route/not-found "Not found"))

(def app (middleware/app-handler
          [(wrap-friend app-routes)]
           :middleware []
           :access-rules []))

(def war-handler (middleware/war-handler app))
