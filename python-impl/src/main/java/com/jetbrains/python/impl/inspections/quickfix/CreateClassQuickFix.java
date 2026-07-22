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

import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyFile;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.editor.template.TemplateBuilderFactory;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;

/**
 * @author yole
 */
public class CreateClassQuickFix implements LocalQuickFix {
    private final String myClassName;
    private final PsiElement myAnchor;

    public CreateClassQuickFix(String className, PsiElement anchor) {
        myClassName = className;
        myAnchor = anchor;
    }

    @Override
    public LocalizeValue getName() {
        if (myAnchor instanceof PyFile) {
            return LocalizeValue.localizeTODO("Create class '" + myClassName + "' in module " + ((PyFile) myAnchor).getName());
        }
        return LocalizeValue.localizeTODO("Create class '" + myClassName + "'");
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement anchor = myAnchor;
        if (!anchor.isValid()) {
            return;
        }
        if (!(anchor instanceof PyFile)) {
            while (!(anchor.getParent() instanceof PyFile)) {
                anchor = anchor.getParent();
            }
        }
        PyClass pyClass = PyElementGenerator.getInstance(project)
            .createFromText(LanguageLevel.getDefault(), PyClass.class, "class " + myClassName + "(object):\n    pass");
        if (anchor instanceof PyFile) {
            pyClass = (PyClass) anchor.add(pyClass);
        }
        else {
            pyClass = (PyClass) anchor.getParent().addBefore(pyClass, anchor);
        }
        pyClass = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(pyClass);
        TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(pyClass);
        builder.replaceElement(pyClass.getSuperClassExpressions()[0], "object");
        builder.replaceElement(pyClass.getStatementList(), PyNames.PASS);
        builder.run();
    }
}
