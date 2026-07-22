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
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.ParamHelper;
import com.jetbrains.python.impl.psi.impl.PyFunctionBuilder;
import com.jetbrains.python.impl.psi.types.PyModuleType;
import com.jetbrains.python.inspections.PyInspectionExtension;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.codeEditor.Editor;
import consulo.component.extension.Extensions;
import consulo.fileEditor.FileEditorManager;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.editor.template.TemplateBuilderFactory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.navigation.OpenFileDescriptorFactory;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.NotificationType;
import consulo.virtualFileSystem.VirtualFile;

import java.util.List;

import static com.jetbrains.python.impl.psi.PyUtil.sure;

/**
 * Adds a missing top-level function to a module.
 *
 * @author dcheryasov
 * @since 2010-09-15
 * @see AddMethodQuickFix AddMethodQuickFix
 */
public class AddFunctionQuickFix implements LocalQuickFix {

  private final String myIdentifier;
  private final String myModuleName;

  public AddFunctionQuickFix(String identifier, String moduleName) {
    myIdentifier = identifier;
    myModuleName = moduleName;
  }

  @Override
  public LocalizeValue getName() {
    return PyLocalize.qfixNameAddFunction$0ToModule$1(myIdentifier, myModuleName);
  }

  @Override
  @RequiredWriteAction
  public void applyFix(Project project, ProblemDescriptor descriptor) {
    try {
        if (!(descriptor.getPsiElement() instanceof PyQualifiedExpression problemElement)) {
        return;
      }
      PyExpression qualifier = problemElement.getQualifier();
      if (qualifier == null) {
        return;
      }
      PyType type = TypeEvalContext.userInitiated(problemElement.getProject(), problemElement.getContainingFile()).getType(qualifier);
      if (!(type instanceof PyModuleType moduleType)) {
        return;
      }
      PyFile file = moduleType.getModule();
      sure(file);
      sure(FileModificationService.getInstance().preparePsiElementForWrite(file));
      // try to at least match parameter count
      // TODO: get parameter style from code style
      PyFunctionBuilder builder = new PyFunctionBuilder(myIdentifier, problemElement);
      PsiElement problemParent = problemElement.getParent();
      if (problemParent instanceof PyCallExpression callExpr) {
        PyArgumentList argList = callExpr.getArgumentList();
        if (argList == null) {
          return;
        }
        PyExpression[] args = argList.getArguments();
        for (PyExpression arg : args) {
          if (arg instanceof PyKeywordArgument kwArg) { // foo(bar) -> def foo(bar_1)
            builder.parameter(kwArg.getKeyword());
          }
          else if (arg instanceof PyReferenceExpression refEx) {
            builder.parameter(refEx.getReferencedName());
          }
          else { // use a boring name
            builder.parameter("param");
          }
        }
      }
      else if (problemParent != null) {
        for (PyInspectionExtension extension : Extensions.getExtensions(PyInspectionExtension.EP_NAME)) {
          List<String> params = extension.getFunctionParametersFromUsage(problemElement);
          if (params != null) {
            for (String param : params) {
              builder.parameter(param);
            }
            break;
          }
        }
      }
      // else: no argList, use empty args
      PyFunction function = builder.buildFunction(project, LanguageLevel.forElement(file));

      // add to the bottom
      function = (PyFunction)file.add(function);
      showTemplateBuilder(function, file);
    }
    catch (IncorrectOperationException ignored) {
      // we failed. tell about this
      PyUtil.showBalloon(project, PyLocalize.qfixFailedToAddFunction(), NotificationType.ERROR);
    }
  }

  @RequiredReadAction
  private static void showTemplateBuilder(PyFunction method, PsiFile file) {
    method = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(method);

    final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(method);
    ParamHelper.walkDownParamArray(method.getParameterList().getParameters(), new ParamHelper.ParamVisitor() {
      @Override
      public void visitNamedParameter(PyNamedParameter param, boolean first, boolean last) {
        builder.replaceElement(param, param.getName());
      }
    });

    // TODO: detect expected return type from call site context: PY-1863
    builder.replaceElement(method.getStatementList(), "return None");
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
