(ns bluegenes-tool-store.auth
  (:require [imcljs.auth :as im-auth]
            [cheshire.core :as cheshire]
            [ring.util.http-response :as response]
            [config.core :refer [env]]
            [taoensso.timbre :as timbre :refer [error]]))

(defn get-service-root [env]
  (or (not-empty (:bluegenes-backend-service-root env))
      (:bluegenes-default-service-root env)))

(defn check-priv
  "Makes a request to the InterMine server to verify that the user in `req` is
  superuser on the mine which this BlueGenes instance belongs to. Only if this
  is true, will the `handler` function be run."
  [handler req]
  (let [{:keys [token] :as service} (get-in req [:params :service])
        mine-name (get-in req [:params :mine-name])
        ;; Doesn't care what mine you're on - will always verify with the default service.
        service (assoc service :root (get-service-root env))]
    (try
      (let [res (im-auth/who-am-i? service token)]
        (cond
          (:superuser res)
          (handler req)

          (contains? res :superuser)
          (response/unauthorized
            {:error (str "You need to login as a superuser on '" mine-name "' to perform tool operations on this BlueGenes.")})

          :else
          ;; The InterMine instance is too old to support returning the
          ;; superuser flag. We will instead give the user a useful message.
          (response/not-implemented
            {:error "Managing BlueGenes tools automatically is only supported on InterMine version 4.2.0 or newer."})))
      (catch java.net.ConnectException _e
        ;; Request timed out.
        (response/not-found
          {:error (str "Unable to reach InterMine service '" (:bluegenes-default-service-root env) "' to check privileges.")}))
      (catch Exception e
        (let [{:keys [status] :as res} (ex-data e)]
          (case status
            ;; User likely passed the token for a different mine.
            (401 403) (response/unauthorized
                        {:error (str "You need to login as a superuser on '" mine-name "' to perform tool operations on this BlueGenes.")})
            ;; Internal error thrown by one of our functions.
            nil (response/internal-server-error
                  {:error (str "Server error: " (ex-message e))})
            ;; Catch-all for unexpected error. Log and forward the erroneous response.
            (do (error e) res)))))))
