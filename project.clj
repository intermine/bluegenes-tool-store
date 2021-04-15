(defproject org.intermine/bluegenes-tool-store "0.2.2"
  :licence "LGPL-2.1-only"
  :description "Microservice which serves tools for use with BlueGene's Tool API"
  :url "http://www.intermine.org"
  :dependencies [; Clojure
                 [org.clojure/clojure "1.10.1"]
                 [org.clojure/tools.reader "1.3.4"]

                 ; HTTP
                 [clj-http "3.10.0"]
                 [compojure "1.6.2"]
                 [ring "1.8.2"]
                 [ring/ring-defaults "0.3.2"]
                 [cheshire "5.10.0"]
                 [metosin/ring-http-response "0.9.1"]
                 [metosin/muuntaja "0.6.7"]

                 ; Build tools
                 [yogthos/config "1.1.7"]

                 ; Logging
                 [com.taoensso/timbre "4.10.0"]
                 [com.fzakaria/slf4j-timbre "0.3.19"]

                 ; Utility libraries
                 [org.apache.commons/commons-compress "1.19"]

                 ; Intermine Assets
                 [org.intermine/imcljs "1.4.3"]]

  :deploy-repositories {"clojars" {:sign-releases false}}
  :plugins [[lein-codox "0.10.5"]
            [lein-ancient "0.6.15"]
            [lein-pdo "0.1.1"]
            [lein-cljfmt "0.6.1"]
            ;; Populates .lein-env with a profile's :env map,
            ;; so they're accessible to config.
            [lein-environ "1.2.0"]]

  :aliases {"dev" ["do" "clean," "run"]
            "prod" ["do" "clean,"
                    ["with-profile" "prod" "run"]]
            "uberjar" ["with-profile" "prod" "uberjar"]
            "install" ["do" "clean,"
                       ["with-profile" "noaot" "install"]]
            "deploy" ["do" "clean,"
                      ["with-profile" "noaot" "deploy" "clojars"]]
            "format" ["cljfmt" "fix"]
            "tools" ["run" "-m" "bluegenes-tool-store.tools"]}

  :min-lein-version "2.8.1"

  :source-paths ["src/clj"]

  :clean-targets ^{:protect false} ["target"]

  :profiles {:dev {:resource-paths ^:replace ["config/dev" "config/defaults" "resources"]
                   :env {:development true}}
             :prod {:resource-paths ^:replace ["config/prod" "config/defaults" "resources"]}
             :uberjar {:resource-paths ^:replace ["config/defaults" "resources"]
                       :prep-tasks ["clean" "compile"]
                       :aot :all}
             :noaot {:resource-paths ^:replace ["config/defaults" "resources"]
                     ;; Stop install/deploy from AOT compiling.
                     :prep-tasks []}
             :java9 {:jvm-opts ["--add-modules" "java.xml.bind"]}}

  :main bluegenes-tool-store.core

  :uberjar-name "bluegenes-tool-store.jar"

  :repositories [["clojars"
                  {:url "https://clojars.org/repo"}]])
                   ;; How often should this repository be checked for
                   ;; snapshot updates? (:daily, :always, or :never)
                   ;:update :always

