(ns d3-tree.tree
  (:require [strokes :refer [d3]]
            [mrhyde.extend-js :as h]
            [mrhyde.typepatcher :refer [repersist]]
            [clojure.set :refer [rename-keys]]
            [clojure.walk :refer [prewalk postwalk-replace]]))

(strokes/bootstrap)

(def root-node (atom {}))

(def duration 750)

(def margin {:top 20 :right 120 :bottom 20 :left 120})
(def width (- 1200 (:right margin) (:left margin)))
(def height (- 800 (:top margin) (:bottom margin)))

(def diagonal
  (-> d3/svg
      (.diagonal)
      (.projection
       (fn [node]
         [(.-y node) (.-x node)]))))

(def tree-layout
  (-> d3/layout
      (.tree)
      (.size [height, width])))

(def tree-canvas
  (->  d3
       (.select "body")
       (.append "svg")
       (.attr "width" (+ width (:right margin) (:left margin)))
       (.attr "height" (+ height (:top margin) (:bottom margin)))
       (.append "g")
       (.attr "transform" (str "translate(" (:left margin) "," (:top margin) ")"))))

(defn repersist-nodes
  [nodes]
  (repersist
   nodes
   :skip [:children :_children :parent]))

(defn expand-node
  [node]
  (do
    (->> (prewalk
          #(if (= (:id node) (:id %))
             (rename-keys % {:_children :children}) %)
          @root-node)
         (reset! root-node))
    node))

(defn collapse-node
  [node]
  (do
    (->> (prewalk
          #(if (= (:id node) (:id %))
             (rename-keys % {:children :_children}) %)
          @root-node)
         (reset! root-node))
    node))

(defn collapse-root
  [root]
  (let [collapsed (->> root
                       (postwalk-replace {:children :_children}))]
    (rename-keys collapsed {:_children :children})))

(defn normalize-nodes
  [nodes]
  (h/map
   (fn [node]
     (h/assoc! node :y (* (.-depth node) 180)))
   nodes))

(defn click-handler
  [node]
  (if (contains? node :children)
    (collapse-node node)
    (when (contains? node :_children)
      (expand-node node))))

(defn stash-position
  [nodes]
  (h/map
   (fn [node]
     (h/assoc! node :x0 (.-x node) :y0 (.-y node)))
   nodes))

(defn node-enter
  [node source-node draw-tree]
  (let [enter (-> node
                  (.enter)
                  (.append "g")
                  (.attr "class" "node")
                  (.attr "transform" #(str "translate(" (:y0 source-node) "," (:x0 source-node) ")"))
                  (.on "click" #(-> % (repersist-nodes) (click-handler) (draw-tree))))]

    (-> enter
        (.append "circle")
        (.attr "r" 1e-6)
        (.style "fill" #(if (:_children %) "lightsteelblue" "#fff")))

    (-> enter
        (.append "text")
        (.attr "x" #(if (or (:children  %) (:_children %)) -10 10))
        (.attr "dy" ".35em")
        (.attr "text-anchor" #(if (or (:children %) (:_children %)) "end" "start"))
        (.text #(:name %))
        (.style "fill-opacity" 1e-6))))

(defn node-update
  [node]
  (let [update (-> node
                   (.transition)
                   (.duration duration)
                   (.attr "transform" #(str "translate(" (.-y %) "," (.-x  %) ")")))]

    (-> update
        (.select "circle")
        (.attr "r" 4.5)
        (.style "fill" #(if (:_children %) "lightsteelblue" "#fff")))

    (-> update
        (.select "text")
        (.style "fill-opacity" 1))))

(defn node-exit
  [node source-node]
  (let [exit (-> node
                 (.exit)
                 (.transition)
                 (.duration duration)
                 (.attr "transform" #(str "translate(" (:y source-node) "," (:x source-node) ")"))
                 (.remove))]

    (-> exit
        (.select "circle")
        (.attr "r" 1e-6))

    (-> exit
        (.select "text")
        (.style "fill-opacity" 1e-6))))

(defn update-links
  [link source-node]
  (-> link
      (.enter)
      (.insert "path" "g")
      (.attr "class" "link")
      (.attr "d" #(let [o {:x (:x0 source-node) :y (:y0 source-node)}]
                    (diagonal {:source o :target o}))))

  (-> link
      (.transition)
      (.duration duration)
      (.attr "d" diagonal))

  (-> link
      (.exit)
      (.transition)
      (.duration duration)
      (.attr "d" #(let [o {:x (:x source-node) :y (:y source-node)}]
                    (diagonal {:source o :target o})))
      (.remove)))

(defn draw-tree
  [source-node]
  (let [nodes (-> tree-layout (.nodes @root-node) (.reverse) (normalize-nodes))
        links (-> tree-layout (.links nodes))
        node (-> tree-canvas (.selectAll "g.node") (.data nodes #(:id %)))
        link (-> tree-canvas (.selectAll "path.link") (.data links #(:id (repersist-nodes (.-target %)))))]

    (-> node (node-enter source-node draw-tree))

    (-> node (node-update))

    (-> node (node-exit source-node))

    (-> link (update-links source-node))

    (-> nodes (stash-position))))

(-> d3 (.json "/get-tree" (fn [error, jsroot]
                    (let [cljsroot (js->clj jsroot :keywordize-keys true)]
                      (-> cljsroot
                          (assoc :x0 (/ height 2))
                          (assoc :y0 0)
                          (as-> root
                                (do (reset! root-node (collapse-root root))
                                    (draw-tree root))))))))

(.. d3 (select (.-frameElement js/self)) (style "height" "800px"))
