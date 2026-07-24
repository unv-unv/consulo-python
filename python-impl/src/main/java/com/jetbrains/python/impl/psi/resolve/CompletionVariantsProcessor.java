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
package com.jetbrains.python.impl.psi.resolve;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.completion.PyClassInsertHandler;
import com.jetbrains.python.impl.codeInsight.completion.PyFunctionInsertHandler;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.ui.image.Image;

/**
 * @author yole
 */
public class CompletionVariantsProcessor extends VariantsProcessor
{
	private final Map<String, LookupElement> myVariants = new HashMap<>();
	private boolean mySuppressParentheses = false;

	public CompletionVariantsProcessor(PsiElement context)
	{
		super(context);
	}

	public CompletionVariantsProcessor(PsiElement context, @Nullable Predicate<PsiElement> nodeFilter, @Nullable Predicate<String> nameFilter)
	{
		super(context, nodeFilter, nameFilter);
	}

	public void suppressParentheses()
	{
		mySuppressParentheses = true;
	}

	private LookupElementBuilder setupItem(LookupElementBuilder item)
	{
		PsiElement element = item.getPsiElement();
		if(!myPlainNamesOnly)
		{
			if(!mySuppressParentheses &&
					element instanceof PyFunction && ((PyFunction) element).getProperty() == null &&
					!PyUtil.hasCustomDecorators((PyFunction) element) &&
					!isSingleArgDecoratorCall(myContext, (PyFunction) element))
			{
				Project project = element.getProject();
				item = item.withInsertHandler(PyFunctionInsertHandler.INSTANCE);
				TypeEvalContext context = TypeEvalContext.codeCompletion(project, myContext != null ? myContext.getContainingFile() : null);
				List<PyParameter> parameters = PyUtil.getParameters((PyFunction) element, context);
				String params = StringUtil.join(parameters, pyParameter -> pyParameter.getName(), ", ");
				item = item.withTailText("(" + params + ")");
			}
			else if(element instanceof PyClass)
			{
				item = item.withInsertHandler(PyClassInsertHandler.INSTANCE);
			}
		}
		String source = null;
		if(element != null)
		{
			PyClass cls = null;

			if(element instanceof PyFunction)
			{
				cls = ((PyFunction) element).getContainingClass();
			}
			else if(element instanceof PyTargetExpression)
			{
				PyTargetExpression expr = (PyTargetExpression) element;
				if(expr.isQualified() || ScopeUtil.getScopeOwner(expr) instanceof PyClass)
				{
					cls = expr.getContainingClass();
				}
			}
			else if(element instanceof PyClass)
			{
				ScopeOwner owner = ScopeUtil.getScopeOwner(element);
				if(owner instanceof PyClass)
				{
					cls = (PyClass) owner;
				}
			}

			if(cls != null)
			{
				source = cls.getName();
			}
			else if(myContext == null || !PyUtil.inSameFile(myContext, element))
			{
				QualifiedName path = QualifiedNameFinder.findCanonicalImportPath(element, null);
				if(path != null)
				{
					if(element instanceof PyFile)
					{
						path = path.removeLastComponent();
					}
					source = path.toString();
				}
			}
		}
		if(source != null)
		{
			item = item.withTypeText(source);
		}
		return item;
	}

	private static boolean isSingleArgDecoratorCall(PsiElement elementInCall, PyFunction callee)
	{
		// special case hack to avoid the need of patching generator3.py
		PyClass containingClass = callee.getContainingClass();
		if(containingClass != null && PyNames.PROPERTY.equals(containingClass.getName()) &&
				PyBuiltinCache.getInstance(elementInCall).isBuiltin(containingClass))
		{
			return true;
		}

		if(callee.getParameterList().getParameters().length > 1)
		{
			return false;
		}
		PyDecorator decorator = PsiTreeUtil.getParentOfType(elementInCall, PyDecorator.class);
		if(decorator == null)
		{
			return false;
		}
		return PsiTreeUtil.isAncestor(decorator.getCallee(), elementInCall, false);
	}

	protected static LookupElementBuilder setItemNotice(LookupElementBuilder item, String notice)
	{
		return item.withTypeText(notice);
	}

	public LookupElement[] getResult()
	{
		Collection<LookupElement> variants = myVariants.values();
		return variants.toArray(new LookupElement[variants.size()]);
	}

	public List<LookupElement> getResultList()
	{
		return new ArrayList<>(myVariants.values());
	}

	@Override
	protected void addElement(String name, PsiElement element)
	{
		if(PyUtil.isClassPrivateName(name) && !PyUtil.inSameFile(element, myContext))
		{
			return;
		}
		myVariants.put(name, setupItem(LookupElementBuilder.createWithSmartPointer(name, element).withIcon(IconDescriptorUpdaters.getIcon(element, 0))));
	}

	@Override
	protected void addImportedElement(String referencedName, PyElement expr)
	{
		Image icon = IconDescriptorUpdaters.getIcon(expr, 0);
		LookupElementBuilder lookupItem = setupItem(LookupElementBuilder.createWithSmartPointer(referencedName, expr).withIcon(icon));
		myVariants.put(referencedName, lookupItem);
	}
}
