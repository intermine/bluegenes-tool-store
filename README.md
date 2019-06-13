# BlueGenes Tool Store

## Development server

    lein dev

## Production server

    lein prod

## Deploying to Clojars

    lein with-profile +uberjar deploy clojars

## Note on using OpenJDK 9

If you use OpenJDK 9, you will need to add the `java9` profile to your leiningen task. You can do this by adding `with-profile +java9` to your command, like the following:

    lein with-profile +java9 <YOUR_TASK>

## System requirements

* OpenJDK, version 8 or 9 (only until we make our software compatible with OpenJDK 11)
* Latest [Leiningen](https://leiningen.org/)
* Latest supported [nodejs](https://nodejs.org/).  You can check your version using `node -v`). We recommend installing node using [nvm](https://github.com/creationix/nvm)
* Latest supported [npm](https://www.npmjs.com/)
