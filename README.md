[![JetBrains Plugin Version](https://img.shields.io/jetbrains/plugin/v/com.github.clojure-repl?style=flat-square&labelColor=91B6FB&color=93DA52&link=https%3A%2F%2Fplugins.jetbrains.com%2Fplugin%2F23073-clojure-repl)](https://plugins.jetbrains.com/plugin/23073-clojure-repl)
[![Slack community](https://img.shields.io/badge/Slack-chat-blue?style=flat-square&labelColor=91B6FB&color=93DA52)](https://clojurians.slack.com/archives/C06DZSPDCPJ)

<img src="images/logo.svg" width="180" align="right">

# clojure-repl-intellij

<!-- Plugin description -->

Free OpenSource IntelliJ plugin for Clojure REPL development. 

Checkout all available [features](https://github.com/afucher/clojure-repl-intellij#features)

<!-- Plugin description end -->

![Clojure LSP Intellij](images/demo.png)

---
## Getting Started
After installing the plugin in IntelliJ, you can add a REPL to your Run
configurations.
1. Go to `Run` > `Edit Configurations`
2. Click `Add new` > `Clojure REPL` > `Remote`
3. Copy the values of host and port from an existing nREPL process

## Features

- Connect to an existing nREPL process
- Load file to REPL (`alt + shift + l` / `opt + shift + l`)
- Eval code at point (`alt + shift + e` / `opt + shift + e`)
- Switch to file namespace (`alt + shift + n` / `opt + shift + n`)

### Soon

- Start a nREPL server from IntelliJ
- Customize REPL UI
- Run test support

## Contributing

Contributions are very welcome, check the [issues page](https://github.com/afucher/clojure-repl-intellij/issues) for more information about what are good first issues or open an issue describing the desired support.


## Developing

`bb run-ide` to spawn a new IntelliJ session with the plugin.

or

`bb build-plugin` to build the plugin, then install it from disk in IntelliJ, the zip should be on `./build/distributions/*.zip`.

## Release

1. `bb tag x.y.z` to tag and push the new tag
2. `bb publish-plugin` to publish to Jetbrains Marketplace (requires JETBRAINS_TOKEN on env).
