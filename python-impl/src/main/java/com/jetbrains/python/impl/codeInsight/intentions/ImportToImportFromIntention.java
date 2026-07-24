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

import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.codeEditor.Editor;
import consulo.language.editor.FileModificationService;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.NotificationType;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static com.jetbrains.python.impl.psi.PyUtil.sure;

/**
 * Converts {@code import foo} to {@code from foo import names} or {@code from ... import module} to {@code from ...module import names}.
 * Module names used as qualifiers are removed.
 *
 * <p><i>NOTE: currently we only check usage of module name in the same file. For re-exported module names this is not sufficient.</i></p>
 *
 * @author dcheryasov
 * @since 2009-09-22
 */
public class ImportToImportFromIntention extends PyBaseIntentionAction {
    private static class IntentionState {
        private String myModuleName = null;
        private String myQualifierName = null;
        private PsiElement myReferee = null;
        private PyImportElement myImportElement = null;
        private Collection<PsiReference> myReferences = null;
        private boolean myHasModuleReference = false;
        // is anything that resolves to our imported module is just an exact reference to that module
        private int myRelativeLevel; // true if "from ... import"

        @RequiredReadAction
        public IntentionState(Editor editor, PsiFile file) {
            boolean available = false;
            myImportElement = findImportElement(editor, file);
            if (myImportElement != null) {
                PsiElement parent = myImportElement.getParent();
                if (parent instanceof PyImportStatement) {
                    myRelativeLevel = 0;
                    available = true;
                }
                else if (parent instanceof PyFromImportStatement fromImport) {
                    int relativeLevel = fromImport.getRelativeLevel();
                    PyPsiUtils.assertValid(fromImport);
                    if (fromImport.isValid() && relativeLevel > 0 && fromImport.getImportSource() == null) {
                        myRelativeLevel = relativeLevel;
                        available = true;
                    }
                }
            }
            if (available) {
                collectReferencesAndOtherData(file); // this will cache data for the invocation
            }
        }

        public boolean isAvailable() {
            return myReferences != null && myReferences.size() > 0;
        }

        @RequiredReadAction
        private void collectReferencesAndOtherData(PsiFile file) {
            //PyImportElement myImportElement = findImportElement(editor, file);
            assert myImportElement != null : "isAvailable() must have returned true, but myImportElement is null";

            // usages of imported name are qualifiers; what they refer to?
            PyReferenceExpression importReference = myImportElement.getImportReferenceExpression();
            if (importReference != null) {
                myModuleName = PyPsiUtils.toPath(importReference);
                myQualifierName = myImportElement.getVisibleName();
                myReferee = importReference.getReference().resolve();
                myHasModuleReference = false;
                if (myReferee != null && myModuleName != null && myQualifierName != null) {
                    Collection<PsiReference> references = new ArrayList<>();
                    PsiTreeUtil.processElements(file, element -> {
                        if (element instanceof PyReferenceExpression ref
                            && PsiTreeUtil.getParentOfType(element, PyImportElement.class) == null) {
                            if (myQualifierName.equals(PyPsiUtils.toPath(ref))) {  // filter out other names that might resolve to our target
                                PsiElement parentElt = ref.getParent();
                                if (parentElt instanceof PyQualifiedExpression) { // really qualified by us, not just referencing?
                                    PsiElement resolved = ref.getReference().resolve();
                                    if (resolved == myReferee) {
                                        references.add(ref.getReference());
                                    }
                                }
                                else {
                                    myHasModuleReference = true;
                                }
                            }
                        }
                        return true;
                    });
                    myReferences = references;
                }
            }
        }

