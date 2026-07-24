/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import consulo.language.ast.ASTNode;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

public class PyAddExceptionSuperClassQuickFix implements LocalQuickFix {
    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixNameAddExceptionBase();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        if (descriptor.getPsiElement() instanceof PyCallExpression call
            && call.getCallee() instanceof PyReferenceExpression callee
            && callee.getReference().resolve() instanceof PyClass pyClass) {
            PyElementGenerator generator = PyElementGenerator.getInstance(project);
            PyArgumentList list = pyClass.getSuperClassExpressionList();
            if (list != null) {
                PyExpression exception = generator.createExpressionFromText(LanguageLevel.forElement(call), "Exception");
                list.addArgument(exception);
            }
            else {
                PyArgumentList expressionList =
                    generator.createFromText(LanguageLevel.forElement(call), PyClass.class, "class A(Exception): pass")
                        .getSuperClassExpressionList();
                assert expressionList != null;
                ASTNode nameNode = pyClass.getNameNode();
                assert nameNode != null;
                pyClass.addAfter(expressionList, nameNode.getPsi());
            }
        }
    }
}
