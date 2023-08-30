package com.github.clojure_repl.intellij

open class WithLoader {
    companion object {
        init {
            bind()
        }

        @JvmStatic
        fun bind() {
            Thread.currentThread().setContextClassLoader(WithLoader::class.java.getClassLoader())
        }
    }
}
