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
package com.jetbrains.python.impl.refactoring.introduce;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.psi.PyStringLiteralUtil;
import com.jetbrains.python.impl.psi.types.PyNoneType;
import com.jetbrains.python.impl.refactoring.NameSuggesterUtil;
import com.jetbrains.python.impl.refactoring.PyRefactoringUtil;
import com.jetbrains.python.impl.refactoring.PyReplaceExpressionUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.application.Application;
import consulo.application.Result;
import consulo.codeEditor.CaretModel;
import consulo.codeEditor.Editor;
import consulo.codeEditor.SelectionModel;
import consulo.dataContext.DataContext;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.ast.TokenType;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.event.RefactoringEventData;
import consulo.language.editor.refactoring.event.RefactoringEventListener;
import consulo.language.editor.refactoring.introduce.IntroduceTargetChooser;
import consulo.language.editor.refactoring.introduce.inplace.InplaceVariableIntroducer;
import consulo.language.editor.refactoring.introduce.inplace.OccurrencesChooser;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.editor.template.TemplateManager;
import consulo.language.editor.template.TemplateState;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.lang.Couple;
import consulo.util.lang.Pair;
import org.jspecify.annotations.Nullable;

import java.util.*;

import static com.jetbrains.python.impl.psi.PyUtil.as;
import static com.jetbrains.python.inspections.PyStringFormatParser.*;

/**
 * @author Alexey.Ivanov
 * @author vlan
 */
abstract public class IntroduceHandler implements RefactoringActionHandler {
  protected static PsiElement findAnchor(List<PsiElement> occurrences) {
    PsiElement anchor = occurrences.get(0);
    Pair<PsiElement, TextRange> data = anchor.getUserData(PyReplaceExpressionUtil.SELECTION_BREAKS_AST_NODE);
    // Search anchor in the origin file, not in dummy.py, if selection breaks statement and thus element was generated
    if (data != null && occurrences.size() == 1) {
      return PsiTreeUtil.getParentOfType(data.getFirst(), PyStatement.class);
    }
    next:
    do {
      PyStatement statement = PsiTreeUtil.getParentOfType(anchor, PyStatement.class);
      if (statement != null) {
        PsiElement parent = statement.getParent();
        for (PsiElement element : occurrences) {
          if (!PsiTreeUtil.isAncestor(parent, element, true)) {
            anchor = statement;
            continue next;
          }
        }
      }
      return statement;
    }
    while (true);
  }

  protected static void ensureName(IntroduceOperation operation) {
    if (operation.getName() == null) {
      Collection<String> suggestedNames = operation.getSuggestedNames();
      if (suggestedNames.size() > 0) {
        operation.setName(suggestedNames.iterator().next());
      }
      else {
        operation.setName("x");
      }
    }
  }

  @Nullable
  @RequiredReadAction
  protected static PsiElement findOccurrenceUnderCaret(List<PsiElement> occurrences, Editor editor) {
    if (occurrences.isEmpty()) {
      return null;
    }
    int offset = editor.getCaretModel().getOffset();
    for (PsiElement occurrence : occurrences) {
      if (occurrence != null && occurrence.getTextRange().contains(offset)) {
        return occurrence;
      }
    }
    int line = editor.getDocument().getLineNumber(offset);
    for (PsiElement occurrence : occurrences) {
      PyPsiUtils.assertValid(occurrence);
      if (occurrence.isValid() && editor.getDocument().getLineNumber(occurrence.getTextRange().getStartOffset()) == line) {
        return occurrence;
      }
    }
    for (PsiElement occurrence : occurrences) {
      PyPsiUtils.assertValid(occurrence);
      return occurrence;
    }
    return null;
  }

  public enum InitPlace {
    SAME_METHOD,
    CONSTRUCTOR,
    SET_UP
  }

  @Nullable
  @RequiredWriteAction
  protected PsiElement replaceExpression(PsiElement expression, PyExpression newExpression, IntroduceOperation operation) {
    PyExpressionStatement statement = PsiTreeUtil.getParentOfType(expression, PyExpressionStatement.class);
    if (statement != null) {
      if (statement.getExpression() == expression && expression.getUserData(PyReplaceExpressionUtil.SELECTION_BREAKS_AST_NODE) == null) {
        statement.delete();
        return null;
      }
    }
    return PyReplaceExpressionUtil.replaceExpression(expression, newExpression);
  }

