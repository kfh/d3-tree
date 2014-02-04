(ns d3-tree.parser
  (:require [clojure.string :as str]
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

(defn get-sub-categories
  [node]
  (l/select (lz/zip node) (l/class= "bsnch")))

(defn get-sub-sub-categories
  [node]
  (l/select (lz/zip node) (l/class= "bsnclco") (l/class= "noborder")))

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

(defn filter-sub-sub-cat
  [sub-sub-cat]
  (if (= "bsnclco" (get-in sub-sub-cat [:attrs :class]))
    (:content sub-sub-cat) (vector sub-sub-cat)))

(defn merge-sub-cat-with-sub-tree
  [pair]
  {:id (swap! id inc) :name (-> pair first (str/trim)) :children (second pair)})

(defn build-category-sub-tree
  [sub-cats sub-sub-cats]
  (let [sub-tree (for [cat sub-sub-cats
                       :let [sub-sub-cat (filter-sub-sub-cat cat)]]
                   (build-product-tree sub-sub-cat))]
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
     (let [sub-cats (get-sub-categories multi-menu)]
       (when-not (empty? sub-cats)
         (->> (get-sub-sub-categories multi-menu)
              (build-category-sub-tree sub-cats)
              (build-category-main-tree multi-menu)))))
   html))

(defn scrape-html
  []
  (let [tree (->> url
                  (slurp)
                  (l/parse)
                  (get-all-categories)
                  (build-category-tree)
                  (clojure.walk/postwalk identity))]
    {:id 0 :name "Categories" :children tree}))
