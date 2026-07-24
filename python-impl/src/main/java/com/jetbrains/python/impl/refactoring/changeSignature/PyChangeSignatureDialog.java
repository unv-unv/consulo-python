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
package com.jetbrains.python.impl.refactoring.changeSignature;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.refactoring.introduce.IntroduceValidator;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyParameterList;
import consulo.annotation.access.RequiredReadAction;
import consulo.document.Document;
import consulo.document.event.DocumentAdapter;
import consulo.document.event.DocumentEvent;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.NamesValidator;
import consulo.language.editor.refactoring.changeSignature.CallerChooserBase;
import consulo.language.editor.refactoring.changeSignature.ChangeSignatureDialogBase;
import consulo.language.editor.refactoring.changeSignature.ParameterTableModelItemBase;
import consulo.language.editor.refactoring.ui.ComboBoxVisibilityPanel;
import consulo.language.editor.refactoring.ui.JBListTableWitEditors;
import consulo.language.editor.refactoring.ui.VisibilityPanelBase;
import consulo.language.editor.ui.awt.EditorTextField;
import consulo.language.file.LanguageFileType;
import consulo.language.psi.PsiCodeFragment;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awt.ValidationInfo;
import consulo.ui.ex.awt.VerticalFlowLayout;
import consulo.ui.ex.awt.table.JBTableRow;
import consulo.ui.ex.awt.table.JBTableRowEditor;
import consulo.ui.ex.awt.tree.Tree;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * @author ktisha
 */
public class PyChangeSignatureDialog extends ChangeSignatureDialogBase<PyParameterInfo, PyFunction, String, PyMethodDescriptor, PyParameterTableModelItem, PyParameterTableModel> {

  public PyChangeSignatureDialog(Project project,
                                 PyMethodDescriptor method) {
    super(project, method, false, method.getMethod().getContext());
  }

  @Override
  protected LanguageFileType getFileType() {
    return PythonFileType.INSTANCE;
  }

  @Override
  @RequiredReadAction
  protected PyParameterTableModel createParametersInfoModel(PyMethodDescriptor method) {
    PyParameterList parameterList = PsiTreeUtil.getChildOfType(method.getMethod(), PyParameterList.class);
    return new PyParameterTableModel(parameterList, myDefaultValueContext, myProject);
  }

  @Override
  protected BaseRefactoringProcessor createRefactoringProcessor() {
    List<PyParameterInfo> parameters = getParameters();
    return new PyChangeSignatureProcessor(myProject, myMethod.getMethod(), getMethodName(),
                                          parameters.toArray(new PyParameterInfo[parameters.size()]));
  }

  @Nullable
  @Override
  protected PsiCodeFragment createReturnTypeCodeFragment() {
    return null;
  }

  @Nullable
  @Override
  protected CallerChooserBase<PyFunction> createCallerChooser(String title, Tree treeToReuse, Consumer<Set<PyFunction>> callback) {
    return null;
  }

  public boolean isNameValid(String name, Project project) {
    NamesValidator validator = NamesValidator.forLanguage(PythonLanguage.getInstance());
    return name != null
        && validator.isIdentifier(name, project)
        && !validator.isKeyword(name, project);
  }

