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

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.types.PyClassTypeImpl;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
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
import consulo.localize.LocalizeValue;
import consulo.navigation.OpenFileDescriptorFactory;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.NotificationType;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

/**
 * Available on self.my_something when my_something is unresolved.
 *
 * @author dcheryasov
 * @since 2009-04-04
 */
public class AddFieldQuickFix implements LocalQuickFix {

  private final String myInitializer;
  private final String myClassName;
  private String myIdentifier;
  private boolean replaceInitializer = false;

  public AddFieldQuickFix(String identifier, String initializer, String className, boolean replace) {
    myIdentifier = identifier;
    myInitializer = initializer;
    myClassName = className;
    replaceInitializer = replace;
  }

  @Override
  public LocalizeValue getName() {
    return PyLocalize.qfixNameAddField$0ToClass$1(myIdentifier, myClassName);
  }

  @Nullable
  @RequiredWriteAction
  public static PsiElement appendToMethod(PyFunction init, Function<String, PyStatement> callback) {
    // add this field as the last stmt of the constructor
    PyStatementList statementList = init.getStatementList();
    // name of 'self' may be different for fancier styles
    String selfName = PyNames.CANONICAL_SELF;
    PyParameter[] params = init.getParameterList().getParameters();
    if (params.length > 0) {
      selfName = params[0].getName();
    }
    PyStatement newStmt = callback.apply(selfName);
    if (!FileModificationService.getInstance().preparePsiElementForWrite(statementList)) {
      return null;
    }
    PsiElement result = PyUtil.addElementToStatementList(newStmt, statementList, true);
    PyPsiUtils.removeRedundantPass(statementList);
    return result;
  }

  @Override
  @RequiredWriteAction
  public void applyFix(Project project, ProblemDescriptor descriptor) {
    // expect the descriptor to point to the unresolved identifier.
    PsiElement element = descriptor.getPsiElement();
    PyClassType type = getClassType(element);
    if (type == null) {
      return;
    }
    PyClass cls = type.getPyClass();
    PsiElement initStatement;
    if (!type.isDefinition()) {
      initStatement = addFieldToInit(project, cls, myIdentifier, new CreateFieldCallback(project, myIdentifier, myInitializer));
    }
    else {
      PyStatement field = PyElementGenerator.getInstance(project)
                                            .createFromText(LanguageLevel.getDefault(),
                                                            PyStatement.class,
                                                            myIdentifier + " = " + myInitializer);
      initStatement = PyUtil.addElementToStatementList(field, cls.getStatementList(), true);
    }
    if (initStatement != null) {
      showTemplateBuilder(initStatement, cls.getContainingFile());
      return;
    }
    // somehow we failed. tell about this
    PyUtil.showBalloon(project, PyLocalize.qfixFailedToAddField(), NotificationType.ERROR);
  }

  private static PyClassType getClassType(PsiElement element) {
    if (element instanceof PyQualifiedExpression) {
      PyExpression qualifier = ((PyQualifiedExpression)element).getQualifier();
      if (qualifier == null) {
        return null;
      }
      PyType type = TypeEvalContext.userInitiated(element.getProject(), element.getContainingFile()).getType(qualifier);
      return type instanceof PyClassType ? (PyClassType)type : null;
    }
    PyClass aClass = PsiTreeUtil.getParentOfType(element, PyClass.class);
    return aClass != null ? new PyClassTypeImpl(aClass, false) : null;
  }

