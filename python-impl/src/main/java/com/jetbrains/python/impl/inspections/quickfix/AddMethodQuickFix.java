/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.ParamHelper;
import com.jetbrains.python.impl.psi.impl.PyFunctionBuilder;
import com.jetbrains.python.impl.psi.types.PyClassTypeImpl;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.codeEditor.Editor;
import consulo.fileEditor.FileEditorManager;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.editor.template.TemplateBuilderFactory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.navigation.OpenFileDescriptorFactory;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.NotificationType;
import consulo.virtualFileSystem.VirtualFile;

import static com.jetbrains.python.impl.psi.PyUtil.sure;

/**
 * Adds a method foo to class X if X.foo() is unresolved.
 *
 * @author dcheryasov
 * @since 2009-04-05
 */
public class AddMethodQuickFix implements LocalQuickFix {

  private final String myClassName;
  private final boolean myReplaceUsage;
  private String myIdentifier;

  public AddMethodQuickFix(String identifier, String className, boolean replaceUsage) {
    myIdentifier = identifier;
    myClassName = className;
    myReplaceUsage = replaceUsage;
  }

  @Override
  public LocalizeValue getName() {
    return PyLocalize.qfixNameAddMethod$0ToClass$1(myIdentifier, myClassName);
  }

  @Override
  @RequiredWriteAction
  public void applyFix(Project project, ProblemDescriptor descriptor) {
    try {
      // there can be no name clash, else the name would have resolved, and it hasn't.
      PsiElement problemElement = descriptor.getPsiElement();
      PyClassType type = getClassType(problemElement);
      if (type == null) {
        return;
      }
      PyClass cls = type.getPyClass();
      boolean callByClass = type.isDefinition();
      PyStatementList clsStmtList = cls.getStatementList();
      sure(FileModificationService.getInstance().preparePsiElementForWrite(clsStmtList));
      // try to at least match parameter count
      // TODO: get parameter style from code style
      PyFunctionBuilder builder = new PyFunctionBuilder(myIdentifier, cls);
        String decoratorName = null; // set to non-null to add a decorator
      PyExpression[] args = PyExpression.EMPTY_ARRAY;
      if (problemElement.getParent() instanceof PyCallExpression call) {
        PyArgumentList argList = call.getArgumentList();
        if (argList == null) {
          return;
        }
        args = argList.getArguments();
      }
      boolean madeInstance = false;
      if (callByClass) {
        if (args.length > 0) {
          TypeEvalContext context = TypeEvalContext.userInitiated(cls.getProject(), cls.getContainingFile());
            if (context.getType(args[0]) instanceof PyClassType firstArgType && firstArgType.getPyClass().isSubclass(cls, context)) {
            // class, first arg ok: instance method
            builder.parameter("self"); // NOTE: might use a name other than 'self', according to code style.
            madeInstance = true;
          }
        }
        if (!madeInstance) { // class, first arg absent or of different type: class-method
          builder.parameter("cls"); // NOTE: might use a name other than 'cls', according to code style.
          decoratorName = PyNames.CLASSMETHOD;
        }
      }
      else { // instance method
        builder.parameter("self"); // NOTE: might use a name other than 'self', according to code style.
      }
      boolean skipFirst = callByClass && madeInstance; // ClassFoo.meth(foo_instance)
      for (PyExpression arg : args) {
        if (skipFirst) {
          skipFirst = false;
          continue;
        }
        if (arg instanceof PyKeywordArgument kwArg) { // foo(bar) -> def foo(self, bar_1)
          builder.parameter(kwArg.getKeyword());
        }
        else if (arg instanceof PyReferenceExpression refEx) {
          builder.parameter(refEx.getReferencedName());
        }
        else { // use a boring name
          builder.parameter("param");
        }
      }
      PyFunction method = builder.buildFunction(project, LanguageLevel.getDefault());
      if (decoratorName != null) {
        PyElementGenerator generator = PyElementGenerator.getInstance(project);
        PyDecoratorList decoratorList =
          generator.createFromText(LanguageLevel.getDefault(), PyDecoratorList.class, "@" + decoratorName + "\ndef foo(): pass", new int[]{
            0,
            0
          });
        method.addBefore(decoratorList, method.getFirstChild()); // in the very beginning
      }

      method = (PyFunction)PyUtil.addElementToStatementList(method, clsStmtList, PyNames.INIT.equals(method.getName()));
      if (myReplaceUsage) {
        showTemplateBuilder(method);
      }
    }
    catch (IncorrectOperationException ignored) {
      // we failed. tell about this
      PyUtil.showBalloon(project, PyLocalize.qfixFailedToAddMethod(), NotificationType.ERROR);
    }
  }

  private static PyClassType getClassType(PsiElement problemElement) {
    if ((problemElement instanceof PyQualifiedExpression)) {
      PyExpression qualifier = ((PyQualifiedExpression)problemElement).getQualifier();
      if (qualifier == null) {
        return null;
      }
      PyType type = TypeEvalContext.userInitiated(problemElement.getProject(), problemElement.getContainingFile()).getType(qualifier);
      return type instanceof PyClassType ? (PyClassType)type : null;
    }
    PyClass pyClass = PsiTreeUtil.getParentOfType(problemElement, PyClass.class);
    return pyClass != null ? new PyClassTypeImpl(pyClass, false) : null;
  }

  @RequiredReadAction
  private static void showTemplateBuilder(PyFunction method) {
    method = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(method);
    PsiFile file = method.getContainingFile();
    if (file == null) {
      return;
    }
    final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(method);
    ParamHelper.walkDownParamArray(method.getParameterList().getParameters(), new ParamHelper.ParamVisitor() {
      @Override
      public void visitNamedParameter(PyNamedParameter param, boolean first, boolean last) {
        builder.replaceElement(param, param.getName());
      }
    });

    PyStatementList statementList = method.getStatementList();
    builder.replaceElement(statementList, PyNames.PASS);

    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) {
      return;
    }
    Editor editor = FileEditorManager.getInstance(file.getProject())
      .openTextEditor(OpenFileDescriptorFactory.getInstance(file.getProject()).builder(virtualFile).build(), true);
    if (editor == null) {
      return;
    }
    builder.run(editor, false);
  }
}
