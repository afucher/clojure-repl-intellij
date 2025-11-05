# Changelog

## [Unreleased]

## 2.7.0

- Fix REPL start commands for Windows

## 2.6.3

- Fix Custom Code Action not working in new versions of IntelliJ (2025)

## 2.6.2

- Fix Custom Code Action not working in new versions of IntelliJ (2025)

## 2.6.1

- Fix parser to handle namespaced def forms like `s/deftest` in `find-var-at-pos` function

## 2.6.0

- Add Custom Code Actions feature, enabling the user to define custom code snippets to be executed as an action.
  - Config can be defined at different levels: user or project. And can be exported inside libraries.
  - To check all details, read the [feature doc](./doc/custom-code-actions.md)
- Fix REPL output when evaluation generates stdout. #153

## 2.5.3

- Add client info to clone op for metrics.
- Bump nrepl + cider-nrepl version on local repl.

## 2.5.2

- Disable "clear REPL" action when REPL is not connected. #126
- Bump clj4intellij to 0.8.0
- Configure project with IntelliJ integration tests (headless)

## 2.5.1

- Fix REPL window horizontal scrollbar not working.
- Fix REPL window broken after making any change to its layout. #144

## 2.5.0

- Enhance REPL evaluations. #108
  - Isolate ns from REPL windows and file editors
  - Evaluate ns form from file automatically to avoid namespace-not-found errors. 

## 2.4.0

- Send to REPL eval results. #92
- Fix repl input when evaluated the same input of last eval.
- Fix history navigation via shortcut not working after 2.0.0.

## 2.3.0

- Update repl window ns after switching ns.
- Fix exception on settings page.
- Fix special form evaluations. #135
- Add support for JVM args on local REPL configuration. #124

## 2.2.0

 - Fix `eval defun at cursor` action error. #121
 - Create view error on test error. #128
 - Block backspace on repl input.

## 2.1.0

 - Add default name for RunConfigurations instead of save as Unnamed. #123
 - Add REPL syntax highlight. #18

## 2.0.0

 - Add icons of REPL commands to REPL window (clear and entry history navigation). #99
 - Drop support of older IntelliJ versions (2021/2022). Now requires minimum IntelliJ 2023.3 (Build 233)
 - Fix namespace-not-found error handling. Now shows a message to the user. #107
 - Add eval inlay hint support. #106
 - Add action to interrupt evals on the REPL session (`shift alt R` + `shift alt S`).  #104
 - Add color settings page for customization of some tokens.

## 1.6.2

- Fix remote config running wrongly as repl-file.

## 1.6.1

- Fix error when running tests after open multiple projects

## 1.6.0

- Fix shortcuts not being added after 1.5.1.
- Add Re-run last test action. #93

## 1.5.1

- Fix entries history navigation in REPL.
- Fix Local repl configuration setup when more than one project is opened.
- Fix default shortcuts being added for already customized shortcuts. #94

## 1.5.0

- Add support for env vars on local repl.
- Fix support for windows. #85

## 1.4.2

- Fix ignore namespace metadata in REPL 
- add dynamic background color to REPL
- add instructions to download babashka

## 1.4.1

- Fix REPL input parse of functions with `>` char. #79

## 1.4.0

- Fix freezing when running actions.
- Add action to clear REPL output. #78
- Add action to refresh all namespaces. #80
- Add action to refresh changed namespaces. #81

## 1.3.1

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