  private final IntroduceValidator myValidator;
  protected final LocalizeValue myDialogTitle;

  protected IntroduceHandler(IntroduceValidator validator, String dialogTitle) {
    myValidator = validator;
    myDialogTitle = LocalizeValue.ofNullable(dialogTitle);
  }

  @Override
  @RequiredUIAccess
  public void invoke(Project project, Editor editor, PsiFile file, DataContext dataContext) {
    performAction(new IntroduceOperation(project, editor, file, null));
  }

  @Override
  @RequiredUIAccess
  public void invoke(Project project, PsiElement[] elements, DataContext dataContext) {
  }

  @RequiredReadAction
  public Collection<String> getSuggestedNames(PyExpression expression) {
    Collection<String> candidates = generateSuggestedNames(expression);

    Collection<String> res = new ArrayList<>();
    for (String name : candidates) {
      if (myValidator.checkPossibleName(name, expression)) {
        res.add(name);
      }
    }

    if (res.isEmpty()) {  // no available names found, generate disambiguated suggestions
      for (String name : candidates) {
        int index = 1;
        while (!myValidator.checkPossibleName(name + index, expression)) {
          index++;
        }
        res.add(name + index);
      }
    }

    return res;
  }

  @RequiredReadAction
  protected Collection<String> generateSuggestedNames(PyExpression expression) {
    Collection<String> candidates = new LinkedHashSet<>() {
      @Override
      public boolean add(String s) {
        if (PyNames.isReserved(s)) {
          return false;
        }
        return super.add(s);
      }
    };
    String text = expression.getText();
    Pair<PsiElement, TextRange> selection = expression.getUserData(PyReplaceExpressionUtil.SELECTION_BREAKS_AST_NODE);
    if (selection != null) {
      text = selection.getSecond().substring(selection.getFirst().getText());
    }
    if (expression instanceof PyCallExpression) {
      PyExpression callee = ((PyCallExpression)expression).getCallee();
      if (callee != null) {
        text = callee.getText();
      }
    }
    if (text != null) {
      candidates.addAll(NameSuggesterUtil.generateNames(text));
    }
    TypeEvalContext context = TypeEvalContext.userInitiated(expression.getProject(), expression.getContainingFile());
    PyType type = context.getType(expression);
    if (type != null && type != PyNoneType.INSTANCE) {
      String typeName = type.getName();
      if (typeName != null) {
        if (type.isBuiltin()) {
          typeName = typeName.substring(0, 1);
        }
        candidates.addAll(NameSuggesterUtil.generateNamesByType(typeName));
      }
    }
    PyKeywordArgument kwArg = PsiTreeUtil.getParentOfType(expression, PyKeywordArgument.class);
    if (kwArg != null && kwArg.getValueExpression() == expression) {
      candidates.add(kwArg.getKeyword());
    }

    PyArgumentList argList = PsiTreeUtil.getParentOfType(expression, PyArgumentList.class);
    if (argList != null) {
      PyCallExpression callExpr = argList.getCallExpression();
      if (callExpr != null) {
        PyResolveContext resolveContext = PyResolveContext.noImplicits();
        PyCallExpression.PyArgumentsMapping mapping = callExpr.mapArguments(resolveContext);
        if (mapping.getMarkedCallee() != null) {
          PyNamedParameter namedParameter = mapping.getMappedParameters().get(expression);
          if (namedParameter != null) {
            candidates.add(namedParameter.getName());
          }
        }
      }
    }
    return candidates;
  }

