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

import com.jetbrains.python.psi.PyFile;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

/**
 * @author Alexey.Ivanov
 * @since 2010-03-24
 */
public class MoveFromFutureImportQuickFix implements LocalQuickFix {
    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixMoveFromFutureImport();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement problemElement = descriptor.getPsiElement();
        if (problemElement.getContainingFile() instanceof PyFile file) {
            file.addBefore(problemElement, file.getStatements().get(0));
            problemElement.delete();
        }
    }
}
