(ns bluegenes-tool-store.core
  (:require [compojure.core :refer [GET POST defroutes context]]
            [compojure.route :refer [resources files]]
            [config.core :refer [env]]
            [ring.middleware.reload :refer [wrap-reload]]
            [muuntaja.middleware :refer [wrap-format wrap-params]]
            [bluegenes-tool-store.tools :as tools]
            [ring.adapter.jetty :refer [run-jetty]]
            [taoensso.timbre :as timbre :refer [infof errorf]]
            [bluegenes-tool-store.auth :refer [check-priv]])
  (:gen-class))

(defroutes routes
  ;; serve all tool files in bluegenes/tools automatically.
  ;; they can't go in the resource folder b/c then they get jarred
  ;; when running uberjar or clojar targets,
  ;; and make the jars about a million megabytes too big.
  (files "/tools" {:root (.getPath tools/tools-path), :allow-symlinks? true})
  (context "/api" []
           (context "/tools" []
                    (GET "/all" [] (tools/get-all-tools))
                    (GET "/path" [] (tools/get-tools-path))
                    (POST "/install"   req (check-priv tools/install-tool req))
                    (POST "/uninstall" req (check-priv tools/uninstall-tool req))
                    (POST "/update"    req (check-priv tools/update-tools req)))))

(def handler (-> #'routes
                 ;; Watch changes to the .clj and hot reload them
                 (cond-> (:development env) (wrap-reload {:dirs ["src/clj"]}))
                 ;; Merges request :body-params into :params
                 (wrap-params)
                 ;; Decodes requests and encodes responses based on headers.
                 ;; Primarily to take transit+json body from frontend and put
                 ;; into :body-params.
                 (wrap-format)))

(defn -main
  "Start the BlueGenes Tool Store server.
  This is the main entry point for the application"
  [& args]
  ;; Parse the port from the configuration file, environment variables, or default to 5001
  ;; "PORT" is often the default value for app serving platforms such as Heroku and Dokku
  (let [port (Integer/parseInt (or (:server-port env) (:port env) "5001"))]
    (timbre/set-level! :info) ; Enable Logging
    (tools/initialise-tools)
    ;; Start the Jetty server by passing in the URL routes defined in `handler`
    (run-jetty handler {:port port :join? false})
    (infof "=== Bluegenes Tool Server server started on port: %s" port)))