  @RequiredUIAccess
  public void performAction(IntroduceOperation operation) {
    PsiFile file = operation.getFile();
    if (!CommonRefactoringUtil.checkReadOnlyStatus(file)) {
      return;
    }
    Editor editor = operation.getEditor();
    if (editor.getSettings().isVariableInplaceRenameEnabled()) {
      TemplateState templateState = TemplateManager.getInstance(operation.getProject()).getTemplateState(operation.getEditor());
      if (templateState != null && !templateState.isFinished()) {
        return;
      }
    }

    PsiElement element1 = null;
    PsiElement element2 = null;
    SelectionModel selectionModel = editor.getSelectionModel();
    boolean singleElementSelection = false;
    if (selectionModel.hasSelection()) {
      element1 = file.findElementAt(selectionModel.getSelectionStart());
      element2 = file.findElementAt(selectionModel.getSelectionEnd() - 1);
      if (element1 instanceof PsiWhiteSpace) {
        int startOffset = element1.getTextRange().getEndOffset();
        element1 = file.findElementAt(startOffset);
      }
      if (element2 instanceof PsiWhiteSpace) {
        int endOffset = element2.getTextRange().getStartOffset();
        element2 = file.findElementAt(endOffset - 1);
      }
      if (element1 == element2) {
        singleElementSelection = true;
      }
    }
    else {
      if (smartIntroduce(operation)) {
        return;
      }
      CaretModel caretModel = editor.getCaretModel();
      Document document = editor.getDocument();
      int lineNumber = document.getLineNumber(caretModel.getOffset());
      if ((lineNumber >= 0) && (lineNumber < document.getLineCount())) {
        element1 = file.findElementAt(document.getLineStartOffset(lineNumber));
        element2 = file.findElementAt(document.getLineEndOffset(lineNumber) - 1);
      }
    }
    Project project = operation.getProject();
    if (element1 == null || element2 == null) {
      showCannotPerformError(project, editor);
      return;
    }

    element1 = PyRefactoringUtil.getSelectedExpression(project, file, element1, element2);
    PyComprehensionElement comprehension = PsiTreeUtil.getParentOfType(element1, PyComprehensionElement.class, true);
    if (element1 == null || comprehension != null) {
      showCannotPerformError(project, editor);
      return;
    }

    if (singleElementSelection && element1 instanceof PyStringLiteralExpression) {
      PyStringLiteralExpression literal = (PyStringLiteralExpression)element1;
      // Currently introduce for substrings of a multi-part string literals is not supported
      if (literal.getStringNodes().size() > 1) {
        showCannotPerformError(project, editor);
        return;
      }
      int offset = element1.getTextOffset();
      TextRange selectionRange = TextRange.create(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
      TextRange elementRange = element1.getTextRange();
      if (!elementRange.equals(selectionRange) && elementRange.contains(selectionRange)) {
        TextRange innerRange = literal.getStringValueTextRange();
        TextRange intersection = selectionRange.shiftRight(-offset).intersection(innerRange);
        TextRange finalRange = intersection != null ? intersection : selectionRange;
        String text = literal.getText();
        if (getFormatValueExpression(literal) != null && breaksStringFormatting(text, finalRange) ||
          getNewStyleFormatValueExpression(literal) != null && breaksNewStyleStringFormatting(text, finalRange) ||
          breaksStringEscaping(text, finalRange)) {
          showCannotPerformError(project, editor);
          return;
        }
        element1.putUserData(PyReplaceExpressionUtil.SELECTION_BREAKS_AST_NODE, Pair.create(element1, finalRange));
      }
    }

    if (!checkIntroduceContext(file, editor, element1)) {
      return;
    }
    operation.setElement(element1);
    performActionOnElement(operation);
  }

  private static boolean breaksStringFormatting(String s, TextRange range) {
    return breaksRanges(substitutionsToRanges(filterSubstitutions(parsePercentFormat(s))), range);
  }

  private static boolean breaksNewStyleStringFormatting(String s, TextRange range) {
    return breaksRanges(substitutionsToRanges(filterSubstitutions(parseNewStyleFormat(s))), range);
  }

  private static boolean breaksStringEscaping(String s, TextRange range) {
    return breaksRanges(getEscapeRanges(s), range);
  }

  private static boolean breaksRanges(List<TextRange> ranges, TextRange range) {
    for (TextRange r : ranges) {
      if (range.contains(r)) {
        continue;
      }
      if (range.intersectsStrict(r)) {
        return true;
      }
    }
    return false;
  }

  @RequiredUIAccess
  private void showCannotPerformError(Project project, Editor editor) {
    CommonRefactoringUtil.showErrorHint(project,
                                        editor,
        PyLocalize.refactoringIntroduceSelectionError(),
                                        myDialogTitle,
                                        "refactoring.extractMethod");
  }

  @RequiredUIAccess
  private boolean smartIntroduce(IntroduceOperation operation) {
    Editor editor = operation.getEditor();
    PsiFile file = operation.getFile();
    int offset = editor.getCaretModel().getOffset();
    PsiElement elementAtCaret = file.findElementAt(offset);
    if ((elementAtCaret instanceof PsiWhiteSpace && offset == elementAtCaret.getTextOffset() || elementAtCaret == null) && offset > 0) {
      elementAtCaret = file.findElementAt(offset - 1);
    }
    if (!checkIntroduceContext(file, editor, elementAtCaret)) {
      return true;
    }
    List<PyExpression> expressions = new ArrayList<>();
    while (elementAtCaret != null) {
      if (elementAtCaret instanceof PyStatement || elementAtCaret instanceof PyFile) {
        break;
      }
      if (elementAtCaret instanceof PyExpression && isValidIntroduceVariant(elementAtCaret)) {
        expressions.add((PyExpression)elementAtCaret);
      }
      elementAtCaret = elementAtCaret.getParent();
    }
    if (expressions.size() == 1 || Application.get().isUnitTestMode()) {
      operation.setElement(expressions.get(0));
      performActionOnElement(operation);
      return true;
    }
    else if (expressions.size() > 1) {
      IntroduceTargetChooser.showChooser(editor, expressions, pyExpression -> {
        operation.setElement(pyExpression);
        performActionOnElement(operation);
      }, PsiElement::getText);
      return true;
    }
    return false;
  }

  @RequiredUIAccess
  protected boolean checkIntroduceContext(PsiFile file, Editor editor, PsiElement element) {
    if (!isValidIntroduceContext(element)) {
      CommonRefactoringUtil.showErrorHint(
          file.getProject(),
          editor,
          PyLocalize.refactoringIntroduceSelectionError(),
          myDialogTitle,
          "refactoring.extractMethod"
      );
      return false;
    }
    return true;
  }

  protected boolean isValidIntroduceContext(PsiElement element) {
    PyDecorator decorator = PsiTreeUtil.getParentOfType(element, PyDecorator.class);
    if (decorator != null && PsiTreeUtil.isAncestor(decorator.getCallee(), element, false)) {
      return false;
    }
    return PsiTreeUtil.getParentOfType(element, PyParameterList.class) == null;
  }

  private static boolean isValidIntroduceVariant(PsiElement element) {
    PyCallExpression call = as(element.getParent(), PyCallExpression.class);
    if (call != null && call.getCallee() == element) {
      return false;
    }
    PyComprehensionElement comprehension = PsiTreeUtil.getParentOfType(element, PyComprehensionElement.class, true);
    if (comprehension != null) {
      return false;
    }
    return true;
  }

  @RequiredUIAccess
  private void performActionOnElement(IntroduceOperation operation) {
    if (!checkEnabled(operation)) {
      return;
    }
    PsiElement element = operation.getElement();

    PsiElement parent = element.getParent();
    PyExpression initializer =
      parent instanceof PyAssignmentStatement ? ((PyAssignmentStatement)parent).getAssignedValue() : (PyExpression)element;
    operation.setInitializer(initializer);

    if (initializer != null) {
      operation.setOccurrences(getOccurrences(element, initializer));
      operation.setSuggestedNames(getSuggestedNames(initializer));
    }
    if (operation.getOccurrences().size() == 0) {
      operation.setReplaceAll(false);
    }

    performActionOnElementOccurrences(operation);
  }

  @RequiredUIAccess
  protected void performActionOnElementOccurrences(final IntroduceOperation operation) {
    Editor editor = operation.getEditor();
    if (editor.getSettings().isVariableInplaceRenameEnabled()) {
      ensureName(operation);
      if (operation.isReplaceAll() != null) {
        performInplaceIntroduce(operation);
      }
      else {
        OccurrencesChooser.simpleChooser(editor).showChooser(
          operation.getElement(),
          operation.getOccurrences(),
          replaceChoice -> {
            operation.setReplaceAll(replaceChoice == OccurrencesChooser.ReplaceChoice.ALL);
            performInplaceIntroduce(operation);
          }
        );
      }
    }
    else {
      performIntroduceWithDialog(operation);
    }
  }

  @RequiredUIAccess
  protected void performInplaceIntroduce(IntroduceOperation operation) {
    PsiElement statement = performRefactoring(operation);
    if (statement instanceof PyAssignmentStatement) {
      PyTargetExpression target = (PyTargetExpression)((PyAssignmentStatement)statement).getTargets()[0];
      List<PsiElement> occurrences = operation.getOccurrences();
      PsiElement occurrence = findOccurrenceUnderCaret(occurrences, operation.getEditor());
      PsiElement elementForCaret = occurrence != null ? occurrence : target;
      operation.getEditor().getCaretModel().moveToOffset(elementForCaret.getTextRange().getStartOffset());
      InplaceVariableIntroducer<PsiElement> introducer = new PyInplaceVariableIntroducer(target, operation, occurrences);
      introducer.performInplaceRefactoring(new LinkedHashSet<>(operation.getSuggestedNames()));
    }
  }

  @RequiredUIAccess
  protected void performIntroduceWithDialog(IntroduceOperation operation) {
    Project project = operation.getProject();
    if (operation.getName() == null) {
      PyIntroduceDialog dialog = new PyIntroduceDialog(project, myDialogTitle.get(), myValidator, getHelpId(), operation);
      if (!dialog.showAndGet()) {
        return;
      }
      operation.setName(dialog.getName());
      operation.setReplaceAll(dialog.doReplaceAllOccurrences());
      operation.setInitPlace(dialog.getInitPlace());
    }

    PsiElement declaration = performRefactoring(operation);
    Editor editor = operation.getEditor();
    editor.getCaretModel().moveToOffset(declaration.getTextRange().getEndOffset());
    editor.getSelectionModel().removeSelection();
  }

  @RequiredUIAccess
  protected PsiElement performRefactoring(IntroduceOperation operation) {
    PsiElement declaration = createDeclaration(operation);

    declaration = performReplace(declaration, operation);
    declaration = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(declaration);
    return declaration;
  }

  @RequiredReadAction
  public PyAssignmentStatement createDeclaration(IntroduceOperation operation) {
    Project project = operation.getProject();
    PyExpression initializer = operation.getInitializer();
    String assignmentText = operation.getName() + " = " + new InitializerTextBuilder(initializer).result();
    PsiElement anchor =
      operation.isReplaceAll() ? findAnchor(operation.getOccurrences()) : PsiTreeUtil.getParentOfType(initializer, PyStatement.class);
    return createDeclaration(project, assignmentText, anchor);
  }

  private static class InitializerTextBuilder extends PyRecursiveElementVisitor {
    private final StringBuilder myResult = new StringBuilder();

    @RequiredReadAction
    public InitializerTextBuilder(PyExpression expression) {
      if (PsiTreeUtil.findChildOfType(expression, PsiComment.class) != null) {
        myResult.append(expression.getText());
      }
      else {
        expression.accept(this);
      }
      if (needToWrapTopLevelExpressionInParenthesis(expression)) {
        myResult.insert(0, "(").append(")");
      }
    }

    @Override
    public void visitWhiteSpace(PsiWhiteSpace space) {
      myResult.append(space.getText().replace('\n', ' ').replace("\\", ""));
    }

    @RequiredReadAction
    @Override
    public void visitPyStringLiteralExpression(PyStringLiteralExpression node) {
      Pair<PsiElement, TextRange> data = node.getUserData(PyReplaceExpressionUtil.SELECTION_BREAKS_AST_NODE);
      if (data != null) {
        PsiElement parent = data.getFirst();
        String text = parent.getText();
        Pair<String, String> detectedQuotes = PyStringLiteralUtil.getQuotes(text);
        Pair<String, String> quotes = detectedQuotes != null ? detectedQuotes : Couple.of("'", "'");
        TextRange range = data.getSecond();
        String substring = range.substring(text);
        myResult.append(quotes.getFirst()).append(substring).append(quotes.getSecond());
      }
      else {
        ASTNode child = node.getNode().getFirstChildNode();
        while (child != null) {
          String text = child.getText();
          if (child.getElementType() == TokenType.WHITE_SPACE) {
            if (text.contains("\n")) {
              if (!text.contains("\\")) {
                myResult.append("\\");
              }
              myResult.append(text);
            }
          }
          else {
            myResult.append(text);
          }
          child = child.getTreeNext();
        }
      }
    }

    @Override
    @RequiredReadAction
    public void visitElement(PsiElement element) {
      if (element.getChildren().length == 0) {
        myResult.append(element.getText());
      }
      else {
        super.visitElement(element);
      }
    }

    @RequiredReadAction
    private boolean needToWrapTopLevelExpressionInParenthesis(PyExpression node) {
      if (node instanceof PyGeneratorExpression) {
        PsiElement firstChild = node.getFirstChild();
        if (firstChild != null && firstChild.getNode().getElementType() != PyTokenTypes.LPAR) {
          return true;
        }
      }
      return false;
    }

    public String result() {
      return myResult.toString();
    }
  }

  protected abstract String getHelpId();

  @RequiredReadAction
  protected PyAssignmentStatement createDeclaration(Project project, String assignmentText, PsiElement anchor) {
    LanguageLevel langLevel = ((PyFile)anchor.getContainingFile()).getLanguageLevel();
    return PyElementGenerator.getInstance(project).createFromText(langLevel, PyAssignmentStatement.class, assignmentText);
  }

  protected boolean checkEnabled(IntroduceOperation operation) {
    return true;
  }

  protected List<PsiElement> getOccurrences(PsiElement element, PyExpression expression) {
    return PyRefactoringUtil.getOccurrences(expression, ScopeUtil.getScopeOwner(expression));
  }

  @RequiredUIAccess
  private PsiElement performReplace(final PsiElement declaration, final IntroduceOperation operation) {
    final PyExpression expression = operation.getInitializer();
    final Project project = operation.getProject();
    return new WriteCommandAction<PsiElement>(project, expression.getContainingFile()) {
      @Override
      @RequiredWriteAction
      protected void run(Result<PsiElement> result) throws Throwable {
        try {
          RefactoringEventData afterData = new RefactoringEventData();
          afterData.addElement(declaration);
          project.getMessageBus()
                 .syncPublisher(RefactoringEventListener.class)
                 .refactoringStarted(getRefactoringId(), afterData);

          result.setResult(addDeclaration(operation, declaration));

          PyExpression newExpression = createExpression(project, operation.getName(), declaration);

          if (operation.isReplaceAll()) {
            List<PsiElement> newOccurrences = new ArrayList<>();
            for (PsiElement occurrence : operation.getOccurrences()) {
              PsiElement replaced = replaceExpression(occurrence, newExpression, operation);
              if (replaced != null) {
                newOccurrences.add(replaced);
              }
            }
            operation.setOccurrences(newOccurrences);
          }
          else {
            PsiElement replaced = replaceExpression(expression, newExpression, operation);
            operation.setOccurrences(Collections.singletonList(replaced));
          }

          postRefactoring(operation.getElement());
        }
        finally {
          RefactoringEventData afterData = new RefactoringEventData();
          afterData.addElement(declaration);
          project.getMessageBus()
                 .syncPublisher(RefactoringEventListener.class)
                 .refactoringDone(getRefactoringId(), afterData);
        }
      }
    }.execute().getResultObject();
  }

  protected abstract String getRefactoringId();

  @Nullable
  public PsiElement addDeclaration(IntroduceOperation operation, PsiElement declaration) {
    PsiElement expression = operation.getInitializer();
    Pair<PsiElement, TextRange> data = expression.getUserData(PyReplaceExpressionUtil.SELECTION_BREAKS_AST_NODE);
    if (data == null) {
      return addDeclaration(expression, declaration, operation);
    }
    else {
      return addDeclaration(data.first, declaration, operation);
    }
  }

  protected PyExpression createExpression(Project project, String name, PsiElement declaration) {
    return PyElementGenerator.getInstance(project).createExpressionFromText(LanguageLevel.forElement(declaration), name);
  }

  @Nullable
  protected abstract PsiElement addDeclaration(PsiElement expression,
                                               PsiElement declaration,
                                               IntroduceOperation operation);

  protected void postRefactoring(PsiElement element) {
  }

  private static class PyInplaceVariableIntroducer extends InplaceVariableIntroducer<PsiElement> {
    private final PyTargetExpression myTarget;

    @RequiredUIAccess
    public PyInplaceVariableIntroducer(PyTargetExpression target, IntroduceOperation operation, List<PsiElement> occurrences) {
      super(
        target,
        operation.getEditor(),
        operation.getProject(),
        "Introduce Variable",
        occurrences.toArray(new PsiElement[occurrences.size()]),
        null
      );
      myTarget = target;
    }

    @Override
    protected PsiElement checkLocalScope() {
      return myTarget.getContainingFile();
    }
  }
}
