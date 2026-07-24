/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.impl.psi.impl;

import consulo.language.Language;
import consulo.language.impl.psi.LightElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiNamedElement;
import com.jetbrains.python.psi.PyElement;
import consulo.language.util.IncorrectOperationException;

/**
 * @author yole
 */
public class LightNamedElement extends LightElement implements PyElement, PsiNamedElement {
  protected final String myName;

  public LightNamedElement(PsiManager manager, Language language, String name) {
    super(manager, language);
    myName = name;
  }

  @Override
  public String getText() {
    return myName;
  }

  @Override
  public void accept(PsiElementVisitor visitor) {
    visitor.visitElement(this);
  }

  @Override
  public PsiElement copy() {
    return null;
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public PsiElement setName(String name) throws IncorrectOperationException
  {
    throw new UnsupportedOperationException("LightNamedElement#setName() is not supported");
  }

  @Override
  public String toString() {
    return "LightNamedElement(" + myName + ")";
  }
}
