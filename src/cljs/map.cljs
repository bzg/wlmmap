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
(macros/rpc (set-db-options-from-lang (.-language js/navigator)) [p] p)
(def zoomlimit 10)
(def layers ())

(defn removelastlayer []
  (when (not (nil? (last layers)))
    (do (.removeLayer mymap (last layers))
        (set! (.-value (dom/by-id "per")) "")
        (set! layers (butlast layers)))))

(defn addmarkers [dbb]
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
                            (<! (timeout 5))
                            (>! ch [a cnt (/ (* cnt 100) (count %))]))))

    (.addLayer mymap markers)
    (remote-callback
     :get-center [dbb]
     #(.setView mymap (vector (first %) (last %)) 5))))

(defn addmarkers-toolserver [map-bounds-string]
  (let [ch (chan)
        markers (L/MarkerClusterGroup.
                 {:disableClusteringAtZoom (+ zoomlimit 3)})
        per (dom/by-id "per")]
    (set! layers (conj layers markers))
    (go (while (not= stopper "stop")
          (let [[[id [lat lng] img title] cnt perc] (<! ch)
                icon ((get-in L [:mapbox :marker :icon])
                      {:marker-symbol ""
                       :marker-color (if img "FF0000" "0044FF")})
                marker (-> L (.marker (L/LatLng. lat lng) {:icon icon}))]
            (set! (.-value per) (str (Math/round perc) "%"))
            (.bindPopup marker title)
            (.addLayer markers marker))))
    (remote-callback :get-markers-toolserver [map-bounds-string]
                     #(go (doseq [[a cnt] (map list % (range 100000))]
                            (<! (timeout 5))
                            (>! ch [a cnt (/ (* cnt 100) (count %))]))))
    (.addLayer mymap markers)))

(defn maybe-show-here []
  (let [z (.getZoom mymap)
        sh (dom/by-id "showhere")]
    (when (>= z zoomlimit)
      (set! (.-innerHTML sh) "HERE"))
    (when (< z zoomlimit)
      (set! (.-innerHTML sh) "...."))))

(.on mymap "zoomend" #(maybe-show-here))

(defn setmap []
  (let [db (dom/by-id "db")
        ex (dom/by-id "ex")
        show (dom/by-id "sm")
        stop (dom/by-id "stop")
        per (dom/by-id "per")
        sh (dom/by-id "showhere")]
    (set! (.-onclick ex) #(.reload js/location))
    (set! (.-onclick show)
          #(do
             (when (not (empty? layers)) (removelastlayer))
             (addmarkers (clojure.string/replace (.-value db) #" ?/ ?" ""))))
    (set! (.-onclick stop) #(set! stopper "stop"))
    (set! (.-onclick sh)
          #(let [z (.getZoom mymap)]
             (when (< z zoomlimit)
               (js/alert
                (str "This will load up to 5000 monuments from the database.\n\n"
                     "It takes a while if the focus is too large.\n\n"
                     "Zoom " (- zoomlimit z)
                     " times to be more comfortable.")))
             (do (set! stopper "stop")
                 (when (not (empty? layers)) (removelastlayer))
                 (set! stopper "go")
                 (addmarkers-toolserver (.toBBoxString (.getBounds mymap))))))))

;; initialize the HTML page in unobtrusive way
(set! (.-onload js/window) setmap)
