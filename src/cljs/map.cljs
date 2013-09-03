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

(def markers (L/MarkerClusterGroup.))

(defn setmap []
  (let [ch (chan)]
    (remote-callback :set-db0 [(.-language js/navigator)] nil)
    (go (while true
          (let [a (<! ch)]
            (macros/rpc
             (get-marker a) [p]
             (let [lat (first (first p))
                   lng (last (first p))
                   title (last p)
                   icon ((get-in L [:mapbox :marker :icon])
                         {:marker-symbol "" :marker-color "0044FF"})
                   marker (-> L (.marker (L/LatLng. lat lng)
                                         {:icon icon}))]
               (.bindPopup marker title)
               (.addLayer markers marker))))))
    (remote-callback :get-markers []
                     #(go (doseq [a %]
                            (<! (timeout 1))
                            (>! ch a))))
    (.addLayer mymap markers)
    (remote-callback
     :get-center []
     #(.setView mymap (vector (first %) (last %)) 5))))

;; initialize the HTML page in unobtrusive way
(set! (.-onload js/window) setmap)