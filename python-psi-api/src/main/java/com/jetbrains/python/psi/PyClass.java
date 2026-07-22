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
package com.jetbrains.python.psi;

import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.stubs.PyClassStub;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.language.psi.StubBasedPsiElement;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.util.collection.ArrayFactory;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Represents a class declaration in source.
 */
public interface PyClass extends PsiNameIdentifierOwner, PyStatement, PyDocStringOwner, StubBasedPsiElement<PyClassStub>, ScopeOwner, PyDecoratable, PyTypedElement, PyQualifiedNameOwner,
		PyStatementListContainer, PyWithAncestors
{
	ArrayFactory<PyClass> ARRAY_FACTORY = PyClass[]::new;

	@Nullable
	ASTNode getNameNode();


	/**
	 * Returns only those ancestors from the hierarchy, that are resolved to PyClass PSI elements.
	 *
	 * @param context type eval context (pass null to use loose, but better provide one)
	 * @see #getAncestorTypes(TypeEvalContext) for the full list of ancestors.
	 */
	List<PyClass> getAncestorClasses(@Nullable TypeEvalContext context);

	/**
	 * Returns types of expressions in the super classes list.
	 * <p>
	 * If no super classes are specified, returns the type of the implicit super class for old- and new-style classes.
	 *
	 * @see #getAncestorTypes(TypeEvalContext) for the full list of ancestors.
	 */
	List<PyClassLikeType> getSuperClassTypes(TypeEvalContext context);

	/**
	 * Returns only those super classes for expressions from the super classes list, that are resolved to PyClass PSI elements.
	 * <p>
	 * If no super classes are specified, returns the implicit super class for old- and new-style classes.
	 *
	 * @param context
	 * @see #getSuperClassTypes(TypeEvalContext) for the full list of super classes.
	 * @see #getAncestorTypes(TypeEvalContext) for the full list of ancestors.
	 */
	PyClass[] getSuperClasses(@Nullable TypeEvalContext context);

	/**
	 * Returns a PSI element for the super classes list.
	 * <p>
	 * Operates at the AST level.
	 */
	@Nullable
	PyArgumentList getSuperClassExpressionList();

	/**
	 * Returns PSI elements for the expressions in the super classes list.
	 * <p>
	 * Operates at the AST level.
	 */
	PyExpression[] getSuperClassExpressions();

	/**
	 * Collects methods defined in the class.
	 * <p>
	 * This method does not access AST if underlying PSI is stub based.
	 *
	 * @return class methods
	 */
	PyFunction[] getMethods();

	/**
	 * Get class properties.
	 *
	 * @return Map [property_name] = [{@link com.jetbrains.python.psi.Property}]
	 */
	Map<String, Property> getProperties();

	/**
	 * Finds a method with given name.
	 *
	 * @param name      what to look for
	 * @param inherited true: search in superclasses; false: only look for methods defined in this class.
	 * @param context
	 * @return
	 */
	@Nullable
	PyFunction findMethodByName(@Nullable String name, boolean inherited, TypeEvalContext context);

	/**
	 * Finds either __init__ or __new__, whichever is defined for given class.
	 * If __init__ is defined, it is found first. This mimics the way initialization methods
	 * are searched for and called by Python when a constructor call is made.
	 * Since __new__ only makes sense for new-style classes, an old-style class never finds it with this method.
	 *
	 * @param inherited true: search in superclasses, too.
	 * @param context   TODO: DOC
	 * @return a method that would be called first when an instance of this class is instantiated.
	 */
	@Nullable
	PyFunction findInitOrNew(boolean inherited, @Nullable TypeEvalContext context);

	/**
	 * Finds a property with the specified name in the class or one of its ancestors.
	 *
	 * @param name      of the property
	 * @param inherited
	 * @param context   type eval (null to use loose context, but you better provide one)
	 * @return descriptor of property accessors, or null if such property does not exist.
	 */
	@Nullable
	Property findProperty(String name, boolean inherited, @Nullable TypeEvalContext context);

	/**
	 * Apply a processor to every method, looking at superclasses in method resolution order as needed.
	 * Consider using {@link PyClassLikeType#visitMembers(Predicate, boolean, TypeEvalContext)}
	 *
	 * @param processor what to apply
	 * @param inherited true: search in superclasses, too.
	 * @param context   loose context will be used if no context provided
	 * @see PyClassLikeType#visitMembers(Predicate, boolean, TypeEvalContext)
	 */
	boolean visitMethods(Predicate<PyFunction> processor, boolean inherited, @Nullable TypeEvalContext context);

	/**
	 * Consider using {@link PyClassLikeType#visitMembers(Predicate, boolean, TypeEvalContext)}
	 *
	 * @see PyClassLikeType#visitMembers(Predicate, boolean, TypeEvalContext)
	 */
	boolean visitClassAttributes(Predicate<PyTargetExpression> processor, boolean inherited, TypeEvalContext context);

	/**
	 * Effectively collects assignments inside the class body.
	 * <p>
	 * This method does not access AST if underlying PSI is stub based.
	 * Note that only <strong>own</strong> attrs are fetched, not parent attrs.
	 * If you need parent attributes, consider using {@link #getClassAttributesInherited(TypeEvalContext)}
	 *
	 * @see #getClassAttributesInherited(TypeEvalContext)
	 */
	List<PyTargetExpression> getClassAttributes();


	/**
	 * Returns all class attributes this class class contains, including inherited one.
	 * Process may be heavy, depending or your context.
	 *
	 * @param context context to use for this process
	 * @return list of attrs.
	 * <p>
	 * TODO: Replace it and {@link #getClassAttributes()} with a single getClassAttributes(@NotNull TypeEvalContext context, boolean inherited)
	 */
	List<PyTargetExpression> getClassAttributesInherited(TypeEvalContext context);

	@Nullable
	PyTargetExpression findClassAttribute(String name, boolean inherited, TypeEvalContext context);

	/**
	 * Effectively collects assignments to attributes of {@code self} in {@code __init__}, {@code __new__} and
	 * other methods defined in the class.
	 * <p>
	 * This method does not access AST if underlying PSI is stub based.
	 */
	List<PyTargetExpression> getInstanceAttributes();

	@Nullable
	PyTargetExpression findInstanceAttribute(String name, boolean inherited);

	PyClass[] getNestedClasses();

	@Nullable
	PyClass findNestedClass(String name, boolean inherited);

	/**
	 * @param context
	 * @return true if the class is new-style and descends from 'object'.
	 */
	boolean isNewStyleClass(TypeEvalContext context);

	/**
	 * Scan properties in order of definition, until processor returns true for one of them.
	 *
	 * @param processor to check properties
	 * @param inherited whether inherited properties need to be scanned, too
	 * @return a property that processor accepted, or null.
	 */
	@Nullable
	Property scanProperties(Predicate<Property> processor, boolean inherited);

	/**
	 * Non-recursively searches for a property for which the given function is a getter, setter or deleter.
	 *
	 * @param callable the function which may be an accessor
	 * @return the property, or null
	 */
	@Nullable
	Property findPropertyByCallable(PyCallable callable);

	/**
	 * @param parent
	 * @return True iff this and parent are the same or parent is one of our superclasses.
	 */
	boolean isSubclass(PyClass parent, @Nullable TypeEvalContext context);

	boolean isSubclass(String superClassQName, @Nullable TypeEvalContext context);

	/**
	 * Returns the aggregated list of names defined in __slots__ attributes of the class and its ancestors.
	 *
	 * @param context (will be used default if null)
	 */
	@Nullable
	List<String> getSlots(@Nullable TypeEvalContext context);

	/**
	 * Returns the list of names in the class' __slots__ attribute, or null if the class
	 * does not define such an attribute.
	 *
	 * @return the list of names or null.
	 */
	@Nullable
	List<String> getOwnSlots();

	@Nullable
    @Override
	String getDocStringValue();

	boolean processClassLevelDeclarations(PsiScopeProcessor processor);

	boolean processInstanceLevelDeclarations(PsiScopeProcessor processor, @Nullable PsiElement location);

	//TODO: Add "addMetaClass" or move methods out of here

	/**
	 * Returns the type representing the metaclass of the class if it is explicitly set, null otherwise.
	 * <p>
	 * The metaclass might be defined outside the class in case of Python 2 file-level __metaclass__ attributes.
	 */
	@Nullable
	PyType getMetaClassType(TypeEvalContext context);

	/**
	 * Returns the expression that defines the metaclass of the class.
	 * <p>
	 * Operates at the AST level.
	 */
	@Nullable
	PyExpression getMetaClassExpression();

	/**
	 * @param context eval context
	 * @return {@link com.jetbrains.python.psi.types.PyType} casted if it has right type
	 */
	@Nullable
	PyClassLikeType getType(TypeEvalContext context);
}
