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
package com.jetbrains.python.rest.run.docutils;

import com.jetbrains.rest.RestFileType;
import com.jetbrains.python.rest.run.RestRunConfiguration;
import com.jetbrains.python.rest.run.RestRunConfigurationType;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.execution.RunnerAndConfigurationSettings;
import consulo.execution.action.ConfigurationContext;
import consulo.execution.action.Location;
import consulo.execution.action.RuntimeConfigurationProducer;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.module.Module;
import consulo.project.Project;
import consulo.util.io.FileUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;

import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * @author catherine
 */
@ExtensionImpl
public class DocutilsConfigurationProducer extends RuntimeConfigurationProducer implements Cloneable {
  private PsiFile mySourceFile = null;

  public DocutilsConfigurationProducer() {
    super(RestRunConfigurationType.getInstance().DOCUTILS_FACTORY);
  }

  @Override
  public PsiElement getSourceElement() {
    return mySourceFile;
  }

  @Override
  @RequiredReadAction
  protected RunnerAndConfigurationSettings createConfigurationByElement(Location location, ConfigurationContext context) {
    PsiFile script = location.getPsiElement().getContainingFile();
    if (script == null || script.getFileType() != RestFileType.INSTANCE) {
      return null;
    }
    Module module = script.getModule();
    mySourceFile = script;

    Project project = mySourceFile.getProject();
    RunnerAndConfigurationSettings settings = cloneTemplateConfiguration(project, context);
    DocutilsRunConfiguration configuration = (DocutilsRunConfiguration) settings.getConfiguration();
    VirtualFile vFile = mySourceFile.getVirtualFile();
    if (vFile == null) return null;
    configuration.setInputFile(vFile.getPath());
    configuration.setName(script.getName());

    String outputPath = vFile.getPath();
    int index = outputPath.lastIndexOf('.');
    if (index > 0) outputPath = outputPath.substring(0, index);
    outputPath += ".html";
    VirtualFile outputFile = LocalFileSystem.getInstance().findFileByPath(outputPath);
    if (outputFile == null) {
      configuration.setOutputFile(outputPath);
      configuration.setOpenInBrowser(true);
    }

    if (configuration.getTask().isEmpty())
      configuration.setTask("rst2html");
    VirtualFile parent = vFile.getParent();
    if (parent != null) {
      configuration.setWorkingDirectory(parent.getPath());
    }
    configuration.setName(configuration.suggestedName());
    if (module != null) {
      configuration.setUseModuleSdk(true);
      configuration.setModule(module);
    }
    return settings;
  }

  @Nullable
  @Override
  protected RunnerAndConfigurationSettings findExistingByElement(Location location,
                                                                 List<RunnerAndConfigurationSettings> existingConfigurations,
                                                                 ConfigurationContext context) {
    PsiFile script = location.getPsiElement().getContainingFile();
    if (script == null) {
      return null;
    }
    VirtualFile vFile = script.getVirtualFile();
    if (vFile == null) {
      return null;
    }
    String path = vFile.getPath();
    for (RunnerAndConfigurationSettings configuration : existingConfigurations) {
      String scriptName = ((RestRunConfiguration)configuration.getConfiguration()).getInputFile();
      if (FileUtil.toSystemIndependentName(scriptName).equals(FileUtil.toSystemIndependentName(path))) {
        return configuration;
      }
    }
    return null;
  }

  @Override
  public int compareTo(RuntimeConfigurationProducer o) {
    return PREFERED;
  }
}
