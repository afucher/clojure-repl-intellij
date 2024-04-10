package com.github.clojure_repl.intellij.configuration

import com.intellij.execution.configurations.RunConfigurationOptions

class ReplRemoteRunOptions : RunConfigurationOptions() {

    private var nreplHostOption = string("localhost").provideDelegate(this, "nreplHost")
    private var nreplPortOption = string("").provideDelegate(this, "nreplPort")
    private var projectOption = string("").provideDelegate(this, "project")
    private var modeOption = string("manual-config").provideDelegate(this, "mode")

    var nreplHost: String
        get() = nreplHostOption.getValue(this) ?: "localhost"
        set(value) = nreplHostOption.setValue(this, value)

    var nreplPort: String
        get() = nreplPortOption.getValue(this) ?: ""
        set(value) = nreplPortOption.setValue(this, value)

    var project: String
        get() = projectOption.getValue(this) ?: ""
        set(value) = projectOption.setValue(this, value)

    var mode: String
        get() = modeOption.getValue(this) ?: "manual-config"
        set(value) = modeOption.setValue(this, value)
}

class ReplLocalRunOptions : RunConfigurationOptions() {

    private var projectOption = string("").provideDelegate(this, "project")

    var project: String
        get() = projectOption.getValue(this) ?: ""
        set(value) = projectOption.setValue(this, value)

}
