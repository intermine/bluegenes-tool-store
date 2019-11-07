(ns bluegenes-tool-store.package
  (:require [cheshire.core :as cheshire]
            [clj-http.client :as client]
            [taoensso.timbre :refer [error warnf]]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.edn :as edn])
  (:import [java.util.zip GZIPInputStream]
           [org.apache.commons.compress.archivers.tar TarArchiveInputStream TarArchiveEntry]))

(defn- delete-dir
  "Safely delete a `package` directory and its contents, if it exists and is a
  child of the `target` directory."
  [target package]
  (when (and (.exists package)
             ;; Verify that package is a child of the target directory.
             (let [p (.getCanonicalPath package)
                   t (.getCanonicalPath target)]
               (and (string/starts-with? p t) (not= p t))))
    ;; Recursively delete contents of package directory.
    (doseq [file (reverse (file-seq package))]
      (io/delete-file file))))

;; Archive streaming code taken from:
;; https://github.com/Raynes/fs/blob/master/src/me/raynes/fs/compression.clj

(defn- tar-entries
  "Get a lazy-seq of entries in a tarfile."
  [^TarArchiveInputStream tin]
  (when-let [entry (.getNextTarEntry tin)]
    (cons entry (lazy-seq (tar-entries tin)))))

(defn- download-package-tgz
  "Downloads and decompresses a tgz file at `uri` into the folder `target`.
  Removes any leading 'package/' in the tar archive's entry paths. This is
  because it's prepended automatically by npmjs.org and we don't want it."
  [{:keys [uri name] :as package-data} target]
  (let [package-dir (io/file target name)]
    (try
      (delete-dir target package-dir)
      (with-open [in (TarArchiveInputStream. (GZIPInputStream. (io/input-stream uri)))]
        (doseq [^TarArchiveEntry entry (tar-entries in)
                 :when (not (.isDirectory entry))
                 :let [entry-path (string/replace-first (.getName entry) #"^package/" "")
                       output-file (io/file package-dir entry-path)]]
          (io/make-parents output-file)
          (io/copy in output-file)))
      package-data
      (catch Exception e
        (error e)
        (throw
          (ex-info (format "Failed to download NPM archive `%s`. This is likely due to a network error." uri)
                   {:uri uri :name name :target target}))))))

(defn- get-package-tgz
  "Uses the NPMJS Registry API to get a link to the tarball of the latest
  version of `package-name`."
  [package-name]
  (let [registry-url (str "https://registry.npmjs.org/" package-name)]
    (try
      (let [res (-> registry-url
                    client/get
                    :body
                    (cheshire/parse-string true))
            latest-tag (keyword (get-in res [:dist-tags :latest]))]
        {:uri (get-in res [:versions latest-tag :dist :tarball])
         :name package-name
         :version latest-tag})
      (catch Exception e
        (error e)
        (throw
          (ex-info (format "Failed to GET `%s`. This is likely due to a network error." registry-url)
                   {:package-name package-name :uri registry-url}))))))

(defn- add-package-to-config
  "Adds the package name and version to the tool `config`."
  [{:keys [name version]} config]
  (let [tools  (try (edn/read-string (slurp config))
                 (catch Exception _
                   (warnf "Tool config %s is missing or invalid. It will be recreated with %s installed." config name)))
        tools' (assoc-in tools [:tools name] version)]
    (spit config tools')
    tools'))

(defn- remove-package-from-config
  "Removes `package-name` from the tool `config`."
  [package-name config]
  (let [tools  (try (edn/read-string (slurp config))
                 (catch Exception _
                   (warnf "Tool config %s is missing or invalid. An empty one will be created." config)))
        tools' (update tools :tools dissoc package-name)]
    (spit config tools')
    tools'))

(defn install-package
  "Installs a package using its `package-name` into `target-dir` and updates
  `tools-config`. Do not call this function directly; use
  `bluegenes-tool-store.tools/package-operation` instead."
  [tools-config target-dir package-name]
  (some-> package-name
          get-package-tgz
          (download-package-tgz target-dir)
          (add-package-to-config tools-config)))

(defn uninstall-package
  "Uninstalls a package using its `package-name` from `target-dir` and updates
  `tools-config`. Do not call this function directly; use
  `bluegenes-tool-store.tools/package-operation` instead."
  [tools-config target-dir package-name]
  (let [package-dir (io/file target-dir package-name)]
    (delete-dir target-dir package-dir)
    (remove-package-from-config package-name tools-config)))
