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

import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiInvalidElementAccessException;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.util.QualifiedName;
import consulo.util.dataholder.Key;
import consulo.util.io.FileUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public abstract class VariantsProcessor implements PsiScopeProcessor
{
	protected final PsiElement myContext;
	protected Predicate<PsiElement> myNodeFilter;
	protected Predicate<String> myNameFilter;

	protected boolean myPlainNamesOnly = false; // if true, add insert handlers to known things like functions
	private List<String> myAllowedNames;
	private final List<String> mySeenNames = new ArrayList<>();

	public VariantsProcessor(PsiElement context)
	{
		// empty
		myContext = context;
	}

	public VariantsProcessor(PsiElement context, @Nullable Predicate<PsiElement> nodeFilter, @Nullable Predicate<String> nameFilter)
	{
		myContext = context;
		myNodeFilter = nodeFilter;
		myNameFilter = nameFilter;
	}

	public boolean isPlainNamesOnly()
	{
		return myPlainNamesOnly;
	}

	public void setPlainNamesOnly(boolean plainNamesOnly)
	{
		myPlainNamesOnly = plainNamesOnly;
	}

    @Override
    @RequiredReadAction
	public boolean execute(PsiElement element, ResolveState substitutor)
	{
		if(myNodeFilter != null && !myNodeFilter.test(element))
		{
			return true; // skip whatever the filter rejects
		}
		// TODO: refactor to look saner; much code duplication
		if(element instanceof PsiNamedElement psiNamedElement)
		{
			String name = PyUtil.getElementNameWithoutExtension(psiNamedElement);
			if(name != null && nameIsAcceptable(name))
			{
				addElement(name, psiNamedElement);
			}
		}
		else if(element instanceof PyReferenceExpression expr)
		{
			String referencedName = expr.getReferencedName();
			if(nameIsAcceptable(referencedName))
			{
				addElement(referencedName, expr);
			}
		}
		else if(element instanceof PyImportedNameDefiner)
		{
			boolean handledAsImported = false;
			if(element instanceof PyImportElement importElement)
			{
				handledAsImported = handleImportElement(importElement);
			}
			if(!handledAsImported)
			{
				PyImportedNameDefiner definer = (PyImportedNameDefiner) element;
				for(PyElement expr : definer.iterateNames())
				{
					if(expr != null && expr != myContext)
					{ // NOTE: maybe rather have SingleIterables skip nulls outright?
						if(!expr.isValid())
						{
							throw new PsiInvalidElementAccessException(expr, "Definer: " + definer);
						}
						String referencedName = expr instanceof PyFile ? FileUtil.getNameWithoutExtension(((PyFile) expr).getName()) : expr.getName();
						if(referencedName != null && nameIsAcceptable(referencedName))
						{
							addImportedElement(referencedName, expr);
						}
					}
				}
			}
		}

		return true;
	}

	protected boolean handleImportElement(PyImportElement importElement)
	{
		QualifiedName qName = importElement.getImportedQName();
		if(qName != null && qName.getComponentCount() == 1)
		{
			String name = importElement.getAsName() != null ? importElement.getAsName() : qName.getLastComponent();
			if(name != null && nameIsAcceptable(name))
			{
				PsiElement resolved = importElement.resolve();
				if(resolved instanceof PsiNamedElement)
				{
					addElement(name, resolved);
					return true;
				}
			}
		}
		return false;
	}

	protected void addElement(String name, PsiElement psiNamedElement)
	{
		mySeenNames.add(name);
	}

	protected void addImportedElement(String referencedName, PyElement expr)
	{
		addElement(referencedName, expr);
	}

	private boolean nameIsAcceptable(String name)
	{
		if(name == null)
		{
			return false;
		}
		if(mySeenNames.contains(name))
		{
			return false;
		}
		if(myNameFilter != null && !myNameFilter.test(name))
		{
			return false;
		}
        return myAllowedNames == null || myAllowedNames.contains(name);
    }

	@Override
	@Nullable
	public <T> T getHint(Key<T> hintKey)
	{
		return null;
	}

	@Override
	public void handleEvent(Event event, Object associated)
	{
	}

	public void setAllowedNames(List<String> namesFilter)
	{
		myAllowedNames = namesFilter;
	}
}
