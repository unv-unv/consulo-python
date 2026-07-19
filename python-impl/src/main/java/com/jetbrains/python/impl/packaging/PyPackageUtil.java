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
package com.jetbrains.python.impl.packaging;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.packaging.setupPy.SetupTaskIntrospector;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.packaging.PyRequirement;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedResolveResult;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.Application;
import consulo.content.bundle.Sdk;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.process.ExecutionException;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.Ref;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import consulo.virtualFileSystem.util.VirtualFileVisitor;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author vlan
 */
public class PyPackageUtil {
    public static final String SETUPTOOLS = "setuptools";
    public static final String PIP = "pip";
    public static final String DISTRIBUTE = "distribute";
    private static final Logger LOG = Logger.getInstance(PyPackageUtil.class);

    private static final String REQUIRES = "requires";

    private static final String INSTALL_REQUIRES = "install_requires";

    private static final String[] SETUP_PY_REQUIRES_KWARGS_NAMES = new String[]{
        REQUIRES,
        INSTALL_REQUIRES,
        "setup_requires",
        "tests_require"
    };

    private static final String DEPENDENCY_LINKS = "dependency_links";

    private PyPackageUtil() {
    }

    public static boolean hasSetupPy(Module module) {
        return findSetupPy(module) != null;
    }

    @Nullable
    @RequiredReadAction
    public static PyFile findSetupPy(Module module) {
        for (VirtualFile root : PyUtil.getSourceRoots(module)) {
            VirtualFile child = root.findChild("setup.py");
            if (child != null) {
                PsiFile file = PsiManager.getInstance(module.getProject()).findFile(child);
                if (file instanceof PyFile) {
                    return (PyFile) file;
                }
            }
        }
        return null;
    }

    public static boolean hasRequirementsTxt(Module module) {
        return findRequirementsTxt(module) != null;
    }

    @Nullable
    public static VirtualFile findRequirementsTxt(Module module) {
        String requirementsPath = PyPackageRequirementsSettings.getInstance(module).getRequirementsPath();
        if (!requirementsPath.isEmpty()) {
            VirtualFile file = LocalFileSystem.getInstance().findFileByPath(requirementsPath);
            if (file != null) {
                return file;
            }
            ModuleRootManager manager = ModuleRootManager.getInstance(module);
            for (VirtualFile root : manager.getContentRoots()) {
                VirtualFile fileInRoot = root.findFileByRelativePath(requirementsPath);
                if (fileInRoot != null) {
                    return fileInRoot;
                }
            }
        }
        return null;
    }

    @Nullable
    private static PyListLiteralExpression findSetupPyInstallRequires(Module module, @Nullable PyCallExpression setupCall) {
        if (setupCall == null) {
            return null;
        }

        return Stream.of(REQUIRES, INSTALL_REQUIRES)
            .map(setupCall::getKeywordArgument)
            .map(requires -> resolveRequiresValue(module, requires))
            .filter(requires -> requires != null)
            .findFirst()
            .orElse(null);
    }

    @Nullable
    public static List<PyRequirement> findSetupPyRequires(Module module) {
        PyCallExpression setupCall = findSetupCall(module);

        if (setupCall == null) {
            return null;
        }

        List<PyRequirement> requirementsFromRequires = getSetupPyRequiresFromArguments(module, setupCall, SETUP_PY_REQUIRES_KWARGS_NAMES);
        List<PyRequirement> requirementsFromLinks = getSetupPyRequiresFromArguments(module, setupCall, DEPENDENCY_LINKS);

        return mergeSetupPyRequirements(requirementsFromRequires, requirementsFromLinks);
    }

    private static List<PyRequirement> getSetupPyRequiresFromArguments(Module module, PyCallExpression setupCall, String... argumentNames) {
        return PyRequirement.fromText(
            Stream.of(argumentNames)
                .map(setupCall::getKeywordArgument)
                .map(requires -> resolveRequiresValue(module, requires))
                .filter(requires -> requires != null)
                .flatMap(requires -> Stream.of(requires.getElements()))
                .filter(PyStringLiteralExpression.class::isInstance)
                .map(requirement -> ((PyStringLiteralExpression) requirement).getStringValue())
                .collect(Collectors.joining("\n"))
        );
    }

