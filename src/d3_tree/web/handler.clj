(ns d3-tree.web.handler
  (:require [compojure.route :as route]
            [compojure.handler :as handler]
            [compojure.core :refer [defroutes GET]]
            [ring.middleware.json]
            [d3-tree.parser :refer [scrape-html]]
            [ring.util.response :refer [response resource-response]]))

(declare category-tree)

(defn lein-ring-init
  []
  (println "Initializing screen scraping..")
  (def category-tree (scrape-html))
  (println "Scraping done!"))

(defn get-category-tree
  []
  (response category-tree))

(defroutes app-routes
  (GET "/" [] (resource-response "index.html" {:root "public"}))
  (GET "/get-tree" [] (get-category-tree))
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (ring.middleware.json/wrap-json-response
   (-> app-routes
       (handler/site))))
