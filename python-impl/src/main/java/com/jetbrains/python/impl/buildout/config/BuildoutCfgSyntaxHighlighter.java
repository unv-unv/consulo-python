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
package com.jetbrains.python.impl.buildout.config;

import com.google.common.collect.Maps;
import consulo.language.lexer.Lexer;
import consulo.codeEditor.DefaultLanguageHighlighterColors;
import consulo.colorScheme.TextAttributesKey;
import consulo.language.editor.highlight.SyntaxHighlighterBase;
import consulo.language.ast.IElementType;
import com.jetbrains.python.impl.buildout.config.lexer.BuildoutCfgFlexLexer;

import java.util.Map;

/**
 * @author traff
 */
public class BuildoutCfgSyntaxHighlighter extends SyntaxHighlighterBase implements BuildoutCfgTokenTypes {
  static final String COMMENT_ID = "BUILDOUT_COMMENT";
  static final String TEXT_ID = "BUILDOUT_TEXT";
  static final String BRACKETS_ID = "BUILDOUT_BRACKETS";

  public static final TextAttributesKey BUILDOUT_SECTION_NAME = TextAttributesKey.createTextAttributesKey(
    "BUILDOUT.SECTION_NAME",
    DefaultLanguageHighlighterColors.NUMBER
  );

  public static final TextAttributesKey BUILDOUT_KEY = TextAttributesKey.createTextAttributesKey(
    "BUILDOUT.KEY",
    DefaultLanguageHighlighterColors.KEYWORD
  );

  public static final TextAttributesKey BUILDOUT_VALUE = TextAttributesKey.createTextAttributesKey(
    "BUILDOUT.VALUE",
    DefaultLanguageHighlighterColors.STRING
  );

  public static final TextAttributesKey BUILDOUT_COMMENT = TextAttributesKey.createTextAttributesKey(
    "BUILDOUT.LINE_COMMENT",
    DefaultLanguageHighlighterColors.LINE_COMMENT
  );

  public static final TextAttributesKey BUILDOUT_KEY_VALUE_SEPARATOR = TextAttributesKey.createTextAttributesKey(
    "BUILDOUT.KEY_VALUE_SEPARATOR",
    DefaultLanguageHighlighterColors.OPERATION_SIGN
  );
  public static final TextAttributesKey BUILDOUT_VALID_STRING_ESCAPE = TextAttributesKey.createTextAttributesKey(
    "BUILDOUT.VALID_STRING_ESCAPE",
    DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE
  );
  public static final TextAttributesKey BUILDOUT_INVALID_STRING_ESCAPE = TextAttributesKey.createTextAttributesKey(
    "BUILDOUT.INVALID_STRING_ESCAPE",
    DefaultLanguageHighlighterColors.INVALID_STRING_ESCAPE
  );


  private static final Map<IElementType, TextAttributesKey> ATTRIBUTES = Maps.newHashMap();

  static {
    ATTRIBUTES.put(COMMENT, BUILDOUT_COMMENT);
    ATTRIBUTES.put(SECTION_NAME, BUILDOUT_SECTION_NAME);
    ATTRIBUTES.put(KEY_VALUE_SEPARATOR, BUILDOUT_KEY_VALUE_SEPARATOR);
    ATTRIBUTES.put(KEY_CHARACTERS, BUILDOUT_KEY);
    ATTRIBUTES.put(VALUE_CHARACTERS, BUILDOUT_VALUE);
  }

  @Override
  public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
    return SyntaxHighlighterBase.pack(ATTRIBUTES.get(tokenType));
  }

  @Override
  public Lexer getHighlightingLexer() {
    return new BuildoutCfgFlexLexer();
  }
}
