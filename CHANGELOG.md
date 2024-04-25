# Changelog

## [Unreleased]

- Fix regression on 1.2.0: unable to run old local REPL configurations.

## 1.3.0

- Add entry history navigation with `ctrl + PG_UP` and `ctrl + PG_DOWN`

## 1.2.0

- Fix remote run configuration wrong state after open a previously saved configuration.
- Add support alias for Clojure and Lein project types
- Add support choosing project type for local repl instead of only guess, now we guess when creating the run configuration but let user choose a different one.

## 1.1.2

- Fix ANSI chars in console removing it. #70

## 1.1.1

- Fix minor regression exception on 1.1.0. 

## 1.1.0

- Fixes Prints to stdout/stderr do not show on REPL when happens async (tests, async threads) #65
- Fix a exception that happens after some seconds of repl running
- Add new eval defun action.

## 1.0.4

- Fix print failing tests. #62

## 1.0.3

- Fix Load file action not showing the error in case of eval error.
- Fix Override configuration when editing multiple configs in same project. #42

## 1.0.2

- Fix noisy exceptions introduced on 1.0.1 when opening multiple projects.

## 1.0.1
 
- Fix support for IntelliJ 2024.1

## 1.0.0

- Improve success test report message UI.
- Support multiple opened projects. #51
- Fix eval not using same session as load-file. #52

## 0.1.7

- Use cider-nrepl middleware to support more features.
- Add test support. #46
- Fix freeze on evaluation. #48

## 0.1.6

## 0.1.5

- Fix cwd of the spawned repl process to be the project dir.

## 0.1.4

- Add support for starting REPL from inside IntelliJ. #40
- Add support to read .nrepl-port file to connect to a running nREPL process. #5

## 0.1.3

- Fix repl output duplicated.
- Clear repl state on repl disconnection.

## 0.1.2

## 0.1.1

## 0.1.0

## 0.1.0

- Connect to an existing nREPL process via host and port
- Load file to REPL
- Eval code at point
