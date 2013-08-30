(ns wlmmap.map
  (:require [mrhyde.extend-js]
            [blade :refer [L]]
            [cljs.core.async :refer [chan <! >!]]
            [shoreleave.remotes.http-rpc :refer [remote-callback]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(blade/bootstrap)

(def mymap (-> L .-mapbox (.map "map" "examples.map-uci7ul8p")
      (.setView [45 3.215] 5)))

(def markers (L/MarkerClusterGroup.))

(remote-callback
 :testremote []
 #(doseq [a %]
    (when (and (:lat a) (:lon a) (:name a))
      (let [lat (:lat a)
            lng (:lon a)
            title (:name a)
            icon ((get-in L [:mapbox :marker :icon])
                  {:marker-symbol "post" :marker-color "0044FF"})
            marker (-> L (.marker (L/LatLng. lat lng) {:icon icon :title title}))]
        (.bindPopup marker title)
        (.addLayer markers marker)))))

(.addLayer mymap markers)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; (def tile-url
;;   "http://{s}.tile.cloudmade.com/3068c9a9c9b648cb910837cf3c5fce10/997/256/{z}/{x}/{y}.png")

;; (let [mappy (-> L (.map "mappy")
;;                 (.setView [48, 1.2] 3))]

;;   (-> L (.tileLayer tile-url {
;;             :maxZoom 18
;;             :attribution "Map data &copy; <a href=\"http://openstreetmap.org\">OpenStreetMap</a> contributors, <a href=\"http://creativecommons.org/licenses/by-sa/2.0/\">CC-BY-SA</a>, Design <a href=\"http://bzg.fr"})
;;         (.addTo mappy))

;;   (let [ch (chan)]
;;     (go (while true
;;           (let [v (<! ch)]
;;             (-> L (.marker v)
;;                 (.addTo mappy)
;;                 (.bindPopup "<b>Hello world!</b><br />I am a popup.")))))
;;     (go
;;      (doseq [v [[31.0586 -118.4486]
;;                 [32.1086 -118.4186]
;;                 [33.1286 -118.4116]]]
;;        (>! ch v)))))

;; (remote-callback :testremote [] #(js/alert %))
