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

import com.jetbrains.python.impl.buildout.config.lexer.BuildoutCfgFlexLexer;
import com.jetbrains.python.impl.buildout.config.psi.BuildoutCfgASTFactory;
import com.jetbrains.python.impl.buildout.config.psi.impl.BuildoutCfgFile;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IFileElementType;
import consulo.language.ast.TokenSet;
import consulo.language.file.FileViewProvider;
import consulo.language.lexer.Lexer;
import consulo.language.parser.ParserDefinition;
import consulo.language.parser.PsiParser;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.version.LanguageVersion;

import org.jspecify.annotations.Nullable;

/**
 * @author traff
 */
@ExtensionImpl
public class BuildoutCfgParserDefinition implements ParserDefinition, BuildoutCfgElementTypes, BuildoutCfgTokenTypes {
  private final BuildoutCfgASTFactory astFactory = new BuildoutCfgASTFactory();

  @Override
  public Language getLanguage() {
    return BuildoutCfgLanguage.INSTANCE;
  }

  @Override
  public Lexer createLexer(LanguageVersion languageVersion) {
    return new BuildoutCfgFlexLexer();
  }

  @Nullable
  @Override
  public PsiParser createParser(LanguageVersion languageVersion) {
    return new BuildoutCfgParser();
  }

  @Override
  public IFileElementType getFileNodeType() {
    return FILE;
  }

  @Override
  public TokenSet getWhitespaceTokens(LanguageVersion languageVersion) {
    return TokenSet.create(WHITESPACE);
  }

  @Override
  public TokenSet getCommentTokens(LanguageVersion languageVersion) {
    return TokenSet.create(COMMENT);
  }

  @Override
  public TokenSet getStringLiteralElements(LanguageVersion languageVersion) {
    return TokenSet.create(TEXT);
  }

  @Override
  public PsiElement createElement(ASTNode node) {
    return astFactory.create(node);
  }

  @Override
  public PsiFile createFile(FileViewProvider viewProvider) {
    return new BuildoutCfgFile(viewProvider);
  }
}
