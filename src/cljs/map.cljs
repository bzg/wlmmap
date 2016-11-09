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

;; Promote the possibility of uploading a picture to Wikimedia Commons
(def wlm "<a target=\"_blank\" href=\"https://commons.wikimedia.org/wiki/Commons:Wiki_Loves_Monuments_upload\">Upload</a> a picture for <a target=\"_blank\" href=\"http://www.wikilovesmonuments.org\">Wiki Loves Monuments</a>!</a>")

;; Format string for the "Link to here" link in markers' popup 
(def lth "<a target=\"_blank\" href=\"https://www.panoramap.org/%s/%s/%s/%s\">Permalink to this position.</a>")

(def mymap (-> L .-mapbox (.map "map" "bzg.i8bb9pdk")
               (.setView [45 3.215] 6)))

(def stopper "stop")
(def lang (.-language js/navigator))
(def zoomlimit 10)
(def layers '())

(defn- removelastlayer []
  (when (seq (last layers))
    (do (.removeLayer mymap (last layers))
        (set! (.-value (dom/by-id "per")) "")
        (set! layers (butlast layers)))))

(defn- addmarkers-toolserver [map-bounds-string]
  (let [ch (chan)
        markers (L/MarkerClusterGroup.)
        z (.getZoom mymap)
        lang (last (re-find #"https://[^/]+/(..)/"
                            (.-location js/window)))]
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
              (.bindPopup marker (str title "<br/>" wlm
                                      "<br/>" (format lth lang lat lng z)))
              marker)
           res)))
    (.addLayer mymap markers)))

(defn- geolocalize [position]
  (let [longitude (.-longitude js/position.coords)
        latitude (.-latitude js/position.coords)]
    (.setView mymap (vector latitude longitude) 15)
    (addmarkers-toolserver (.toBBoxString (.getBounds mymap)))))

(defn- maybe-show-here []
  (let [z (.getZoom mymap)
        sh (dom/by-id "showhere")]
    (when (>= z zoomlimit)
      (set! (.-innerHTML sh) "->?<-"))
    (when (< z zoomlimit)
      (set! (.-innerHTML sh) "..."))))

(.on mymap "zoomend" maybe-show-here)

(defn- setmap [local]
  (let [db (dom/by-id "db")
        ;; show (dom/by-id "sm")
        stop (dom/by-id "stop")
        per (dom/by-id "per")
        sh (dom/by-id "showhere")]
    ;; FIXME: first switch to https for panoramap.org
    ;; http://stackoverflow.com/questions/32106849/getcurrentposition-and-watchposition-are-deprecated-on-insecure-origins
    ;; (when (= local 1)
    (.getCurrentPosition (.-geolocation js/navigator) geolocalize))
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
        latlonz (re-find #"https://[^/]+/../([^/#]+)/([^/#]+)/([^/#]+)#?$" loc)
        lat (when latlonz (second latlonz))
        lon (when latlonz (nth latlonz 2))
        zoom (when latlonz (last latlonz))
        wdb (re-find #"/../([^/#]+)#?$" loc)]
    (cond latlonz
          (do (setmap 0)
              (.setView mymap (vector lat lon) zoom)
              (addmarkers-toolserver (.toBBoxString (.getBounds mymap))))
          (not (re-find #"/../([^/#]+)?#?$" loc))
          (do (set! (.-href loc) (str loc (subs lang0 0 2) "/"))
              (setmap 0))
          ;; wdb (do (setmap 0) (addmarkers (second wdb)))
          :else (setmap 1))))

(set! (.-onload js/window) init)
