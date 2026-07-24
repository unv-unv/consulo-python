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

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.PyAugAssignmentStatement;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;

import org.jspecify.annotations.Nullable;

/**
 * @author yole
 */
public class PyAugAssignmentStatementImpl extends PyElementImpl implements PyAugAssignmentStatement
{
	public PyAugAssignmentStatementImpl(ASTNode astNode)
	{
		super(astNode);
	}

	@Override
	protected void acceptPyVisitor(PyElementVisitor pyVisitor)
	{
		pyVisitor.visitPyAugAssignmentStatement(this);
	}

	@Override
    @RequiredReadAction
    public PyExpression getTarget()
	{
		PyExpression target = childToPsi(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens(), 0);
		if(target == null)
		{
			throw new RuntimeException("Target missing in augmented assignment statement");
		}
		return target;
	}

    @Nullable
    @Override
    @RequiredReadAction
	public PyExpression getValue()
	{
		return childToPsi(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens(), 1);
	}

	@Nullable
    @Override
	public PsiElement getOperation()
	{
		return PyPsiUtils.getChildByFilter(this, PyTokenTypes.AUG_ASSIGN_OPERATIONS, 0);
	}
}
