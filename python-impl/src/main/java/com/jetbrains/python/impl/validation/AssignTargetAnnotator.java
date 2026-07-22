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
package com.jetbrains.python.impl.validation;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import com.jetbrains.python.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.virtualFileSystem.VirtualFile;

/**
 * @author yole
 */
public class AssignTargetAnnotator extends PyAnnotator {
  private enum Operation {
    Assign, AugAssign, Delete, Except, For, With
  }

  @Override
  public void visitPyAssignmentStatement(PyAssignmentStatement node) {
    for (PyExpression expression : node.getRawTargets()) {
      expression.accept(new ExprVisitor(Operation.Assign));
    }
  }

  @Override
  public void visitPyAugAssignmentStatement(PyAugAssignmentStatement node) {
    node.getTarget().accept(new ExprVisitor(Operation.AugAssign));
  }

  @Override
  public void visitPyDelStatement(PyDelStatement node) {
    ExprVisitor visitor = new ExprVisitor(Operation.Delete);
    for (PyExpression expr : node.getTargets()) {
      expr.accept(visitor);
    }
  }

  @Override
  public void visitPyExceptBlock(PyExceptPart node) {
    PyExpression target = node.getTarget();
    if (target != null) {
      target.accept(new ExprVisitor(Operation.Except));
    }
  }

  @Override
  public void visitPyForStatement(PyForStatement node) {
    PyExpression target = node.getForPart().getTarget();
    if (target != null) {
      target.accept(new ExprVisitor(Operation.For));
    }
  }

  @Override
  public void visitPyWithItem(PyWithItem node) {
    PyExpression target = node.getTarget();
    if (target != null) {
      target.accept(new ExprVisitor(Operation.With));
    }
  }

  private class ExprVisitor extends PyElementVisitor {
    private final Operation myOp;

    public ExprVisitor(Operation op) {
      myOp = op;
    }

    @Override
    @RequiredReadAction
    public void visitPyReferenceExpression(PyReferenceExpression node) {
      String referencedName = node.getReferencedName();
      if (PyNames.NONE.equals(referencedName)) {
        getHolder().newError(myOp == Operation.Delete ? PyLocalize.annDeletingNone() : PyLocalize.annAssignToNone()).range(node).create();
      }
    }

    @Override
    @RequiredReadAction
    public void visitPyTargetExpression(PyTargetExpression node) {
      String targetName = node.getName();
      if (PyNames.NONE.equals(targetName)) {
        VirtualFile vFile = node.getContainingFile().getVirtualFile();
        if (vFile != null && !vFile.getUrl().contains("/" + PythonSdkType.SKELETON_DIR_NAME + "/")){
          getHolder().newError(myOp == Operation.Delete ? PyLocalize.annDeletingNone() : PyLocalize.annAssignToNone()).range(node).create();
        }
      }
      if (PyNames.DEBUG.equals(targetName)) {
        if (LanguageLevel.forElement(node).isPy3K()) {
          getHolder().newError(LocalizeValue.localizeTODO("assignment to keyword")).range(node).create();
        }
        else {
          getHolder().newError(LocalizeValue.localizeTODO("cannot assign to __debug__")).range(node).create();
        }
      }
    }

    @Override
    @RequiredReadAction
    public void visitPyCallExpression(PyCallExpression node) {
      getHolder()
          .newError(myOp == Operation.Delete ? PyLocalize.annCantDeleteCall() : PyLocalize.annCantAssignToCall())
          .range(node)
          .create();
    }

    @Override
    @RequiredReadAction
    public void visitPyGeneratorExpression(PyGeneratorExpression node) {
      getHolder()
          .newError(myOp == Operation.AugAssign ? PyLocalize.annCantAugAssignToGenerator() : PyLocalize.annCantAssignToGenerator())
          .range(node)
          .create();
    }

    @Override
    @RequiredReadAction
    public void visitPyBinaryExpression(PyBinaryExpression node) {
      getHolder().newError(PyLocalize.annCantAssignToOperator()).range(node).create();
    }

