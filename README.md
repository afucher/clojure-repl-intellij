<img src="images/logo.svg" width="180" align="right">

# clojure-repl-intellij

<!-- Plugin description -->

Free OpenSource Intellij plugin for Clojure REPL development.

<!-- Plugin description end -->

---

## Features

- Connect to an existing nREPL process
- Load file to REPL
- Eval code at point

### Soon

- Start a nREPL server from Intellij

## Contributing

Contributions are very welcome, check the [issues page](https://github.com/afucher/clojure-repl-intellij/issues) for more information about what are good first issues or open an issue describing the desired support.


## Developing

`bb run-ide` to spawn a new Intellij session with the plugin.

or

`bb build-plugin` to build the plugin, then install it from disk in Intellij, the zip should be on `./build/distributions/*.zip`.


