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

import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.*;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.lang.StringUtil;

import java.util.Map;

public class PyAddPropertyForFieldQuickFix implements LocalQuickFix {
    private final LocalizeValue myName;

    public PyAddPropertyForFieldQuickFix(LocalizeValue name) {
        myName = name;
    }

    @Override
    public LocalizeValue getName() {
        return myName;
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (element instanceof PyReferenceExpression) {
            PsiReference reference = element.getReference();
            if (reference == null) {
                return;
            }
            PsiElement resolved = reference.resolve();
            if (resolved instanceof PyTargetExpression target) {
                PyClass containingClass = target.getContainingClass();
                if (containingClass != null) {
                    String name = target.getName();
                    if (name == null) {
                        return;
                    }
                    String propertyName = StringUtil.trimStart(name, "_");
                    Map<String, Property> properties = containingClass.getProperties();
                    PyElementGenerator generator = PyElementGenerator.getInstance(project);
                    if (!properties.containsKey(propertyName)) {
                        PyFunction property =
                            generator.createProperty(LanguageLevel.forElement(containingClass), propertyName, name, AccessDirection.READ);
                        PyUtil.addElementToStatementList(property, containingClass.getStatementList(), false);
                    }
                    PyExpression qualifier = ((PyReferenceExpression) element).getQualifier();
                    if (qualifier != null) {
                        String newElementText = qualifier.getText() + "." + propertyName;
                        PyExpression newElement =
                            generator.createExpressionFromText(LanguageLevel.forElement(containingClass), newElementText);
                        element.replace(newElement);
                    }
                }
            }
        }
    }
}
