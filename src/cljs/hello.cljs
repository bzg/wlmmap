(ns wlmmap.hello
  (:require
   [blade :refer [L]]
   [shoreleave.remotes.http-rpc :refer [remote-callback]]))

(blade/bootstrap)

(def tile-url
  "http://{s}.tile.cloudmade.com/3068c9a9c9b648cb910837cf3c5fce10/997/256/{z}/{x}/{y}.png")

(let [mappy (-> L (.map "mappy") 
                  (.setView [34.0086, -118.4986] 12))]

  (-> L (.tileLayer tile-url {
            :maxZoom 18
            :attribution "Map data &copy; <a href=\"http://openstreetmap.org\">OpenStreetMap</a> contributors, <a href=\"http://creativecommons.org/licenses/by-sa/2.0/\">CC-BY-SA</a>, Imagery © <a href=\"http://cloudmade.com\">CloudMade</a>"})
        (.addTo mappy))
  
  (doall
   (map
    #(-> L (.marker (vector (first %) (last %)))
         (.addTo mappy)
         (.bindPopup "<b>Hello world!</b><br />I am a popup.")
         (.openPopup))
    [[34.0086 -118.4986] [34.0586 -118.4486]]
    ))

  ;; (-> L (.marker (vector 34.0086 -118.4986))
  ;;     (.addTo mappy)
  ;;     (.bindPopup "<b>Hello world!</b><br />I am a popup.")
  ;;     (.openPopup))

  ;; (-> L (.circle [34.0286, -118.5486] 1000 {
  ;;           :color "red"
  ;;           :fillColor "#f03"
  ;;           :fillOpacity "0.5"})
  ;;       (.addTo mappy)
  ;;       (.bindPopup "I am a circle."))

  ;; (-> L (.polygon [
  ;;         [33.979, -118.48]
  ;;         [33.973, -118.46]
  ;;         [33.98, -118.447]])
  ;;       (.addTo mappy)
  ;;       (.bindPopup "I am a <del>polygon</del> triangle"))

  ;; (let [popup (-> L .popup)]
  ;;   (.on mappy "click" (fn [{:keys [latlng]} e]
  ;;   (-> popup (.setLatLng latlng)
  ;;             (.setContent (str "You clicked the map at " latlng))
  ;;             (.openOn mappy)))))

  )

;; (remote-callback :testremote [] #(js/alert %))
