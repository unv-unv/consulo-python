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
package com.jetbrains.python.psi.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.references.PyOperatorReference;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyABCUtil;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyCollectionType;
import com.jetbrains.python.psi.types.PyNoneType;
import com.jetbrains.python.psi.types.PyTupleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.types.PyUnionType;
import com.jetbrains.python.psi.types.TypeEvalContext;

/**
 * @author yole
 */
public class PySubscriptionExpressionImpl extends PyElementImpl implements PySubscriptionExpression
{
	public PySubscriptionExpressionImpl(ASTNode astNode)
	{
		super(astNode);
	}

	@Nonnull
	public PyExpression getOperand()
	{
		return childToPsiNotNull(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens(), 0);
	}

	@Nonnull
	@Override
	public PyExpression getRootOperand()
	{
		PyExpression operand = getOperand();
		while(operand instanceof PySubscriptionExpression)
		{
			operand = ((PySubscriptionExpression) operand).getOperand();
		}
		return operand;
	}

	@Nullable
	public PyExpression getIndexExpression()
	{
		return childToPsi(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens(), 1);
	}

	@Override
	protected void acceptPyVisitor(final PyElementVisitor pyVisitor)
	{
		pyVisitor.visitPySubscriptionExpression(this);
	}

	@Nullable
	@Override
	public PyType getType(@Nonnull TypeEvalContext context, @Nonnull TypeEvalContext.Key key)
	{
		final PsiPolyVariantReference reference = getReference(PyResolveContext.noImplicits().withTypeEvalContext(context));
		final List<PyType> members = new ArrayList<>();
		for(PsiElement resolved : PyUtil.multiResolveTopPriority(reference))
		{
			PyType res = null;
			if(resolved instanceof PyCallable)
			{
				res = ((PyCallable) resolved).getCallType(context, this);
			}
			if(PyTypeChecker.isUnknown(res) || res instanceof PyNoneType)
			{
				final PyExpression indexExpression = getIndexExpression();
				if(indexExpression != null)
				{
					final PyType type = context.getType(getOperand());
					final PyClass cls = (type instanceof PyClassType) ? ((PyClassType) type).getPyClass() : null;
					if(cls != null && PyABCUtil.isSubclass(cls, PyNames.MAPPING, context))
					{
						return res;
					}
					if(type instanceof PyTupleType)
					{
						final PyTupleType tupleType = (PyTupleType) type;

						res = Optional.ofNullable(PyConstantExpressionEvaluator.evaluate(indexExpression)).map(value -> PyUtil.as(value, Integer.class)).map(tupleType::getElementType).orElse(null);
					}
					else if(type instanceof PyCollectionType)
					{
						res = ((PyCollectionType) type).getIteratedItemType();
					}
				}
			}
			members.add(res);
		}
		return PyUnionType.union(members);
	}

	@Override
	public PsiReference getReference()
	{
		return getReference(PyResolveContext.noImplicits());
	}

	@Nonnull
	@Override
	public PsiPolyVariantReference getReference(PyResolveContext context)
	{
		return new PyOperatorReference(this, context);
	}

	@Override
	public PyExpression getQualifier()
	{
		return getOperand();
	}

	@Nullable
	@Override
	public QualifiedName asQualifiedName()
	{
		return PyPsiUtils.asQualifiedName(this);
	}

	@Override
	public boolean isQualified()
	{
		return getQualifier() != null;
	}

	@Override
	public String getReferencedName()
	{
		String res = PyNames.GETITEM;
		switch(AccessDirection.of(this))
		{
			case READ:
				res = PyNames.GETITEM;
				break;
			case WRITE:
				res = PyNames.SETITEM;
				break;
			case DELETE:
				res = PyNames.DELITEM;
				break;
		}
		return res;
	}

	@Override
	public ASTNode getNameElement()
	{
		return getNode().findChildByType(PyTokenTypes.LBRACKET);
	}
}
