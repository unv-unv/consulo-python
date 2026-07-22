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

import com.jetbrains.python.impl.codeInsight.intentions.PyGenerateDocstringIntention;
import com.jetbrains.python.impl.documentation.docstrings.PyDocstringGenerator;
import com.jetbrains.python.psi.*;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

/**
 * @author catherine
 */
public class DocstringQuickFix implements LocalQuickFix {
    private final SmartPsiElementPointer<PyNamedParameter> myMissingParam;
    private final String myUnexpectedParamName;

    public DocstringQuickFix(@Nullable PyNamedParameter missing, @Nullable String unexpectedParamName) {
        if (missing != null) {
            myMissingParam = SmartPointerManager.getInstance(missing.getProject()).createSmartPsiElementPointer(missing);
        }
        else {
            myMissingParam = null;
        }
        myUnexpectedParamName = unexpectedParamName;
    }

    @Override
    public LocalizeValue getName() {
        if (myMissingParam != null) {
            PyNamedParameter param = myMissingParam.getElement();
            if (param == null) {
                throw new IncorrectOperationException("Parameter was invalidates before quickfix is called");
            }
            return PyLocalize.qfixDocstringAdd$0(param.getName());
        }
        else if (myUnexpectedParamName != null) {
            return PyLocalize.qfixDocstringRemove$0(myUnexpectedParamName);
        }
        else {
            return PyLocalize.qfixDocstringInsertStub();
        }
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PyDocStringOwner.class);
        if (docStringOwner == null) {
            return;
        }
        PyStringLiteralExpression docStringExpression = docStringOwner.getDocStringExpression();
        if (docStringExpression == null && myMissingParam == null && myUnexpectedParamName == null) {
            addEmptyDocstring(docStringOwner);
            return;
        }
        if (docStringExpression != null) {
            PyDocstringGenerator generator = PyDocstringGenerator.forDocStringOwner(docStringOwner);
            if (myMissingParam != null) {
                PyNamedParameter param = myMissingParam.getElement();
                if (param != null) {
                    generator.withParam(param);
                }
            }
            else if (myUnexpectedParamName != null) {
                generator.withoutParam(myUnexpectedParamName.trim());
            }
            generator.buildAndInsert();
        }
    }

    private static void addEmptyDocstring(PyDocStringOwner docStringOwner) {
        if (docStringOwner instanceof PyFunction
            || docStringOwner instanceof PyClass pyClass && pyClass.findInitOrNew(false, null) != null) {
            PyGenerateDocstringIntention.generateDocstring(docStringOwner, PyQuickFixUtil.getEditor(docStringOwner));
        }
    }
}
