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
package com.jetbrains.python.impl.console;

import consulo.application.dumb.DumbAware;
import consulo.application.ui.wm.ApplicationIdeFocusManager;
import consulo.dataContext.DataContext;
import consulo.execution.ExecutionHelper;
import consulo.execution.icon.ExecutionIconGroup;
import consulo.execution.ui.RunContentDescriptor;
import consulo.process.ProcessHandler;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * @author traff
 */
public class PyOpenDebugConsoleAction extends AnAction implements DumbAware, AnActionWithSyncUpdate {
    public PyOpenDebugConsoleAction() {
        super();
        getTemplatePresentation().setIcon(ExecutionIconGroup.console());
    }

    @Override
    public void update(AnActionEvent e) {
        e.getPresentation().setVisible(false);
        e.getPresentation().setEnabled(true);
        Project project = e.getData(Project.KEY);
        if (project != null) {
            e.getPresentation().setVisible(getConsoles(project).size() > 0);
        }
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getRequiredData(Project.KEY);
        selectRunningProcess(
            e.getDataContext(),
            project,
            view -> {
                view.enableConsole(false);
                ApplicationIdeFocusManager.getInstance()
                    .getInstanceForProject(project)
                    .requestFocus(view.getPydevConsoleView().getComponent(), true);
            }
        );
    }


    private static void selectRunningProcess(
        DataContext dataContext,
        Project project,
        Consumer<PythonDebugLanguageConsoleView> consumer
    ) {
        Collection<RunContentDescriptor> consoles = getConsoles(project);

        ExecutionHelper.selectContentDescriptor(
            dataContext,
            project,
            consoles,
            "Select running python process",
            descriptor -> {
                if (descriptor != null && descriptor.getExecutionConsole() instanceof PythonDebugLanguageConsoleView consoleView) {
                    consumer.accept(consoleView);
                }
            }
        );
    }

    private static Collection<RunContentDescriptor> getConsoles(Project project) {
        return ExecutionHelper.findRunningConsole(
            project,
            dom -> dom.getExecutionConsole() instanceof PythonDebugLanguageConsoleView && isAlive(dom)
        );
    }

    private static boolean isAlive(RunContentDescriptor dom) {
        ProcessHandler processHandler = dom.getProcessHandler();
        return processHandler != null && !processHandler.isProcessTerminated();
    }
}
