(ns bluegenes-tool-store.package
  (:require [bluegenes-tool-store.tools :as tools]
            [cheshire.core :as cheshire]
            [clj-http.client :as client]
            [taoensso.timbre :refer [infof]]))

(defn get-all-npm-tools
  "Send an HTTP request to the NPMJS API to get a list of bluegenes tools."
  []
  (-> (client/get "https://api.npms.io/v2/search?q=keywords:bluegenes-intermine-tool")
      :body
      (cheshire/parse-string true)
      :results))

(defn install-all-npm-tools
  "Call NPM through the shell to install all bluegenes tools from the NPM registry."
  []
  (let [packages (mapv (comp :name :package) (get-all-npm-tools))]
    (tools/sync-sh-req (into ["npm" "install" "--save"] packages)
                       (tools/tools-path :tools))))

(defn setup-package-json
  "Checks for package.json containing tools and initialises one if it doesn't exist."
  []
  (let [config-path (tools/tools-path :config :file true)]
    (when-not (.exists config-path)
      (infof "Tool config %s does not exist. It will automatically be created and all bluegenes tools will be installed." config-path)
      (spit (.getAbsolutePath config-path)
            (cheshire/generate-string {}))
      (future (install-all-npm-tools))
      nil)))
