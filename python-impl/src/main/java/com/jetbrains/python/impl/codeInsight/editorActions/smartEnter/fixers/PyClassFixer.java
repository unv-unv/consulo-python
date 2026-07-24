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
package com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.fixers;

import static com.jetbrains.python.impl.psi.PyUtil.sure;

import consulo.annotation.access.RequiredWriteAction;
import consulo.codeEditor.Editor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.language.util.IncorrectOperationException;

/**
 * @author Alexey.Ivanov
 * @since 2010-04-16
 */
public class PyClassFixer extends PyFixer<PyClass> {
    public PyClassFixer() {
        super(PyClass.class);
    }

    @Override
    @RequiredWriteAction
    public void doApply(Editor editor, PySmartEnterProcessor processor, PyClass pyClass) throws IncorrectOperationException {
        PsiElement colon = PyPsiUtils.getFirstChildOfType(pyClass, PyTokenTypes.COLON);
        if (colon == null) {
            PyArgumentList argList = PsiTreeUtil.getChildOfType(pyClass, PyArgumentList.class);
            int colonOffset = sure(argList).getTextRange().getEndOffset();
            String textToInsert = ":";
            if (pyClass.getNameNode() == null) {
                int newCaretOffset = argList.getTextOffset();
                if (argList.getTextLength() == 0) {
                    newCaretOffset += 1;
                    textToInsert = " :";
                }
                processor.registerUnresolvedError(newCaretOffset);
            }
            editor.getDocument().insertString(colonOffset, textToInsert);
        }
    }
}