    private static List<PyRequirement> mergeSetupPyRequirements(List<PyRequirement> requirementsFromRequires, List<PyRequirement> requirementsFromLinks) {
        if (!requirementsFromLinks.isEmpty()) {
            Map<String, List<PyRequirement>> nameToRequirements = requirementsFromRequires.stream()
                .collect(Collectors.groupingBy(PyRequirement::getName, LinkedHashMap::new, Collectors.toList()));

            for (PyRequirement requirementFromLinks : requirementsFromLinks) {
                nameToRequirements.replace(requirementFromLinks.getName(), Collections.singletonList(requirementFromLinks));
            }

            return nameToRequirements.values().stream().flatMap(Collection::stream).collect(Collectors.toCollection(ArrayList::new));
        }

        return requirementsFromRequires;
    }

    @Nullable
    private static PyListLiteralExpression resolveRequiresValue(Module module, @Nullable PyExpression requires) {
        if (requires instanceof PyListLiteralExpression) {
            return (PyListLiteralExpression) requires;
        }
        if (requires instanceof PyReferenceExpression) {
            TypeEvalContext context = TypeEvalContext.deepCodeInsight(module.getProject());
            PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context);
            QualifiedResolveResult result = ((PyReferenceExpression) requires).followAssignmentsChain(resolveContext);
            PsiElement element = result.getElement();
            if (element instanceof PyListLiteralExpression) {
                return (PyListLiteralExpression) element;
            }
        }
        return null;
    }

    public static List<String> getPackageNames(Module module) {
        // TODO: Cache found module packages, clear cache on module updates
        List<String> packageNames = new ArrayList<>();
        Project project = module.getProject();
        VirtualFile[] roots = ModuleRootManager.getInstance(module).getSourceRoots();
        if (roots.length == 0) {
            roots = ModuleRootManager.getInstance(module).getContentRoots();
        }
        for (VirtualFile root : roots) {
            collectPackageNames(project, root, packageNames);
        }
        return packageNames;
    }

    public static String requirementsToString(List<PyRequirement> requirements) {
        return StringUtil.join(requirements, requirement -> String.format("'%s'", requirement.toString()), ", ");
    }

    @Nullable
    public static PyCallExpression findSetupCall(PyFile file) {
        final Ref<PyCallExpression> result = new Ref<>(null);
        file.acceptChildren(new PyRecursiveElementVisitor() {
            @Override
            public void visitPyCallExpression(PyCallExpression node) {
                PyExpression callee = node.getCallee();
                String name = PyUtil.getReadableRepr(callee, true);
                if ("setup".equals(name)) {
                    result.set(node);
                }
            }

            @Override
            public void visitPyElement(PyElement node) {
                if (!(node instanceof ScopeOwner)) {
                    super.visitPyElement(node);
                }
            }
        });
        return result.get();
    }

    @Nullable
    public static PyCallExpression findSetupCall(Module module) {
        return Optional.ofNullable(findSetupPy(module)).map(PyPackageUtil::findSetupCall).orElse(null);
    }

    private static void collectPackageNames(Project project, final VirtualFile root, final List<String> results) {
        final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        VirtualFileUtil.visitChildrenRecursively(root, new VirtualFileVisitor() {
            @Override
            public boolean visitFile(VirtualFile file) {
                if (!fileIndex.isExcluded(file) && file.isDirectory() && file.findChild(PyNames.INIT_DOT_PY) != null) {
                    results.add(VirtualFileUtil.getRelativePath(file, root, '.'));
                }
                return true;
            }
        });
    }

    @Deprecated
    public static boolean packageManagementEnabled(@Nullable Sdk sdk) {
        return true;
    }

    @Nullable
    public static List<PyPackage> refreshAndGetPackagesModally(Sdk sdk) {
        Ref<List<PyPackage>> packagesRef = Ref.create();
        @SuppressWarnings("ThrowableInstanceNeverThrown") Throwable callStacktrace = new Throwable();
        LOG.debug("Showing modal progress for collecting installed packages", new Throwable());
        PyUtil.runWithProgress(null, PyLocalize.sdkScanningInstalledPackages().get(), true, false, indicator -> {
            indicator.setIndeterminate(true);
            try {
                packagesRef.set(PyPackageManager.getInstance(sdk).refreshAndGetPackages(false));
            }
            catch (ExecutionException e) {
                if (LOG.isDebugEnabled()) {
                    e.initCause(callStacktrace);
                    LOG.debug(e);
                }
                else {
                    LOG.warn(e.getMessage());
                }
            }
        });
        return packagesRef.get();
    }

    /**
     * Run unconditional update of the list of packages installed in SDK. Normally only one such of updates should run at time.
     * This behavior in enforced by the parameter isUpdating.
     *
     * @param manager    package manager for SDK
     * @param isUpdating flag indicating whether another refresh is already running
     * @return whether packages were refreshed successfully, e.g. this update wasn't cancelled because of another refresh in progress
     */
    public static boolean updatePackagesSynchronouslyWithGuard(PyPackageManager manager, AtomicBoolean isUpdating) {
        assert !Application.get().isDispatchThread();
        if (!isUpdating.compareAndSet(false, true)) {
            return false;
        }
        try {
            if (manager instanceof PyPackageManagerImpl) {
                LOG.info("Refreshing installed packages for SDK " + ((PyPackageManagerImpl) manager).getSdk().getHomePath());
            }
            manager.refreshAndGetPackages(true);
        }
        catch (ExecutionException ignored) {
        }
        finally {
            isUpdating.set(false);
        }
        return true;
    }


    @Nullable
    public static PyPackage findPackage(List<PyPackage> packages, String name) {
        for (PyPackage pkg : packages) {
            if (name.equalsIgnoreCase(pkg.getName())) {
                return pkg;
            }
        }
        return null;
    }

    public static boolean hasManagement(List<PyPackage> packages) {
        return (findPackage(packages, SETUPTOOLS) != null || findPackage(packages, DISTRIBUTE) != null) || findPackage(packages, PIP) != null;
    }

    @Nullable
    public static List<PyRequirement> getRequirementsFromTxt(Module module) {
        VirtualFile requirementsTxt = findRequirementsTxt(module);
        if (requirementsTxt != null) {
            return PyRequirement.fromFile(requirementsTxt);
        }
        return null;
    }

    public static void addRequirementToTxtOrSetupPy(Module module, String requirementName, LanguageLevel languageLevel) {
        VirtualFile requirementsTxt = findRequirementsTxt(module);
        if (requirementsTxt != null && requirementsTxt.isWritable()) {
            Document document = FileDocumentManager.getInstance().getDocument(requirementsTxt);
            if (document != null) {
                document.insertString(0, requirementName + "\n");
            }
            return;
        }

        PyFile setupPy = findSetupPy(module);
        if (setupPy == null) {
            return;
        }

        PyCallExpression setupCall = findSetupCall(setupPy);
        PyListLiteralExpression installRequires = findSetupPyInstallRequires(module, setupCall);
        PyElementGenerator generator = PyElementGenerator.getInstance(module.getProject());

        if (installRequires != null && installRequires.isWritable()) {
            String text = String.format("'%s'", requirementName);
            PyExpression generated = generator.createExpressionFromText(languageLevel, text);
            installRequires.add(generated);

            return;
        }

        if (setupCall != null) {
            PyArgumentList argumentList = setupCall.getArgumentList();
            PyKeywordArgument requiresArg = generateRequiresKwarg(setupPy, requirementName, languageLevel, generator);

            if (argumentList != null && requiresArg != null) {
                argumentList.addArgument(requiresArg);
            }
        }
    }

    @Nullable
    private static PyKeywordArgument generateRequiresKwarg(PyFile setupPy, String requirementName, LanguageLevel languageLevel, PyElementGenerator generator) {
        String keyword = SetupTaskIntrospector.usesSetuptools(setupPy) ? INSTALL_REQUIRES : REQUIRES;
        String text = String.format("foo(%s=['%s'])", keyword, requirementName);
        PyExpression generated = generator.createExpressionFromText(languageLevel, text);

        if (generated instanceof PyCallExpression) {
            PyCallExpression callExpression = (PyCallExpression) generated;

            return Stream.of(callExpression.getArguments())
                .filter(PyKeywordArgument.class::isInstance)
                .map(PyKeywordArgument.class::cast)
                .filter(kwarg -> keyword.equals(kwarg.getKeyword()))
                .findFirst()
                .orElse(null);
        }

        return null;
    }
}
