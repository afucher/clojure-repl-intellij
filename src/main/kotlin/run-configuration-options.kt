package com.github.clojure_repl.intellij.configuration

import com.intellij.execution.configurations.RunConfigurationOptions

class ReplClientRunOptions : RunConfigurationOptions() {

    private var nreplPortOption = string("").provideDelegate(this, "nreplPort")

    var nreplPort: String
        get() = nreplPortOption.getValue(this) ?: ""
        set(value) = nreplPortOption.setValue(this, value)
}
