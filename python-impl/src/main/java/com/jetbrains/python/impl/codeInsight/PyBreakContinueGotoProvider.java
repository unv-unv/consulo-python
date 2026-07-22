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
package com.jetbrains.python.impl.codeInsight;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.editor.navigation.GotoDeclarationHandlerBase;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;

/**
 * Provides reaction on ctrl+click for {@code break} and {@code continue} statements.
 * @author dcheryasov
 * @since 2009-11-05
 */
@ExtensionImpl
public class PyBreakContinueGotoProvider extends GotoDeclarationHandlerBase {

  @Override
  @RequiredReadAction
  public PsiElement getGotoDeclarationTarget(PsiElement source, Editor editor) {
    if (source.getLanguage() instanceof PythonLanguage) {
      PyLoopStatement loop = PsiTreeUtil.getParentOfType(source, PyLoopStatement.class, false, PyFunction.class, PyClass.class);
      if (loop != null) {
        ASTNode node = source.getNode();
        if (node != null) {
          IElementType node_type = node.getElementType();
          if (node_type == PyTokenTypes.CONTINUE_KEYWORD) {
            return loop;
          }
          if (node_type == PyTokenTypes.BREAK_KEYWORD) {
            PsiElement outer_element = loop;
            PsiElement after_cycle;
            while (true) {
              after_cycle = outer_element.getNextSibling();
              if (after_cycle != null) {
                if (after_cycle instanceof PsiWhiteSpace) {
                  after_cycle = after_cycle.getNextSibling();
                }
                if (after_cycle instanceof PyStatement) return after_cycle;
              }
              outer_element = outer_element.getParent();
              if (PyUtil.instanceOf(outer_element, PsiFile.class, PyFunction.class, PyClass.class)) {
                break;
              }
            }
            // cycle is the last statement in the flow of execution. its last element is our best bet.
            return PsiTreeUtil.getDeepestLast(loop);
          }
        }
      }
    }
    return null;
  }
}
