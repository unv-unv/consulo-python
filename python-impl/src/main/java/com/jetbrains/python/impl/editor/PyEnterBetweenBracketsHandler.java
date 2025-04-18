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
package com.jetbrains.python.impl.editor;

import com.jetbrains.python.PythonLanguage;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.codeEditor.action.EditorActionHandler;
import consulo.dataContext.DataContext;
import consulo.ide.impl.idea.codeInsight.editorActions.enter.EnterBetweenBracesHandler;
import consulo.language.psi.PsiFile;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;

/**
 * @author yole
 */
@ExtensionImpl
public class PyEnterBetweenBracketsHandler extends EnterBetweenBracesHandler {
    @Override
    @RequiredReadAction
    public Result preprocessEnter(
        @Nonnull PsiFile file,
        @Nonnull Editor editor,
        @Nonnull SimpleReference<Integer> caretOffsetRef,
        @Nonnull SimpleReference<Integer> caretAdvance,
        @Nonnull DataContext dataContext,
        EditorActionHandler originalHandler
    ) {
        if (!file.getLanguage().is(PythonLanguage.getInstance())) {
            return Result.Continue;
        }
        return super.preprocessEnter(file, editor, caretOffsetRef, caretAdvance, dataContext, originalHandler);
    }

    @Override
    protected boolean isBracePair(char c1, char c2) {
        return c1 == '[' && c2 == ']';
    }
}
