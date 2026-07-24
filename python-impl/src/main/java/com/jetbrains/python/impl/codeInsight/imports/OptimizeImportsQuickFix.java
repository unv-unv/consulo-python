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
package com.jetbrains.python.impl.codeInsight.imports;

import consulo.localize.LocalizeValue;
import consulo.language.editor.intention.HighPriorityAction;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.refactoring.ImportOptimizer;
import consulo.language.editor.WriteCommandAction;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import com.jetbrains.python.psi.PyFile;
import consulo.language.util.IncorrectOperationException;
import consulo.ui.annotation.RequiredUIAccess;

/**
 * @author yole
 */
public class OptimizeImportsQuickFix implements LocalQuickFix, IntentionAction, HighPriorityAction {
    @Override
    public LocalizeValue getText() {
        return LocalizeValue.localizeTODO("Optimize imports");
    }

    @Override
    public LocalizeValue getName() {
        return getText();
    }

    @Override
    public boolean isAvailable(Project project, Editor editor, PsiFile file) {
        return file instanceof PyFile;
    }

    @Override
    @RequiredUIAccess
    public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        optimizeImports(project, file);
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    @Override
    @RequiredUIAccess
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (element == null) {  // stale PSI
            return;
        }
        PsiFile file = element.getContainingFile();
        optimizeImports(project, file);
    }

    @RequiredUIAccess
    private void optimizeImports(final Project project, final PsiFile file) {
        ImportOptimizer optimizer = new PyImportOptimizer();
        final Runnable runnable = optimizer.processFile(file);
        new WriteCommandAction.Simple(project, getName().get(), file) {
            @Override
            protected void run() throws Throwable {
                runnable.run();
            }
        }.execute();
    }
}