  @Nullable
  @Override
  @RequiredReadAction
  protected String validateAndCommitData() {
    String functionName = myNameField.getText().trim();
    if (!functionName.equals(myMethod.getName())) {
      boolean defined = IntroduceValidator.isDefinedInScope(functionName, myMethod.getMethod());
      if (defined) {
        return PyLocalize.refactoringChangeSignatureDialogValidationNameDefined().get();
      }
      if (!isNameValid(functionName, myProject)) {
        return PyLocalize.refactoringChangeSignatureDialogValidationFunctionName().get();
      }
    }
    List<PyParameterTableModelItem> parameters = myParametersTableModel.getItems();
    Set<String> parameterNames = new HashSet<>();
    boolean hadPositionalContainer = false;
    boolean hadKeywordContainer = false;
    boolean hadDefaultValue = false;
    boolean hadSingleStar = false;
    boolean hadParamsAfterSingleStar = false;
    LanguageLevel languageLevel = LanguageLevel.forElement(myMethod.getMethod());

    int parametersLength = parameters.size();

    for (int index = 0; index != parametersLength; ++index) {
      PyParameterTableModelItem info = parameters.get(index);
      PyParameterInfo parameter = info.parameter;
      String name = parameter.getName();
      if (parameterNames.contains(name)) {
        return PyLocalize.annDuplicateParamName().get();
      }
      parameterNames.add(name);

      if (name.equals("*")) {
        hadSingleStar = true;
        if (index == parametersLength - 1) {
          return PyLocalize.annNamedArgumentsAfterStar().get();
        }
      }
      else if (name.startsWith("*") && !name.startsWith("**")) {
        if (hadKeywordContainer) {
          return PyLocalize.annStarredParamAfterKwparam().get();
        }
        if (hadSingleStar) {
          return PyLocalize.refactoringChangeSignatureDialogValidationMultipleStar().get();
        }
        hadPositionalContainer = true;
      }
      else if (name.startsWith("**")) {
        hadKeywordContainer = true;
        if (hadSingleStar && !hadParamsAfterSingleStar) {
          return PyLocalize.annNamedArgumentsAfterStar().get();
        }
      }
      else {
        if (!isNameValid(name, myProject)) {
          return PyLocalize.refactoringChangeSignatureDialogValidationParameterName().get();
        }
        if (hadSingleStar) {
          hadParamsAfterSingleStar = true;
        }
        if (hadPositionalContainer && !languageLevel.isPy3K()) {
          return PyLocalize.annRegularParamAfterVararg().get();
        }
        else if (hadKeywordContainer) {
          return PyLocalize.annRegularParamAfterKeyword().get();
        }
        String defaultValue = info.getDefaultValue();
        if (defaultValue != null && !StringUtil.isEmptyOrSpaces(defaultValue) && parameter.getDefaultInSignature()) {
          hadDefaultValue = true;
        }
        else {
          if (hadDefaultValue && !hadSingleStar && (!languageLevel.isPy3K() || !hadPositionalContainer)) {
            return PyLocalize.annNonDefaultParamAfterDefault().get();
          }
        }
      }
      if (parameter.getOldIndex() < 0 && !parameter.getName().startsWith("*")) {
        if (StringUtil.isEmpty(info.defaultValueCodeFragment.getText()))
          return PyLocalize.refactoringChangeSignatureDialogValidationDefaultMissing().get();
        if (StringUtil.isEmptyOrSpaces(parameter.getName()))
          return PyLocalize.refactoringChangeSignatureDialogValidationParameterMissing().get();
      }
    }


    return null;
  }

  @Override
  @RequiredUIAccess
  protected ValidationInfo doValidate() {
    String message = validateAndCommitData();
    SwingUtilities.invokeLater(() -> {
      getRefactorAction().setEnabled(message == null);
      getPreviewAction().setEnabled(message == null);
    });
    if (message != null) return new ValidationInfo(message);
    return super.doValidate();
  }

  @Override
  @RequiredUIAccess
  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  @Override
  @RequiredReadAction
  protected String calculateSignature() {
    StringBuilder builder = new StringBuilder();
    builder.append(getMethodName());
    builder.append("(");
    List<PyParameterTableModelItem> parameters = myParametersTableModel.getItems();
    for (int i = 0; i != parameters.size(); ++i) {
      PyParameterTableModelItem parameterInfo = parameters.get(i);
      builder.append(parameterInfo.parameter.getName());
      String defaultValue = parameterInfo.defaultValueCodeFragment.getText();
      if (!defaultValue.isEmpty() && parameterInfo.isDefaultInSignature()) {
        builder.append(" = " + defaultValue);
      }
      if (i != parameters.size() - 1)
        builder.append(", ");
    }
    builder.append(")");
    return builder.toString();
  }

  @Override
  protected VisibilityPanelBase<String> createVisibilityControl() {
    return new ComboBoxVisibilityPanel<>(new String[0]);
  }

  @Override
  @RequiredReadAction
  protected JComponent getRowPresentation(ParameterTableModelItemBase<PyParameterInfo> item, boolean selected, boolean focused) {
    String text = item.parameter.getName();
    String defaultCallValue = item.defaultValueCodeFragment.getText();
    PyParameterTableModelItem pyItem = (PyParameterTableModelItem)item;
    String defaultValue = pyItem.isDefaultInSignature() ? pyItem.defaultValueCodeFragment.getText() : "";

    if (StringUtil.isNotEmpty(defaultValue)) {
      text += " = " + defaultValue;
    }

    String tail = "";
    if (StringUtil.isNotEmpty(defaultCallValue)) {
      tail += " default value = " + defaultCallValue;
    }
    if (!StringUtil.isEmpty(tail)) {
      text += " //" + tail;
    }
    return JBListTableWitEditors.createEditorTextFieldPresentation(getProject(), getFileType(), " " + text, selected, focused);
  }

  @Override
  protected boolean isListTableViewSupported() {
    return true;
  }

