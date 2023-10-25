package com.github.clojure_repl.intellij.configuration

import com.intellij.execution.configurations.RunConfigurationOptions

class ReplClientRunOptions : RunConfigurationOptions() {

    private var nreplHostOption = string("").provideDelegate(this, "nreplHost")
    private var nreplPortOption = string("").provideDelegate(this, "nreplPort")
    private var projectOption = string("").provideDelegate(this, "project")

    var nreplHost: String
        get() = nreplHostOption.getValue(this) ?: ""
        set(value) = nreplHostOption.setValue(this, value)

    var nreplPort: String
        get() = nreplPortOption.getValue(this) ?: ""
        set(value) = nreplPortOption.setValue(this, value)

    var project: String
        get() = projectOption.getValue(this) ?: ""
        set(value) = projectOption.setValue(this, value)
}
