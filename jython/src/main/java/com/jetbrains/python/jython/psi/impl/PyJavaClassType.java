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
package com.jetbrains.python.jython.psi.impl;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMethod;
import com.jetbrains.python.impl.psi.impl.ResolveResultList;
import com.jetbrains.python.impl.psi.resolve.CompletionVariantsProcessor;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyCallSiteExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.util.ProcessingContext;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

/**
 * @author yole
 */
public class PyJavaClassType implements PyClassLikeType {
  private final PsiClass myClass;
  private final boolean myDefinition;

  public PyJavaClassType(PsiClass aClass, boolean definition) {
    myClass = aClass;
    myDefinition = definition;
  }

  @Nullable
  @Override
  public List<? extends RatedResolveResult> resolveMember(String name,
                                                          @Nullable PyExpression location,
                                                          AccessDirection direction,
                                                          PyResolveContext resolveContext) {
    return resolveMember(name, location, direction, resolveContext, true);
  }

  @Nullable
  @Override
  public List<? extends RatedResolveResult> resolveMember(String name,
                                                          @Nullable PyExpression location,
                                                          AccessDirection direction,
                                                          PyResolveContext resolveContext,
                                                          boolean inherited) {
    PsiMethod[] methods = myClass.findMethodsByName(name, inherited);
    if (methods.length > 0) {
      ResolveResultList resultList = new ResolveResultList();
      for (PsiMethod method : methods) {
        resultList.poke(method, RatedResolveResult.RATE_NORMAL);
      }
      return resultList;
    }
    PsiField field = myClass.findFieldByName(name, inherited);
    if (field != null) {
      return ResolveResultList.to(field);
    }
    return null;
  }

  @Override
  public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context) {
    CompletionVariantsProcessor processor = new CompletionVariantsProcessor(location);
    myClass.processDeclarations(processor, ResolveState.initial(), null, location);
    return processor.getResult();
  }

  @Override
  @RequiredReadAction
  public String getName() {
    return myClass != null ? myClass.getName() : null;
  }

  @Override
  public boolean isBuiltin() {
    return false;  // TODO: JDK's types could be considered built-in.
  }

  @Override
  public void assertValid(String message) {
  }

  @Override
  public boolean isCallable() {
    return myDefinition;
  }

  @Nullable
  @Override
  public PyType getReturnType(TypeEvalContext context) {
    if (myDefinition) {
      return new PyJavaClassType(myClass, false);
    }
    return null;
  }

  @Nullable
  @Override
  public PyType getCallType(TypeEvalContext context, PyCallSiteExpression callSite) {
    return getReturnType(context);
  }

  @Nullable
  @Override
  public List<PyCallableParameter> getParameters(TypeEvalContext context) {
    return null;
  }

  @Override
  public boolean isDefinition() {
    return myDefinition;
  }

  @Override
  public PyClassLikeType toInstance() {
    return myDefinition ? new PyJavaClassType(myClass, false) : this;
  }

  @Nullable
  @Override
  public String getClassQName() {
    return myClass.getQualifiedName();
  }

  @Override
  public List<PyClassLikeType> getSuperClassTypes(TypeEvalContext context) {
    List<PyClassLikeType> result = new ArrayList<>();
    for (PsiClass cls : myClass.getSupers()) {
      result.add(new PyJavaClassType(cls, myDefinition));
    }
    return result;
  }

  @Override
  public void visitMembers(Predicate<PsiElement> processor, boolean inherited, TypeEvalContext context) {
    for (PsiMethod method : myClass.getAllMethods()) {
      processor.test(method);
    }

    for (PsiField field : myClass.getAllFields()) {
      processor.test(field);
    }

    if (!inherited) {
      return;
    }

    for (PyClassLikeType type : getAncestorTypes(context)) {
      if (type != null) {
        type.visitMembers(processor, false, context);
      }
    }
  }

  @Override
  @RequiredReadAction
  public Set<String> getMemberNames(boolean inherited, TypeEvalContext context) {
    Set<String> result = new LinkedHashSet<>();

    for (PsiMethod method : myClass.getAllMethods()) {
      result.add(method.getName());
    }

    for (PsiField field : myClass.getAllFields()) {
      result.add(field.getName());
    }

    if (inherited) {
      for (PyClassLikeType type : getAncestorTypes(context)) {
        if (type != null) {
          result.addAll(type.getMemberNames(false, context));
        }
      }
    }

    return result;
  }

  @Override
  public List<PyClassLikeType> getAncestorTypes(TypeEvalContext context) {
    List<PyClassLikeType> result = new ArrayList<>();

    Deque<PsiClass> deque = new LinkedList<>();
    Set<PsiClass> visited = new HashSet<>();

    deque.addAll(Arrays.asList(myClass.getSupers()));

    while (!deque.isEmpty()) {
      PsiClass current = deque.pollFirst();

      if (current == null || !visited.add(current)) {
        continue;
      }

      result.add(new PyJavaClassType(current, myDefinition));

      deque.addAll(Arrays.asList(current.getSupers()));
    }

    return result;
  }

  @Override
  @RequiredReadAction
  public boolean isValid() {
    return myClass.isValid();
  }

  @Nullable
  @Override
  public PyClassLikeType getMetaClassType(TypeEvalContext context, boolean inherited) {
    return null;
  }

  public PsiClass getPsiClass() {
    return myClass;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    return o instanceof PyJavaClassType type
      && myDefinition == type.myDefinition
      && Objects.equals(myClass, type.myClass);
  }

  @Override
  public int hashCode() {
    int result = myClass != null ? myClass.hashCode() : 0;
    result = 31 * result + (myDefinition ? 1 : 0);
    return result;
  }
}
