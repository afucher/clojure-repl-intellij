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
    private var projectTypeOption = string("").provideDelegate(this, "projectType")
    private var aliasesOption = list<String>().provideDelegate(this, "aliases")
    private var envVarsOption = list<String>().provideDelegate(this, "envVars")

    var project: String
        get() = projectOption.getValue(this) ?: ""
        set(value) = projectOption.setValue(this, value)

    var projectType: String?
        get() = projectTypeOption.getValue(this)
        set(value) = projectTypeOption.setValue(this, value)

    var aliases: MutableList<String>
        get() = aliasesOption.getValue(this)
        set(value) = aliasesOption.setValue(this, value)

    var envVars: MutableList<String>
        get() = envVarsOption.getValue(this)
        set(value) = envVarsOption.setValue(this, value)

}
