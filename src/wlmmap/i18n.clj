(ns wlmmap.i18n)

(def trad
  {:dev-mode? true
   :fallback-locale :en
   :dictionary
   {:en {:main {:show "Show"
                :stop "Stop"
                :here "Here"
                :about "About"
                :links "Links"}
         :links {:intro "These links auto-display the monuments on the map."}
         :about {:a "About"
                 :b "This map has been developed during "
                 :c "It allows you to explore cultural heritage treasures of the world."
                 :d "Blue markers are for monuments with a photo."
                 :e "Red markers are for monuments without one."
                 :f "All the pictures are from "
                 :g ", available under a free license."
                 :h "The code behind this website is available from "
                 :i "I appreciate feedback and suggestions! "
                 :j "Drop me an email"}}
    :fr {:main {:show "Afficher"
                :stop "Stop"
                :here "Ici"
                :about "À propos"
                :links "Liens"}
         :links {:intro "Ces liens directs affichent immédiatement les monuments sur la carte."}
         :about {
                 :a "À propos"
                 :b "Cette carte a été développée pour "
                 :c "Elle permet d'explorer les monuments historiques du monde entier."
                 :d "Les points en rouge indiquent des monuments avec photo."
                 :e "Les points en bleu indiquent des monuments <b>sans</b> photo."
                 :f "Toutes les images viennent de "
                 :g ", disponibles sous licence libre."
                 :h "Le code derrière ce site web est disponible depuis "
                 :i "J'apprécie les remarques, critiques et suggestions! "
                 :j "Envoyez-moi un e-mail"
                 }}}})
