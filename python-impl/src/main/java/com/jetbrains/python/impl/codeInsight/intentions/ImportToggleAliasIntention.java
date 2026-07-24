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
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiLanguageInjectionHost;
import consulo.language.psi.PsiReference;
import consulo.language.psi.resolve.PsiElementProcessor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.NotificationType;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.InputValidator;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.util.lang.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.python.impl.codeInsight.intentions.DeclarationConflictChecker.findDefinitions;
import static com.jetbrains.python.impl.codeInsight.intentions.DeclarationConflictChecker.showConflicts;
import static com.jetbrains.python.impl.psi.PyUtil.sure;

/**
 * Adds an alias to "import foo" or "from foo import bar" import elements, or removes it if it's already present.
 *
 * @author dcheryasov
 * @since 2009-10-09
 */
public class ImportToggleAliasIntention extends PyBaseIntentionAction {
    private static class IntentionState {
        private PyImportElement myImportElement;
        private PyFromImportStatement myFromImportStatement;
        private PyImportStatement myImportStatement;
        private String myAlias;

        @RequiredReadAction
        private static IntentionState fromContext(Editor editor, PsiFile file) {
            IntentionState state = new IntentionState();
            state.myImportElement =
                PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyImportElement.class);
            PyPsiUtils.assertValid(state.myImportElement);
            if (state.myImportElement != null) {
                PyTargetExpression target = state.myImportElement.getAsNameElement();
                PyPsiUtils.assertValid(target);
                if (target != null) {
                    state.myAlias = target.getName();
                }
                else {
                    state.myAlias = null;
                }
                state.myFromImportStatement = PsiTreeUtil.getParentOfType(state.myImportElement, PyFromImportStatement.class);
                state.myImportStatement = PsiTreeUtil.getParentOfType(state.myImportElement, PyImportStatement.class);
            }
            return state;
        }

        @RequiredReadAction
        public boolean isAvailable() {
            if (myFromImportStatement != null) {
                PyPsiUtils.assertValid(myFromImportStatement);
                if (!myFromImportStatement.isValid() || myFromImportStatement.isFromFuture()) {
                    return false;
                }
            }
            else {
                PyPsiUtils.assertValid(myImportStatement);
                if (myImportStatement == null || !myImportStatement.isValid()) {
                    return false;
                }
            }
            PyReferenceExpression referenceExpression = myImportElement.getImportReferenceExpression();
            return referenceExpression != null && referenceExpression.getReference().resolve() != null;
        }

