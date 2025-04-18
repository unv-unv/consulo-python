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
package com.jetbrains.python.impl.refactoring.rename;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;

import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
@ExtensionImpl(id = "pyClass", order = "before pyVar, after pyClass")
public class RenamePyClassProcessor extends RenamePyElementProcessor
{
	@Override
	public boolean canProcessElement(@Nonnull PsiElement element)
	{
		return element instanceof PyClass;
	}

	@Override
	public boolean isToSearchInComments(PsiElement element)
	{
		return PyCodeInsightSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_CLASS;
	}

	@Override
	public void setToSearchInComments(PsiElement element, boolean enabled)
	{
		PyCodeInsightSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_CLASS = enabled;
	}

	@Override
	public boolean isToSearchForTextOccurrences(PsiElement element)
	{
		return PyCodeInsightSettings.getInstance().RENAME_SEARCH_NON_CODE_FOR_CLASS;
	}

	@Override
	public void setToSearchForTextOccurrences(PsiElement element, boolean enabled)
	{
		PyCodeInsightSettings.getInstance().RENAME_SEARCH_NON_CODE_FOR_CLASS = enabled;
	}

	@Nonnull
	@Override
	public Collection<PsiReference> findReferences(final PsiElement element)
	{
		if(element instanceof PyClass)
		{
			final PyFunction initMethod = ((PyClass) element).findMethodByName(PyNames.INIT, true, null);
			if(initMethod != null)
			{
				final List<PsiReference> allRefs = Collections.synchronizedList(new ArrayList<PsiReference>());
				allRefs.addAll(super.findReferences(element));
				ReferencesSearch.search(initMethod, GlobalSearchScope.projectScope(element.getProject())).forEach(psiReference -> {
					if(psiReference.getCanonicalText().equals(((PyClass) element).getName()))
					{
						allRefs.add(psiReference);
					}
					return true;
				});
				return allRefs;
			}
		}
		return super.findReferences(element);
	}
}
