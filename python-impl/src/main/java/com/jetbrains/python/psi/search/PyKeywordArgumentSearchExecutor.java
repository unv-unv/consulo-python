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

package com.jetbrains.python.psi.search;

import javax.annotation.Nonnull;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Processor;
import com.jetbrains.python.psi.*;

/**
 * @author yole
 */
public class PyKeywordArgumentSearchExecutor extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {
  @Override
  public void processQuery(@Nonnull ReferencesSearch.SearchParameters queryParameters, @Nonnull final Processor<PsiReference> consumer) {
    final PsiElement element = queryParameters.getElementToSearch();
    if (!(element instanceof PyNamedParameter)) {
      return;
    }
    PyFunction owner = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    if (owner == null) {
      return;
    }
    ReferencesSearch.search(owner, queryParameters.getScope()).forEach(new Processor<PsiReference>() {
      @Override
      public boolean process(PsiReference reference) {
        final PsiElement refElement = reference.getElement();
        final PyCallExpression call = PsiTreeUtil.getParentOfType(refElement, PyCallExpression.class);
        if (call != null && PsiTreeUtil.isAncestor(call.getCallee(), refElement, false)) {
          final PyArgumentList argumentList = call.getArgumentList();
          if (argumentList != null) {
            final PyKeywordArgument keywordArgument = argumentList.getKeywordArgument(((PyNamedParameter)element).getName());
            if (keywordArgument != null) {
              return consumer.process(keywordArgument.getReference());
            }
          }
        }
        return true;
      }
    });
  }
}
