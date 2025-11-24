# Changelog

## [Unreleased]

## 0.8.0

- Drop support of older IntelliJ versions (2021/2022). Now requires minimum IntelliJ 2023.3 (Build 233)
- Bump JAVA min version to 17
- Add support for tests.

## 0.7.1

- Fix clojure-lsp hook

## 0.7.0

- Create `def-extension` to create plugin.xml extension points easily and more idiomatic.

## 0.6.3

- Add clj-kondo hook for proxy+.

## 0.6.0

- Add unregister-action! and improve register-action!

## 0.5.4

- Fix keyboard shortcut side effect from 0.5.3 change.

## 0.5.3

- Fix keyboard shortcut register to not always add the default shortcut.

## 0.5.2

- Register actions only if not registered before.

## 0.5.1

- Support multiple keystrokes on action keymap.

## 0.5.0

- Add `com.github.ericdallo.clj4intellij.action` ns to register actions dynamically.

## 0.4.0

- Add tasks and util namespace.

## 0.3.7

## 0.3.6

## 0.3.5

## 0.3.4

- Allow skip nrepl server setup if nrepl key is nullable.

## 0.3.3

- Fix prod jar

## 0.3.2

- Include clojure code to jar.

## 0.3.1

- Support custom nrepl ports

## 0.3.0

- Add NREPL and logger support.

## 0.2.1

- Include clojure source to jar.

## 0.2.0

- Add `com.github.ericdallo.clj4intellij.app-manager` namespace with functions to access `ApplicationManager`.

## 0.1.3

- First release
