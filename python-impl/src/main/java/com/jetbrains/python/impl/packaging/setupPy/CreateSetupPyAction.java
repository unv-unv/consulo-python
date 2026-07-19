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
package com.jetbrains.python.impl.packaging.setupPy;

import com.jetbrains.python.impl.packaging.PyPackageUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.ApplicationPropertiesComponent;
import consulo.dataContext.DataContext;
import consulo.fileTemplate.AttributesDefaults;
import consulo.fileTemplate.FileTemplate;
import consulo.fileTemplate.FileTemplateManager;
import consulo.ide.action.ui.CreateFromTemplateDialog;
import consulo.ide.impl.idea.ide.fileTemplates.actions.CreateFromTemplateAction;
import consulo.language.editor.util.IdeView;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.ProjectFileIndex;
import consulo.platform.Platform;
import consulo.project.Project;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithAsyncUpdate;
import consulo.ui.ex.action.coroutine.ActionSafeReadLock;
import consulo.util.concurrent.coroutine.Coroutine;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;

import java.util.Collection;
import java.util.Properties;

/**
 * @author yole
 */
public class CreateSetupPyAction extends CreateFromTemplateAction implements AnActionWithAsyncUpdate {
    private static final String AUTHOR_PROPERTY = "python.packaging.author";
    private static final String EMAIL_PROPERTY = "python.packaging.author.email";

    public CreateSetupPyAction() {
        super(FileTemplateManager.getDefaultInstance().getInternalTemplate("Setup Script"));
        getTemplatePresentation().setText(LocalizeValue.localizeTODO("Create setup.py"));
    }

    @Override
    public FileTemplate getTemplate() {
        // to ensure changes are picked up, reload the template on every call (PY-6681)
        return FileTemplateManager.getDefaultInstance().getInternalTemplate("Setup Script");
    }

    @Override
    public Coroutine<?, ?> updateAsync(AnActionEvent e) {
        return ActionSafeReadLock.run(e, presentation -> {
            Module module = e.getData(Module.KEY);
            e.getPresentation().setEnabled(module != null && PyPackageUtil.findSetupPy(module) == null);
        }).toCoroutine();
    }

    @Override
    public AttributesDefaults getAttributesDefaults(DataContext dataContext) {
        Project project = dataContext.getData(Project.KEY);
        AttributesDefaults defaults = new AttributesDefaults("setup.py").withFixedName(true);
        if (project != null) {
            defaults.add("Package_name", project.getName());
            ApplicationPropertiesComponent properties = ApplicationPropertiesComponent.getInstance();
            defaults.add("Author", properties.getValue(AUTHOR_PROPERTY, Platform.current().user().name()));
            defaults.add("Author_Email", properties.getValue(EMAIL_PROPERTY, ""));
            defaults.addPredefined("PackageList", getPackageList(dataContext));
            defaults.addPredefined("PackageDirs", getPackageDirs(dataContext));
        }
        return defaults;
    }

    private static String getPackageList(DataContext dataContext) {
        Module module = dataContext.getData(Module.KEY);
        if (module != null) {
            return "['" + StringUtil.join(PyPackageUtil.getPackageNames(module), "', '") + "']";
        }
        return "[]";
    }

    private static String getPackageDirs(DataContext dataContext) {
        Module module = dataContext.getData(Module.KEY);
        if (module != null) {
            VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots();
            if (sourceRoots.length > 0) {
                for (VirtualFile sourceRoot : sourceRoots) {
                    // TODO notify if we have multiple source roots and can't build mapping automatically
                    VirtualFile contentRoot = ProjectFileIndex.getInstance(module.getProject()).getContentRootForFile(sourceRoot);
                    if (contentRoot != null && !Comparing.equal(contentRoot, sourceRoot)) {
                        String relativePath = VirtualFileUtil.getRelativePath(sourceRoot, contentRoot, '/');
                        return "\n    package_dir={'': '" + relativePath + "'},";
                    }
                }
            }
        }
        return "";
    }

    @Override
    @RequiredReadAction
    protected PsiDirectory getTargetDirectory(DataContext dataContext, IdeView view) {
        Module module = dataContext.getData(Module.KEY);
        if (module != null) {
            Collection<VirtualFile> sourceRoots = PyUtil.getSourceRoots(module);
            if (sourceRoots.size() > 0) {
                return PsiManager.getInstance(module.getProject()).findDirectory(sourceRoots.iterator().next());
            }
        }
        return super.getTargetDirectory(dataContext, view);
    }

    @Override
    protected void elementCreated(CreateFromTemplateDialog dialog, PsiElement createdElement) {
        ApplicationPropertiesComponent propertiesComponent = ApplicationPropertiesComponent.getInstance();
        Properties properties = dialog.getEnteredProperties();
        String author = properties.getProperty("Author");
        if (author != null) {
            propertiesComponent.setValue(AUTHOR_PROPERTY, author);
        }
        String authorEmail = properties.getProperty("Author_Email");
        if (authorEmail != null) {
            propertiesComponent.setValue(EMAIL_PROPERTY, authorEmail);
        }
    }
}
