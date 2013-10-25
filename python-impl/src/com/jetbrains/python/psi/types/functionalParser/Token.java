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

package com.jetbrains.python.psi.types.functionalParser;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

/**
* @author vlan
*/
public class Token<T> {
  @NotNull private final CharSequence myText;
  @NotNull private final TextRange myRange;
  @NotNull private final T myType;

  public Token(@NotNull T type, @NotNull CharSequence text, @NotNull TextRange range) {
    myText = text;
    myRange = range;
    myType = type;
  }

  @NotNull
  public T getType() {
    return myType;
  }

  @NotNull
  public CharSequence getText() {
    return myText;
  }

  @NotNull
  public TextRange getRange() {
    return myRange;
  }

  @Override
  public String toString() {
    return String.format("Token(<%s>, \"%s\", %s)", myType, myText, myRange);
  }
}
