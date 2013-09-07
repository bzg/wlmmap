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

(def mymap (-> L .-mapbox (.map "map" "examples.map-uci7ul8p")
               (.setView [45 3.215] 5)))

(def maxi 0)
(def stop "stop")
(def lang (.-language js/navigator))

(declare setdb addmarkers)

(def layers ())

(defn removelastlayer []
  (do (.removeLayer mymap (last layers))
      (set! layers (butlast layers))))

(defn setmap []
  (do
    (macros/rpc (set-db-options-from-lang lang) [p] p)
    (macros/rpc (get-lang-list lang) [p] (def db0 p))
    ;; FIXME: is [p] needed here?
    (setdb)
    ;; (addmarkers db0)
    ))

(defn setdb0 [ldb mx]
  (do (def db0 (list ldb))
      (def maxi mx)
      (addmarkers db0)))

(defn setdb []
  (let [db (dom/by-id "db")
        mx (dom/by-id "max")
        yo (dom/by-id "go")
        sp (dom/by-id "stop")]
    (set! (.-onclick yo)
          #(do (set! stop "go")
               (when (not (empty? layers)) (removelastlayer))
               (setdb0 (clojure.string/replace
                        (.-value db) #"/" "")
                       (js/parseInt (.-value mx)))))
    (set! (.-onclick sp)
          #(set! stop "stop"))))

(defn addmarkers [dbb]
  (let [ch (chan)
        markers (L/MarkerClusterGroup.)
        per (dom/by-id "per")]
    (set! layers (conj layers markers))
    (go (while (not= stop "stop")
          (let [[a cnt perc] (<! ch)]
            (macros/rpc
             (get-marker a dbb) [p]
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
                     #(go (doseq [[a cnt] (map list
                                               (if (= maxi 0) % (take maxi %))
                                               (range 100000))]
                            (<! (timeout 1))
                            (>! ch [a cnt (/ (* cnt 100) (count %))]))))
    (.addLayer mymap markers)
    (remote-callback
     :get-center [dbb]
     #(.setView mymap (vector (first %) (last %)) 5))))

;; initialize the HTML page in unobtrusive way
(set! (.-onload js/window) setmap)
