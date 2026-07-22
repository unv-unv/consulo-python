/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
 */

package com.jetbrains.python.impl;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.BracePair;
import consulo.language.Language;
import consulo.language.PairedBraceMatcher;
import consulo.language.ast.IElementType;

import org.jspecify.annotations.Nullable;

/**
 * @author yole
 */
@ExtensionImpl
public class PyBraceMatcher implements PairedBraceMatcher {
  private final BracePair[] PAIRS;

  public PyBraceMatcher() {
    PAIRS = new BracePair[]{new BracePair(PyTokenTypes.LPAR, PyTokenTypes.RPAR, false),
      new BracePair(PyTokenTypes.LBRACKET, PyTokenTypes.RBRACKET, false), new BracePair(PyTokenTypes.LBRACE, PyTokenTypes.RBRACE, false)};
  }

  @Override
  public BracePair[] getPairs() {
    return PAIRS;
  }

  @Override
  public boolean isPairedBracesAllowedBeforeType(IElementType lbraceType, @Nullable IElementType contextType) {
    return
      PyTokenTypes.WHITESPACE_OR_LINEBREAK.contains(contextType) ||
        contextType == PyTokenTypes.END_OF_LINE_COMMENT ||
        contextType == PyTokenTypes.COLON ||
        contextType == PyTokenTypes.COMMA ||
        contextType == PyTokenTypes.RPAR ||
        contextType == PyTokenTypes.RBRACKET ||
        contextType == PyTokenTypes.RBRACE ||
        contextType == PyTokenTypes.LBRACE ||
        contextType == null;
  }

  @Override
  public Language getLanguage() {
    return PythonLanguage.INSTANCE;
  }
}
