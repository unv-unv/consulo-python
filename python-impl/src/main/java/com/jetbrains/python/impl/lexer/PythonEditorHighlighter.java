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

package com.jetbrains.python.impl.lexer;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonFileType;
import consulo.codeEditor.HighlighterClient;
import consulo.colorScheme.EditorColorsScheme;
import consulo.document.Document;
import consulo.document.event.DocumentEvent;
import consulo.document.util.TextRange;
import consulo.language.editor.highlight.LexerEditorHighlighter;
import consulo.language.editor.highlight.SyntaxHighlighterFactory;
import consulo.language.lexer.LayeredLexer;
import consulo.language.lexer.Lexer;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * User : catherine
 */
public class PythonEditorHighlighter extends LexerEditorHighlighter
{

  public PythonEditorHighlighter(@Nonnull EditorColorsScheme scheme, @Nullable Project project, @Nullable VirtualFile file) {
    super(SyntaxHighlighterFactory.getSyntaxHighlighter(file != null ? file.getFileType() : PythonFileType.INSTANCE,
                                                               project,
                                                               file),
          scheme);
  }

  private Boolean hadUnicodeImport = false;

  public static final Key<Boolean> KEY = new Key<Boolean>("python.future.import");
  @Override
  public void documentChanged(DocumentEvent e) {
    synchronized (this) {
      final Document document = e.getDocument();
      Lexer l = getLexer();
      // if the document been changed before "from __future__ import unicode_literals"
      // we should update the whole document
      if (l instanceof LayeredLexer) {
        Lexer delegate = ((LayeredLexer)l).getDelegate();
        int offset = e.getOffset();
        int lineNumber = document.getLineNumber(offset);
        TextRange tr = new TextRange(document.getLineStartOffset(lineNumber), document.getLineEndOffset(lineNumber));
        document.putUserData(KEY, document.getText(tr).indexOf(PyNames.UNICODE_LITERALS) == -1);
        Boolean hasUnicodeImport = document.getUserData(KEY);
        if (delegate instanceof PythonHighlightingLexer &&
            (((PythonHighlightingLexer)delegate).getImportOffset() > e.getOffset()
             || hasUnicodeImport != hadUnicodeImport)) {
          ((PythonHighlightingLexer)delegate).clearState(e.getDocument().getTextLength());
          setText(document.getCharsSequence());
        }
        else super.documentChanged(e);
      }
      else super.documentChanged(e);
    }
  }

  @Override
  public void beforeDocumentChange(DocumentEvent e) {
    final Document document = e.getDocument();
    hadUnicodeImport = document.getUserData(KEY);
  }

  @Override
  public void setEditor(HighlighterClient editor) {
    Lexer l = getLexer();
    if (l instanceof LayeredLexer) {
      editor.getDocument().putUserData(KEY, editor.getDocument().getText().indexOf(PyNames.UNICODE_LITERALS) == -1);
    }
    super.setEditor(editor);
  }
}
