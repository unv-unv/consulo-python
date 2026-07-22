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

import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.*;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.ref.SimpleReference;

/**
 * @author oleg
 */
public class AddGlobalQuickFix implements LocalQuickFix {
  @Override
  public LocalizeValue getName() {
    return PyLocalize.qfixAddGlobal();
  }

  @Override
  @RequiredWriteAction
  public void applyFix(Project project, ProblemDescriptor descriptor) {
    PsiElement problemElt = descriptor.getPsiElement();
    if (problemElt instanceof PyReferenceExpression expression) {
      final String name = expression.getReferencedName();
      ScopeOwner owner = PsiTreeUtil.getParentOfType(problemElt, ScopeOwner.class);
      assert owner instanceof PyClass || owner instanceof PyFunction : "Add global quickfix is available only inside class or function, but applied for " + owner;
      final SimpleReference<Boolean> added = new SimpleReference<>(false);
      owner.accept(new PyRecursiveElementVisitor(){
        @Override
        public void visitElement(PsiElement element) {
          if (!added.get()){
            super.visitElement(element);
          }
        }
        @Override
        public void visitPyGlobalStatement(PyGlobalStatement node) {
          if (!added.get()){
            node.addGlobal(name);
            added.set(true);
          }
        }
      });
      if (added.get()){
        return;
      }
      PyGlobalStatement globalStatement =
        PyElementGenerator.getInstance(project).createFromText(LanguageLevel.getDefault(), PyGlobalStatement.class, "global " + name);
      PyStatementList statementList;
      boolean hasDocString = false;
      if (owner instanceof PyClass){
        statementList = ((PyClass)owner).getStatementList();
        if (((PyClass)owner).getDocStringExpression() != null) hasDocString = true;
      }
      else {
        statementList = ((PyFunction)owner).getStatementList();
        if (((PyFunction)owner).getDocStringExpression() != null) hasDocString = true;
      }
      PyStatement first = statementList.getStatements()[0];
      if (hasDocString)
        first = statementList.getStatements()[1];
      statementList.addBefore(globalStatement, first);
    }
  }
}
