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
package com.jetbrains.python.impl.refactoring.move;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.impl.actions.CreatePackageAction;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.codeInsight.imports.PyImportOptimizer;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.impl.refactoring.PyRefactoringUtil;
import com.jetbrains.python.impl.refactoring.classes.PyClassRefactoringUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.refactoring.RefactoringSettings;
import consulo.language.editor.refactoring.move.MoveFileHandler;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.language.util.IncorrectOperationException;
import consulo.usage.UsageInfo;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.Key;
import consulo.util.io.PathUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.Couple;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.*;

/**
 * @author vlan
 */
@ExtensionImpl
public class PyMoveFileHandler extends MoveFileHandler
{
	private static final Key<PsiNamedElement> REFERENCED_ELEMENT = Key.create("PY_REFERENCED_ELEMENT");
	private static final Key<String> ORIGINAL_FILE_LOCATION = Key.create("PY_ORIGINAL_FILE_LOCATION");

	@Override
	public boolean canProcessElement(PsiFile element)
	{
		return element.getFileType() == PythonFileType.INSTANCE;
	}

	@Override
	public void prepareMovedFile(PsiFile file, PsiDirectory moveDestination, Map<PsiElement, PsiElement> oldToNewMap)
	{
		if(file != null)
		{
			VirtualFile virtualFile = file.getVirtualFile();
			if(virtualFile != null)
			{
				PyUtil.deletePycFiles(virtualFile.getPath());
			}
			final Collection<VirtualFile> roots = PyUtil.getSourceRoots(file);
			PsiDirectory root = moveDestination;
			while(root != null && !roots.contains(root.getVirtualFile()))
			{
				root = root.getParentDirectory();
			}
			final boolean searchForReferences = RefactoringSettings.getInstance().MOVE_SEARCH_FOR_REFERENCES_FOR_FILE;
			if(moveDestination != root && root != null && searchForReferences && !probablyNamespacePackage(file, moveDestination, root))
			{
				CreatePackageAction.createInitPyInHierarchy(moveDestination, root);
			}
		}
	}

	@Override
	public void updateMovedFile(PsiFile file) throws IncorrectOperationException
	{
		if(file instanceof PyFile)
		{
			updateRelativeImportsInModule((PyFile) file);
		}
	}

	private static void updateRelativeImportsInModule(@Nonnull PyFile module)
	{
		final String originalLocation = module.getUserData(ORIGINAL_FILE_LOCATION);
		if(originalLocation == null)
		{
			return;
		}
		//module.putUserData(ORIGINAL_FILE_LOCATION, null);
		for(PyFromImportStatement statement : module.getFromImports())
		{
			if(!canBeRelative(statement))
			{
				continue;
			}
			final int relativeLevel = Math.max(statement.getRelativeLevel(), 1);
			final PsiFileSystemItem sourceElement = resolveRelativeImportFromModuleLocation(statement.getManager(), originalLocation, statement.getImportSource(), relativeLevel);
			if(sourceElement == null)
			{
				continue;
			}
			final QualifiedName newName = QualifiedNameFinder.findShortestImportableQName(sourceElement);
			replaceRelativeImportSourceWithQualifiedExpression(statement, newName);
		}

		for(PyImportElement importElement : module.getImportTargets())
		{
			final PyReferenceExpression referenceExpr = importElement.getImportReferenceExpression();
			if(!canBeRelative(importElement) || referenceExpr == null)
			{
				continue;
			}
			final PsiFileSystemItem resolved = resolveRelativeImportFromModuleLocation(importElement.getManager(), originalLocation, referenceExpr, 1);
			if(resolved == null)
			{
				continue;
			}
			final QualifiedName newName = QualifiedNameFinder.findShortestImportableQName(resolved);
			replaceWithQualifiedExpression(referenceExpr, newName);
			final QualifiedName oldQualifiedName = referenceExpr.asQualifiedName();
			if(!Comparing.equal(oldQualifiedName, newName))
			{
				final ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(importElement);
				if(scopeOwner == null)
				{
					continue;
				}
				scopeOwner.accept(new PyRecursiveElementVisitor()
				{
					@Override
					public void visitPyReferenceExpression(PyReferenceExpression node)
					{
						if(Comparing.equal(node.asQualifiedName(), oldQualifiedName))
						{
							replaceWithQualifiedExpression(node, newName);
						}
						else
						{
							super.visitPyReferenceExpression(node);
						}
					}
				});
			}
		}
	}

	private static boolean canBeRelative(@Nonnull PyFromImportStatement statement)
	{
		return !LanguageLevel.forElement(statement).isPy3K() || statement.getRelativeLevel() > 0;
	}


	private static boolean canBeRelative(@Nonnull PyImportElement statement)
	{
		return !LanguageLevel.forElement(statement).isPy3K();
	}

