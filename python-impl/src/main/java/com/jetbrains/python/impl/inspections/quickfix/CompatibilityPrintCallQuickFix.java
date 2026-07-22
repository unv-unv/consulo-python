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

import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.StringUtil;

/**
 * QuickFix to replace statement that has no effect with function call
 *
 * @author catherine
 */
public class CompatibilityPrintCallQuickFix implements LocalQuickFix {
    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixStatementEffect();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement expression = descriptor.getPsiElement();
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
        replacePrint(expression, elementGenerator);
    }

    @RequiredWriteAction
    private static void replacePrint(PsiElement expression, PyElementGenerator elementGenerator) {
        StringBuilder stringBuilder = new StringBuilder("print(");

        PyExpression[] target = PsiTreeUtil.getChildrenOfType(expression, PyExpression.class);
        if (target != null) {
            stringBuilder.append(StringUtil.join(target, PsiElement::getText, ", "));
        }

        stringBuilder.append(")");
        expression.replace(elementGenerator.createFromText(
            LanguageLevel.forElement(expression),
            PyExpression.class,
            stringBuilder.toString()
        ));
    }
}
