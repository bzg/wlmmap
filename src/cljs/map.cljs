(ns wlmmap.map
  (:require [mrhyde.extend-js]
            [blade :refer [L]]
            [cljs.core.async :refer [chan <! >! timeout]]
            [shoreleave.remotes.http-rpc :refer [remote-callback]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [shoreleave.remotes.macros :as macros]))

(blade/bootstrap)

(def mymap (-> L .-mapbox (.map "map" "examples.map-uci7ul8p")
               (.setView [45 3.215] 5)))

(def maxi 0)
(def stop "stop")
(def lang (.-language js/navigator))

;; FIXME: is [p] needed here?
(macros/rpc (set-db-options-from-lang lang) [p] p)

(declare setdb addmarkers)

(def layers ())

(defn removelastlayer []
  (do (.removeLayer mymap (last layers))
      (set! layers (butlast layers))))

(macros/rpc (get-lang-list lang) [p] (def db0 p))

(defn setmap []
  (do (setdb)
      ;; (addmarkers db0)
      ))

(defn setdb0 [ldb mx]
  (do (def db0 (list ldb))
      (def maxi mx)
      (addmarkers db0)))

(defn setdb []
  (let [db (.getElementById js/document "db")
        mx (.getElementById js/document "max")
        yo (.getElementById js/document "go")
        sp (.getElementById js/document "stop")]
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
        markers (L/MarkerClusterGroup.)]
    (set! layers (conj layers markers))
    (go (while (not= stop "stop")
          (let [a (<! ch)]
            (macros/rpc
             (get-marker a dbb) [p]
             (let [[[lat lng] img title] p
                   icon ((get-in L [:mapbox :marker :icon])
                         {:marker-symbol ""
                          :marker-color (if img "FF0000" "0044FF")})
                   marker (-> L (.marker (L/LatLng. lat lng)
                                         {:icon icon}))]
               (.bindPopup marker title)
               (.addLayer markers marker))))))
    (remote-callback :get-markers [dbb]
                     #(go (doseq [a (if (= maxi 0) % (take maxi %))]
                            (<! (timeout 50))
                            (>! ch a))))
    (.addLayer mymap markers)
    (remote-callback
     :get-center [dbb]
     #(.setView mymap (vector (first %) (last %)) 5))))

;; initialize the HTML page in unobtrusive way
(set! (.-onload js/window) setmap)
