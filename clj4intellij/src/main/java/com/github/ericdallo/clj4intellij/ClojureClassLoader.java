package com.github.ericdallo.clj4intellij;

public class ClojureClassLoader {
    static {
        bind();
    }
    public static void bind() {
        Thread.currentThread().setContextClassLoader(ClojureClassLoader.class.getClassLoader());
    }
}
