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

import com.google.common.collect.Sets;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.types.PyModuleType;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.language.ast.ASTNode;
import consulo.language.psi.*;
import consulo.language.psi.resolve.PsiElementProcessor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.NotificationType;
import org.jspecify.annotations.Nullable;

import java.util.*;

import static com.jetbrains.python.impl.codeInsight.intentions.DeclarationConflictChecker.findDefinitions;
import static com.jetbrains.python.impl.codeInsight.intentions.DeclarationConflictChecker.showConflicts;
import static com.jetbrains.python.impl.psi.PyUtil.sure;

/**
 * Transforms:<ul>
 * <li>{@code from module_name import names} into {@code import module_name};</li>
 * <li>{@code from ...module_name import names} into {@code from ... import module_name};</li>
 * <li>{@code from ...moduleA.moduleB import names} into {@code from ...moduleA import moduleB}.</li>
 * Qualifies any names imported from that module by module name.
 * </ul>
 *
 * @author dcheryasov
 * @since 2009-09-26
 */
public class ImportFromToImportIntention extends PyBaseIntentionAction {
    /**
     * This class exists to extract bunches of info we can't store in our stateless instance.
     * Instead, we store it per thread.
     */
    private static class InfoHolder {
        PyFromImportStatement myFromImportStatement = null;
        PyReferenceExpression myModuleReference = null;
        String myModuleName = null;
        int myRelativeLevel = 0;

        public LocalizeValue getText() {
            String name = myModuleName != null ? myModuleName : "...";
            if (myRelativeLevel > 0) {
                String[] relative_names = getRelativeNames(false, this);
                return PyLocalize.intnConvertToFrom$0Import$1(relative_names[0], relative_names[1]);
            }
            else {
                return PyLocalize.intnConvertToImport$0(name);
            }
        }

        public static InfoHolder collect(PsiElement position) {
            InfoHolder ret = new InfoHolder();
            ret.myModuleReference = null;
            ret.myFromImportStatement = PsiTreeUtil.getParentOfType(position, PyFromImportStatement.class);
            PyPsiUtils.assertValid(ret.myFromImportStatement);
            if (ret.myFromImportStatement != null && !ret.myFromImportStatement.isFromFuture()) {
                ret.myRelativeLevel = ret.myFromImportStatement.getRelativeLevel();
                ret.myModuleReference = ret.myFromImportStatement.getImportSource();
            }
            if (ret.myModuleReference != null) {
                ret.myModuleName = PyPsiUtils.toPath(ret.myModuleReference);
            }
            return ret;
        }
    }

    @Nullable
    private static PsiElement getElementFromEditor(Editor editor, PsiFile file) {
        PsiElement element = null;
        Document doc = editor.getDocument();
        PsiFile a_file = file;
        if (a_file == null) {
            Project project = editor.getProject();
            if (project != null) {
                a_file = PsiDocumentManager.getInstance(project).getPsiFile(doc);
            }
        }
        if (a_file != null) {
            element = a_file.findElementAt(editor.getCaretModel().getOffset());
        }
        return element;
    }

    // given module "...foo.bar". returns "...foo" and "bar"; if not strict, undefined names become "?".
    @Nullable
    private static String[] getRelativeNames(boolean strict, InfoHolder info) {
        String remaining_name = "?";
        String separated_name = "?";
        boolean failure = true;
        if (info.myModuleReference != null) {
            PyExpression remaining_module = info.myModuleReference.getQualifier();
            if (remaining_module instanceof PyQualifiedExpression) {
                remaining_name = PyPsiUtils.toPath((PyQualifiedExpression) remaining_module);
            }
            else {
                remaining_name = ""; // unqualified name: "...module"
            }
            separated_name = info.myModuleReference.getReferencedName();
            failure = false;
            if (separated_name == null) {
                separated_name = "?";
                failure = true;
            }
        }
        if (strict && failure) {
            return null;
        }
        else {
            return new String[]{
                getDots(info.myRelativeLevel) + remaining_name,
                separated_name
            };
        }
    }

