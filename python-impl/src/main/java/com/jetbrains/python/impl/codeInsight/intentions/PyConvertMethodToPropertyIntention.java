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

package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.impl.psi.PyUtil;
import consulo.language.editor.intention.BaseIntentionAction;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.usage.UsageInfo;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.impl.refactoring.PyRefactoringUtil;
import consulo.language.util.IncorrectOperationException;

import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

/**
 * User: ktisha
 */
public class PyConvertMethodToPropertyIntention extends BaseIntentionAction {
  @Nonnull
  public String getFamilyName() {
    return PyBundle.message("INTN.convert.method.to.property");
  }

  @Nonnull
  public String getText() {
    return PyBundle.message("INTN.convert.method.to.property");
  }

  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PyFile)) {
      return false;
    }
    if (!LanguageLevel.forElement(file).isAtLeast(LanguageLevel.PYTHON26)) return false;
    final PsiElement element = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
    final PyFunction function = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    if (function == null) return false;
    final PyClass containingClass = function.getContainingClass();
    if (containingClass == null) return false;
    if (function.getParameterList().getParameters().length > 1) return false;

    final PyDecoratorList decoratorList = function.getDecoratorList();
    if (decoratorList != null) return false;

    final boolean[] available = {false};
    function.accept(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyReturnStatement(PyReturnStatement node) {
        if (node.getExpression() != null)
          available[0] = true;
      }

      @Override
      public void visitPyYieldExpression(PyYieldExpression node) {
        available[0] = true;
      }
    });

    return available[0];
  }

  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException
  {
    final PsiElement element = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
    PyFunction problemFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    if (problemFunction == null) return;
    final PyClass containingClass = problemFunction.getContainingClass();
    if (containingClass == null) return;
    final List<UsageInfo> usages = PyRefactoringUtil.findUsages(problemFunction, false);

    final PyDecoratorList problemDecoratorList = problemFunction.getDecoratorList();
    List<String> decoTexts = new ArrayList<String>();
    decoTexts.add("@property");
    if (problemDecoratorList != null) {
      final PyDecorator[] decorators = problemDecoratorList.getDecorators();
      for (PyDecorator deco : decorators) {
        decoTexts.add(deco.getText());
      }
    }

    PyElementGenerator generator = PyElementGenerator.getInstance(project);
    final PyDecoratorList decoratorList = generator.createDecoratorList(decoTexts.toArray(new String[decoTexts.size()]));

    if (problemDecoratorList != null) {
      problemDecoratorList.replace(decoratorList);
    }
    else {
      problemFunction.addBefore(decoratorList, problemFunction.getFirstChild());
    }

    for (UsageInfo usage : usages) {
      final PsiElement usageElement = usage.getElement();
      if (usageElement instanceof PyReferenceExpression) {
        final PsiElement parent = usageElement.getParent();
        if (parent instanceof PyCallExpression) {
          final PyArgumentList argumentList = ((PyCallExpression)parent).getArgumentList();
          if (argumentList != null) argumentList.delete();
        }
      }
    }
  }
}
