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

package com.jetbrains.python.debugger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.impl.PyExpressionCodeFragmentImpl;
import com.jetbrains.python.psi.impl.PyPsiUtils;


public class PyDebuggerEditorsProvider extends XDebuggerEditorsProvider {

  @Nonnull
  @Override
  public FileType getFileType() {
    return PythonFileType.INSTANCE;
  }

  @Nonnull
  @Override
  public Document createDocument(@Nonnull final Project project,
                                 @Nonnull String text,
                                 @Nullable final XSourcePosition sourcePosition,
                                 @Nonnull EvaluationMode mode) {
    text = text.trim();
    final PyExpressionCodeFragmentImpl fragment = new PyExpressionCodeFragmentImpl(project, "fragment.py", text, true);

    // Bind to context
    final PsiElement element = getContextElement(project, sourcePosition);
    fragment.setContext(element);

    return PsiDocumentManager.getInstance(project).getDocument(fragment);
  }

  @Nullable
  private static PsiElement getContextElement(final Project project, XSourcePosition sourcePosition) {
    if (sourcePosition != null) {
      final Document document = FileDocumentManager.getInstance().getDocument(sourcePosition.getFile());
      final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
      if (psiFile != null) {
        int offset = sourcePosition.getOffset();
        if (offset >= 0 && offset < document.getTextLength()) {
          final int lineEndOffset = document.getLineEndOffset(document.getLineNumber(offset));
          do {
            PsiElement element = psiFile.findElementAt(offset);
            if (element != null && !(element instanceof PsiWhiteSpace || element instanceof PsiComment)) {
              return PyPsiUtils.getStatement(element);
            }
            offset = element.getTextRange().getEndOffset() + 1;
          }
          while (offset < lineEndOffset);
        }
      }
    }
    return null;
  }
}
