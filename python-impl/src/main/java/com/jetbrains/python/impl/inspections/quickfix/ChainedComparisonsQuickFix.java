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

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

/**
 * QuickFix to replace chained comparisons with more simple version
 * For instance, a < b and b < c  --> a < b < c
 *
 * @author catherine
 */
public class ChainedComparisonsQuickFix implements LocalQuickFix {
    boolean myIsLeftLeft;
    boolean myIsRightLeft;
    boolean getInnerRight;

    public ChainedComparisonsQuickFix(boolean isLeft, boolean isRight, boolean getInner) {
        myIsLeftLeft = isLeft;
        myIsRightLeft = isRight;
        getInnerRight = getInner;
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixChainedComparison();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        if (descriptor.getPsiElement() instanceof PyBinaryExpression binaryExpr
            && binaryExpr.isWritable()
            && binaryExpr.getRightExpression() instanceof PyBinaryExpression rightExpr
            && binaryExpr.getLeftExpression() instanceof PyBinaryExpression leftExpr
            && binaryExpr.getOperator() == PyTokenTypes.AND_KEYWORD) {
            PyBinaryExpression leftRightmostExpr = leftExpr;
            if (getInnerRight
                && leftExpr.getRightExpression() instanceof PyBinaryExpression leftRightExpression
                && PyTokenTypes.AND_KEYWORD == leftExpr.getOperator()) {
                leftRightmostExpr = leftRightExpression;
            }
            checkOperator(leftRightmostExpr, rightExpr, project);
        }
    }

    @RequiredWriteAction
    private void checkOperator(PyBinaryExpression leftExpression, PyBinaryExpression rightExpression, Project project) {
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
        if (myIsLeftLeft) {
            PyExpression newLeftExpression = invertExpression(leftExpression, elementGenerator);

            if (myIsRightLeft) {
                PsiElement operator = getLeftestOperator(rightExpression);
                PyBinaryExpression binaryExpression = elementGenerator.createBinaryExpression(
                    operator.getText(),
                    newLeftExpression,
                    getLargeRightExpression(rightExpression, project)
                );
                leftExpression.replace(binaryExpression);
                rightExpression.delete();
            }
            else {
                String operator = invertOperator(rightExpression.getPsiOperator());
                PyBinaryExpression binaryExpression = elementGenerator.createBinaryExpression(
                    operator,
                    newLeftExpression,
                    rightExpression.getLeftExpression()
                );
                leftExpression.replace(binaryExpression);
                rightExpression.delete();
            }
        }
        else if (myIsRightLeft) {
            PsiElement operator = getLeftestOperator(rightExpression);
            PyBinaryExpression binaryExpression = elementGenerator.createBinaryExpression(
                operator.getText(),
                leftExpression,
                getLargeRightExpression(rightExpression, project)
            );
            leftExpression.replace(binaryExpression);
            rightExpression.delete();
        }
        else {
            PyExpression expression = rightExpression.getLeftExpression();
            if (expression instanceof PyBinaryExpression binExpr) {
                expression = invertExpression(binExpr, elementGenerator);
            }
            String operator = invertOperator(rightExpression.getPsiOperator());
            PyBinaryExpression binaryExpression = elementGenerator.createBinaryExpression(operator, leftExpression, expression);
            leftExpression.replace(binaryExpression);
            rightExpression.delete();
        }
    }

    private PsiElement getLeftestOperator(PyBinaryExpression expression) {
        PsiElement op = expression.getPsiOperator();
        while (expression.getLeftExpression() instanceof PyBinaryExpression binExpr) {
            expression = binExpr;
            op = expression.getPsiOperator();
        }
        assert op != null;
        return op;
    }

    @RequiredReadAction
    private PyExpression invertExpression(PyBinaryExpression leftExpression, PyElementGenerator elementGenerator) {
        PsiElement operator = leftExpression.getPsiOperator();
        PyExpression right = leftExpression.getRightExpression();
        PyExpression left = leftExpression.getLeftExpression();
        if (left instanceof PyBinaryExpression leftBinary) {
            left = invertExpression(leftBinary, elementGenerator);
        }
        String newOperator = invertOperator(operator);
        return elementGenerator.createBinaryExpression(newOperator, right, left);
    }

    @RequiredReadAction
    private String invertOperator(PsiElement op) {
        if (op.getText().equals(">")) {
            return "<";
        }
        if (op.getText().equals("<")) {
            return ">";
        }
        if (op.getText().equals(">=")) {
            return "<=";
        }
        if (op.getText().equals("<=")) {
            return ">=";
        }
        return op.getText();
    }

    @Nullable
    @RequiredReadAction
    static private PyExpression getLargeRightExpression(PyBinaryExpression expression, Project project) {
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
        PyExpression left = expression.getLeftExpression();
        PyExpression right = expression.getRightExpression();
        PsiElement operator = expression.getPsiOperator();
        while (left instanceof PyBinaryExpression leftBinary) {
            assert operator != null;
            right = elementGenerator.createBinaryExpression(operator.getText(), leftBinary.getRightExpression(), right);
            operator = leftBinary.getPsiOperator();
            left = leftBinary.getLeftExpression();
        }
        return right;
    }
}
