(defproject org.intermine/bluegenes-tool-store "0.2.0-ALPHA"
  :licence "LGPL-2.1-only"
  :description "Microservice which serves tools for use with BlueGene's Tool API"
  :url "http://www.intermine.org"
  :dependencies [; Clojure
                 [org.clojure/clojure "1.10.1"]

                 ; HTTP
                 [clj-http "3.10.0"]
                 [compojure "1.6.1"]
                 [ring "1.7.1"]
                 [ring/ring-defaults "0.3.2"]
                 [ring/ring-json "0.4.0" :exclusions [cheshire.core]]
                 [cheshire "5.8.1"]
                 [metosin/ring-http-response "0.9.1"]
                 [ring-middleware-format "0.7.4"]

                 ; Build tools
                 [yogthos/config "0.9"]

                 ; Logging
                 [com.taoensso/timbre "4.10.0"]
                 [com.fzakaria/slf4j-timbre "0.3.14"]]

  :deploy-repositories {"clojars" {:sign-releases false}}
  :plugins [[lein-codox "0.10.5"]
            [lein-ancient "0.6.15"]
            [lein-pdo "0.1.1"]
            [lein-cljfmt "0.6.1"]]

  :aliases {"dev" ["do" "clean" ["pdo" ["run"]]]
            "prod" ["do" "clean" ["pdo" ["run"]]]
            "format" ["cljfmt" "fix"]}

  :min-lein-version "2.8.1"

  :source-paths ["src/clj"]

  :clean-targets ^{:protect false} ["target"]

  :profiles {:dev {:resource-paths ["config/dev" "tools" "config/defaults"]}
             :prod {:resource-paths ["config/prod" "tools"  "config/defaults"]}
             :uberjar {:resource-paths ["config/prod" "config/defaults"]
                       :prep-tasks ["clean" "compile"]
                       :aot :all}
             :java9 { :jvm-opts ["--add-modules" "java.xml.bind"]}}

  :main bluegenes-tool-store.core

  :uberjar-name "bluegenes-tool-store.jar"

  :repositories [["clojars"
                  {:url "https://clojars.org/repo"}]])
                   ;; How often should this repository be checked for
                   ;; snapshot updates? (:daily, :always, or :never)
                   ;:update :always

