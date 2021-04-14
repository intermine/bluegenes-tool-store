[![Clojars Project](https://img.shields.io/clojars/v/org.intermine/bluegenes-tool-store.svg)](https://clojars.org/org.intermine/bluegenes-tool-store)

# BlueGenes Tool Store

## Development server

    lein dev

## Production server

    lein prod

## Deploying to Clojars

    lein deploy

## Initialising tools

The first time you start *bluegenes-tool-store*, all [npmjs](https://www.npmjs.com/) packages under the scope *intermine* with the keyword *bluegenes-intermine-tool* will be downloaded and their tool installed (for further tool management, use the BlueGenes interface).

To replicate this behaviour in a project where *bluegenes-tool-store* is used as a dependency, you need to add the following function call to your initialisation code.

```clojure
  ;; Add the require to your ns declaration.
  (:require [bluegenes-tool-store.tools :as tools]))

    ;; In your initialisation code.
    (tools/initialise-tools)
```

## Tool management via CLI

Note: The tools CLI doesn't give very user-friendly feedback, and is only meant as a temporary solution for older InterMine instances which don't support tool management via the BlueGenes interface due to security reasons.

If you still wish to use the tools CLI, run the following command for a usage guide.

```
lein tools help
```

If *bluegenes-tool-store* is used as a dependency in a different leiningen project, you can still use the tools CLI by adding `"tools" ["run" "-m" "bluegenes-tool-store.tools"]` to the `:alises` map in your *project.clj*.

## Note on using OpenJDK 9

If you use OpenJDK 9, you will need to add the `java9` profile to your leiningen task. You can do this by adding `with-profile +java9` to your command, like the following:

    lein with-profile +java9 <YOUR_TASK>

## System requirements

* Java 8-11 (we recommend [OpenJDK](https://adoptopenjdk.net/))
* Latest [Leiningen](https://leiningen.org/)
