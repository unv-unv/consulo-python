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

package com.jetbrains.python.inspections.quickfix;

import javax.annotation.Nonnull;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyFile;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   24.03.2010
 * Time:   20:15:23
 */
public class MoveFromFutureImportQuickFix implements LocalQuickFix {
  @Nonnull
  public String getName() {
    return PyBundle.message("QFIX.move.from.future.import");
  }

  @Nonnull
  public String getFamilyName() {
    return getName();
  }

  public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
    PsiElement problemElement = descriptor.getPsiElement();
    PsiFile psiFile = problemElement.getContainingFile();
    if (psiFile instanceof PyFile) {
      PyFile file = (PyFile)psiFile;
      file.addBefore(problemElement, file.getStatements().get(0));
      problemElement.delete();
    }
  }
}
