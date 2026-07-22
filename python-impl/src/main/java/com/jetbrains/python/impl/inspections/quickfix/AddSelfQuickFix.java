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

import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameterList;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

/**
 * Insert 'self' in a method that lacks any arguments
 *
 * @author dcheryasov
 * @since 2008-11-19
 */
public class AddSelfQuickFix implements LocalQuickFix {
    private final String myParamName;

    public AddSelfQuickFix(String paramName) {
        myParamName = paramName;
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixAddParameterSelf(myParamName);
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        if (descriptor.getPsiElement() instanceof PyParameterList paramList) {
            if (!FileModificationService.getInstance().preparePsiElementForWrite(paramList)) {
                return;
            }
            PyNamedParameter new_param = PyElementGenerator.getInstance(project).createParameter(myParamName);
            paramList.addParameter(new_param);
        }
    }
}
