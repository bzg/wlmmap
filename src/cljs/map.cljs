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

(defn- cleanup-name [n]
  (-> n
      (clojure.string/replace #"^\[\[|\]\]" "")
      (clojure.string/replace #"[\n\r]" "")
      (clojure.string/replace #"\"" "\\\\\"")))

(let [ch (chan)]
  (go (while true
        (let [a (<! ch)]
          (when (and (:lat a) (:lon a) (not (= "" (:name a))))
          (let [lat (:lat a)
                lng (:lon a)
                title (cleanup-name (:name a))
                icon ((get-in L [:mapbox :marker :icon])
                      {:marker-symbol "post" :marker-color "0044FF"})
                marker (-> L (.marker (L/LatLng. lat lng) {:icon icon :title title}))]
            (.bindPopup marker title)
            (.addLayer markers marker))
          (.addLayer mymap markers)
          ))))
  (remote-callback
   :testremote []
   #(doseq [a %] (go (>! ch a)))))

;; (.addLayer mymap markers)
