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
package com.jetbrains.python.impl.codeInsight.intentions;

import jakarta.annotation.Nonnull;

import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.ast.IElementType;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyIfPart;
import com.jetbrains.python.psi.PyIfStatement;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.impl.PyPsiUtils;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   10.03.2010
 * Time:   18:52:52
 */
public class PySplitIfIntention extends PyBaseIntentionAction
{
	@Nonnull
	public String getFamilyName()
	{
		return PyBundle.message("INTN.split.if");
	}

	public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file)
	{
		if(!(file instanceof PyFile))
		{
			return false;
		}

		PsiElement elementAtOffset = file.findElementAt(editor.getCaretModel().getOffset());
		if(elementAtOffset == null || elementAtOffset.getNode() == null)
		{
			return false;
		}

		// PY-745
		final IElementType elementType = elementAtOffset.getNode().getElementType();
		if(elementType == PyTokenTypes.COLON)
		{
			elementAtOffset = elementAtOffset.getPrevSibling();
			elementAtOffset = PyPsiUtils.getPrevNonCommentSibling(elementAtOffset, false);
		}
		else if(elementType == PyTokenTypes.IF_KEYWORD)
		{
			elementAtOffset = elementAtOffset.getNextSibling();
			elementAtOffset = PyPsiUtils.getNextNonCommentSibling(elementAtOffset, false);
		}

		PsiElement element = PsiTreeUtil.getParentOfType(elementAtOffset, PyBinaryExpression.class, false);
		if(element == null)
		{
			return false;
		}

		while(element.getParent() instanceof PyBinaryExpression)
		{
			element = element.getParent();
		}
		if(((PyBinaryExpression) element).getOperator() != PyTokenTypes.AND_KEYWORD || ((PyBinaryExpression) element).getRightExpression() == null)
		{
			return false;
		}
		final PsiElement parent = element.getParent();
		if(!(parent instanceof PyIfPart))
		{
			return false;
		}
		setText(PyBundle.message("INTN.split.if.text"));
		return true;
	}

	public void doInvoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException
	{
		PsiElement elementAtOffset = file.findElementAt(editor.getCaretModel().getOffset());
		// PY-745
		final IElementType elementType = elementAtOffset.getNode().getElementType();
		if(elementType == PyTokenTypes.COLON)
		{
			elementAtOffset = elementAtOffset.getPrevSibling();
			elementAtOffset = PyPsiUtils.getPrevNonCommentSibling(elementAtOffset, false);
		}
		else if(elementType == PyTokenTypes.IF_KEYWORD)
		{
			elementAtOffset = elementAtOffset.getNextSibling();
			elementAtOffset = PyPsiUtils.getNextNonCommentSibling(elementAtOffset, false);
		}

		PyBinaryExpression element = PsiTreeUtil.getParentOfType(elementAtOffset, PyBinaryExpression.class, false);
		while(element.getParent() instanceof PyBinaryExpression)
		{
			element = (PyBinaryExpression) element.getParent();
		}
		PyIfStatement ifStatement = PsiTreeUtil.getParentOfType(element, PyIfStatement.class);
		PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

		PyIfStatement subIf = (PyIfStatement) ifStatement.copy();

		subIf.getIfPart().getCondition().replace(element.getRightExpression());
		ifStatement.getIfPart().getCondition().replace(element.getLeftExpression());
		PyStatementList statementList = elementGenerator.createFromText(LanguageLevel.getDefault(), PyIfStatement.class, "if a:\n    a = 1").getIfPart().getStatementList();
		statementList.getStatements()[0].replace(subIf);
		PyIfStatement newIf = elementGenerator.createFromText(LanguageLevel.getDefault(), PyIfStatement.class, "if a:\n    a = 1");
		newIf.getIfPart().getCondition().replace(ifStatement.getIfPart().getCondition());
		newIf.getIfPart().getStatementList().replace(statementList);
		for(PyIfPart elif : ifStatement.getElifParts())
		{
			newIf.add(elif);
		}
		if(ifStatement.getElsePart() != null)
		{
			newIf.add(ifStatement.getElsePart());
		}
		ifStatement.replace(newIf);
	}
}
