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

package com.jetbrains.python.facet;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.DefaultComboBoxModel;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTable;
import com.intellij.openapi.projectRoots.ui.SingleSdkEditor;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.jetbrains.python.sdk.PySdkListCellRenderer;
import com.jetbrains.python.sdk.PythonSdkType;

/**
 * @author yole
 */
public class PythonSdkComboBox extends ComboboxWithBrowseButton {
  private Project myProject;

  public PythonSdkComboBox() {
    getComboBox().setRenderer(new PySdkListCellRenderer("<No Interpreter>", null));
    addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        Sdk selectedSdk = getSelectedSdk();
        final Project project = myProject != null ? myProject : ProjectManager.getInstance().getDefaultProject();
        SingleSdkEditor editor = new SingleSdkEditor(selectedSdk, project, PythonSdkComboBox.this);
        editor.show();
        if (editor.isOK()) {
          selectedSdk = editor.getSelectedSdk();
          updateSdkList(selectedSdk, false);
        }
      }
    });
    updateSdkList(null, true);
  }

  public void setProject(Project project) {
    myProject = project;
  }

  public void updateSdkList(Sdk sdkToSelect, boolean selectAnySdk) {
    final List<Sdk> sdkList = SdkTable.getInstance().getSdksOfType(PythonSdkType.getInstance());
    if (selectAnySdk && sdkList.size() > 0) {
      sdkToSelect = sdkList.get(0);
    }
    sdkList.add(0, null);
    getComboBox().setModel(new DefaultComboBoxModel(sdkList.toArray(new Sdk[sdkList.size()])));
    getComboBox().setSelectedItem(sdkToSelect);
  }

  public void updateSdkList() {
    updateSdkList((Sdk) getComboBox().getSelectedItem(), false);
  }

  public Sdk getSelectedSdk() {
    return (Sdk) getComboBox().getSelectedItem();
  }
}
