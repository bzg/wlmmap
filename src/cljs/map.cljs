(ns wlmmap.map
  (:require [mrhyde.extend-js]
            [blade :refer [L]]
            [cljs.core.async :refer [chan <! >! timeout]]
            [shoreleave.remotes.http-rpc :refer [remote-callback]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(blade/bootstrap)

(def lang (.-language js/navigator))

(remote-callback :set-db0 [lang] nil)

(def mymap (-> L .-mapbox (.map "map" "examples.map-uci7ul8p")
               (.setView [45 3.215] 5)))

(def markers (L/MarkerClusterGroup.))

(let [ch (chan)]
  (go (while true
        (let [a (<! ch)]
          (let [lat (first (first a))
                lng (last (first a))
                title (last a)
                icon ((get-in L [:mapbox :marker :icon])
                      {:marker-symbol "" :marker-color "0044FF"})
                marker (-> L (.marker (L/LatLng. lat lng)
                                      {:icon icon}))]
            (.bindPopup marker title)
            (.addLayer markers marker)))))
  (remote-callback :get-markers []
                   #(go (doseq [a %]
                          (<! (timeout 1))
                          (>! ch a)))))

(.addLayer mymap markers)

(remote-callback
 :get-center []
 #(.setView mymap (vector (first %) (last %)) 5))
