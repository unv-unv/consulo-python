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

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.impl.refactoring.changeSignature.PyChangeSignatureDialog;
import com.jetbrains.python.impl.refactoring.changeSignature.PyMethodDescriptor;
import com.jetbrains.python.impl.refactoring.changeSignature.PyParameterInfo;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.annotation.RequiredUIAccess;

import java.util.List;

public class PyChangeSignatureQuickFix implements LocalQuickFix {
    private final boolean myOverridenMethod;

    public PyChangeSignatureQuickFix(boolean overriddenMethod) {
        myOverridenMethod = overriddenMethod;
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixNameChangeSignature();
    }

    @Override
    @RequiredUIAccess
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        final PyFunction function = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PyFunction.class);
        if (function == null) {
            return;
        }
        PyClass cls = function.getContainingClass();
        assert cls != null;
        String functionName = function.getName();
        String complementaryName = PyNames.NEW.equals(functionName) ? PyNames.INIT : PyNames.NEW;
        TypeEvalContext context = TypeEvalContext.userInitiated(project, descriptor.getEndElement().getContainingFile());
        final PyFunction complementaryMethod =
            myOverridenMethod ? (PyFunction) PySuperMethodsSearch.search(function, context).findFirst() : cls.findMethodByName(
                complementaryName,
                true,
                null
            );

        assert complementaryMethod != null;
        PyMethodDescriptor methodDescriptor = new PyMethodDescriptor(function) {
            @Override
            public List<PyParameterInfo> getParameters() {
                List<PyParameterInfo> parameterInfos = super.getParameters();
                int paramLength = function.getParameterList().getParameters().length;
                int complementaryParamLength = complementaryMethod.getParameterList().getParameters().length;
                if (complementaryParamLength > paramLength) {
                    parameterInfos.add(new PyParameterInfo(-1, "**kwargs", "", false));
                }
                return parameterInfos;
            }
        };
        PyChangeSignatureDialog dialog = new PyChangeSignatureDialog(project, methodDescriptor);
        dialog.show();
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }
}
