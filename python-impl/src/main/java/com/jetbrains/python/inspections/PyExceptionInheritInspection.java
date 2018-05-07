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
package com.jetbrains.python.inspections;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jetbrains.annotations.Nls;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiPolyVariantReference;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.inspections.quickfix.PyAddExceptionSuperClassQuickFix;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyRaiseStatement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.types.PyClassLikeType;

/**
 * @author Alexey.Ivanov
 */
public class PyExceptionInheritInspection extends PyInspection
{
	@Nls
	@Nonnull
	@Override
	public String getDisplayName()
	{
		return PyBundle.message("INSP.NAME.exception.not.inherit");
	}

	@Nonnull
	@Override
	public PsiElementVisitor buildVisitor(@Nonnull ProblemsHolder holder, boolean isOnTheFly, @Nonnull LocalInspectionToolSession session)
	{
		return new Visitor(holder, session);
	}

	private static class Visitor extends PyInspectionVisitor
	{
		public Visitor(@Nullable ProblemsHolder holder, @Nonnull LocalInspectionToolSession session)
		{
			super(holder, session);
		}

		@Override
		public void visitPyRaiseStatement(PyRaiseStatement node)
		{
			PyExpression[] expressions = node.getExpressions();
			if(expressions.length == 0)
			{
				return;
			}
			PyExpression expression = expressions[0];
			if(expression instanceof PyCallExpression)
			{
				PyExpression callee = ((PyCallExpression) expression).getCallee();
				if(callee instanceof PyReferenceExpression)
				{
					final PsiPolyVariantReference reference = ((PyReferenceExpression) callee).getReference(getResolveContext());
					if(reference == null)
					{
						return;
					}
					PsiElement psiElement = reference.resolve();
					if(psiElement instanceof PyClass)
					{
						PyClass aClass = (PyClass) psiElement;
						for(PyClassLikeType type : aClass.getAncestorTypes(myTypeEvalContext))
						{
							if(type == null)
							{
								return;
							}
							final String name = type.getName();
							if(name == null || "BaseException".equals(name) || "Exception".equals(name))
							{
								return;
							}
						}
						registerProblem(expression, "Exception doesn't inherit from base \'Exception\' class", new PyAddExceptionSuperClassQuickFix());
					}
				}
			}
		}
	}
}