    @Override
    @RequiredReadAction
    public void visitPyTupleExpression(PyTupleExpression node) {
      if (node.getElements().length == 0) {
        getHolder().newError(PyLocalize.annCantAssignToParens()).range(node).create();
      }
      else if (myOp == Operation.AugAssign) {
        getHolder().newError(PyLocalize.annCantAugAssignToTupleOrGenerator()).range(node).create();
      }
      else {
        node.acceptChildren(this);
      }
    }

    @Override
    @RequiredReadAction
    public void visitPyParenthesizedExpression(PyParenthesizedExpression node) {
      if (myOp == Operation.AugAssign) {
        getHolder().newError(PyLocalize.annCantAugAssignToTupleOrGenerator()).range(node).create();
      }
      else {
        node.acceptChildren(this);
      }
    }

    @Override
    @RequiredReadAction
    public void visitPyListLiteralExpression(PyListLiteralExpression node) {
      if (node.getElements().length == 0) {
        getHolder().newError(PyLocalize.annCantAssignToBrackets()).range(node).create();
      }
      else if (myOp == Operation.AugAssign) {
        getHolder().newError(PyLocalize.annCantAugAssignToListOrComprh()).range(node).create();
      }
      else {
        node.acceptChildren(this);
      }
    }

    @Override
    @RequiredReadAction
    public void visitPyListCompExpression(PyListCompExpression node) {
        getHolder()
            .newError(myOp == Operation.AugAssign ? PyLocalize.annCantAugAssignToComprh() : PyLocalize.annCantAssignToComprh())
            .range(node)
            .create();
    }

    @Override
    @RequiredReadAction
    public void visitPyDictCompExpression(PyDictCompExpression node) {
        getHolder()
            .newError(myOp == Operation.AugAssign ? PyLocalize.annCantAugAssignToDictComprh() : PyLocalize.annCantAssignToDictComprh())
            .range(node)
            .create();
    }

    @Override
    @RequiredReadAction
    public void visitPySetCompExpression(PySetCompExpression node) {
        getHolder()
            .newError(myOp == Operation.AugAssign ? PyLocalize.annCantAugAssignToSetComprh() : PyLocalize.annCantAssignToSetComprh())
            .range(node)
            .create();
    }

    @Override
    @RequiredReadAction
    public void visitPyStarExpression(PyStarExpression node) {
      super.visitPyStarExpression(node);
      if (!(node.getParent() instanceof PySequenceExpression)) {
        getHolder().newError(LocalizeValue.localizeTODO("starred assignment target must be in a list or tuple")).range(node).create();
      }
    }

    @Override
    @RequiredReadAction
    public void visitPyDictLiteralExpression(PyDictLiteralExpression node) {
      checkLiteral(node);
    }

    @Override
    @RequiredReadAction
    public void visitPySetLiteralExpression(PySetLiteralExpression node) {
      checkLiteral(node);
    }

    @Override
    @RequiredReadAction
    public void visitPyNumericLiteralExpression(PyNumericLiteralExpression node) {
      checkLiteral(node);
    }

    @Override
    @RequiredReadAction
    public void visitPyStringLiteralExpression(PyStringLiteralExpression node) {
      checkLiteral(node);
    }

    @RequiredReadAction
    private void checkLiteral(PyExpression node) {
      getHolder().newError(myOp == Operation.Delete ? PyLocalize.annCantDeleteLiteral() : PyLocalize.annCantAssignToLiteral())
        .range(node)
        .create();
    }

    @Override
    @RequiredReadAction
    public void visitPyLambdaExpression(PyLambdaExpression node) {
      getHolder().newError(PyLocalize.annCantAssignToLambda()).range(node).create();
    }

    @Override
    @RequiredReadAction
    public void visitPyNoneLiteralExpression(PyNoneLiteralExpression node) {
      getHolder().newError(LocalizeValue.localizeTODO("assignment to keyword")).range(node).create();
    }

    @Override
    @RequiredReadAction
    public void visitPyBoolLiteralExpression(PyBoolLiteralExpression node) {
      getHolder().newError(LocalizeValue.localizeTODO("assignment to keyword")).range(node).create();
    }
  }
}
