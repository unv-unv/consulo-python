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
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.inspections.quickfix.PyChangeSignatureQuickFix;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.search.PySuperMethodsSearch;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import org.jetbrains.annotations.Nls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Alexey.Ivanov
 */
@ExtensionImpl
public class PyMethodOverridingInspection extends PyInspection {
  @Nls
  @Nonnull
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.method.over");
  }

  @Nonnull
  @Override
  public PsiElementVisitor buildVisitor(@Nonnull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @Nonnull LocalInspectionToolSession session,
                                        Object state) {
    return new Visitor(holder, session);
  }

  public static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @Nonnull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyFunction(final PyFunction function) {
      // sanity checks
      PyClass cls = function.getContainingClass();
      if (cls == null) {
        return; // not a method, ignore
      }
      String name = function.getName();
      if (PyNames.INIT.equals(name) || PyNames.NEW.equals(name)) {
        return;  // these are expected to change signature
      }
      // real work
      for (PsiElement psiElement : PySuperMethodsSearch.search(function, myTypeEvalContext)) {
        if (psiElement instanceof PyFunction) {
          final PyFunction baseMethod = (PyFunction)psiElement;
          final PyClass baseClass = baseMethod.getContainingClass();
          if (!PyUtil.isSignatureCompatibleTo(function, baseMethod, myTypeEvalContext)) {
            final String msg =
              PyBundle.message("INSP.signature.mismatch", cls.getName() + "." + name + "()", baseClass != null ? baseClass.getName() : "");
            registerProblem(function.getParameterList(), msg, new PyChangeSignatureQuickFix(true));
          }
        }
      }
    }
  }
}
