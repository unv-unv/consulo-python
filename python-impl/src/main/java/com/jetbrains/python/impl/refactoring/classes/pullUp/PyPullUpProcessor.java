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
package com.jetbrains.python.impl.refactoring.classes.pullUp;

import java.util.Collection;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.impl.refactoring.classes.membersManager.PyMemberInfo;
import com.jetbrains.python.impl.refactoring.classes.membersManager.PyMembersRefactoringBaseProcessor;

/**
 * @author Ilya.Kazakevich
 */
class PyPullUpProcessor extends PyMembersRefactoringBaseProcessor
{

	PyPullUpProcessor(@Nonnull final PyClass from, @Nonnull final PyClass to, @Nonnull final Collection<PyMemberInfo<PyElement>> membersToMove)
	{
		super(from.getProject(), membersToMove, from, to);
	}


	@Override
	protected String getCommandName()
	{
		return PyPullUpHandler.REFACTORING_NAME;
	}

	@Override
	public String getProcessedElementsHeader()
	{
		return PyBundle.message("refactoring.pull.up.dialog.move.members.to.class");
	}

	@Override
	public String getCodeReferencesText(final int usagesCount, final int filesCount)
	{
		return PyBundle.message("refactoring.pull.up.dialog.members.to.be.moved");
	}

	@Nullable
	@Override
	public String getCommentReferencesText(final int usagesCount, final int filesCount)
	{
		return getCodeReferencesText(usagesCount, filesCount);
	}

	@Nullable
	@Override
	protected String getRefactoringId()
	{
		return "refactoring.python.pull.up";
	}
}
