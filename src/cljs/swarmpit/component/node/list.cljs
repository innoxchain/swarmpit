(ns swarmpit.component.node.list
  (:require [material.icon :as icon]
            [material.components :as comp]
            [material.component.list.util :as list-util]
            [material.component.form :as form]
            [material.component.label :as label]
            [material.component.chart :as chart]
            [swarmpit.component.mixin :as mixin]
            [swarmpit.component.state :as state]
            [swarmpit.component.common :as common]
            [swarmpit.ajax :as ajax]
            [swarmpit.routes :as routes]
            [swarmpit.url :refer [dispatch!]]
            [sablono.core :refer-macros [html]]
            [rum.core :as rum]
            [goog.string.format]
            [clojure.contrib.inflect :as inflect]
            [clojure.contrib.humanize :as humanize]))

(enable-console-print!)

(defn- node-item-state [value]
  (case value
    "ready" (label/green value)
    "down" (label/red value)))

(defn- node-item-labels [item]
  (form/item-labels
    [(node-item-state (:state item))
     (when (:leader item)
       (label/primary "leader"))
     (label/grey (:role item))
     (if (= "active" (:availability item))
       (label/green "active")
       (label/grey (:availability item)))]))

(defn node-used [stat]
  (cond
    (< stat 75) {:name  "actual"
                 :value stat
                 :color "#43a047"}
    (> stat 90) {:name  "actual"
                 :value stat
                 :color "#d32f2f"}
    :else {:name  "actual"
           :value stat
           :color "#ffa000"}))

(rum/defc node-graph [stat label id]
  (let [data [(node-used stat)
              {:name  "rest"
               :value (- 100 stat)
               :color "#ccc"}]]
    (chart/pie
      data
      label
      "Swarmpit-node-stat-graph"
      id
      nil)))

(defn- node-item
  [item index]
  (let [cpu (-> item :resources :cpu (int))
        memory-bytes (-> item :resources :memory (* 1024 1024))
        disk-bytes (-> item :stats :disk :total)]
    (comp/grid
      {:item true
       :key  (str "nlig-" index)}
      (html
        [:a {:class "Swarmpit-node-href"
             :key   (str "nligah-" index)
             :href  (routes/path-for-frontend :node-info {:id (:id item)})}
         (comp/card
           {:className "Swarmpit-form-card"
            :key       (str "nlic-" index)}
           (comp/card-header
             {:title     (:nodeName item)
              :className "Swarmpit-form-card-header"
              :key       (str "nlich-" index)
              :subheader (:address item)
              :avatar    (comp/svg (icon/os (:os item)))})
           (comp/card-content
             {:key (str "nlicc-" index)}
             (str "docker " (:engine item)))
           (comp/card-content
             {:key (str "nliccl-" index)}
             (node-item-labels item))
           (comp/card-content
             {:className "Swarmpit-table-card-content"
              :key       (str "nliccs-" index)}
             (html
               [:div {:class "Swarmpit-node-stat"
                      :key   (str "nliccsc-" index)}
                (node-graph
                  (get-in item [:stats :cpu :usedPercentage])
                  (str cpu " " (inflect/pluralize-noun cpu "core"))
                  (str "graph-cpu-" index))
                (node-graph
                  (get-in item [:stats :disk :usedPercentage])
                  (str (humanize/filesize disk-bytes :binary false) " disk")
                  (str "graph-disk-" index))
                (node-graph
                  (get-in item [:stats :memory :usedPercentage])
                  (str (humanize/filesize memory-bytes :binary false) " ram")
                  (str "graph-memory-" index))])))]))))

(defn- nodes-handler
  []
  (ajax/get
    (routes/path-for-backend :nodes)
    {:on-success (fn [{:keys [response]}]
                   (state/update-value [:items] response state/form-value-cursor))}))

(defn form-search-fn
  [event]
  (state/update-value [:filter :query] (-> event .-target .-value) state/form-state-cursor))

(defn- init-form-state
  []
  (state/set-value {:filter {:query ""}} state/form-state-cursor))

(def mixin-init-form
  (mixin/init-form
    (fn [_]
      (init-form-state)
      (nodes-handler))))

(rum/defc form < rum/reactive
                 mixin-init-form
                 mixin/subscribe-form
                 mixin/focus-filter [_]
  (let [{:keys [items]} (state/react state/form-value-cursor)
        {:keys [filter]} (state/react state/form-state-cursor)
        filtered-items (list-util/filter items (:query filter))]
    (comp/mui
      (html
        [:div.Swarmpit-form
         [:div.Swarmpit-form-context
          (if (empty? filtered-items)
            (common/list-no-items-found)
            (comp/grid
              {:container true
               :spacing   40}
              (map-indexed
                (fn [index item]
                  (node-item item index)) (sort-by :nodeName filtered-items))))]]))))

