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

import consulo.python.impl.icon.PythonImplIconGroup;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.module.Module;
import consulo.application.dumb.DumbAware;
import consulo.project.Project;
import consulo.content.bundle.Sdk;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.util.lang.Pair;
import consulo.ui.annotation.RequiredUIAccess;


/**
 * @author oleg
 */
public class RunPythonConsoleAction extends AnAction implements DumbAware, AnActionWithSyncUpdate {
    public RunPythonConsoleAction() {
        super();
        getTemplatePresentation().setIcon(PythonImplIconGroup.pythonPythonconsole());
    }

    @Override
    public void update(AnActionEvent e) {
        e.getPresentation().setVisible(true);
        e.getPresentation().setEnabled(false);
        Project project = e.getData(Project.KEY);
        if (project != null) {
            Pair<Sdk, Module> sdkAndModule = PydevConsoleRunner.findPythonSdkAndModule(project, e.getData(Module.KEY));
            if (sdkAndModule.first != null) {
                e.getPresentation().setEnabled(true);
            }
        }
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getRequiredData(Project.KEY);

        PythonConsoleRunnerFactory runnerFactory = PythonConsoleRunnerFactory.getInstance();

        PydevConsoleRunner runner = runnerFactory.createConsoleRunner(project, e.getData(Module.KEY));
        runner.open();
    }
}
