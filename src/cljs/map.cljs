(ns wlmmap.map
  (:require [mrhyde.extend-js]
            [blade :refer [L]]
            [cljs.core.async :refer [chan <! >! timeout]]
            [shoreleave.remotes.http-rpc :refer [remote-callback]]
            [domina :as dom]
            [domina.events :as ev])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [shoreleave.remotes.macros :as macros]))

(blade/bootstrap)

(def mymap (-> L .-mapbox (.map "map" "examples.map-9ijuk24y")
               (.setView [45 3.215] 6)))

(def stopper "stop")
(def lang (.-language js/navigator))
(def zoomlimit 10)
(def layers '())

(defn removelastlayer []
  (when (seq (last layers))
    (do (.removeLayer mymap (last layers))
        (set! (.-value (dom/by-id "per")) "")
        (set! layers (butlast layers)))))

(defn- addmarkers [dbb]
  (set! stopper "go")
  (let [ch (chan)
        markers (L/MarkerClusterGroup.)
        per (dom/by-id "per")]
    (set! layers (conj layers markers))
    (go (while (not= stopper "stop")
          (let [[a cnt perc] (<! ch)]
            (macros/rpc
             (get-marker dbb a) [p]
             (let [[[lat lng] img title] p
                   icon ((get-in L [:mapbox :marker :icon])
                         {:marker-symbol ""
                          :marker-color (if img "FF0000" "0044FF")})
                   marker (-> L (.marker (L/LatLng. lat lng)
                                         {:icon icon}))]
               (set! (.-value per) (str (Math/round perc) "%"))
               (.bindPopup marker title)
               (.addLayer markers marker))))))
    (remote-callback :get-markers [dbb]
                     #(go (doseq [[a cnt] (map list % (range 100000))]
                            (<! (timeout 20))
                            (>! ch [a cnt (/ (* cnt 100) (count %))]))))
    (.addLayer mymap markers)
    (remote-callback
     :get-center [dbb]
     #(.setView mymap (vector (first %) (last %)) 5))))

(defn addmarkers-toolserver [map-bounds-string]
  (let [ch (chan)
        markers (L/MarkerClusterGroup.)]
    (set! layers (conj layers markers))
    (macros/rpc
     (get-markers-toolserver map-bounds-string) [res]
     (.addLayers
      markers
      (map #(let [[_ [lat lng] img title] %
                  icon ((get-in L [:mapbox :marker :icon])
                        {:marker-symbol ""
                         :marker-color (if img "FF0000" "0044FF")})
                  marker (-> L (.marker (L/LatLng. lat lng) {:icon icon}))]
              (.bindPopup marker title)
              marker)
           res)))
    (.addLayer mymap markers)))

(defn- geolocalize [position]
  (let [longitude (.-longitude js/position.coords)
        latitude (.-latitude js/position.coords)]
    (.setView mymap (vector latitude longitude) 15)
    (addmarkers-toolserver (.toBBoxString (.getBounds mymap)))))

(defn maybe-show-here []
  (let [z (.getZoom mymap)
        sh (dom/by-id "showhere")]
    (when (>= z zoomlimit)
      (set! (.-innerHTML sh) "->?<-"))
    (when (< z zoomlimit)
      (set! (.-innerHTML sh) "...."))))

(.on mymap "zoomend" maybe-show-here)

(defn- setmap [local]
  (let [db (dom/by-id "db")
        show (dom/by-id "sm")
        stop (dom/by-id "stop")
        per (dom/by-id "per")
        sh (dom/by-id "showhere")]
    (when (= local 1)
      (.getCurrentPosition (.-geolocation js/navigator) geolocalize))
    (set! (.-onclick show)
          #(do (set! stopper "stop")
               (when (seq layers) (removelastlayer))
               (addmarkers (clojure.string/replace (.-value db) #" ?/ ?" ""))))
    (set! (.-onclick stop) #(set! stopper "stop"))
    (set! (.-onclick sh)
          #(let [z (.getZoom mymap)]
             (when (or (>= z zoomlimit)
                       (and (< z zoomlimit)
                            (js/confirm
                             (str "This will load up to 5000 monuments from the database.\n\n"
                                  "It takes a while if the focus is too large.\n\n"
                                  "Zoom " (- zoomlimit z)
                                  " times to be more comfortable."))))
               (set! stopper "stop")
               (when (seq layers) (removelastlayer))
               (addmarkers-toolserver (.toBBoxString (.getBounds mymap))))))))

(defn- init []
  (let [lang0 (.-language js/navigator)
        loc (.-location js/window)
        wdb (re-find #"/../([^/]+)#?$" loc)]
    (cond (not (re-find #"/../([^/]+)?#?$" loc))
          (do (set! (.-href loc) (str loc (subs lang0 0 2) "/"))
              (setmap 0))
          wdb
          (do (setmap 0) (addmarkers (second wdb)))
          :else (setmap 1))))

(set! (.-onload js/window) init)