    private static String getDots(int level) {
        String dots = "";
        for (int i = 0; i < level; i += 1) {
            dots += "."; // this generally runs 1-2 times, so it's cheaper than allocating a StringBuilder
        }
        return dots;
    }

    @Override
    @RequiredReadAction
    public boolean isAvailable(Project project, Editor editor, PsiFile file) {
        if (!(file instanceof PyFile)) {
            return false;
        }

        InfoHolder info = InfoHolder.collect(getElementFromEditor(editor, file));
        info.myModuleReference = null;
        PsiElement position = file.findElementAt(editor.getCaretModel().getOffset());
        info.myFromImportStatement = PsiTreeUtil.getParentOfType(position, PyFromImportStatement.class);
        PyPsiUtils.assertValid(info.myFromImportStatement);
        if (info.myFromImportStatement != null && !info.myFromImportStatement.isFromFuture()) {
            info.myRelativeLevel = info.myFromImportStatement.getRelativeLevel();
            info.myModuleReference = info.myFromImportStatement.getImportSource();
            if (info.myRelativeLevel > 0) {
                // make sure we aren't importing a module from the relative path
                for (PyImportElement import_element : info.myFromImportStatement.getImportElements()) {
                    PyReferenceExpression ref = import_element.getImportReferenceExpression();
                    PyPsiUtils.assertValid(ref);
                    if (ref != null) {
                        PsiElement target = ref.getReference().resolve();
                        TypeEvalContext context = TypeEvalContext.codeAnalysis(file.getProject(), file);
                        if (target instanceof PyExpression && context.getType((PyExpression) target) instanceof PyModuleType) {
                            return false;
                        }
                    }
                }
            }
        }
        if (info.myModuleReference != null) {
            info.myModuleName = PyPsiUtils.toPath(info.myModuleReference);
        }
        if (info.myModuleReference != null && info.myModuleName != null && info.myFromImportStatement != null) {
            setText(info.getText());
            return true;
        }
        return false;
    }

    /**
     * Adds myModuleName as a qualifier to target.
     *
     * @param targetNode what to qualify
     * @param project
     * @param qualifier
     */
    @RequiredWriteAction
    private static void qualifyTarget(ASTNode targetNode, Project project, String qualifier) {
        PyElementGenerator generator = PyElementGenerator.getInstance(project);
        targetNode.addChild(generator.createDot(), targetNode.getFirstChildNode());
        targetNode.addChild(sure(generator.createFromText(LanguageLevel.getDefault(), PyReferenceExpression.class, qualifier, new int[]{
            0,
            0
        }).getNode()), targetNode.getFirstChildNode());
    }

