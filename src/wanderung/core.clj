(ns wanderung.core
  (:require
   [datahike.api :as d])
  (:import
   [java.util Date]))

(defprotocol IConnection
  (connect [database]))

(defprotocol IExtract
  (extract-datoms [source]))

(defprotocol ILoad
  (load-datoms [target datoms]))

(defprotocol IMigrate
  (execute [plan]))

(defrecord DatahikeDB [state config]
  IConnection
  (connect [{:keys [state config]}]
    (swap! state assoc :conn (d/connect config)))
  IExtract
  (extract-datoms [{:keys [state]}]
    (->> (d/datoms @(:conn @state) :eavt)
         (mapv (fn [d] (vec (seq d))))
         (sort-by #(nth % 3))
         (partition-by #(nth % 3))
         (mapcat (fn [txs]
                   (let [tid (-> txs first (nth 3))]
                     (into [[tid :db/txInstant (Date.) tid true]] txs))))
         vec)
    )
  ILoad
  (load-datoms [{:keys [state]} datoms]
    @(d/load-entities (:conn @state) datoms)))

(defmulti create-source :wanderung/type)
(defmulti create-target :wanderung/type)

(defmethod create-source :datahike [cfg]
  (map->DatahikeDB {:state (atom nil)
                    :config cfg}))

(defmethod create-target :datahike [cfg]
  (map->DatahikeDB {:state (atom nil)
                    :config cfg}))

(defn migrate [source-cfg target-cfg]
  (let [source (create-source source-cfg)
        target (create-target target-cfg)]
    (connect source)
    (connect target)
    (load-datoms target (extract-datoms source))))


(comment

  (def cfg {:store {:backend :mem
                    :id "data-source"}
            :attribute-refs? false
            :keep-history? false
            :wanderung/type :datahike
            :name "Data Source"})

  (do
    (d/delete-database cfg)

    (d/create-database cfg)

    (def conn (d/connect cfg))

    (d/transact conn [{:db/ident :name
                       :db/valueType :db.type/string
                       :db/cardinality :db.cardinality/one}]))

  (def target-cfg {:store {:backend :mem
                           :id "data-target"}
                   :attribute-refs? false
                   :keep-history? false
                   :wanderung/type :datahike
                   :name "Data Target"})

  (do 
    (d/delete-database target-cfg)
    (d/create-database target-cfg))

  (migrate cfg target-cfg)

  (def target-conn (d/connect target-cfg))

  (d/datoms @target-conn :eavt)

  (d/datoms @conn :eavt)

  )
