(ns bluegenes-tool-store.tools
  (:require [cheshire.core :as cheshire]
            [config.core :refer [env]]
            [ring.util.http-response :as response]
            [clojure.java.io :as io]
            [taoensso.timbre :refer [infof errorf warnf]]
            [bluegenes-tool-store.package :refer [install-package uninstall-package]]
            [clojure.edn :as edn]
            [clj-http.client :as client])
  (:import [java.util.concurrent.locks ReentrantLock]))

(def tools-path
  "Path to the tools directory containing the config file and packages."
  (io/file (:bluegenes-tool-path env)))

(def tools-config
  "Path to the tools config file."
  (io/file tools-path "tools.edn"))

(defn get-all-npm-tools
  "Send an HTTP request to the NPMJS API to get a list of bluegenes tools."
  []
  (try
    (-> (client/get (str "https://api.npms.io/v2/search"
                         "?q=scope:intermine"
                         "+keywords:bluegenes-intermine-tool"))
        :body
        (cheshire/parse-string true)
        :results)
    (catch Exception e
      (errorf "Failed to acquire list of bluegenes-intermine-tool from `api.npms.io`: %s"
              (.getMessage e)))))

(declare package-operation)
(defn install-all-npm-tools
  "Install all bluegenes tools from the NPM registry."
  []
  (package-operation :install (mapv (comp :name :package) (get-all-npm-tools))))

(defn initialise-tools
  "Checks for tools config and initialises one if it doesn't exist."
  []
  (when-not (.exists tools-config)
    (infof "Tool config %s does not exist. It will automatically be created and all bluegenes tools will be installed." tools-config)
    (io/make-parents tools-config)
    (spit tools-config (pr-str {:tools {}}))
    (future (install-all-npm-tools))
    nil))

(defn installed-tools-list
  "Return a list of the installed tools in the tools config file."
  []
  (try
    (let [config (edn/read-string (slurp tools-config))]
      (keys (:tools config)))
    (catch Exception _
      (errorf "Tool config %s is missing. Please clear the tools directory at %s and restart BlueGenes." tools-config tools-path))))

(defn parse-tool
  "check tool folder for config and other relevant files and return as
  a map of useful info. This is used client-side by the browser to
  load tools relevant for a given report page.
  A valid tool needs to have the following:
  - package.json
  - config.json
  - dist/bundle.js"
  [tool-name]
  (try
    (let [path (io/file tools-path tool-name)
          ;; this is the bluegenes-specific config.
          bluegenes-config-path (io/file path "config.json")
          config (cheshire/parse-string (slurp bluegenes-config-path) true)
          ;;this is the default npm package file
          package-path (io/file path "package.json")
          package (cheshire/parse-string (slurp package-path) true)
          ;;optional preview image for each tool.
          preview-image "preview.png"
          preview-path (io/file path preview-image)
          ;; Bundle for running the tool.
          bundle-path (io/file path "dist" "bundle.js")]
      (when-not (.exists bundle-path)
        (warnf "The bluegenes tool bundle file %s is missing, causing the tool to not work in bluegenes. It's likely that the bundle file has not been included in the npm package." bundle-path))
      ;; so many naming rules that conflict - we need three names.
      ;; npm requires kebab-case bluegenes-tool-protvista
      ;; but js vars forbid kebab case bluegenesToolProtvista
      ;; humans want something with spaces "Protein viewer"
      ;; this is terminally incompatible, hence three names. Argh.
      {:names {:human (get-in config [:toolName :human])
               :cljs (get-in config [:toolName :cljs])
               :npm (get-in package [:name])}
       :config config
       :package (select-keys package [:description :license :homepage :name :author :version])
        ;; return image path if it exists, or false otherwise.
       :hasimage (if (.exists preview-path)
                   (.getAbsolutePath (io/file "/tools" tool-name preview-image))
                   false)})
    (catch Exception e
      (warnf "An error occured when parsing tool %s: %s" tool-name (.getMessage e)))))

(defn tools-list-res
  "Create response containing the list of tools along with their parsed data."
  []
  (response/ok
    {:tools (remove nil? ; Faulty tools will return nil.
                    (map parse-tool (installed-tools-list)))}))

(let [lock      (ReentrantLock.)
      install   (partial install-package tools-config tools-path)
      uninstall (partial uninstall-package tools-config tools-path)]
  (defn package-operation
    "Perform a package install or uninstall `operation` on `package+` which can
    be a single package name or a collection of multiple. Returns an error
    response if a different package-operation is already in progress.
    On success it will return a response with the new tool list."
    [operation package+]
    (if (.tryLock lock)
      (try
        (case operation
          :install (if (coll? package+)
                     (doseq [package-name package+]
                       (install package-name))
                     (install package+))
          :uninstall (if (coll? package+)
                       (doseq [package-name package+]
                         (uninstall package-name))
                       (uninstall package+)))
        (tools-list-res)
        (catch Exception e
          (response/bad-gateway {:error (.getMessage e)}))
        (finally
          (.unlock lock)))
      (response/service-unavailable {:error "A tool operation is already in progress. Please try again later."}))))

(defn verify-package-params
  "Helper to wrap API functions to check for validity of package parameters."
  [f]
  (fn [{:keys [params] :as req}]
    (if (or (contains? params :package)
            (contains? params :packages))
      (f req)
      (response/bad-request {:error "Please include your package name(s) in the package or packages parameter."}))))

;; Following is used for the Tool API routes.

(defn get-all-tools
  "Return list of tools as a REST response to our GET."
  []
  (tools-list-res))

(defn get-tools-path
  "Respond with tool path."
  []
  (response/ok {:path (.getAbsolutePath tools-path)}))

(def install-tool
  "Takes a request to install one or more tools."
  (verify-package-params
    (fn [{{:keys [package packages]} :params}]
      (package-operation :install (or package packages)))))

(def uninstall-tool
  "Takes a request to uninstall a tool."
  (verify-package-params
    (fn [{{:keys [package packages]} :params}]
      (package-operation :uninstall (or package packages)))))

(def update-tools
  "Takes a request to update a list of tools."
  (verify-package-params
    (fn [{{:keys [package packages]} :params}]
      (package-operation :install (or package packages)))))
