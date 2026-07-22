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

import com.jetbrains.python.psi.*;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

import java.util.ArrayList;
import java.util.List;

/**
 * @author catherine
 */
public class ListCreationQuickFix implements LocalQuickFix {
    private final PyAssignmentStatement myStatement;
    private final List<PyExpressionStatement> myStatements = new ArrayList<>();

    public ListCreationQuickFix(PyAssignmentStatement statement) {
        myStatement = statement;
    }

    public void addStatement(PyExpressionStatement statement) {
        myStatements.add(statement);
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixListCreation();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
        StringBuilder stringBuilder = new StringBuilder();
        PyExpression assignedValue = myStatement.getAssignedValue();
        if (assignedValue == null) {
            return;
        }

        for (PyExpression expression : ((PyListLiteralExpression) assignedValue).getElements()) {
            stringBuilder.append(expression.getText()).append(", ");
        }
        for (PyExpressionStatement statement : myStatements) {
            for (PyExpression expr : ((PyCallExpression) statement.getExpression()).getArguments())
                stringBuilder.append(expr.getText()).append(", ");
            statement.delete();
        }
        assignedValue.replace(
            elementGenerator.createExpressionFromText("[" + stringBuilder.substring(0, stringBuilder.length() - 2) + "]")
        );
    }
}
