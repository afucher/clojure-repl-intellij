# Custom Code Actions for Clojure REPL IntelliJ

Custom Code Actions allow you to define and run your own code snippets directly in the REPL, with support for dynamic placeholders that are replaced with context-specific values. This feature is highly configurable and can be tailored per user, per project, or even provided by libraries.

## How It Works

- **Custom actions** are defined in EDN config files as a list of maps, each with a `:name` and a `:code` string.
- When you trigger a custom action, the code snippet is evaluated in the REPL, with placeholders replaced by values from the current editor context (such as the current var, namespace, or selection).
- Actions are registered automatically at startup from all available config sources.

## Available Placeholders

You can use the following placeholders in your code snippets. They will be replaced at runtime:

| Placeholder         | Description                                               |
|---------------------|-----------------------------------------------------------|
| `$current-var`      | Fully qualified name (ns/var) at the cursor position      |
| `$file-namespace`   | Namespace of the current file                             |
| `$selection`        | Currently selected text in the editor                     |

Example usage in code:
```clojure
{:name "Test current var"
 :code "(clojure.test/test-var #'$current-var)"}
```

## Where to Place Config Files

Custom actions can be defined in any of the following locations (checked in order):

1. **User-level config:**
   - `~/.config/clj-repl-intellij/config.edn`
2. **Project-level config:**
   - `<project-root>/.clj-repl-intellij/config.edn`
3. **Library-level config (for library authors):**
   - Inside a JAR at `clj-repl-intellij.exports/<project-name>/config.edn`

All configs found are merged, and all actions are registered.

## Config File Format

The config file must be a valid EDN map with an `:eval-code-actions` key, whose value is a vector of action maps. Each action map must have at least `:name` and `:code` keys.

Example (`.clj-repl-intellij/config.edn`):

```clojure
{:eval-code-actions
 [{:name "Hello World Action"
   :code (println "Hello world")}
  {:name "Test current var"
   :code (clojure.test/test-var #'$current-var)}
  {:name "Print selection"
   :code (println "Selected: $selection")}]}
```

## How to Use

1. **Define your actions** in one of the config files above.
2. **Restart the IDE** or use the "Reload custom actions" command to reload actions.
3. **Trigger your custom action** from the "Custom actions" menu or via the assigned action ID.

## Notes
- Placeholders are replaced as plain text, so ensure your code is valid after substitution.
- If a placeholder is not available in the current context (e.g., no selection), it will be replaced with an empty string.
- Library authors can ship default actions by including a `clj-repl-intellij.exports/config.edn` in their JAR.

---
For more advanced usage or troubleshooting, see the source code or open an issue on the project repository.
