/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.inspections.quickfix;

import javax.annotation.Nonnull;

import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;

/**
 * @author yole
 */
public class AddIgnoredIdentifierQuickFix implements LocalQuickFix, LowPriorityAction
{
	public static final String END_WILDCARD = ".*";

	@Nonnull
	private final QualifiedName myIdentifier;
	private final boolean myIgnoreAllAttributes;

	public AddIgnoredIdentifierQuickFix(@Nonnull QualifiedName identifier, boolean ignoreAllAttributes)
	{
		myIdentifier = identifier;
		myIgnoreAllAttributes = ignoreAllAttributes;
	}

	@Nonnull
	@Override
	public String getName()
	{
		if(myIgnoreAllAttributes)
		{
			return "Mark all unresolved attributes of '" + myIdentifier + "' as ignored";
		}
		else
		{
			return "Ignore unresolved reference '" + myIdentifier + "'";
		}
	}

	@Nonnull
	@Override
	public String getFamilyName()
	{
		return "Ignore unresolved reference";
	}

	@Override
	public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor)
	{
		final PsiElement context = descriptor.getPsiElement();
		InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
		profile.modifyProfile(model -> {
			PyUnresolvedReferencesInspection inspection = (PyUnresolvedReferencesInspection) model.getUnwrappedTool(PyUnresolvedReferencesInspection.class.getSimpleName(), context);
			String name = myIdentifier.toString();
			if(myIgnoreAllAttributes)
			{
				name += END_WILDCARD;
			}
			assert inspection != null;
			if(!inspection.ignoredIdentifiers.contains(name))
			{
				inspection.ignoredIdentifiers.add(name);
			}
		});
	}
}
