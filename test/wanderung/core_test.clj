(ns wanderung.core-test
  (:require [clojure.test :refer :all]
            [datahike.api :as d]
            [wanderung.core :as w]))

(def base-txs
  [[{:db/ident       :name
     :db/cardinality :db.cardinality/one
     :db/index       true
     :db/unique      :db.unique/identity
     :db/valueType   :db.type/string}
    {:db/ident       :parents
     :db/cardinality :db.cardinality/many
     :db/valueType   :db.type/ref}
    {:db/ident       :age
     :db/cardinality :db.cardinality/one
     :db/valueType   :db.type/long}]
   [{:name "Alice"
     :age  25}
    {:name "Bob"
     :age 30}]
   [{:name    "Charlie"
     :age     5
     :parents [[:name "Alice"]
               [:name "Bob"]]}]])

(defn setup-db [cfg]
  (d/delete-database cfg)
  (d/create-database cfg))

(defn hydrate-db [cfg]
  (let [conn (d/connect cfg)]
    (doseq [tx base-txs]
      (d/transact conn {:tx-data tx}))
    (d/release conn)))

(deftest test-datahike-datahike-migration
  (testing "Same source and target configuration"
    (let [base-config {:store {:backend :mem
                               :id "base-db"}
                       :attribute-refs? false
                       :keep-history? false
                       :wanderung/type :datahike
                       :name "Base Database"}
          source-config (-> base-config
                            (assoc-in [:store :id] "source")
                            (assoc :name "source"))
          target-config (-> base-config
                            (assoc-in [:store :id] "target")
                            (assoc :name "target"))]
      (setup-db source-config)
      (hydrate-db source-config)
      (setup-db target-config)
      (w/migrate source-config target-config)
      )))