    @Override
    @RequiredWriteAction
    public void doInvoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        InfoHolder info = InfoHolder.collect(getElementFromEditor(editor, file));
        try {
            String qualifier; // we don't always qualify with module name
            sure(info.myModuleReference);
            sure(info.myModuleName);
            String[] relative_names = null; // [0] is remaining import path, [1] is imported module name
            if (info.myRelativeLevel > 0) {
                relative_names = getRelativeNames(true, info);
                if (relative_names == null) {
                    throw new IncorrectOperationException("failed to get relative names");
                }
                qualifier = relative_names[1];
            }
            else {
                qualifier = info.myModuleName;
            }
            // find all unqualified references that lead to one of our import elements
            PyImportElement[] iElts = info.myFromImportStatement.getImportElements();
            PyStarImportElement starIElt = info.myFromImportStatement.getStarImportElement();
            Map<PsiReference, PyImportElement> references = new HashMap<>();
            List<PsiReference> star_references = new ArrayList<>();
            PsiTreeUtil.processElements(file, element -> {
                PyPsiUtils.assertValid(element);
                if (element instanceof PyReferenceExpression && PsiTreeUtil.getParentOfType(element, PyImportElement.class) == null) {
                    PyReferenceExpression ref = (PyReferenceExpression) element;
                    if (!ref.isQualified()) {
                        ResolveResult[] resolved = ref.getReference().multiResolve(false);
                        for (ResolveResult rr : resolved) {
                            if (rr.isValidResult()) {
                                if (rr.getElement() == starIElt) {
                                    star_references.add(ref.getReference());
                                }
                                for (PyImportElement iElt : iElts) {
                                    if (rr.getElement() == iElt) {
                                        references.put(ref.getReference(), iElt);
                                    }
                                }
                            }
                        }
                    }
                }
                return true;
            });

            // check that at every replacement site our topmost qualifier name is visible
            PyQualifiedExpression topQualifier;
            PyExpression feeler = info.myModuleReference;
            do {
                sure(feeler instanceof PyQualifiedExpression); // if for some crazy reason module name refers to numbers, etc, no point to continue.
                topQualifier = (PyQualifiedExpression) feeler;
                feeler = topQualifier.getQualifier();
            }
            while (feeler != null);
            String top_name = topQualifier.getName();
            Collection<PsiReference> possible_targets = references.keySet();
            if (star_references.size() > 0) {
                possible_targets = new ArrayList<>(references.keySet().size() + star_references.size());
                possible_targets.addAll(references.keySet());
                possible_targets.addAll(star_references);
            }
            Set<PsiElement> ignored = Sets.<PsiElement>newHashSet(Arrays.asList(info.myFromImportStatement.getImportElements()));
            if (top_name != null && showConflicts(
                project,
                findDefinitions(top_name, possible_targets, ignored),
                top_name,
                info.myModuleName
            )) {
                return; // got conflicts
            }

            // add qualifiers
            PyElementGenerator generator = PyElementGenerator.getInstance(project);
            for (Map.Entry<PsiReference, PyImportElement> entry : references.entrySet()) {
                PsiElement referringElt = entry.getKey().getElement();
                assert referringElt.isValid(); // else we won't add it
                ASTNode targetNode = referringElt.getNode();
                assert targetNode != null; // else it won't be valid
                PyImportElement iElt = entry.getValue();
                if (iElt.getAsNameElement() != null) {
                    // we have an alias, replace it with real name
                    PyReferenceExpression refEx = iElt.getImportReferenceExpression();
                    assert refEx != null; // else we won't resolve to this iElt
                    String realName = refEx.getReferencedName();
                    ASTNode newQualifier = generator.createExpressionFromText(realName).getNode();
                    assert newQualifier != null;
                    //ASTNode first_under_target = target_node.getFirstChildNode();
                    //if (first_under_target != null) new_qualifier.addChildren(first_under_target, null, null); // save the children if any
                    targetNode.getTreeParent().replaceChild(targetNode, newQualifier);
                    targetNode = newQualifier;
                }
                qualifyTarget(targetNode, project, qualifier);
            }
            for (PsiReference reference : star_references) {
                PsiElement referringElt = reference.getElement();
                assert referringElt.isValid(); // else we won't add it
                ASTNode targetNode = referringElt.getNode();
                assert targetNode != null; // else it won't be valid
                qualifyTarget(targetNode, project, qualifier);
            }
            // transform the import statement
            PyStatement new_import;
            if (info.myRelativeLevel == 0) {
                new_import =
                    sure(generator.createFromText(LanguageLevel.getDefault(), PyImportStatement.class, "import " + info.myModuleName));
            }
            else {
                new_import = sure(generator.createFromText(
                    LanguageLevel.getDefault(),
                    PyFromImportStatement.class,
                    "from " + relative_names[0] + " import " + relative_names[1]
                ));
            }
            ASTNode parent = sure(info.myFromImportStatement.getParent().getNode());
            ASTNode old_node = sure(info.myFromImportStatement.getNode());
            parent.replaceChild(old_node, sure(new_import.getNode()));
            //myFromImportStatement.replace(new_import);
        }
        catch (IncorrectOperationException ignored) {
            PyUtil.showBalloon(project, PyLocalize.qfixActionFailed(), NotificationType.WARNING);
        }
    }
}