        @RequiredReadAction
        public LocalizeValue getText() {
            LocalizeValue addName = LocalizeValue.localizeTODO("Add alias");
            if (myImportElement != null) {
                PyReferenceExpression refEx = myImportElement.getImportReferenceExpression();
                if (refEx != null) {
                    addName = PyLocalize.intnAddAliasForImport$0(refEx.getText());
                }
            }
            return myAlias == null ? addName : PyLocalize.intnRemoveAliasForImport$0(myAlias);
        }
    }

    @Override
    @RequiredReadAction
    public boolean isAvailable(Project project, Editor editor, PsiFile file) {
        if (!(file instanceof PyFile)) {
            return false;
        }

        IntentionState state = IntentionState.fromContext(editor, file);
        setText(state.getText());
        return state.isAvailable();
    }

    @Override
    @RequiredWriteAction
    public void doInvoke(final Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        // sanity check: isAvailable must have set it.
        IntentionState state = IntentionState.fromContext(editor, file);
        //
        String targetName; // we set in in the source
        final String removeName; // we replace it in the source
        PyReferenceExpression reference = sure(state.myImportElement.getImportReferenceExpression());
        // search for references to us with the right name
        try {
            String importedName = PyPsiUtils.toPath(reference);
            if (state.myAlias != null) {
                // have to remove alias, rename everything to original
                targetName = importedName;
                removeName = state.myAlias;
            }
            else {
                // ask for and add alias
                Application application = ApplicationManager.getApplication();
                if (application != null && !application.isUnitTestMode()) {
                    String alias = Messages.showInputDialog(
                        project,
                        PyLocalize.intnAliasFor$0DialogTitle(importedName).get(),
                        "Add Alias",
                        UIUtil.getQuestionIcon(),
                        "",
                        new InputValidator() {
                            @Override
                            @RequiredUIAccess
                            public boolean checkInput(String inputString) {
                                return PyNames.isIdentifier(inputString);
                            }

                            @Override
                            @RequiredUIAccess
                            public boolean canClose(String inputString) {
                                return PyNames.isIdentifier(inputString);
                            }
                        }
                    );
                    if (alias == null) {
                        return;
                    }
                    targetName = alias;
                }
                else { // test mode
                    targetName = "alias";
                }
                removeName = importedName;
            }
            final PsiElement referee = reference.getReference().resolve();
            if (referee != null && importedName != null) {
                final Collection<PsiReference> references = new ArrayList<>();
                ScopeOwner scope = PsiTreeUtil.getParentOfType(state.myImportElement, ScopeOwner.class);
                PsiTreeUtil.processElements(scope, new PsiElementProcessor() {
                    @Override
                    public boolean execute(PsiElement element) {
                        getReferences(element);
                        if (element instanceof PyStringLiteralExpression) {
                            PsiLanguageInjectionHost host = (PsiLanguageInjectionHost) element;
                            List<Pair<PsiElement, TextRange>> files =
                                InjectedLanguageManager.getInstance(project).getInjectedPsiFiles(host);
                            if (files != null) {
                                for (Pair<PsiElement, TextRange> pair : files) {
                                    PsiElement first = pair.getFirst();
                                    if (first instanceof ScopeOwner) {
                                        ScopeOwner scopeOwner = (ScopeOwner) first;
                                        PsiTreeUtil.processElements(scopeOwner, element1 -> {
                                            getReferences(element1);
                                            return true;
                                        });
                                    }
                                }
                            }
                        }
                        return true;
                    }

                    private void getReferences(PsiElement element) {
                        if (element instanceof PyReferenceExpression ref
                            && PsiTreeUtil.getParentOfType(element, PyImportElement.class) == null) {
                            if (removeName.equals(PyPsiUtils.toPath(ref))) {  // filter out other names that might resolve to our target
                                PsiElement resolved = ref.getReference().resolve();
                                if (resolved == referee) {
                                    references.add(ref.getReference());
                                }
                            }
                        }
                    }
                });
                // no references here is OK by us.
                if (showConflicts(
                    project,
                    findDefinitions(targetName, references, Collections.<PsiElement>emptySet()),
                    targetName,
                    null
                )) {
                    return; // got conflicts
                }

                // alter the import element
                PyElementGenerator generator = PyElementGenerator.getInstance(project);
                LanguageLevel languageLevel = LanguageLevel.forElement(state.myImportElement);
                if (state.myAlias != null) {
                    // remove alias
                    ASTNode node = sure(state.myImportElement.getNode());
                    ASTNode parent = sure(node.getTreeParent());
                    node = sure(node.getFirstChildNode()); // this is the reference
                    node = sure(node.getTreeNext()); // things past the reference: space, 'as', and alias
                    parent.removeRange(node, null);
                }
                else {
                    // add alias
                    ASTNode myIEltNode = sure(state.myImportElement.getNode());
                    PyImportElement fountain =
                        generator.createFromText(languageLevel, PyImportElement.class, "import foo as " + targetName, new int[]{0, 2});
                    ASTNode graft_node = sure(fountain.getNode()); // at import elt
                    graft_node = sure(graft_node.getFirstChildNode()); // at ref
                    graft_node = sure(graft_node.getTreeNext()); // space
                    myIEltNode.addChild((ASTNode) graft_node.clone());
                    graft_node = sure(graft_node.getTreeNext()); // 'as'
                    myIEltNode.addChild((ASTNode) graft_node.clone());
                    graft_node = sure(graft_node.getTreeNext()); // space
                    myIEltNode.addChild((ASTNode) graft_node.clone());
                    graft_node = sure(graft_node.getTreeNext()); // alias
                    myIEltNode.addChild((ASTNode) graft_node.clone());
                }
                // alter references
                for (PsiReference ref : references) {
                    ASTNode ref_name_node = sure(sure(ref.getElement()).getNode());
                    ASTNode parent = sure(ref_name_node.getTreeParent());
                    ASTNode newNameNode = generator.createExpressionFromText(languageLevel, targetName).getNode();
                    assert newNameNode != null;
                    parent.replaceChild(ref_name_node, newNameNode);
                }
            }
        }
        catch (IncorrectOperationException ignored) {
            PyUtil.showBalloon(project, PyLocalize.qfixActionFailed(), NotificationType.WARNING);
        }
    }
}
