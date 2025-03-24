# Developing  

⚠️ Before start developing, if you are not familiar with developing IntelliJ plugins, is recommended to read the [IntelliJ Plugin Development](https://github.com/ericdallo/clj4intellij/blob/master/doc/intellij-plugin-development.md) doc to onboard you.

The flow to develop the plugin is usually: 
 1. build locally;
 2. install in a local IntelliJ;
 3. connect REPl and evaluate your changes;

## Prerequisites

[Babashka](https://github.com/babashka/babashka#installation): Used to make run required tasks easier during development.


## Build and run locally

There is some ways to build and run this plugin inside the InteliJ locally, they are listed below in the order that we consider that is more efficient when developing:

`bb install-plugin` to builds the plugin, and install it from disk in IntelliJ automatically, then restart your IntelliJ. You need to pass the IntelliJ plugins path:  
e.g: ```bb install-plugin /home/youruser/.local/share/JetBrains/IdeaIC2024.3```

or

`bb build-plugin` to build the plugin, then install it manually from disk in IntelliJ, the zip should be on `./build/distributions/*.zip`. IntelliJ will ask to restart to get the new version.

or

`bb run-ide` to spawn a new IntelliJ session with the plugin.

## NREPL

Unless you need to edit some generated extension file or kotlin file, mostly clojure code is editable via repl while your plugin is running!

NREPL is included in the plugin during development, so you can jack in and edit most of the plugin behavior while running it.

It runs on port `7770`, you can configure the port in the [clj4intellij.edn](../src/main/dev-resources/META-INF/clj4intellij.edn) file.