  @Override
  protected JBTableRowEditor getTableEditor(final JTable t,
                                            final ParameterTableModelItemBase<PyParameterInfo> item) {
    return new JBTableRowEditor() {
      private EditorTextField myNameEditor;
      private EditorTextField myDefaultValueEditor;
      private JCheckBox myDefaultInSignature;

      @Override
      public void prepareEditor(JTable table, int row) {
        setLayout(new GridLayout(1, 3));
        JPanel parameterPanel = createParameterPanel();
        add(parameterPanel);
        JPanel defaultValuePanel = createDefaultValuePanel();
        add(defaultValuePanel);
        JPanel defaultValueCheckBox = createDefaultValueCheckBox();
        add(defaultValueCheckBox);

        String nameText = myNameEditor.getText();
        myDefaultValueEditor.setEnabled(!nameText.startsWith("*")
                                          && !PyNames.CANONICAL_SELF.equals(nameText));
        myDefaultInSignature.setEnabled(!nameText.startsWith("*")
                                          && !PyNames.CANONICAL_SELF.equals(nameText));
      }

      private JPanel createDefaultValueCheckBox() {
        JPanel defaultValuePanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 4, 2, true, false));

        JBLabel inSignatureLabel =
            new JBLabel(PyLocalize.refactoringChangeSignatureDialogDefaultValueCheckbox().get(), UIUtil.ComponentStyle.SMALL);

        defaultValuePanel.add(inSignatureLabel, BorderLayout.WEST);
        myDefaultInSignature = new JCheckBox();
        myDefaultInSignature.setSelected(
          ((PyParameterTableModelItem)item).isDefaultInSignature());
        myDefaultInSignature.addItemListener(
          event -> ((PyParameterTableModelItem)item).setDefaultInSignature(myDefaultInSignature.isSelected())
        );
        myDefaultInSignature.addChangeListener(getSignatureUpdater());
        myDefaultInSignature.setEnabled(item.parameter.getOldIndex() == -1);
        defaultValuePanel.add(myDefaultInSignature, BorderLayout.EAST);
        return defaultValuePanel;
      }

      private JPanel createDefaultValuePanel() {
        JPanel defaultValuePanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 4, 2, true, false));
        Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(item.defaultValueCodeFragment);
        myDefaultValueEditor = new EditorTextField(doc, getProject(), getFileType());
        JBLabel defaultValueLabel =
            new JBLabel(PyLocalize.refactoringChangeSignatureDialogDefaultValueLabel().get(), UIUtil.ComponentStyle.SMALL);
        defaultValuePanel.add(defaultValueLabel);
        defaultValuePanel.add(myDefaultValueEditor);
        myDefaultValueEditor.setPreferredWidth(t.getWidth() / 2);
        myDefaultValueEditor.addDocumentListener(getSignatureUpdater());
        return defaultValuePanel;
      }

      private JPanel createParameterPanel() {
        JPanel namePanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 4, 2, true, false));
        myNameEditor = new EditorTextField(item.parameter.getName(), getProject(), getFileType());
        JBLabel nameLabel =
          new JBLabel(PyLocalize.refactoringChangeSignatureDialogNameLabel().get(), UIUtil.ComponentStyle.SMALL);
        namePanel.add(nameLabel);
        namePanel.add(myNameEditor);
        myNameEditor.setPreferredWidth(t.getWidth() / 2);
        myNameEditor.addDocumentListener(new DocumentAdapter() {
          @Override
          public void documentChanged(DocumentEvent event) {
            fireDocumentChanged(event, 0);
            myDefaultValueEditor.setEnabled(!myNameEditor.getText().startsWith("*"));
            myDefaultInSignature.setEnabled(!myNameEditor.getText().startsWith("*"));
          }
        });

        myNameEditor.addDocumentListener(getSignatureUpdater());
        return namePanel;
      }

      @Override
      public JBTableRow getValue() {
        return column -> switch (column) {
            case 0 -> myNameEditor.getText().trim();
            case 1 -> Pair.create(
                item.defaultValueCodeFragment,
                ((PyParameterTableModelItem) item).isDefaultInSignature()
            );
            default -> null;
        };
      }

      @Override
      public JComponent getPreferredFocusedComponent() {
        return myNameEditor.getFocusTarget();
      }

      @Override
      public JComponent[] getFocusableComponents() {
        List<JComponent> focusable = new ArrayList<>();
        focusable.add(myNameEditor.getFocusTarget());
        if (myDefaultValueEditor != null) {
          focusable.add(myDefaultValueEditor.getFocusTarget());
        }
        return focusable.toArray(new JComponent[focusable.size()]);
      }
    };
  }

  @Override
  protected boolean mayPropagateParameters() {
    return false;
  }

  @Override
  protected boolean postponeValidation() {
    return false;
  }
}
