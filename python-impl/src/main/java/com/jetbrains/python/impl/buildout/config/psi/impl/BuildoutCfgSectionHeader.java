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
package com.jetbrains.python.impl.buildout.config.psi.impl;

import consulo.annotation.access.RequiredReadAction;
import consulo.language.ast.ASTNode;
import org.jspecify.annotations.Nullable;

/**
 * @author traff
 */
public class BuildoutCfgSectionHeader extends BuildoutCfgPsiElement {
  public BuildoutCfgSectionHeader(ASTNode node) {
    super(node);
  }

  @Nullable
  @Override
  @RequiredReadAction
  public String getName() {
    String name = getText().trim();
    if (name.startsWith("[") && name.endsWith("]")) {
      return name.substring(1, name.length()-1).trim();
    }
    return name;
  }

  @Override
  @RequiredReadAction
  public String toString() {
    return "BuildoutCfgSectionHeader:" + getNode().getElementType().toString();
  }
}
