(ns bluegenes-tool-store.auth
  (:require [imcljs.auth :as im-auth]
            [cheshire.core :as cheshire]
            [ring.util.http-response :as response]
            [config.core :refer [env]]))

(defn check-priv
  "Makes a request to the InterMine server to verify that the user in `req` is
  superuser on the mine which this BlueGenes instance belongs to. Only if this
  is true, will the `handler` function be run."
  [handler req]
  (let [{:keys [token] :as service} (get-in req [:params :service])]
    (try
      (let [res      (im-auth/who-am-i? service token)
            env-root (:bluegenes-default-service-root env)]
        (cond
          (and (:superuser res)
               ;; Make sure that this BlueGenes instance belongs to the
               ;; service which we query for the superuser flag.
               (= (:root service) env-root))
               ;; A different way to do this would be to replace the root
               ;; key in the service to be env-root for the who-am-i?.
          (handler req)

          (contains? res :superuser)
          (response/unauthorized
            {:error
             (if env-root
               (str "You need to be a superuser on '" env-root "' to use this API")
               "The default service root for this BlueGenes instance needs to be defined")})

          :else
          ;; The InterMine instance is too old to support returning the
          ;; superuser flag. We will instead give the user a useful message.
          (response/not-implemented
            {:error "Managing BlueGenes tools automatically is only supported on InterMine version 5.0.0 or newer."})))
      (catch Exception e
        (let [{:keys [status body] :as error} (ex-data e)]
          ;; Parse the body of the bad request sent back from the IM server.
          (let [json-response (cheshire/parse-string body)]
            (case status
              401 (response/unauthorized json-response)
              500 (response/internal-server-error json-response)
              (response/not-found {:stack-trace error
                                   :error
                                   (str "Unable to reach InterMine service '"
                                        (:root service)
                                        "' to check privileges")}))))))))