	/**
	 * @param referenceExpr is null if we resolve import of type "from .. import bar", and "foo" for import of type "from foo import bar"
	 */
	@Nullable
	private static PsiFileSystemItem resolveRelativeImportFromModuleLocation(@Nonnull PsiManager manager,
			@Nonnull String moduleLocation,
			@Nullable PyReferenceExpression referenceExpr,
			int relativeLevel)
	{
		String relativeImportBasePath = VirtualFileManager.extractPath(moduleLocation);
		for(int level = 0; level < relativeLevel; level++)
		{
			relativeImportBasePath = PathUtil.getParentPath(relativeImportBasePath);
		}
		if(!relativeImportBasePath.isEmpty())
		{
			final String protocol = VirtualFileManager.extractProtocol(moduleLocation);
			assert protocol != null : "Original location: " + moduleLocation;
			final String relativeImportBaseUrl = VirtualFileManager.constructUrl(protocol, relativeImportBasePath);
			final VirtualFile relativeImportBaseDir = VirtualFileManager.getInstance().findFileByUrl(relativeImportBaseUrl);
			VirtualFile sourceFile = relativeImportBaseDir;
			if(relativeImportBaseDir != null && relativeImportBaseDir.isDirectory() && referenceExpr != null)
			{
				final QualifiedName qualifiedName = referenceExpr.asQualifiedName();
				if(qualifiedName == null)
				{
					return null;
				}
				final String relativePath = qualifiedName.join("/");
				sourceFile = relativeImportBaseDir.findFileByRelativePath(relativePath);
				if(sourceFile == null)
				{
					sourceFile = relativeImportBaseDir.findFileByRelativePath(relativePath + PyNames.DOT_PY);
				}
			}
			if(sourceFile != null)
			{
				final PsiFileSystemItem sourceElement;
				if(sourceFile.isDirectory())
				{
					sourceElement = manager.findDirectory(sourceFile);
				}
				else
				{
					sourceElement = manager.findFile(sourceFile);
				}
				return sourceElement;
			}
		}
		return null;
	}

	private static boolean probablyNamespacePackage(@Nonnull PsiFile anchor, @Nonnull PsiDirectory destination, @Nonnull PsiDirectory root)
	{
		if(!LanguageLevel.forElement(anchor).isAtLeast(LanguageLevel.PYTHON33))
		{
			return false;
		}
		while(destination != null && destination != root)
		{
			if(destination.findFile(PyNames.INIT_DOT_PY) != null)
			{
				return false;
			}
			//noinspection ConstantConditions
			destination = destination.getParent();
		}
		return true;
	}

	@Override
	public List<UsageInfo> findUsages(PsiFile file, PsiDirectory newParent, boolean searchInComments, boolean searchInNonJavaFiles)
	{
		if(file != null)
		{
			file.putUserData(ORIGINAL_FILE_LOCATION, file.getVirtualFile().getUrl());
			final List<UsageInfo> usages = PyRefactoringUtil.findUsages(file, false);
			for(UsageInfo usage : usages)
			{
				final PsiElement element = usage.getElement();
				if(element != null)
				{
					element.putCopyableUserData(REFERENCED_ELEMENT, file);
				}
			}
			return usages;
		}
		return null;
	}

