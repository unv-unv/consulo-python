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

package com.jetbrains.python.impl.inspections.quickfix;

import jakarta.annotation.Nonnull;

import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.editor.template.TemplateBuilderFactory;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NonNls;

/**
 * User: catherine
 *
 * Quickfix to introduce variable if statement seems to have no effect
 */
public class StatementEffectIntroduceVariableQuickFix implements LocalQuickFix {
  @Nonnull
  public String getName() {
    return PyBundle.message("QFIX.introduce.variable");
  }

  @NonNls
  @Nonnull
  public String getFamilyName() {
    return getName();
  }

  public void applyFix(@Nonnull final Project project, @Nonnull final ProblemDescriptor descriptor) {
    PsiElement expression = descriptor.getPsiElement();
    if (expression != null && expression.isValid()) {
      final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
      final PyAssignmentStatement assignment = elementGenerator.createFromText(LanguageLevel.forElement(expression), PyAssignmentStatement.class,
                                                         "var = " + expression.getText());

      expression = expression.replace(assignment);
      if (expression == null) return;
      expression = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(expression);
      final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(expression);
      final PyExpression leftHandSideExpression = ((PyAssignmentStatement)expression).getLeftHandSideExpression();
      assert leftHandSideExpression != null;
      builder.replaceElement(leftHandSideExpression, "var");
      builder.run();
    }
  }
}
