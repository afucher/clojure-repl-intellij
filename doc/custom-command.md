# Custom Commands

> WIP - all names and configs can change  

This doc explain how the REPL Custom Commands work .

## Defining commands
You can write custom actions to evaluate code with some context variables directly to your REPL.

The configuration can be defined in some levels/places:
 - `{USER_HOME}/.config/clj-repl-intellij/config.edn`
 - `{PROJECT_ROOT}/.clj-repl-intellij/config.edn`


`(comming soon)`
For libraries you can export some default commands defining a `clj-repl-intellij.exports/config.edn`

### Example:

```
{:eval-code-actions
 [{:name "Test vars"
   :code (clojure.test/run-test-var (:current-var ctx))}
```
