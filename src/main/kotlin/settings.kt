package com.github.clojure_repl.intellij

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import kotlin.jvm.java

@State(
    name = "ClojureReplSettings",
    storages = [Storage("ClojureReplSettings.xml")]
)
class ClojureReplSettings : PersistentStateComponent<ClojureReplSettings.State> {

    class State {
        var loadFileOnSave: Boolean = false
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    var loadFileOnSave: Boolean
        get() = state.loadFileOnSave
        set(value) {
            state.loadFileOnSave = value
        }

    companion object {
        @JvmStatic
        fun getInstance(): ClojureReplSettings {
            return ApplicationManager.getApplication().getService(ClojureReplSettings::class.java)
        }
    }
}
