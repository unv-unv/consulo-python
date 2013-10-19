package com.jetbrains.python.codeInsight.completion;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;

/**
 * @author yole
 */
public class PyClassInsertHandler implements InsertHandler<LookupElement> {
  public static PyClassInsertHandler INSTANCE = new PyClassInsertHandler();
  
  private PyClassInsertHandler() {
  }

  public void handleInsert(InsertionContext context, LookupElement item) {
    final Editor editor = context.getEditor();
    final Document document = editor.getDocument();
    if (context.getCompletionChar() == '(') {
      context.setAddCompletionChar(false);
      final int offset = context.getTailOffset();
      document.insertString(offset, "()");

      PyClass pyClass = (PyClass) item.getObject();
      PyFunction init = pyClass.findInitOrNew(true);
      if (init != null && PyFunctionInsertHandler.hasParams(context, init)) {
        editor.getCaretModel().moveToOffset(offset+1);
        AutoPopupController.getInstance(context.getProject()).autoPopupParameterInfo(context.getEditor(), init);
      }
      else {
        editor.getCaretModel().moveToOffset(offset+2);
      }
    }
  }
}