  @RequiredReadAction
  private void showTemplateBuilder(PsiElement initStatement, PsiFile file) {
    initStatement = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(initStatement);
    if (initStatement instanceof PyAssignmentStatement assignmentStmt) {
      TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(initStatement);
      PyExpression assignedValue = assignmentStmt.getAssignedValue();
      PyExpression leftExpression = assignmentStmt.getLeftHandSideExpression();
      if (assignedValue != null && leftExpression != null) {
        if (replaceInitializer) {
          builder.replaceElement(assignedValue, myInitializer);
        }
        else {
          builder.replaceElement(leftExpression.getLastChild(), myIdentifier);
        }
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
  }

  @Nullable
  @RequiredWriteAction
  public static PsiElement addFieldToInit(Project project, PyClass cls, String itemName, Function<String, PyStatement> callback) {
    if (cls != null && itemName != null) {
      PyFunction init = cls.findMethodByName(PyNames.INIT, false, null);
      if (init != null) {
        return appendToMethod(init, callback);
      }
      else { // no init! boldly copy ancestor's.
        for (PyClass ancestor : cls.getAncestorClasses(null)) {
          init = ancestor.findMethodByName(PyNames.INIT, false, null);
          if (init != null) {
            break;
          }
        }
        PyFunction newInit = createInitMethod(project, cls, init);
        if (newInit == null) {
          return null;
        }

        appendToMethod(newInit, callback);

        PsiElement addAnchor = null;
        PyFunction[] meths = cls.getMethods();
        if (meths.length > 0) {
          addAnchor = meths[0].getPrevSibling();
        }
        PyStatementList clsContent = cls.getStatementList();
        newInit = (PyFunction)clsContent.addAfter(newInit, addAnchor);

        PyUtil.showBalloon(
            project,
            PyLocalize.qfixAddedConstructor$0ForField$1(cls.getName(), itemName),
            NotificationType.INFO
        );
        PyStatementList statementList = newInit.getStatementList();
        PyStatement[] statements = statementList.getStatements();
        return statements.length != 0 ? statements[0] : null;
      }
    }
    return null;
  }

  @Nullable
  @RequiredReadAction
  private static PyFunction createInitMethod(Project project, PyClass cls, @Nullable PyFunction ancestorInit) {
    // found it; copy its param list and make a call to it.
    if (!FileModificationService.getInstance().preparePsiElementForWrite(cls)) {
      return null;
    }
    String paramList = ancestorInit != null ? ancestorInit.getParameterList().getText() : "(self)";

    String functionText = "def " + PyNames.INIT + paramList + ":\n";
    if (ancestorInit == null) {
      functionText += "    pass";
    }
    else {
      PyClass ancestorClass = ancestorInit.getContainingClass();
      if (ancestorClass != null && !PyUtil.isObjectClass(ancestorClass)) {
        StringBuilder sb = new StringBuilder();
        PyParameter[] params = ancestorInit.getParameterList().getParameters();

        boolean seen = false;
        if (cls.isNewStyleClass(null)) {
          // form the super() call
          sb.append("super(");
          if (!LanguageLevel.forElement(cls).isPy3K()) {
            sb.append(cls.getName());

            // NOTE: assume that we have at least the first param
            String self_name = params[0].getName();
            sb.append(", ").append(self_name);
          }
          sb.append(").").append(PyNames.INIT).append("(");
        }
        else {
          sb.append(ancestorClass.getName());
          sb.append(".__init__(self");
          seen = true;
        }
        for (int i = 1; i < params.length; i += 1) {
          if (seen) {
            sb.append(", ");
          }
          else {
            seen = true;
          }
          sb.append(params[i].getText());
        }
        sb.append(")");
        functionText += "    " + sb.toString();
      }
      else {
        functionText += "    pass";
      }
    }
    return PyElementGenerator.getInstance(project).createFromText(LanguageLevel.getDefault(), PyFunction.class, functionText, new int[]{0});
  }

  private static class CreateFieldCallback implements Function<String, PyStatement> {
    private Project myProject;
    private String myItemName;
    private String myInitializer;

    private CreateFieldCallback(Project project, String itemName, String initializer) {
      myProject = project;
      myItemName = itemName;
      myInitializer = initializer;
    }

    @Override
    public PyStatement apply(String selfName) {
      return PyElementGenerator.getInstance(myProject).createFromText(
        LanguageLevel.getDefault(),
        PyStatement.class,
        selfName + "." + myItemName + " = " + myInitializer
      );
    }
  }
}
