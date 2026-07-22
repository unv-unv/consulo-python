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
package com.jetbrains.python.impl.codeInsight.stdlib;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import consulo.annotation.access.RequiredReadAction;
import org.jspecify.annotations.Nullable;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ArrayUtil;
import consulo.language.util.ProcessingContext;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyCallSiteExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.impl.psi.impl.PyElementImpl;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.impl.psi.types.PyClassTypeImpl;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;

/**
 * @author yole
 */
public class PyNamedTupleType extends PyClassTypeImpl implements PyCallableType
{
	private final String myName;

	// 2 - namedtuple call itself
	// 1 - return type of namedtuple call, aka namedtuple class
	// 0 - namedtuple instance
	private final int myDefinitionLevel;
	private final PsiElement myDeclaration;
	private final List<String> myFields;

	public PyNamedTupleType(PyClass tupleClass, PsiElement declaration, String name, List<String> fields, int definitionLevel)
	{
		super(tupleClass, definitionLevel > 0);
		myDeclaration = declaration;
		myFields = fields;
		myName = name;
		myDefinitionLevel = definitionLevel;
	}

	@Nullable
	@Override
	public List<? extends RatedResolveResult> resolveMember(String name,
			@Nullable PyExpression location,
			AccessDirection direction,
			PyResolveContext resolveContext,
			boolean inherited)
	{
		List<? extends RatedResolveResult> classMembers = super.resolveMember(name, location, direction, resolveContext, inherited);
		if(classMembers != null && !classMembers.isEmpty())
		{
			return classMembers;
		}
		if(myFields.contains(name))
		{
			// It's important to make a copy of declaration otherwise members will have the same type as their class
			return Collections.singletonList(new RatedResolveResult(RatedResolveResult.RATE_HIGH, new PyElementImpl(myDeclaration.getNode())));
		}
		return null;
	}

	@Override
	public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context)
	{
		List<Object> result = new ArrayList<>();
		Collections.addAll(result, super.getCompletionVariants(completionPrefix, location, context));
		for(String field : myFields)
		{
			result.add(LookupElementBuilder.create(field));
		}
		return ArrayUtil.toObjectArray(result);
	}

	@Override
    @RequiredReadAction
	public String getName()
	{
		return myName;
	}

	@Override
	public boolean isBuiltin()
	{
		return false;
	}

	@Nullable
	@Override
	public PyType getCallType(TypeEvalContext context, PyCallSiteExpression callSite)
	{
		if(myDefinitionLevel > 0)
		{
			return new PyNamedTupleType(myClass, myDeclaration, myName, myFields, myDefinitionLevel - 1);
		}
		return null;
	}

	@Override
	public PyClassType toInstance()
	{
		return myDefinitionLevel == 1 ? new PyNamedTupleType(myClass, myDeclaration, myName, myFields, 0) : this;
	}

	@Override
	public String toString()
	{
		return "PyNamedTupleType: " + myName;
	}

	@Override
    @RequiredReadAction
	public Set<String> getMemberNames(boolean inherited, TypeEvalContext context)
	{
		Set<String> result = super.getMemberNames(inherited, context);
		result.addAll(myFields);

		return result;
	}

	public int getElementCount()
	{
		return myFields.size();
	}

	public List<String> getElementNames()
	{
		return Collections.unmodifiableList(myFields);
	}
}
