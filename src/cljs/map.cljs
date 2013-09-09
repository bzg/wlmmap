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

(declare maybe-show-here removelastlayer)
(.on mymap "dragstart" #(set! stopper "stop"))

(.on mymap "zoomstart"
     #(do (set! stopper "stop")
          (removelastlayer)))
     
(.on mymap "dragend"
     #(do (set! stopper "go")
          (removelastlayer)
          (maybe-show-here)))

(.on mymap "zoomend"
     #(do (set! stopper "go")
          (maybe-show-here)))

(def lang (.-language js/navigator))

;; (macros/rpc (set-db-options-from-lang lang) [p] (js/alert (pr-str p)))

(remote-callback :set-db-options-from-lang [lang] #(%))

(declare setdb addmarkers)

(def layers ())

(defn removelastlayer []
  (when (not (nil? (last layers)))
    (do (.removeLayer mymap (last layers))
        (set! layers (butlast layers)))))

(defn setmap []
  (let [db (dom/by-id "db")
        yo (dom/by-id "sm")
        per (dom/by-id "per")]
    (set! (.-value per) "0")
    (set! (.-onclick yo)
          #(do (when (not (empty? layers)) (removelastlayer))
               (addmarkers (clojure.string/replace (.-value db) #"/" ""))))))

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
               (set! (.-value per) (Math/round perc))
               (.bindPopup marker title)
               (.addLayer markers marker))))))
    (remote-callback :get-markers [dbb]
                     ;; #(go (doseq [[a cnt] (map list % (range 100000))]
                     ;;        (<! (timeout 1))
                     ;;        (>! ch [a cnt (/ (* cnt 100) (count %))]))))
                     #(go (doseq [[a cnt] (map list % (range 100000))]
                            (<! (timeout 1))
                            (>! ch [a cnt (/ (* cnt 100) (count %))]))))

    (.addLayer mymap markers)
    ;; (remote-callback
    ;;  :get-center [dbb]
    ;;  #(.setView mymap (vector (first %) (last %)) 5))
    ))

(defn addmarkers-toolserver [map-bounds-string]
  (let [ch (chan)
        markers (L/MarkerClusterGroup. {:disableClusteringAtZoom 10})
        per (dom/by-id "per")]
    (js/alert "go!!!")
    (set! layers (conj layers markers))
    (go (while (not= stopper "stop")
          (let [[[_ [lat lng] img title] cnt perc] (<! ch)
                icon ((get-in L [:mapbox :marker :icon])
                      {:marker-symbol ""
                       :marker-color (if img "FF0000" "0044FF")})
                marker (-> L (.marker (L/LatLng. lat lng) {:icon icon}))]
            (set! (.-value per) (Math/round perc))
            (.bindPopup marker title)
            (.addLayer markers marker))))
    (remote-callback :get-markers-toolserver [map-bounds-string]
                     #(go (doseq [[a cnt] (map list % (range 100000))]
                            (<! (timeout 1))
                            (>! ch [a cnt (/ (* cnt 100) (count %))]))))
    (.addLayer mymap markers)))

(defn maybe-show-here []
  (let [z (.getZoom mymap)]
    (when (> z 10)
      (addmarkers-toolserver (.toBBoxString (.getBounds mymap))))))

;; initialize the HTML page in unobtrusive way
(set! (.-onload js/window) setmap)
