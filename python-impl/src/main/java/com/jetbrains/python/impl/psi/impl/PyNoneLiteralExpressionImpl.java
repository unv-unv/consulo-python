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

package com.jetbrains.python.impl.psi.impl;

import jakarta.annotation.Nonnull;

import consulo.language.ast.ASTNode;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyNoneLiteralExpression;
import com.jetbrains.python.impl.psi.types.PyNoneType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;

/**
 * @author yole
 */
public class PyNoneLiteralExpressionImpl extends PyElementImpl implements PyNoneLiteralExpression {
  public PyNoneLiteralExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyType getType(@Nonnull TypeEvalContext context, @Nonnull TypeEvalContext.Key key) {
    return PyNoneType.INSTANCE;
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyNoneLiteralExpression(this);
  }

  @Override
  public boolean isEllipsis() {
    return getNode().findChildByType(PyTokenTypes.DOT) != null;
  }
}
