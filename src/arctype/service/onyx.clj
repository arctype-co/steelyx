(ns arctype.service.onyx
  (:import 
    [java.util UUID])
  (:require
    [arctype.service.protocol :refer :all]
    [arctype.service.util :refer [rmerge]]
    [clojure.tools.logging :as log]
    [schema.core :as S]
    [onyx.api :as onyx]))

(def Config
  {:peer-config {:zookeeper/address S/Str ; "127.0.0.1:2188"
                 :onyx.messaging/bind-addr S/Str ; "localhost"
                 S/Keyword S/Any}
   :n-peers S/Int ; Number of peers to spawn within this instance
   })

(def ^:private default-config
  {:peer-config {:onyx.peer/job-scheduler :onyx.job-scheduler/greedy
                 :onyx.messaging/impl :aeron
                 :onyx.messaging/peer-port 40200
                 ;;; Change "localhost" to a resolvable hostname
                 ;;; by any node in your cluster.
                 :onyx.messaging/bind-addr "localhost"}
   :n-peers 2})

(defrecord OnyxService [config]
  PLifecycle

  (start [this]
    (log/info {:message "Starting Onyx service"})
    (let [onyx-id (UUID/randomUUID)
          peer-config (assoc (:peer-config config) :onyx/tenancy-id onyx-id)
          peer-group (onyx/start-peer-group peer-config)
          peers (onyx/start-peers (:n-peers config) peer-group)]
      (assoc this
             :tenancy-id onyx-id
             :peer-group peer-group
             :peers peers)))

  (stop [this]
    (log/info {:message "Stopping Onyx service"})
    (doseq [peer (:peers this)]
      (onyx/shutdown-peer peer))
    (onyx/shutdown-peer-group (:peer-group this))
    (dissoc this :tenancy-id :peer-group :peers))
  
  )

(S/defn create
  [resource-name
   config :- Config]
  (let [config (rmerge default-config config)]
    (resource/create-resource
      (map->OnyxService
        {:config config})
      resource-name)))