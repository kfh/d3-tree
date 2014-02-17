(ns d3-tree.parser
  (:require [clojure.walk]
            [clojure.string :as str]
            [me.raynes.laser :as l]
            [me.raynes.laser.zip :as lz]
            [clj-http.client :as http]))

(def url "http://www.lazada.com.ph")
(def listView "?viewType=listView&sort=popularity&dir=desc")

(def id (atom 0))

(defn get-all-categories
  [html]
  (l/select html (l/class= "multiMenu")))

(defn get-main-category
  [node]
  (-> (l/select (lz/zip node) (l/class= "navSubTxt")) first l/text (str/trim)))

(defn get-sub-categories-tree
  [multi-menu html]
  (l/select html (l/id= (-> multi-menu :attrs :data-sub-menu))))

(defn get-sub-categories
  [node]
  (l/select (lz/zip (-> node first)) (l/class= "nav-title")))

(defn get-sub-sub-categories
  [node]
  (l/select (lz/zip (-> node first)) (l/class= "nav-linklist")))

(defn get-top-products
  [html]
  (map #(hash-map :id (swap! id inc) :name (str/trim (l/text %))) (take 5 (l/select html (l/class= "itm-title")))))

(defn get-products
  [uri]
  (let [response (http/get (str url uri listView) {:throw-exceptions false})]
    (if (= 200 (:status response))
      (->  response
           :body
           (l/parse)
           (get-top-products))
      (list {:id (swap! id inc) :name "No products found!"}))))

(defn build-product-tree
  [sub-sub-cats]
  (for [{:keys [attrs content]} sub-sub-cats
        :let [uri (:href attrs)]
        :when (not= nil uri)]
    {:id (swap! id inc) :name (-> content first (str/trim)) :children (get-products uri)}))

(defn merge-sub-cat-with-sub-tree
  [pair]
  {:id (swap! id inc) :name (-> pair first (str/trim)) :children (second pair)})

(defn build-category-sub-tree
  [sub-cats sub-sub-cats]
  (let [sub-tree (map
                  #(build-product-tree (-> % :content))
                  sub-sub-cats)]
    (->> (interleave (map #(l/text %) sub-cats) sub-tree)
         (partition 2)
         (map merge-sub-cat-with-sub-tree))))

(defn build-category-main-tree
  [node sub-tree]
  {:id (swap! id inc) :name (get-main-category node) :children sub-tree})

(defn build-category-tree
  [html]
  (keep
   (fn [multi-menu]
     (let [sub-cats-tree (get-sub-categories-tree multi-menu html)]
       (when-not (empty? sub-cats-tree)
         (let [sub-cats (get-sub-categories sub-cats-tree)
               sub-sub-cats (get-sub-sub-categories sub-cats-tree)]
           (->> (build-category-sub-tree sub-cats sub-sub-cats)
                (build-category-main-tree multi-menu))))))
   (get-all-categories html)))

(defn scrape-html
  []
  (let [tree (->> url
                  (slurp)
                  (l/parse)
                  (build-category-tree)
                  (clojure.walk/postwalk identity))]
    {:id 0 :name "Categories" :children tree}))
