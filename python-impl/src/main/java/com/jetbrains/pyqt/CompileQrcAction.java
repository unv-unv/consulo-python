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
package com.jetbrains.pyqt;

import consulo.execution.RunContentExecutor;
import consulo.execution.process.ProcessTerminatedListener;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.module.Module;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.ProcessHandlerBuilder;
import consulo.process.cmd.GeneralCommandLine;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.TextFieldWithBrowseButton;
import consulo.util.io.FileUtil;
import consulo.virtualFileSystem.VirtualFile;

import javax.swing.*;

/**
 * @author yole
 */
public class CompileQrcAction extends AnAction implements AnActionWithSyncUpdate {
    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        VirtualFile[] vFiles = e.getRequiredData(VirtualFile.KEY_OF_ARRAY);
        Module module = e.getRequiredData(Module.KEY);
        String path = QtFileType.findQtTool(module, "pyrcc4");
        if (path == null) {
            path = QtFileType.findQtTool(module, "pyside-rcc");
        }
        if (path == null) {
            Messages.showErrorDialog(project, "Could not find pyrcc4 or pyside-rcc for selected Python interpreter", "Compile .qrc file");
            return;
        }
        CompileQrcDialog dialog = new CompileQrcDialog(project, vFiles);
        dialog.show();
        if (dialog.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
            return;
        }

        GeneralCommandLine cmdLine = new GeneralCommandLine();
        cmdLine.setPassParentEnvironment(true);
        cmdLine.setExePath(path);
        cmdLine.addParameters("-o", dialog.getOutputPath());
        for (VirtualFile vFile : vFiles) {
            cmdLine.addParameter(vFile.getPath());
        }
        try {
            ProcessHandler process = ProcessHandlerBuilder.create(cmdLine).build();
            ProcessTerminatedListener.attach(process);
            new RunContentExecutor(project, process)
                .withTitle("Compile .qrc")
                .run();
        }
        catch (ExecutionException ex) {
            Messages.showErrorDialog(project, "Error running " + path + ": " + ex.getMessage(), "Compile .qrc file");
        }
    }

    @Override
    public void update(AnActionEvent e) {
        Module module = e.getData(Module.KEY);
        VirtualFile[] vFiles = e.getData(VirtualFile.KEY_OF_ARRAY);
        e.getPresentation().setVisible(module != null && filesAreQrc(vFiles));
    }

    private static boolean filesAreQrc(VirtualFile[] vFiles) {
        if (vFiles == null || vFiles.length == 0) {
            return false;
        }
        for (VirtualFile vFile : vFiles) {
            if (!FileUtil.extensionEquals(vFile.getName(), "qrc")) {
                return false;
            }
        }
        return true;
    }

    public static class CompileQrcDialog extends DialogWrapper {
        private JPanel myPanel;
        private TextFieldWithBrowseButton myOutputFileField;

        protected CompileQrcDialog(Project project, VirtualFile[] vFiles) {
            super(project);
            if (vFiles.length == 1) {
                setTitle("Compile " + vFiles[0].getName());
            }
            else {
                setTitle("Compile " + vFiles.length + " .qrc files");
            }
            myOutputFileField.addBrowseFolderListener(
                "Select output path:",
                null,
                project,
                FileChooserDescriptorFactory.createSingleLocalFileDescriptor()
            );
            init();
        }

        @Override
        protected JComponent createCenterPanel() {
            return myPanel;
        }

        public String getOutputPath() {
            return myOutputFileField.getText();
        }

        @Override
        @RequiredUIAccess
        public JComponent getPreferredFocusedComponent() {
            return myOutputFileField;
        }
    }
}
