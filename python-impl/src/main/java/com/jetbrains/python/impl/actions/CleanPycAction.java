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
import consulo.application.Application;
import consulo.application.progress.ProgressManager;
import consulo.application.util.AsyncFileService;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.util.io.FileUtil;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class CleanPycAction extends AnAction implements AnActionWithSyncUpdate {
    private static void collectPycFiles(File directory, List<File> pycFiles) {
        FileUtil.processFilesRecursively(
            directory,
            file -> {
                if (file.getParentFile().getName().equals(PyNames.PYCACHE)
                    || FileUtil.extensionEquals(file.getName(), "pyc")
                    || FileUtil.extensionEquals(file.getName(), "pyo")) {
                    pycFiles.add(file);
                }
                return true;
            }
        );
    }

    private static boolean isAllDirectories(@Nullable PsiElement[] elements) {
        if (elements == null || elements.length == 0) {
            return false;
        }
        for (PsiElement element : elements) {
            if (!(element instanceof PsiDirectory)) {
                return false;
            }
        }
        return true;
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        PsiElement[] elements = e.getData(PsiElement.KEY_OF_ARRAY);
        if (elements == null) {
            return;
        }
        List<File> pycFiles = new ArrayList<>();
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
            () -> {
                for (PsiElement element : elements) {
                    PsiDirectory dir = (PsiDirectory) element;
                    collectPycFiles(new File(dir.getVirtualFile().getPath()), pycFiles);
                }
                Application.get().getInstance(AsyncFileService.class).asyncDelete(pycFiles);
            },
            "Cleaning up .py files...",
            false,
            e.getData(Project.KEY)
        );
    }

    @Override
    public void update(AnActionEvent e) {
        if (e.getPresentation().isVisible()) {
            PsiElement[] elements = e.getData(PsiElement.KEY_OF_ARRAY);
            e.getPresentation().setEnabled(isAllDirectories(elements));
        }
    }
}
