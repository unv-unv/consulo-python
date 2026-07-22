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
package com.jetbrains.python.psi.types;

import java.util.List;
import java.util.Set;

import consulo.annotation.access.RequiredReadAction;
import org.jspecify.annotations.Nullable;
import consulo.util.dataholder.Key;
import consulo.language.psi.PsiElement;
import consulo.language.util.ProcessingContext;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;

/**
 * Represents a type of an expression.
 *
 * @author yole
 */
public interface PyType {
    /**
     * Resolves an attribute of type.
     *
     * @param name           attribute name
     * @param location       the expression of type qualifierType on which the member is being resolved (optional)
     * @param direction
     * @param resolveContext
     * @return null if name definitely cannot be found (e.g. in a qualified reference),
     * or an empty list if name is not found but other contexts are worth looking at,
     * or a list of elements that define the name, a la multiResolve().
     */
    @Nullable
    List<? extends RatedResolveResult> resolveMember(
        String name,
        @Nullable PyExpression location,
        AccessDirection direction,
        PyResolveContext resolveContext
    );

    /**
     * Proposes completion variants from type's attributes.
     *
     * @param location the reference on which the completion was invoked
     * @param context  to share state between nested invocations
     * @return completion variants good for {@link com.intellij.psi.PsiReference#getVariants} return value.
     */
    Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context);

    /**
     * Context key for access to a set of names already found by variant search.
     */
    Key<Set<String>> CTX_NAMES = new Key<>("Completion variants names");

    /**
     * @return name of the type
     */
    @Nullable
    @RequiredReadAction
    String getName();

    /**
     * @return true if the type is a known built-in type.
     */
    boolean isBuiltin();

    void assertValid(String message);
}
