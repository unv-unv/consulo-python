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
package com.jetbrains.python.impl.formatter;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.editor.PythonEnterHandler;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.StaticSymbolWhiteSpaceDefinitionStrategy;
import consulo.language.psi.PsiElement;
import consulo.util.collection.primitive.ints.IntSet;
import consulo.util.collection.primitive.ints.IntSets;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import java.util.PrimitiveIterator;

/**
 * @author yole
 */
@ExtensionImpl
public class PyWhiteSpaceFormattingStrategy extends StaticSymbolWhiteSpaceDefinitionStrategy {

  public PyWhiteSpaceFormattingStrategy() {
    super('\\');
  }

  @Override
  public CharSequence adjustWhiteSpaceIfNecessary(@Nonnull CharSequence whiteSpaceText, @Nonnull PsiElement startElement, int startOffset,
                                                  int endOffset, CodeStyleSettings codeStyleSettings) {
    CharSequence whiteSpace = super.adjustWhiteSpaceIfNecessary(whiteSpaceText, startElement, startOffset, endOffset, codeStyleSettings);
    if (whiteSpace.length() > 0 && whiteSpace.charAt(0) == '\n' && !StringUtil.contains(whiteSpace, 0, whiteSpace.length(), '\\') &&
      PythonEnterHandler.needInsertBackslash(startElement.getContainingFile(), startOffset, false)) {
      return addBackslashPrefix(whiteSpace, codeStyleSettings);
    }
    return whiteSpace;
  }

  private static String addBackslashPrefix(CharSequence whiteSpace, CodeStyleSettings settings) {
    PyCodeStyleSettings pySettings = settings.getCustomSettings(PyCodeStyleSettings.class);
    return (pySettings.SPACE_BEFORE_BACKSLASH ? " \\" : "\\") + whiteSpace.toString();
  }

  /**
   * Python uses backslashes at the end of the line as indication that next line is an extension of the current one.
   * <p/>
   * Hence, we need to preserve them during white space manipulation.
   *
   * @param whiteSpaceText    white space text to use by default for replacing sub-sequence of the given text
   * @param text              target text which region is to be replaced by the given white space symbols
   * @param startOffset       start offset to use with the given text (inclusive)
   * @param endOffset         end offset to use with the given text (exclusive)
   * @param codeStyleSettings the code style settings
   * @param nodeAfter
   * @return symbols to use for replacing <code>[startOffset; endOffset)</code> sub-sequence of the given text
   */
  @Nonnull
  @Override
  public CharSequence adjustWhiteSpaceIfNecessary(@Nonnull CharSequence whiteSpaceText, @Nonnull CharSequence text, int startOffset,
                                                  int endOffset, CodeStyleSettings codeStyleSettings, ASTNode nodeAfter) {
    // The general idea is that '\' symbol before line feed should be preserved.
    IntSet initialBackSlashes = countBackSlashes(text, startOffset, endOffset);
    if (initialBackSlashes.isEmpty()) {
      if (nodeAfter != null && whiteSpaceText.length() > 0 && whiteSpaceText.charAt(0) == '\n' &&
        PythonEnterHandler.needInsertBackslash(nodeAfter, false)) {
        return addBackslashPrefix(whiteSpaceText, codeStyleSettings);
      }
      return whiteSpaceText;
    }

    final IntSet newBackSlashes = countBackSlashes(whiteSpaceText, 0, whiteSpaceText.length());
    PrimitiveIterator.OfInt iterator = initialBackSlashes.iterator();
    while (iterator.hasNext()) {
      int key = iterator.nextInt();
      if (!newBackSlashes.contains(key)) {
        return whiteSpaceText;
      }
    }

    PyCodeStyleSettings settings = codeStyleSettings.getCustomSettings(PyCodeStyleSettings.class);
    StringBuilder result = new StringBuilder();
    int line = 0;
    for (int i = 0; i < whiteSpaceText.length(); i++) {
      char c = whiteSpaceText.charAt(i);
      if (c != '\n') {
        result.append(c);
        continue;
      }
      if (!newBackSlashes.contains(line++)) {
        if ((i == 0 || (i > 0 && whiteSpaceText.charAt(i - 1) != ' ')) && settings.SPACE_BEFORE_BACKSLASH) {
          result.append(' ');
        }
        result.append('\\');
      }
      result.append(c);
    }
    return result;
  }

  /**
   * Counts number of back slashes per-line.
   *
   * @param text  target text
   * @param start start offset to use with the given text (inclusive)
   * @param end   end offset to use with the given text (exclusive)
   * @return map that holds '{@code line number -> number of back slashes}' mapping for the target text
   */
  static IntSet countBackSlashes(CharSequence text, int start, int end) {
    IntSet result = IntSets.newHashSet();
    int line = 0;
    if (end > text.length()) {
      end = text.length();
    }
    for (int i = start; i < end; i++) {
      char c = text.charAt(i);
      switch (c) {
        case '\n':
          line++;
          break;
        case '\\':
          result.add(line);
          break;
      }
    }
    return result;
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return PythonLanguage.INSTANCE;
  }
}
