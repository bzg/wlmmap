(ns wlmmap.vars
  (:require [cemerick.friend.credentials :refer (hash-bcrypt)]))

(def admin
  (atom {"bzg" {:username "bzg"
                :password (hash-bcrypt (System/getenv "backendpwd"))
                :roles #{::user}}}))

(def lang-pairs
  {
   0, ["aq" 	   "en"]
   1, ["ar" 	   "es"]
   2, ["at" 	   "de"]
   ;; 3, ["be-bru" 	   "nl"]
   4, ["be-vlg" 	   "en"]
   5, ["be-vlg" 	   "fr"]
   6, ["be-vlg" 	   "nl"]
   7, ["be-wal" 	   "en"]
   8, ["be-wal" 	   "fr"]
   9, ["be-wal" 	   "nl"]
   10, ["bo" 	   "es"]
   ;; 11, ["by" 	   "be-x-old"]
   12, ["ca" 	   "en"]
   13, ["ca" 	   "fr"]
   14, ["ch" 	   "fr"]
   ;; 15, ["ch-old" 	   "de"]
   ;; 16, ["ch-old" 	   "en"]
   ;; 17, ["ch-old" 	   "it"]
   18, ["cl" 	   "es"]
   19, ["co" 	   "es"]
   20, ["cz" 	   "cs"]
   21, ["de-by" 	   "de"]
   22, ["de-he" 	   "de"]
   23, ["de-nrw" 	   "de"]
   24, ["de-nrw-bm"   "de"]
   25, ["de-nrw-k"    "de"]
   26, ["dk-bygning"  "da"]
   27, ["dk-fortids"  "da"]
   28, ["ee" 	   "et"]
   29, ["es" 	   "ca"]
   30, ["es" 	   "es"]
   31, ["es" 	   "gl"]
   32, ["fr" 	   "ca"]
   33, ["fr" 	   "fr"]
   34, ["gb-eng" 	   "en"]
   ;; 35, ["gb-nir" 	   "en"]
   36, ["gb-sct" 	   "en"]
   37, ["gb-wls" 	   "en"]
   ;; 38, ["gh" 	   "en"]
   39, ["ie" 	   "en"]
   40, ["il" 	   "he"]
   41, ["in" 	   "en"]
   42, ["it" 	   "it"]
   43, ["it-88" 	   "ca"]
   44, ["it-bz" 	   "de"]
   45, ["ke" 	   "en"]
   46, ["lu" 	   "lb"]
   47, ["mt" 	   "de"]
   48, ["mx" 	   "es"]
   49, ["nl" 	   "nl"]
   50, ["nl-gem" 	   "nl"]
   51, ["no" 	   "no"]
   52, ["pa" 	   "es"]
   53, ["ph" 	   "en"]
   ;; 54, ["pk" 	   "en"]
   55, ["pl" 	   "pl"]
   56, ["pt" 	   "pt"]
   57, ["ro" 	   "ro"]
   58, ["rs" 	   "sr"]
   59, ["ru" 	   "ru"]
   60, ["se-bbr" 	   "sv"]
   61, ["se-fornmin"  "sv"]
   ;; 62, ["se-ship" 	   "sv"]
   63, ["sk" 	   "de"]
   64, ["sk" 	   "sk"]
   65, ["th" 	   "th"]
   ;; 66, ["tn" 	   "fr"]
   67, ["ua" 	   "uk"]
   68, ["us" 	   "en"]
   69, ["us-ca" 	   "en"]
   ;; 70, ["uy" 	   "es"]
   71, ["ve" 	   "es"]
   72, ["za" 	   "en"]
   73, ["ad" 	   "ca"]
   74, ["hu" 	   "hu"]
   })

(def toolserver-url
  "http://toolserver.org/~erfgoed/api/api.php?action=search&format=json&limit=5000&props=lat|lon|name|registrant_url|id|image|lang|monument_article")
;;  "http://tools.wmflabs.org/heritage/api/api.php?action=search&format=json&limit=5000&props=lat|lon|name|registrant_url|id|image|lang|monument_article")
(def toolserver-bbox-format-url
;;  "http://tools.wmflabs.org/heritage/api/api.php?action=search&format=json&limit=5000&props=lat|lon|name|registrant_url|id|image|lang|monument_article&bbox=%s")
  "http://toolserver.org/~erfgoed/api/api.php?action=search&format=json&limit=5000&props=lat|lon|name|registrant_url|id|image|lang|monument_article&bbox=%s")
(def wm-thumbnail-format-url
  "<img src=\"https://commons.wikimedia.org/w/index.php?title=Special%%3AFilePath&file=%s&width=250\" />")
(def wm-img-format-url
  "<a href=\"http://commons.wikimedia.org/wiki/File:%s\" target=\"_blank\">%s</a>")
(def wp-link-format-url
  "<a href=\"http://%s.wikipedia.org/wiki/%s\" target=\"_blank\">%s</a>")
(def src-format-url
  "Source: <a href=\"%s\" target=\"_blank\">%s</a>")
