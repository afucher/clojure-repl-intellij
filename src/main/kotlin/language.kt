/*
 * Copyright 2016-present Greg Shrago
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.github.clojure_repl.intellij

import com.github.clojure_repl.intellij.Icons
import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.psi.tree.IElementType
import com.intellij.lang.BracePair
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.TokenType
import com.intellij.psi.tree.TokenSet

object ClojureLanguage : Language("clojure")

object ClojureFileType : LanguageFileType(ClojureLanguage) {
  override fun getIcon() = Icons.CLOJURE
  override fun getName() = "clojure"
  override fun getDefaultExtension() = "clj"
  override fun getDescription() = "Clojure, ClojureScript and ClojureDart"
}