	@Override
	public void retargetUsages(List<UsageInfo> usages, Map<PsiElement, PsiElement> oldToNewMap)
	{
		final Set<PsiFile> updatedFiles = new HashSet<>();
		for(UsageInfo usage : usages)
		{
			final PsiElement usageElement = usage.getElement();
			if(usageElement != null)
			{
				final PsiNamedElement movedElement = usageElement.getCopyableUserData(REFERENCED_ELEMENT);
				usageElement.putCopyableUserData(REFERENCED_ELEMENT, null);
				if(movedElement != null)
				{
					final PsiFile usageFile = usageElement.getContainingFile();

					final PyImportStatementBase importStmt = PsiTreeUtil.getParentOfType(usageElement, PyImportStatementBase.class);
					// TODO: Retarget qualified expressions in docstrings
					if(importStmt != null)
					{

						if(usageFile.getUserData(ORIGINAL_FILE_LOCATION) != null)
						{
							// Leave relative imports as they are after #updateRelativeImportsInModule
							final TypeEvalContext typeEvalContext = TypeEvalContext.userInitiated(usageFile.getProject(), usageFile);
							final PyResolveContext resolveContext = PyResolveContext.defaultContext().withTypeEvalContext(typeEvalContext);
							if(ContainerUtil.getFirstItem(PyUtil.multiResolveTopPriority(usageElement, resolveContext)) == movedElement)
							{
								continue;
							}
						}

						updatedFiles.add(usageFile);
						final boolean usageInsideImportElement = PsiTreeUtil.getParentOfType(usageElement, PyImportElement.class) != null;
						if(usageInsideImportElement)
						{
							// Handles imported element in "from import" statement (from some.package import module)
							// or simple unqualified import of the module (import module).
							if(PyClassRefactoringUtil.updateUnqualifiedImportOfElement(importStmt, movedElement))
							{
								continue;
							}
						}
						final QualifiedName newElementName = QualifiedNameFinder.findCanonicalImportPath(movedElement, usageElement);
						if(importStmt instanceof PyFromImportStatement)
						{
							if(!usageInsideImportElement)
							{
								replaceRelativeImportSourceWithQualifiedExpression((PyFromImportStatement) importStmt, newElementName);
							}
						}
						else
						{
							replaceWithQualifiedExpression(usageElement, newElementName);
						}
					}
					else if(usageElement instanceof PyReferenceExpression)
					{
						updatedFiles.add(usageFile);
						if(((PyReferenceExpression) usageElement).isQualified())
						{
							final QualifiedName newQualifiedName = QualifiedNameFinder.findCanonicalImportPath(movedElement, usageElement);
							replaceWithQualifiedExpression(usageElement, newQualifiedName);
						}
						else
						{
							final QualifiedName newName = QualifiedName.fromComponents(PyClassRefactoringUtil.getOriginalName(movedElement));
							replaceWithQualifiedExpression(usageElement, newName);
						}
					}
				}
			}
		}
		if(!updatedFiles.isEmpty())
		{
			final PyImportOptimizer optimizer = new PyImportOptimizer();
			for(PsiFile file : updatedFiles)
			{
				final boolean injectedFragment = InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file);
				if(!injectedFragment)
				{
					optimizer.processFile(file).run();
				}
			}
		}
	}

	@Nonnull
	private static PsiElement replaceWithQualifiedExpression(@Nonnull PsiElement oldElement, @Nullable QualifiedName newElementName)
	{
		if(newElementName != null && PyClassRefactoringUtil.isValidQualifiedName(newElementName))
		{
			final PyElementGenerator generator = PyElementGenerator.getInstance(oldElement.getProject());
			final PsiElement newElement = generator.createExpressionFromText(LanguageLevel.forElement(oldElement), newElementName.toString());
			if(newElement != null)
			{
				return oldElement.replace(newElement);
			}
		}
		return oldElement;
	}

	/**
	 * Replace import source with leading dots (if any) with reference expression created from given qualified name.
	 * Basically it does the same thing as {@link #replaceWithQualifiedExpression}, but also removes leading dots.
	 *
	 * @param importStatement import statement to update
	 * @param qualifiedName   qualified name of new import source
	 * @return updated import statement
	 * @see #replaceWithQualifiedExpression(PsiElement, QualifiedName)
	 */
	@Nonnull
	private static PsiElement replaceRelativeImportSourceWithQualifiedExpression(@Nonnull PyFromImportStatement importStatement, @Nullable QualifiedName qualifiedName)
	{
		final Couple<PsiElement> range = getRelativeImportSourceRange(importStatement);
		if(range != null && qualifiedName != null)
		{
			if(range.getFirst() == range.getSecond())
			{
				replaceWithQualifiedExpression(range.getFirst(), qualifiedName);
			}
			else
			{
				importStatement.deleteChildRange(range.getFirst().getNextSibling(), range.getSecond());
				replaceWithQualifiedExpression(range.getFirst(), qualifiedName);
			}
		}
		return importStatement;
	}

	@Nullable
	private static Couple<PsiElement> getRelativeImportSourceRange(@Nonnull PyFromImportStatement statement)
	{
		final PsiElement fromKeyword = statement.getFirstChild();
		assert fromKeyword.getNode().getElementType() == PyTokenTypes.FROM_KEYWORD;
		final PsiElement elementAfterFrom = PsiTreeUtil.skipSiblingsForward(fromKeyword, PsiWhiteSpace.class);
		if(elementAfterFrom == null)
		{
			return null;
		}
		else if(elementAfterFrom instanceof PyReferenceExpression)
		{
			return Couple.of(elementAfterFrom, elementAfterFrom);
		}
		else if(elementAfterFrom.getNode().getElementType() == PyTokenTypes.DOT)
		{
			PsiElement lastDot;
			PsiElement next = elementAfterFrom;
			do
			{
				lastDot = next;
				next = PsiTreeUtil.skipSiblingsForward(next, PsiWhiteSpace.class);
			}
			while(next != null && next.getNode().getElementType() == PyTokenTypes.DOT);
			if(next instanceof PyReferenceExpression)
			{
				return Couple.of(elementAfterFrom, next);
			}
			else
			{
				return Couple.of(elementAfterFrom, lastDot);
			}
		}
		return null;
	}
}
