# Developing

This docs covers the basic concepts to start working on this plugin.


## Overview about IntelliJ

In this doc we will cover some common terms and short descriptions about key concepts. IntelliJ has a official documentation about [plugin development](https://plugins.jetbrains.com/docs/intellij/welcome.html), so to get more information we recommend check it.

When the doc is not enough, you can relay on the [IntelliJ community source code](https://github.com/JetBrains/intellij-community), to check the implementation of some classes, and also find some useful examples of usage.

#### Project
#### Editor
#### Actions
#### Configurations


## Clojure and Java/Kotlin

IntelliJ is written in Java and Kotlin, so to develop a plugin we need to work with it. 
Since Clojure has interop with Java, the plugin development relays a lot in the interoperability:
 - working with IntelliJ Java/Kotlin classes;
 - generating classes using `:gen-class`;
 - making use of `reify`, `proxy` and `proxy+` to extend/instantiate classes;

 To abstract some of those needs, [clj4intellij](https://github.com/ericdallo/clj4intellij) was created.
 > Library for create IntelliJ plugins with Clojure.


### Running locally

`./gradlew runIde` to spawn a new Intellij session with the plugin.

or

`./gradlew buildPlugin` to build the plugin, then install it from disk in Intellij, the zip should be on `./build/distributions/*.zip`.

## NREPL

Unless you need to edit some generated extension file or kotlin file, mostly clojure code is editable via repl while your plugin is running!

NREPL is included in the plugin during development, so you can jack in and edit most of the plugin behavior while running it.

It runs on port `7770`.
