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
package com.jetbrains.python.impl.psi.impl.stubs;

import com.jetbrains.python.impl.PyElementTypes;
import com.jetbrains.python.impl.psi.PyFileElementType;
import com.jetbrains.python.impl.psi.impl.PyClassImpl;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.impl.psi.stubs.PyClassAttributesIndex;
import com.jetbrains.python.impl.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.impl.psi.stubs.PyClassNameIndexInsensitive;
import com.jetbrains.python.impl.psi.stubs.PySuperClassIndex;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.stubs.PyClassStub;
import consulo.annotation.access.RequiredReadAction;
import consulo.index.io.StringRef;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.stub.*;
import consulo.language.psi.util.QualifiedName;

import org.jspecify.annotations.Nullable;
import java.io.IOException;
import java.util.*;

/**
 * @author max
 */
public class PyClassElementType extends PyStubElementType<PyClassStub, PyClass>
{

	public PyClassElementType()
	{
		this("CLASS_DECLARATION");
	}

	public PyClassElementType(String debugName)
	{
		super(debugName);
	}

	@Override
    public PsiElement createElement(ASTNode node)
	{
		return new PyClassImpl(node);
	}

	@Override
    public PyClass createPsi(PyClassStub stub)
	{
		return new PyClassImpl(stub);
	}

	@Override
    @RequiredReadAction
    public PyClassStub createStub(PyClass psi, StubElement parentStub)
	{
        return new PyClassStubImpl(
            psi.getName(),
            parentStub,
            getSuperClassQNames(psi),
            PyPsiUtils.toQualifiedName(psi.getMetaClassExpression()),
            psi.getOwnSlots(),
            PyPsiUtils.strValue(psi.getDocStringExpression()),
            getStubElementType()
        );
	}

	public static Map<QualifiedName, QualifiedName> getSuperClassQNames(PyClass pyClass)
	{
		Map<QualifiedName, QualifiedName> result = new LinkedHashMap<>();

		Arrays.stream(pyClass.getSuperClassExpressions()).filter(expression -> !PyKeywordArgument.class.isInstance(expression)).map(PyClassImpl::unfoldClass).forEach(expression -> result.put
				(PyPsiUtils.toQualifiedName(expression), resolveOriginalSuperClassQName(expression)));

		return result;
	}

	@Nullable
	private static QualifiedName resolveOriginalSuperClassQName(PyExpression superClassExpression)
	{
		if(superClassExpression instanceof PyReferenceExpression)
		{
			PyReferenceExpression reference = (PyReferenceExpression) superClassExpression;
			String referenceName = reference.getName();

			if(referenceName == null)
			{
				return PyPsiUtils.toQualifiedName(superClassExpression);
			}

			Optional<QualifiedName> qualifiedName = PyResolveUtil.resolveLocally(reference).stream().filter(PyImportElement.class::isInstance).map(PyImportElement.class::cast).filter(element
					-> element.getAsName() != null).map(PyImportElement::getImportedQName).findAny();

			if(qualifiedName.isPresent())
			{
				return qualifiedName.get();
			}
		}

		return PyPsiUtils.toQualifiedName(superClassExpression);
	}

	@Override
    public void serialize(PyClassStub pyClassStub, StubOutputStream dataStream) throws IOException
	{
		dataStream.writeName(pyClassStub.getName());

		Map<QualifiedName, QualifiedName> superClasses = pyClassStub.getSuperClasses();
		dataStream.writeByte(superClasses.size());
		for(Map.Entry<QualifiedName, QualifiedName> entry : superClasses.entrySet())
		{
			QualifiedName.serialize(entry.getKey(), dataStream);
			QualifiedName.serialize(entry.getValue(), dataStream);
		}

		QualifiedName.serialize(pyClassStub.getMetaClass(), dataStream);

		PyFileElementType.writeNullableList(dataStream, pyClassStub.getSlots());

		String docString = pyClassStub.getDocString();
		dataStream.writeUTFFast(docString != null ? docString : "");
	}

	@Override
    public PyClassStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException
	{
		String name = StringRef.toString(dataStream.readName());

		int superClassCount = dataStream.readByte();
		Map<QualifiedName, QualifiedName> superClasses = new LinkedHashMap<>();
		for(int i = 0; i < superClassCount; i++)
		{
			superClasses.put(QualifiedName.deserialize(dataStream), QualifiedName.deserialize(dataStream));
		}

		QualifiedName metaClass = QualifiedName.deserialize(dataStream);

		List<String> slots = PyFileElementType.readNullableList(dataStream);

		String docStringInStub = dataStream.readUTFFast();
		String docString = docStringInStub.length() > 0 ? docStringInStub : null;

		return new PyClassStubImpl(name, parentStub, superClasses, metaClass, slots, docString, getStubElementType());
	}

	@Override
    public void indexStub(PyClassStub stub, IndexSink sink)
	{
		String name = stub.getName();
		if(name != null)
		{
			sink.occurrence(PyClassNameIndex.KEY, name);
			sink.occurrence(PyClassNameIndexInsensitive.KEY, name.toLowerCase());
		}

		for(String attribute : PyClassAttributesIndex.getAllDeclaredAttributeNames(createPsi(stub)))
		{
			sink.occurrence(PyClassAttributesIndex.KEY, attribute);
		}

		stub.getSuperClasses().values().stream().filter(Objects::nonNull).map(QualifiedName::getLastComponent).filter(Objects::nonNull).forEach(className -> sink.occurrence(PySuperClassIndex.KEY,
				className));
	}

	protected IStubElementType getStubElementType()
	{
		return PyElementTypes.CLASS_DECLARATION;
	}
}