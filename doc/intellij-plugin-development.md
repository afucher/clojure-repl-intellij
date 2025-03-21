# IntelliJ plugin development 

This docs covers the basic concepts to start working on this plugin.


## Overview about IntelliJ

This doc covers some common terms and short descriptions about key concepts. To go deeper and learn more, IntelliJ has a official documentation about [plugin development](https://plugins.jetbrains.com/docs/intellij/welcome.html).

In some cases just the docs are not enough, so you can check the [IntelliJ community source code](https://github.com/JetBrains/intellij-community), to see the implementation code, and also find some useful examples of usage.

## IntelliJ Concepts

### Project

A project encapsulates all of a project's source code, libraries, and build instructions into a single organizational unit.

> Everything in the IntelliJ Platform SDK is done within the context of a project.

A project defines collections referred to as Modules and Libraries. Depending on the project's logical and functional requirements, a single-module or a multi-module project can be created.

Reference: [Project | IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij/project-model.html#project)

### Editor

Represents an instance of a text editor. 

Once you have the Editor you can get information about Project, File and Document (handles the text of the editor).

### Actions

The Action System allows plugins to add their items to IntelliJ Platform-based IDE menus and toolbars. 
Actions in the IntelliJ Platform require a code implementation and must be registered. The action implementation determines the contexts in which an action is available and its functionality when selected in the UI. Registration determines where an action appears in the IDE UI. Once implemented and registered, an action receives callbacks from the IntelliJ Platform in response to user gestures.

Reference: [Action System | IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij/action-system.html)

### Run Configurations

The IntelliJ Platform Execution API allows running external processes from within the IDE, e.g., the REPL connection.

The name of that concept is Configurations (or Run Configurations), where the user can create/edit configurations using the UI or that can be run based on implementations created from the code. 

## Clojure and Java/Kotlin

IntelliJ is written in Java and Kotlin, so to develop a plugin and extends behaviors from IntelliJ you must write clases to be instantiated by IntelliJ. 
Since Clojure has interop with Java, the plugin development relays a lot in the interoperability:
 - working with IntelliJ Java/Kotlin classes;
 - generating classes using `:gen-class`;
 - making use of `reify`, `proxy` and `proxy+` to extend/instantiate classes;

 To abstract some of those needs, [clj4intellij](https://github.com/ericdallo/clj4intellij) was created.
 > Library for create IntelliJ plugins with Clojure.
