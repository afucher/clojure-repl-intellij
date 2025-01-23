package com.github.clojure_repl.intellij

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object Icons {
  @JvmField val CLOJURE = IconLoader.getIcon("/icons/clojure.svg", Icons::class.java)
  @JvmField val CLOJURE_REPL = IconLoader.getIcon("/icons/clojure_repl.svg", Icons::class.java)
  @JvmField val DELETE = IconLoader.getIcon("/icons/delete.svg", Icons::class.java)
}
