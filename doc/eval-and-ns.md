# Eval and ns

This document describes how this plugin handle evaluation and namespaces for different buffers (files and REPL window).

## REPL window
The REPL window uses by default the `user` namespace.  
If you want to change the namespace from it you can use the `Switch REPL namespace (alt + shift + N)` action or use the `in-ns` function.  
The window shows the namespace in the last line, where you can type to eval your code:
```
user> *ns*
=> #namespace[user]
user> 
```

## Files
Each file uses by default its own namespace for evaluation. The only exception is the `ns` form, that uses the `user` namespace to avoid `namespace-not-found` error.  
When evaluating a code for the first time in a namespace, the `ns` form is evaluated first to also avoid `namespace-not-found` error.  
If you want to change the namespace from it you can use the `in-ns` function, after that all evaluations from this file will start to use the new namespace.
