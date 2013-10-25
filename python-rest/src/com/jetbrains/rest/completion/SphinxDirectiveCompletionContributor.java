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

package com.jetbrains.rest.completion;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.or;

import org.jetbrains.annotations.NotNull;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import com.jetbrains.rest.RestPythonUtil;
import com.jetbrains.rest.RestTokenTypes;
import com.jetbrains.rest.RestUtil;
import com.jetbrains.rest.psi.RestReferenceTarget;

/**
 * User : ktisha
 */
public class SphinxDirectiveCompletionContributor extends CompletionContributor {
  public static final PsiElementPattern.Capture<PsiElement> DIRECTIVE_PATTERN = psiElement().afterSibling(or(psiElement().
    withElementType(RestTokenTypes.WHITESPACE).afterSibling(psiElement(RestReferenceTarget.class)),
       psiElement().withElementType(RestTokenTypes.EXPLISIT_MARKUP_START)));

  public SphinxDirectiveCompletionContributor() {
    extend(CompletionType.BASIC, DIRECTIVE_PATTERN,
       new CompletionProvider<CompletionParameters>() {
         @Override
         protected void addCompletions(@NotNull CompletionParameters parameters,
                                       ProcessingContext context,
                                       @NotNull CompletionResultSet result) {
           Sdk sdk = ProjectRootManager.getInstance(parameters.getPosition().getProject()).getProjectSdk();
           if (sdk != null) {
             String sphinx = RestPythonUtil.findQuickStart(sdk);
             if (sphinx != null) {
               for (String tag : RestUtil.SPHINX_DIRECTIVES) {
                 result.addElement(LookupElementBuilder.create(tag));
               }
             }
           }
         }
       }
       );
  }
}
