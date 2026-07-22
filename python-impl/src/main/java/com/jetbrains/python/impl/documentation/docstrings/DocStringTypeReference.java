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
package com.jetbrains.python.impl.documentation.docstrings;

import com.google.common.collect.Lists;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.ResolveResultList;
import com.jetbrains.python.impl.psi.resolve.ImportedResolveResult;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.impl.psi.types.PyClassTypeImpl;
import com.jetbrains.python.impl.psi.types.PyImportedModuleType;
import com.jetbrains.python.impl.psi.types.PyModuleType;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.document.util.TextRange;
import consulo.language.editor.completion.CompletionUtilCore;
import consulo.language.psi.*;
import consulo.language.psi.util.QualifiedName;
import consulo.language.util.IncorrectOperationException;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * @author catherine
 */
public class DocStringTypeReference extends PsiPolyVariantReferenceBase<PsiElement>
{
	@Nullable
	private PyType myType;
	private TextRange myFullRange;
	@Nullable
	private final PyImportElement myImportElement;

	public DocStringTypeReference(PsiElement element, TextRange range, TextRange fullRange, @Nullable PyType type, @Nullable PyImportElement importElement)
	{
		super(element, range);
		myFullRange = fullRange;
		myType = type;
		myImportElement = importElement;
	}

    @Nullable
	@Override
    @RequiredWriteAction
	public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException
	{
		if(element.equals(resolve()))
		{
			return element;
		}
		if(myElement instanceof PyStringLiteralExpression e && element instanceof PyClass cls)
		{
			QualifiedName qName = QualifiedNameFinder.findCanonicalImportPath(cls, element);
			if(qName != null)
			{
				qName = qName.append(cls.getName());
				ElementManipulator<PyStringLiteralExpression> manipulator = ElementManipulators.getManipulator(e);
				myType = new PyClassTypeImpl(cls, false);
				return manipulator.handleContentChange(e, myFullRange, qName.toString());
			}
		}
		return null;
	}

	@Override
    public boolean isSoft()
	{
		return false;
	}

	@Override
    @RequiredWriteAction
	public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException
	{
		newElementName = StringUtil.trimEnd(newElementName, PyNames.DOT_PY);
		return super.handleElementRename(newElementName);
	}

	@Override
    @RequiredReadAction
	public boolean isReferenceTo(PsiElement element)
	{
		if(myType instanceof PyImportedModuleType)
		{
			return element.equals(PyUtil.turnInitIntoDir(resolve()));
		}
		return super.isReferenceTo(element);
	}

	@Override
    @RequiredReadAction
	public ResolveResult[] multiResolve(boolean incompleteCode)
	{
		PsiElement result = null;
		ResolveResultList results = new ResolveResultList();
		if(myType instanceof PyClassType classType)
		{
			result = classType.getPyClass();
		}
		else if(myType instanceof PyImportedModuleType importedModuleType)
		{
			result = importedModuleType.getImportedModule().resolve();
		}
		else if(myType instanceof PyModuleType moduleType)
		{
			result = moduleType.getModule();
		}
		if(result != null)
		{
			if(myImportElement != null)
			{
				results.add(new ImportedResolveResult(result, RatedResolveResult.RATE_NORMAL, myImportElement));
			}
			else
			{
				results.poke(result, RatedResolveResult.RATE_NORMAL);
			}
		}
		return results.toArray(ResolveResult.EMPTY_ARRAY);
	}

	@Override
    @RequiredReadAction
	public Object[] getVariants()
	{
		// see PyDocstringCompletionContributor
		return ArrayUtil.EMPTY_OBJECT_ARRAY;
	}

	@RequiredReadAction
    public List<Object> collectTypeVariants()
	{
        List<Object> variants = Lists.<Object>newArrayList(
		    "str", "int", "basestring", "bool", "buffer", "bytearray", "complex", "dict", "tuple", "enumerate", "file", "float",
			"frozenset", "list", "long", "set", "object"
        );
		if(myElement.getContainingFile() instanceof PyFile file)
		{
			variants.addAll(file.getTopLevelClasses());
			List<PyFromImportStatement> fromImports = file.getFromImports();
			for(PyFromImportStatement fromImportStatement : fromImports)
			{
				PyImportElement[] elements = fromImportStatement.getImportElements();
				for(PyImportElement element : elements)
				{
					PyReferenceExpression referenceExpression = element.getImportReferenceExpression();
					if(referenceExpression == null)
					{
						continue;
					}
					PyType type = TypeEvalContext.userInitiated(file.getProject(), CompletionUtilCore.getOriginalOrSelf(file)).getType(referenceExpression);
					if(type instanceof PyClassType classType)
					{
						variants.add(classType.getPyClass());
					}
				}
			}
		}
		return variants;
	}
}