        @RequiredWriteAction
        public void invoke() {
            assert myImportElement != null : "isAvailable() must have returned true, but myImportElement is null";
            sure(myImportElement.getImportReferenceExpression());
            Project project = myImportElement.getProject();

            PyElementGenerator generator = PyElementGenerator.getInstance(project);
            LanguageLevel languageLevel = LanguageLevel.forElement(myImportElement);

            // usages of imported name are qualifiers; what they refer to?
            try {
                // remember names and make them drop qualifiers
                Set<String> usedNames = new HashSet<>();
                for (PsiReference ref : myReferences) {
                    PsiElement elt = ref.getElement();
                    PsiElement parentElt = elt.getParent();
                    // TODO: find ident node more properly
                    String nameUsed = sure(sure(parentElt).getLastChild()).getText();
                    usedNames.add(nameUsed);
                    if (!FileModificationService.getInstance().preparePsiElementForWrite(elt)) {
                        return;
                    }
                    assert parentElt instanceof PyReferenceExpression;
                    PyElement newReference = generator.createExpressionFromText(languageLevel, nameUsed);
                    parentElt.replace(newReference);
                }

                // create a separate import stmt for the module
                PsiElement importer = myImportElement.getParent();
                PyImportStatementBase importStatement;
                if (importer instanceof PyImportStatement importStmt) {
                    importStatement = importStmt;
                }
                else if (importer instanceof PyFromImportStatement fromImportStmt) {
                    importStatement = fromImportStmt;
                }
                else {
                    throw new IncorrectOperationException("Not an import at all");
                }
                PyImportElement[] importElements = importStatement.getImportElements();
                PyFromImportStatement newImportStatement =
                    generator.createFromImportStatement(languageLevel, getDots() + myModuleName, StringUtil.join(usedNames, ", "), null);
                PsiElement parent = importStatement.getParent();
                sure(parent);
                sure(parent.isValid());
                if (importElements.length == 1) {
                    if (myHasModuleReference) {
                        parent.addAfter(newImportStatement, importStatement); // add 'import from': we need the module imported as is
                    }
                    else { // replace entire existing import
                        sure(parent.getNode()).replaceChild(sure(importStatement.getNode()), sure(newImportStatement.getNode()));
                        // import_statement.replace(from_import_stmt);
                    }
                }
                else {
                    if (!myHasModuleReference) {
                        // cut the module out of import, add a from-import.
                        for (PyImportElement pie : importElements) {
                            if (pie == myImportElement) {
                                pie.delete();
                                break;
                            }
                        }
                    }
                    parent.addAfter(newImportStatement, importStatement);
                }
            }
            catch (IncorrectOperationException ignored) {
                PyUtil.showBalloon(project, PyLocalize.qfixActionFailed(), NotificationType.WARNING);
            }
        }

        public LocalizeValue getText() {
            String moduleName = "?";
            if (myImportElement != null) {
                PyReferenceExpression reference = myImportElement.getImportReferenceExpression();
                if (reference != null) {
                    moduleName = PyPsiUtils.toPath(reference);
                }
            }
            return PyLocalize.intnConvertToFrom$0Import$1(getDots() + moduleName, "...");
        }

        private String getDots() {
            String dots = "";
            for (int i = 0; i < myRelativeLevel; i += 1) {
                dots += "."; // this generally runs 1-2 times, so it's cheaper than allocating a StringBuilder
            }
            return dots;
        }
    }

    @Nullable
    @RequiredReadAction
    private static PyImportElement findImportElement(Editor editor, PsiFile file) {
        PsiElement elementAtCaret = file.findElementAt(editor.getCaretModel().getOffset());
        PyImportElement importElement = PsiTreeUtil.getParentOfType(elementAtCaret, PyImportElement.class);
        PyPsiUtils.assertValid(importElement);
        if (importElement != null && importElement.isValid()) {
            return importElement;
        }
        else {
            return null;
        }
    }

    @Override
    @RequiredReadAction
    public boolean isAvailable(Project project, Editor editor, PsiFile file) {
        if (!(file instanceof PyFile)) {
            return false;
        }

        IntentionState state = new IntentionState(editor, file);
        if (state.isAvailable()) {
            setText(state.getText());
            return true;
        }
        return false;
    }

    @Override
    @RequiredWriteAction
    public void doInvoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        IntentionState state = new IntentionState(editor, file);
        state.invoke();
    }
}
