/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.impl.testing;

import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.run.AbstractPythonRunConfigurationParams;
import consulo.annotation.access.RequiredReadAction;
import consulo.execution.RuntimeConfigurationException;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.RuntimeConfigurationError;
import consulo.language.editor.refactoring.event.RefactoringElementAdapter;
import consulo.language.editor.refactoring.event.RefactoringElementListener;
import consulo.language.editor.refactoring.event.RefactoringListenerProvider;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.io.FileUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.JDOMExternalizerUtil;
import consulo.util.xml.serializer.WriteExternalException;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import org.jdom.Element;
import org.jspecify.annotations.Nullable;

import java.io.File;

/**
 * @author catherine
 */
public abstract class AbstractPythonTestRunConfiguration extends AbstractPythonRunConfiguration implements AbstractPythonRunConfigurationParams, AbstractPythonTestRunConfigurationParams,
  RefactoringListenerProvider {
  protected String myClassName = "";
  protected String myScriptName = "";
  protected String myMethodName = "";
  protected String myFolderName = "";
  protected TestType myTestType = TestType.TEST_SCRIPT;

  private String myPattern = ""; // pattern for modules in folder to match against
  private boolean usePattern = false;

  @RequiredReadAction
  protected AbstractPythonTestRunConfiguration(Project project, ConfigurationFactory configurationFactory) {
    super(project, configurationFactory);
  }

  @Override
  public String getWorkingDirectorySafe() {
    String workingDirectoryFromConfig = getWorkingDirectory();
    if (StringUtil.isNotEmpty(workingDirectoryFromConfig)) {
      return workingDirectoryFromConfig;
    }

    String folderName = myFolderName;
    if (!StringUtil.isEmptyOrSpaces(folderName)) {
      return folderName;
    }
    String scriptName = myScriptName;
    if (!StringUtil.isEmptyOrSpaces(scriptName)) {
      VirtualFile script = LocalFileSystem.getInstance().findFileByPath(scriptName);
      if (script != null) {
        return script.getParent().getPath();
      }
    }
    return super.getWorkingDirectorySafe();
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    myScriptName = JDOMExternalizerUtil.readField(element, "SCRIPT_NAME");
    myClassName = JDOMExternalizerUtil.readField(element, "CLASS_NAME");
    myMethodName = JDOMExternalizerUtil.readField(element, "METHOD_NAME");
    myFolderName = JDOMExternalizerUtil.readField(element, "FOLDER_NAME");

    myPattern = JDOMExternalizerUtil.readField(element, "PATTERN");
    usePattern = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, "USE_PATTERN"));

    try {
      String testType = JDOMExternalizerUtil.readField(element, "TEST_TYPE");
      myTestType = testType != null ? TestType.valueOf(testType) : TestType.TEST_SCRIPT;
    }
    catch (IllegalArgumentException e) {
      myTestType = TestType.TEST_SCRIPT; // safe default
    }
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);

    JDOMExternalizerUtil.writeField(element, "SCRIPT_NAME", myScriptName);
    JDOMExternalizerUtil.writeField(element, "CLASS_NAME", myClassName);
    JDOMExternalizerUtil.writeField(element, "METHOD_NAME", myMethodName);
    JDOMExternalizerUtil.writeField(element, "FOLDER_NAME", myFolderName);
    JDOMExternalizerUtil.writeField(element, "TEST_TYPE", myTestType.toString());
    JDOMExternalizerUtil.writeField(element, "PATTERN", myPattern);
    JDOMExternalizerUtil.writeField(element, "USE_PATTERN", String.valueOf(usePattern));
  }

  @Override
  public AbstractPythonRunConfigurationParams getBaseParams() {
    return this;
  }

  @Override
  public String getClassName() {
    return myClassName;
  }

  @Override
  public void setClassName(String className) {
    myClassName = className;
  }

  @Override
  public String getFolderName() {
    return myFolderName;
  }

  @Override
  public void setFolderName(String folderName) {
    myFolderName = folderName;
  }

  @Override
  public String getScriptName() {
    return myScriptName;
  }

  @Override
  public void setScriptName(String scriptName) {
    myScriptName = scriptName;
  }

  @Override
  public String getMethodName() {
    return myMethodName;
  }

  @Override
  public void setMethodName(String methodName) {
    myMethodName = methodName;
  }

  @Override
  public TestType getTestType() {
    return myTestType;
  }

  @Override
  public void setTestType(TestType testType) {
    myTestType = testType;
  }

  @Override
  public String getPattern() {
    return myPattern;
  }

  @Override
  public void setPattern(String pattern) {
    myPattern = pattern;
  }

  @Override
  public boolean usePattern() {
    return usePattern;
  }

  @Override
  public void usePattern(boolean usePattern) {
    this.usePattern = usePattern;
  }

  public enum TestType {
    TEST_FOLDER,
    TEST_SCRIPT,
    TEST_CLASS,
    TEST_METHOD,
    TEST_FUNCTION,
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    super.checkConfiguration();

    if (StringUtil.isEmptyOrSpaces(myFolderName) && myTestType == TestType.TEST_FOLDER) {
      throw new RuntimeConfigurationError(PyLocalize.runcfgUnittestNo_folder_name());
    }

    if (StringUtil.isEmptyOrSpaces(getScriptName()) && myTestType != TestType.TEST_FOLDER) {
      throw new RuntimeConfigurationError(PyLocalize.runcfgUnittestNo_script_name());
    }

    if (StringUtil.isEmptyOrSpaces(myClassName) && (myTestType == TestType.TEST_METHOD || myTestType == TestType.TEST_CLASS)) {
      throw new RuntimeConfigurationError(PyLocalize.runcfgUnittestNo_class_name());
    }

    if (StringUtil.isEmptyOrSpaces(myMethodName) && (myTestType == TestType.TEST_METHOD || myTestType == TestType.TEST_FUNCTION)) {
      throw new RuntimeConfigurationError(PyLocalize.runcfgUnittestNo_method_name());
    }
  }

  public boolean compareSettings(AbstractPythonTestRunConfiguration cfg) {
    if (cfg == null) {
      return false;
    }

    if (getTestType() != cfg.getTestType()) {
      return false;
    }

    switch (getTestType()) {
      case TEST_FOLDER:
        return getFolderName().equals(cfg.getFolderName());
      case TEST_SCRIPT:
        return getScriptName().equals(cfg.getScriptName()) && getWorkingDirectory().equals(cfg.getWorkingDirectory());
      case TEST_CLASS:
        return getScriptName().equals(cfg.getScriptName()) &&
          getWorkingDirectory().equals(cfg.getWorkingDirectory()) &&
          getClassName().equals(cfg.getClassName());
      case TEST_METHOD:
        return getScriptName().equals(cfg.getScriptName()) &&
          getWorkingDirectory().equals(cfg.getWorkingDirectory()) &&
          getClassName().equals(cfg.getClassName()) &&
          getMethodName().equals(cfg.getMethodName());
      case TEST_FUNCTION:
        return getScriptName().equals(cfg.getScriptName()) &&
          getWorkingDirectory().equals(cfg.getWorkingDirectory()) &&
          getMethodName().equals(cfg.getMethodName());
      default:
        throw new IllegalStateException("Unknown test type: " + getTestType());
    }
  }

  public static void copyParams(AbstractPythonTestRunConfigurationParams source, AbstractPythonTestRunConfigurationParams target) {
    AbstractPythonRunConfiguration.copyParams(source.getBaseParams(), target.getBaseParams());
    target.setScriptName(source.getScriptName());
    target.setClassName(source.getClassName());
    target.setFolderName(source.getFolderName());
    target.setMethodName(source.getMethodName());
    target.setTestType(source.getTestType());
    target.setPattern(source.getPattern());
    target.usePattern(source.usePattern());
    target.setAddContentRoots(source.shouldAddContentRoots());
    target.setAddSourceRoots(source.shouldAddSourceRoots());
  }

  public AbstractPythonTestRunConfigurationParams getTestRunConfigurationParams() {
    return this;
  }

  @Override
  public String suggestedName() {
    switch (myTestType) {
      case TEST_CLASS:
        return getPluralTitle() + " in " + myClassName;
      case TEST_METHOD:
        return getTitle() + " " + myClassName + "." + myMethodName;
      case TEST_SCRIPT:
        String name = new File(getScriptName()).getName();
        name = StringUtil.trimEnd(name, ".py");
        return getPluralTitle() + " in " + name;
      case TEST_FOLDER:
        String folderName = new File(myFolderName).getName();
        return getPluralTitle() + " in " + folderName;
      case TEST_FUNCTION:
        return getTitle() + " " + myMethodName;
      default:
        throw new IllegalStateException("Unknown test type: " + myTestType);
    }
  }

  @Nullable
  @Override
  public String getActionName() {
    if (TestType.TEST_METHOD.equals(myTestType)) {
      return getTitle() + " " + myMethodName;
    }
    return suggestedName();
  }

  protected abstract String getTitle();

  protected abstract String getPluralTitle();

  @RequiredReadAction
  @Override
  public RefactoringElementListener getRefactoringElementListener(PsiElement element) {
    if (element instanceof PsiDirectory) {
      VirtualFile vFile = ((PsiDirectory)element).getVirtualFile();
      if ((myTestType == TestType.TEST_FOLDER && pathsEqual(vFile, myFolderName)) || pathsEqual(vFile, getWorkingDirectory())) {
        return new RefactoringElementAdapter() {
          @Override
          protected void elementRenamedOrMoved(PsiElement newElement) {
            String newPath = FileUtil.toSystemDependentName(((PsiDirectory)newElement).getVirtualFile().getPath());
            setWorkingDirectory(newPath);
            if (myTestType == TestType.TEST_FOLDER) {
              myFolderName = newPath;
            }
          }

          @Override
          public void undoElementMovedOrRenamed(PsiElement newElement, String oldQualifiedName) {
            String systemDependant = FileUtil.toSystemDependentName(oldQualifiedName);
            setWorkingDirectory(systemDependant);
            if (myTestType == TestType.TEST_FOLDER) {
              myFolderName = systemDependant;
            }
          }
        };
      }
      return null;
    }
    if (myTestType == TestType.TEST_FOLDER) {
      return null;
    }
    File scriptFile = new File(myScriptName);
    if (!scriptFile.isAbsolute()) {
      scriptFile = new File(getWorkingDirectory(), myScriptName);
    }
    PsiFile containingFile = element.getContainingFile();
    VirtualFile vFile = containingFile == null ? null : containingFile.getVirtualFile();
    if (vFile != null && Comparing.equal(new File(vFile.getPath()).getAbsolutePath(), scriptFile.getAbsolutePath())) {
      if (element instanceof PsiFile) {
        return new RefactoringElementAdapter() {
          @Override
          protected void elementRenamedOrMoved(PsiElement newElement) {
            VirtualFile virtualFile = ((PsiFile)newElement).getVirtualFile();
            if (virtualFile != null) {
              myScriptName = FileUtil.toSystemDependentName(virtualFile.getPath());
            }
          }

          @Override
          public void undoElementMovedOrRenamed(PsiElement newElement, String oldQualifiedName) {
            myScriptName = FileUtil.toSystemDependentName(oldQualifiedName);
          }
        };
      }
      if (element instanceof PyClass pyClass && (myTestType == TestType.TEST_CLASS || myTestType == TestType.TEST_METHOD) &&
        Comparing.equal(pyClass.getName(), myClassName)) {
        return new RefactoringElementAdapter() {
          @Override
          @RequiredReadAction
          protected void elementRenamedOrMoved(PsiElement newElement) {
            myClassName = ((PyClass) newElement).getName();
          }

          @Override
          public void undoElementMovedOrRenamed(PsiElement newElement, String oldQualifiedName) {
            myClassName = oldQualifiedName;
          }
        };
      }
      if (element instanceof PyFunction function && Comparing.equal(function.getName(), myMethodName)) {
        ScopeOwner scopeOwner = PsiTreeUtil.getParentOfType(function, ScopeOwner.class);
        if ((myTestType == TestType.TEST_FUNCTION && scopeOwner instanceof PyFile)
            || (myTestType == TestType.TEST_METHOD && scopeOwner instanceof PyClass && Comparing.equal(scopeOwner.getName(), myClassName))) {
          return new RefactoringElementAdapter() {
            @Override
            @RequiredReadAction
            protected void elementRenamedOrMoved(PsiElement newElement) {
              myMethodName = ((PyFunction)newElement).getName();
            }

            @Override
            public void undoElementMovedOrRenamed(PsiElement newElement, String oldQualifiedName) {
              int methodIdx = oldQualifiedName.indexOf("#") + 1;
              if (methodIdx > 0 && methodIdx < oldQualifiedName.length()) {
                myMethodName = oldQualifiedName.substring(methodIdx);
              }
            }
          };
        }
      }
    }
    return null;
  }

  private static boolean pathsEqual(VirtualFile vFile, String folderName) {
    return Comparing.equal(new File(vFile.getPath()).getAbsolutePath(), new File(folderName).getAbsolutePath());
  }
}
