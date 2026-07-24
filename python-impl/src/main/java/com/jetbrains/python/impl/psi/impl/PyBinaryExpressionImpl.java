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
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.PyElementTypes;
import com.jetbrains.python.impl.psi.impl.references.PyOperatorReference;
import com.jetbrains.python.impl.psi.types.PyGenericType;
import com.jetbrains.python.impl.psi.types.PyNoneType;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.language.util.IncorrectOperationException;

import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public class PyBinaryExpressionImpl extends PyElementImpl implements PyBinaryExpression {
  public PyBinaryExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyBinaryExpression(this);
  }

  @Nullable
  @Override
  @RequiredReadAction
  public PyExpression getLeftExpression() {
    return PsiTreeUtil.getChildOfType(this, PyExpression.class);
  }

  @Override
  @RequiredReadAction
  public PyExpression getRightExpression() {
    return PsiTreeUtil.getNextSiblingOfType(getLeftExpression(), PyExpression.class);
  }

  @Nullable
  @Override
  @RequiredReadAction
  public PyElementType getOperator() {
    PsiElement psiOperator = getPsiOperator();
    return psiOperator != null ? (PyElementType)psiOperator.getNode().getElementType() : null;
  }

  @Nullable
  @Override
  @RequiredReadAction
  public PsiElement getPsiOperator() {
    ASTNode node = getNode();
    ASTNode child = node.findChildByType(PyElementTypes.BINARY_OPS);
    if (child != null) {
      return child.getPsi();
    }
    return null;
  }

  @Override
  @RequiredReadAction
  public boolean isOperator(String chars) {
    ASTNode child = getNode().getFirstChildNode();
    StringBuilder buf = new StringBuilder();
    while (child != null) {
      IElementType elType = child.getElementType();
      if (elType instanceof PyElementType && PyElementTypes.BINARY_OPS.contains(elType)) {
        buf.append(child.getText());
      }
      child = child.getTreeNext();
    }
    return buf.toString().equals(chars);
  }

  @Nullable
  @Override
  @RequiredReadAction
  public PyExpression getOppositeExpression(PyExpression expression) throws IllegalArgumentException {
    PyExpression right = getRightExpression();
    PyExpression left = getLeftExpression();
    if (expression.equals(left)) {
      return right;
    }
    if (expression.equals(right)) {
      return left;
    }
    throw new IllegalArgumentException("expression " + expression + " is neither left exp or right exp");
  }

  @Override
  @RequiredWriteAction
  public void deleteChildInternal(ASTNode child) {
    PyExpression left = getLeftExpression();
    PyExpression right = getRightExpression();
    if (left == child.getPsi() && right != null) {
      replace(right);
    }
    else if (right == child.getPsi() && left != null) {
      replace(left);
    }
    else {
      throw new IncorrectOperationException("Element " + child.getPsi() + " is neither left expression or right expression");
    }
  }

  @Override
  public PsiPolyVariantReference getReference() {
    return getReference(PyResolveContext.noImplicits());
  }

  @Override
  public PsiPolyVariantReference getReference(PyResolveContext context) {
    return new PyOperatorReference(this, context);
  }

  @Override
  @RequiredReadAction
  public PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
    if (isOperator("and") || isOperator("or")) {
      PyExpression left = getLeftExpression();
      PyType leftType = left != null ? context.getType(left) : null;
      PyExpression right = getRightExpression();
      PyType rightType = right != null ? context.getType(right) : null;
      if (leftType == null && rightType == null) {
        return null;
      }
      return PyUnionType.union(leftType, rightType);
    }
    List<PyTypeChecker.AnalyzeCallResults> results = PyTypeChecker.analyzeCallSite(this, context);
    if (!results.isEmpty()) {
      List<PyType> types = new ArrayList<>();
      List<PyType> matchedTypes = new ArrayList<>();
      for (PyTypeChecker.AnalyzeCallResults result : results) {
        boolean matched = true;
        for (Map.Entry<PyExpression, PyNamedParameter> entry : result.getArguments().entrySet()) {
          PyExpression argument = entry.getKey();
          PyNamedParameter parameter = entry.getValue();
          if (parameter.isPositionalContainer() || parameter.isKeywordContainer()) {
            continue;
          }
          Map<PyGenericType, PyType> substitutions = new HashMap<>();
          PyType parameterType = context.getType(parameter);
          PyType argumentType = context.getType(argument);
          if (!PyTypeChecker.match(parameterType, argumentType, context, substitutions)) {
            matched = false;
          }
        }
        PyType type = result.getCallable().getCallType(context, this);
        if (!PyTypeChecker.isUnknown(type) && !(type instanceof PyNoneType)) {
          types.add(type);
          if (matched) {
            matchedTypes.add(type);
          }
        }
      }
      if (!matchedTypes.isEmpty()) {
        return PyUnionType.union(matchedTypes);
      }
      if (!types.isEmpty()) {
        return PyUnionType.union(types);
      }
    }
    String referencedName = getReferencedName();
    if (referencedName != null && PyNames.COMPARISON_OPERATORS.contains(referencedName)) {
      return PyBuiltinCache.getInstance(this).getBoolType();
    }
    return null;
  }

  @Override
  @RequiredReadAction
  public PyExpression getQualifier() {
    return getLeftExpression();
  }

  @Nullable
  @Override
  public QualifiedName asQualifiedName() {
    return PyPsiUtils.asQualifiedName(this);
  }

  @Override
  @RequiredReadAction
  public boolean isQualified() {
    return getQualifier() != null;
  }

  @Override
  @RequiredReadAction
  public String getReferencedName() {
    PyElementType t = getOperator();
    return t != null ? t.getSpecialMethodName() : null;
  }

  @Override
  @RequiredReadAction
  public ASTNode getNameElement() {
    PsiElement op = getPsiOperator();
    return op != null ? op.getNode() : null;
  }
}
