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
               (.setView [45 3.215] 5)))

(def setmap
  "Initialize lang and set the map's defaults.")

(def stopper "stop")

(declare maybe-show-here removelastlayer)
(.on mymap "movestart"
     #(do (set! stopper "stop")
          (removelastlayer)))
     
(.on mymap "moveend"
     #(do (set! stopper "go")
          (maybe-show-here)))

;; (.on mymap "zoomend" #(js/alert (.getZoom mymap)))

;; (remote-callback :test-file-exists [] #(js/alert %))
;; (defn test (js/alert "test!"))
;; (def coord (new L.LatLngBounds))
;; (def coord (new L.LatLngBounds))
;; (def coord2 (.toBBoxString coord))
;; (def mapbounds-tostring (.toBBoxString (.getBounds mymap)))

(def maxi 0)
(def lang (.-language js/navigator))

(declare setdb addmarkers)

(def layers ())

(defn removelastlayer []
  (when (not (nil? (last layers)))
    (do (.removeLayer mymap (last layers))
        (set! layers (butlast layers)))))

(defn setmap []
  (do
    (macros/rpc (set-db-options-from-lang lang) [])
    (macros/rpc (get-lang-list lang) [p] (def db0 p))
    ;; FIXME: is [p] needed here?
    (setdb)
    ;; (addmarkers db0)
    ))

(defn setdb0 [ldb mx]
  (do (def db0 (list ldb))
      (def maxi mx)
      (addmarkers db0)))

;; (defn show-here [bounds-string]
;;   (remote-callback :get-markers-toolserver [bounds-string]
;;                    #(js/alert (pr-str (first %)))))

;; (def bs "")

(defn setdb []
  (let [db (dom/by-id "db")
        mx (dom/by-id "max")
        yo (dom/by-id "go")]
    (set! (.-onclick yo)
          #(do 
             (when (not (empty? layers)) (removelastlayer))
             (setdb0 (clojure.string/replace
                      (.-value db) #"/" "")
                     (js/parseInt (.-value mx)))))))

(defn addmarkers [dbb map-bounds-string]
  (let [ch (chan)
        markers (L/MarkerClusterGroup.)
        per (dom/by-id "per")]
    (set! layers (conj layers markers))
    (go (while (not= stopper "stop")
          (let [[a cnt perc] (<! ch)]
            (macros/rpc
             (get-marker a dbb map-bounds-string) [p]
             (let [[[lat lng] img title] p
                   icon ((get-in L [:mapbox :marker :icon])
                         {:marker-symbol ""
                          :marker-color (if img "FF0000" "0044FF")})
                   marker (-> L (.marker (L/LatLng. lat lng)
                                         {:icon icon}))]
               (set! (.-value per) (Math/round perc))
               (.bindPopup marker title)
               (.addLayer markers marker))))))
    (remote-callback :get-markers [dbb map-bounds-string]
                     #(go (doseq [[a cnt] (map list
                                               (if (= maxi 0) % (take maxi %))
                                               (range 100000))]
                            (<! (timeout 1))
                            (>! ch [a cnt (/ (* cnt 100) (count %))]))))
    (.addLayer mymap markers)
    (remote-callback
     :get-center [dbb]
     #(.setView mymap (vector (first %) (last %)) 5))))

(defn addmarkers-toolserver [map-bounds-string]
  (let [ch (chan)
        markers (L/MarkerClusterGroup.)]
    (set! layers (conj layers markers))
    (go (while (not= stopper "stop")
          (let [[_ [lat lng] img title] (<! ch)
                icon ((get-in L [:mapbox :marker :icon])
                      {:marker-symbol ""
                       :marker-color (if img "FF0000" "0044FF")})
                marker (-> L (.marker (L/LatLng. lat lng) {:icon icon}))]
            (.bindPopup marker title)
            (.addLayer markers marker))))
    (remote-callback :get-markers-toolserver [map-bounds-string]
                      #(go (doseq [a %]
                             (<! (timeout 1))
                             (>! ch a))))
    (.addLayer mymap markers)))

(defn maybe-show-here []
  (let [z (.getZoom mymap)]
    (when (> z 10)
      (js/alert "Zoom ok: showing monuments")
      (addmarkers-toolserver (.toBBoxString (.getBounds mymap))))))

;; initialize the HTML page in unobtrusive way
(set! (.-onload js/window) setmap)
