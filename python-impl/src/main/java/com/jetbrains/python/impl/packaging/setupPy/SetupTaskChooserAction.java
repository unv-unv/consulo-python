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
package com.jetbrains.python.impl.packaging.setupPy;

import com.jetbrains.python.impl.packaging.PyPackageUtil;
import com.jetbrains.python.impl.run.PythonTask;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import com.jetbrains.python.psi.PyFile;
import consulo.application.Application;
import consulo.ide.impl.idea.ide.actions.GotoActionBase;
import consulo.ide.impl.idea.ide.util.gotoByName.ChooseByNamePopup;
import consulo.ide.impl.idea.ide.util.gotoByName.ChooseByNamePopupComponent;
import consulo.ide.impl.idea.ide.util.gotoByName.ListChooseByNameModel;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.process.ExecutionException;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.awt.Messages;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class SetupTaskChooserAction extends AnAction implements AnActionWithSyncUpdate {
    public SetupTaskChooserAction() {
        super(LocalizeValue.localizeTODO("Run setup.py Task..."));
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        final Module module = e.getRequiredData(Module.KEY);
        Project project = module.getProject();
        ListChooseByNameModel<SetupTask> model =
            new ListChooseByNameModel<>(project, "Enter setup.py task name", "No tasks found", SetupTaskIntrospector.getTaskList(module));
        ChooseByNamePopup popup = ChooseByNamePopup.createPopup(project, model, GotoActionBase.getPsiContext(e));
        popup.setShowListForEmptyPattern(true);

        Application application = project.getApplication();
        popup.invoke(
            new ChooseByNamePopupComponent.Callback() {
                @Override
                public void onClose() {
                }

                @Override
                public void elementChosen(Object element) {
                    if (element != null) {
                        SetupTask task = (SetupTask) element;
                        application.invokeLater(() -> runSetupTask(task.getName(), module), application.getNoneModalityState());
                    }
                }
            },
            application.getCurrentModalityState(),
            false
        );
    }

    @Override
    public void update(AnActionEvent e) {
        Module module = e.getData(Module.KEY);
        e.getPresentation().setEnabled(module != null && PyPackageUtil.hasSetupPy(module) && PythonSdkType.findPythonSdk(module) != null);
    }

    @RequiredUIAccess
    public static void runSetupTask(String taskName, Module module) {
        PyFile setupPy = PyPackageUtil.findSetupPy(module);
        try {
            List<SetupTask.Option> options = SetupTaskIntrospector.getSetupTaskOptions(module, taskName);
            List<String> parameters = new ArrayList<>();
            parameters.add(taskName);
            if (options != null) {
                SetupTaskDialog dialog = new SetupTaskDialog(module.getProject(), taskName, options);
                if (!dialog.showAndGet()) {
                    return;
                }
                parameters.addAll(dialog.getCommandLine());
            }
            PythonTask task = new PythonTask(module, taskName);
            VirtualFile virtualFile = setupPy.getVirtualFile();
            task.setRunnerScript(virtualFile.getPath());
            task.setWorkingDirectory(virtualFile.getParent().getPath());
            task.setParameters(parameters);
            task.setAfterCompletion(() -> LocalFileSystem.getInstance().refresh(true));
            task.run(null, null);
        }
        catch (ExecutionException ee) {
            Messages.showErrorDialog(module.getProject(), "Failed to run task: " + ee.getMessage(), taskName);
        }
    }
}
