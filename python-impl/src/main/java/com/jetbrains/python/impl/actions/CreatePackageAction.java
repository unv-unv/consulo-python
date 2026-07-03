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
package com.jetbrains.python.impl.actions;

import com.jetbrains.python.PyNames;
import consulo.fileTemplate.FileTemplate;
import consulo.fileTemplate.FileTemplateManager;
import consulo.fileTemplate.FileTemplateUtil;
import consulo.ide.impl.idea.ide.actions.CreateDirectoryOrPackageHandler;
import consulo.ide.localize.IdeLocalize;
import consulo.ide.util.DirectoryChooserUtil;
import consulo.language.editor.util.IdeView;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileFactory;
import consulo.language.psi.PsiFileSystemItem;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.python.module.extension.PyModuleExtension;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.image.Image;
import org.jspecify.annotations.Nullable;

/**
 * @author yole
 */
public class CreatePackageAction extends DumbAwareAction implements AnActionWithSyncUpdate {
    private static final Logger LOG = Logger.getInstance(CreatePackageAction.class);

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        IdeView view = e.getRequiredData(IdeView.KEY);
        final Project project = e.getRequiredData(Project.KEY);
        final PsiDirectory directory = DirectoryChooserUtil.getOrChooseDirectory(view);

        if (directory == null) {
            return;
        }
        CreateDirectoryOrPackageHandler validator =
            new CreateDirectoryOrPackageHandler(project, directory, consulo.ide.impl.actions.CreateDirectoryOrPackageType.Package, ".") {
                @Override
                @RequiredUIAccess
                protected void createDirectories(String subDirName) {
                    super.createDirectories(subDirName);
                    if (getCreatedElement() instanceof PsiDirectory subDir) {
                        createInitPyInHierarchy(subDir, directory);
                    }
                }
            };
        Messages.showInputDialog(
            project,
            IdeLocalize.promptEnterANewPackageName().get(),
            IdeLocalize.titleNewPackage().get(),
            UIUtil.getQuestionIcon(),
            "",
            validator
        );
        PsiFileSystemItem result = validator.getCreatedElement();
        if (result != null) {
            view.selectElement(result);
        }
    }

    @RequiredUIAccess
    public static void createInitPyInHierarchy(PsiDirectory created, PsiDirectory ancestor) {
        do {
            createInitPy(created);
            created = created.getParent();
        }
        while (created != null && created != ancestor);
    }

    @RequiredUIAccess
    private static void createInitPy(PsiDirectory directory) {
        FileTemplateManager fileTemplateManager = FileTemplateManager.getInstance(directory.getProject());
        FileTemplate template = fileTemplateManager.getInternalTemplate("Python Script");
        if (directory.findFile(PyNames.INIT_DOT_PY) != null) {
            return;
        }
        if (template != null) {
            try {
                FileTemplateUtil.createFromTemplate(template, PyNames.INIT_DOT_PY, fileTemplateManager.getDefaultVariables(), directory);
            }
            catch (Exception e) {
                LOG.error(e);
            }
        }
        else {
            PsiFile file = PsiFileFactory.getInstance(directory.getProject()).createFileFromText(PyNames.INIT_DOT_PY, "");
            directory.add(file);
        }
    }

    @Override
    public void update(AnActionEvent e) {
        boolean enabled = isEnabled(e) && e.getPresentation().isEnabled();
        e.getPresentation().setEnabledAndVisible(enabled);
    }

    @Nullable
    @Override
    protected Image getTemplateIcon() {
        return PlatformIconGroup.nodesPackage();
    }

    private static boolean isEnabled(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        IdeView ideView = e.getData(IdeView.KEY);
        if (project == null || ideView == null) {
            return false;
        }
        PsiDirectory[] directories = ideView.getDirectories();
        if (directories.length == 0) {
            return false;
        }
        Module module = e.getData(Module.KEY);
        return module != null && module.getExtension(PyModuleExtension.class) != null;
    }
}
