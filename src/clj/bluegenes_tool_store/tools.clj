(ns bluegenes-tool-store.tools
  (:require [compojure.core :refer [GET POST defroutes]]
            [compojure.route :as route]
            [clojure.string :refer [blank? join ends-with?]]
            [cheshire.core :as cheshire]
            [config.core :refer [env]]
            [clojure.pprint :refer [pprint]]
            [ring.util.http-response :as response]
            [clojure.java.io :as io]
            [taoensso.timbre :as timbre :refer [log warn]]
            [clojure.java.shell :refer [sh with-sh-dir]])
  (:import [java.util.concurrent.locks ReentrantLock]))

(def tools-path
  "Returns the absolute path for the target tool path. We use memoize to cache
  it like a map since config values can't change during runtime. You can
  specify `:file true` to receive the java.io.File object instead."
  (memoize
    (fn [target & {:keys [file]}]
      (let [f (io/file (:bluegenes-tool-path env))]
        (when-not f
          (warn "Missing bluegenes-tool-path config variable."))
        (cond-> (case target
                  ;; node_modules directory containing the tools.
                  :modules f
                  ;; package.json in tools directory.
                  :config  (-> f .getParentFile (io/file "package.json"))
                  ;; tools directory itself.
                  :tools   (.getParentFile f))
          (not file) .getAbsolutePath)))))

(defn tool-config
  "check tool folder for config and other relevant files and return as
   a map of useful info. This is used client-side by the browser to
   load tools relevant for a given report page."
  [tool path]
  (let [tool-name (subs (str tool) 1)
        path (io/file path tool-name)
        ;; this is the bluegenes-specific config.
        bluegenes-config-path (io/file path "config.json")
        config (cheshire/parse-string (slurp bluegenes-config-path) true)
        ;;this is the default npm package file
        package-path (io/file path "package.json")
        package (cheshire/parse-string (slurp package-path) true)
        ;;optional preview image for each tool.
        preview-image "preview.png"
        browser-path (.getAbsolutePath (io/file "/tools" tool-name preview-image))]
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
     :hasimage (if (.exists (io/file path preview-image))
                 browser-path
                 false)}))

;; A valid tool needs to have the following:
;; - package.json
;; - config.json
;; - dist/bundle.js

(defn installed-tools-list
  "Return a list of the installed tools listed in the package.json file. "
  []
  (let [config-path (tools-path :config)]
    (try
     (let [packages (cheshire/parse-string (slurp config-path) true)
           package-names (keys (:dependencies packages))]
       package-names)
     (catch Exception e
            (log :warn
                 (str "Couldn't find tools at " config-path (.getMessage e) "- please run `npm init -y` in the tools directory and install your tools again."))))))

(defn tools-list-res
  "Create response containing the list of tools."
  []
  (let [modules-path (tools-path :modules)]
    (response/ok
      {:tools (map #(tool-config % modules-path)
                   (installed-tools-list))})))

(defn get-all-tools
  "Return list of tools as a REST response to our GET."
  []
  (tools-list-res))

(defn get-tools-path
  "Respond with tool path."
  []
  (response/ok (:bluegenes-tool-path env)))

(let [lock (ReentrantLock.)]
  (defn sync-sh-req
    "Run a synchronous shell command, rejecting in the case where a command
    is already in progress. This is mostly for wrapping calls to npm, which
    only allows a single process at a time.
    Returns a response with the new tool list on success."
    [cmdv dir]
    (if (.tryLock lock)
      (try
        (with-sh-dir dir
          (apply sh cmdv))
        (tools-list-res)
        (finally
          (.unlock lock)))
      (response/service-unavailable "Shell is already working. Please try again later."))))

(defn install-package
  "Call on NPM through the shell to install one or more packages, updating package.json."
  [{{:keys [package packages]} :params}]
  (cond
    package  (sync-sh-req ["npm" "install" "--save" package] (tools-path :tools))
    packages (sync-sh-req (into ["npm" "install" "--save"] packages)
                          (tools-path :tools))))

(defn uninstall-package
  "Call on NPM through the shell to uninstall a package, updating package.json."
  [{{:keys [package]} :params}]
  (sync-sh-req ["npm" "uninstall" "--save" package] (tools-path :tools)))

(defn update-packages
  "Call on NPM through the shell to update to the latest versions of the
  specified packages, which is passed as a vector of strings. Using
  `npm update` only updates the minor and patch versions, so we need to use
  `npm install` and specify the package names explicitly with `@latest` added
  to their name."
  [{{:keys [packages]} :params}]
  (let [to-update (map #(str % "@latest") packages)]
    (sync-sh-req (into ["npm" "install" "--save"] to-update) (tools-path :tools))))
