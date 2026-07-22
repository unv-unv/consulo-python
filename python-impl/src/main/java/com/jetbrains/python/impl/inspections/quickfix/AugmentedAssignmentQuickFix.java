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

import com.jetbrains.python.impl.psi.impl.PyAugAssignmentStatementImpl;
import com.jetbrains.python.psi.*;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

import java.util.List;

/**
 * QuickFix to replace assignment that can be replaced with augmented assignment.
 * for instance, i = i + 1   --> i +=1
 *
 * @author catherine
 */
public class AugmentedAssignmentQuickFix implements LocalQuickFix {
    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixAugmentAssignment();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();

        if (element instanceof PyAssignmentStatement statement && element.isWritable()) {
            PyExpression target = statement.getLeftHandSideExpression();
            PyBinaryExpression expression = (PyBinaryExpression) statement.getAssignedValue();
            if (expression == null) {
                return;
            }
            PyExpression leftExpression = expression.getLeftExpression();
            PyExpression rightExpression = expression.getRightExpression();
            if (rightExpression instanceof PyParenthesizedExpression parenthesizedExpr) {
                rightExpression = parenthesizedExpr.getContainedExpression();
            }
            if (target != null && rightExpression != null) {
                String targetText = target.getText();
                String rightText = rightExpression.getText();
                if (rightText.equals(targetText)) {
                    PyExpression tmp = rightExpression;
                    rightExpression = leftExpression;
                    leftExpression = tmp;
                }
                List<PsiComment> comments = PsiTreeUtil.getChildrenOfTypeAsList(statement, PsiComment.class);

                if ((leftExpression instanceof PyReferenceExpression || leftExpression instanceof PySubscriptionExpression)) {
                    if (leftExpression.getText().equals(targetText)) {
                        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
                        StringBuilder stringBuilder = new StringBuilder();
                        PsiElement psiOperator = expression.getPsiOperator();
                        if (psiOperator == null) {
                            return;
                        }
                        stringBuilder.append(targetText).append(" ")
                            .append(psiOperator.getText()).append("= ").append(rightExpression.getText());
                        PyAugAssignmentStatementImpl augAssignment = elementGenerator.createFromText(
                            LanguageLevel.forElement(element),
                            PyAugAssignmentStatementImpl.class,
                            stringBuilder.toString()
                        );
                        for (PsiComment comment : comments)
                            augAssignment.add(comment);
                        statement.replace(augAssignment);
                    }
                }
            }
        }
    }
}
