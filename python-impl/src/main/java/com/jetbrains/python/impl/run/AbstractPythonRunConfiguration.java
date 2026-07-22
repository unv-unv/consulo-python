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
package com.jetbrains.python.impl.run;

import com.google.common.collect.Lists;
import com.jetbrains.python.impl.sdk.PythonEnvUtil;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import com.jetbrains.python.impl.testing.PyPsiLocationWithFixedClass;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.run.AbstractPythonRunConfigurationParams;
import consulo.annotation.access.RequiredReadAction;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkType;
import consulo.execution.RuntimeConfigurationException;
import consulo.execution.action.Location;
import consulo.execution.configuration.AbstractRunConfiguration;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.LocatableConfiguration;
import consulo.execution.configuration.RuntimeConfigurationError;
import consulo.execution.configuration.log.ui.LogConfigurationPanel;
import consulo.execution.configuration.ui.SettingsEditor;
import consulo.execution.configuration.ui.SettingsEditorGroup;
import consulo.execution.localize.ExecutionLocalize;
import consulo.execution.test.AbstractTestProxy;
import consulo.execution.ui.awt.EnvironmentVariablesComponent;
import consulo.ide.impl.idea.util.PathMappingSettings;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.module.content.ModuleRootManager;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.cmd.ParamsGroup;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.python.module.extension.PyModuleExtension;
import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.JDOMExternalizerUtil;
import consulo.util.xml.serializer.WriteExternalException;
import consulo.virtualFileSystem.VirtualFile;
import org.jdom.Element;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Leonid Shalupov
 */
public abstract class AbstractPythonRunConfiguration<T extends AbstractPythonRunConfiguration> extends AbstractRunConfiguration implements LocatableConfiguration,
		AbstractPythonRunConfigurationParams, CommandLinePatcher
{
	/**
	 * When passing path to test to runners, you should join parts with this char.
	 * I.e.: file.py::PyClassTest::test_method
	 */
	public static final String TEST_NAME_PARTS_SPLITTER = "::";
	private String myInterpreterOptions = "";
	private String myWorkingDirectory = "";
	private String mySdkHome = "";
	private boolean myUseModuleSdk;
	private boolean myAddContentRoots = true;
	private boolean myAddSourceRoots = true;

	protected PathMappingSettings myMappingSettings;
	/**
	 * To prevent "double module saving" child may enable this flag
	 * and no module info would be saved
	 */
	protected boolean mySkipModuleSerialization;

	@RequiredReadAction
    public AbstractPythonRunConfiguration(Project project, ConfigurationFactory factory)
	{
		super(project, factory);
		getConfigurationModule().init();
	}

	@Override
    public List<Module> getValidModules()
	{
		return getValidModules(getProject());
	}

	@Override
    public PathMappingSettings getMappingSettings()
	{
		return myMappingSettings;
	}

	@Override
    public void setMappingSettings(@Nullable PathMappingSettings mappingSettings)
	{
		myMappingSettings = mappingSettings;
	}

	public static List<Module> getValidModules(Project project)
	{
		Module[] modules = ModuleManager.getInstance(project).getModules();
		List<Module> result = Lists.newArrayList();
		for(Module module : modules)
		{
			if(PythonSdkType.findPythonSdk(module) != null)
			{
				result.add(module);
			}
		}
		return result;
	}

	public PyCommonOptionsFormData getCommonOptionsFormData()
	{
		return new PyCommonOptionsFormData()
		{
			@Override
			public Project getProject()
			{
				return AbstractPythonRunConfiguration.this.getProject();
			}

			@Override
			public List<Module> getValidModules()
			{
				return AbstractPythonRunConfiguration.this.getValidModules();
			}

			@Override
			public boolean showConfigureInterpretersLink()
			{
				return false;
			}
		};
	}

	@Override
	public final SettingsEditor<T> getConfigurationEditor()
	{
		SettingsEditor<T> runConfigurationEditor = PythonExtendedConfigurationEditor.create(createConfigurationEditor());

		SettingsEditorGroup<T> group = new SettingsEditorGroup<>();

		// run configuration settings tab:
		group.addEditor(ExecutionLocalize.runConfigurationConfigurationTabTitle(), runConfigurationEditor);

		// tabs provided by extensions:
		//noinspection unchecked
		PythonRunConfigurationExtensionsManager.getInstance().appendEditors(this, (SettingsEditorGroup) group);
		group.addEditor(ExecutionLocalize.logsTabTitle(), new LogConfigurationPanel<>());

		return group;
	}

	protected abstract SettingsEditor<T> createConfigurationEditor();

	@Override
	public void checkConfiguration() throws RuntimeConfigurationException
	{
		super.checkConfiguration();

		checkSdk();

		checkExtensions();
	}

	private void checkExtensions() throws RuntimeConfigurationException
	{
		try
		{
			PythonRunConfigurationExtensionsManager.getInstance().validateConfiguration(this, false);
		}
		catch(RuntimeConfigurationException e)
		{
			throw e;
		}
		catch(Exception ee)
		{
			throw new RuntimeConfigurationException(ee.getMessage());
		}
	}

	private void checkSdk() throws RuntimeConfigurationError
	{
		if(!myUseModuleSdk)
		{
			if(StringUtil.isEmptyOrSpaces(getSdkHome()))
			{
				throw new RuntimeConfigurationError(PyLocalize.runcfgUnittestNo_sdk());
			}
			else if(!PythonSdkType.getInstance().isValidSdkHome(getSdkHome()))
			{
				throw new RuntimeConfigurationError(PyLocalize.runcfgUnittestNo_valid_sdk());
			}
		}
		else
		{
			Sdk sdk = PythonSdkType.findPythonSdk(getModule());
			if(sdk == null)
			{
				throw new RuntimeConfigurationError(PyLocalize.runcfgUnittestNo_module_sdk());
			}
		}
	}

	@Override
    public String getSdkHome()
	{
		String sdkHome = mySdkHome;
		if(StringUtil.isEmptyOrSpaces(mySdkHome))
		{
			Sdk projectJdk = PythonSdkType.findPythonSdk(getModule());
			if(projectJdk != null)
			{
				sdkHome = projectJdk.getHomePath();
			}
		}
		return sdkHome;
	}

	@Nullable
	public String getInterpreterPath()
	{
		String sdkHome;
		if(myUseModuleSdk)
		{
			Sdk sdk = PythonSdkType.findPythonSdk(getModule());
			if(sdk == null)
			{
				return null;
			}
			sdkHome = sdk.getHomePath();
		}
		else
		{
			sdkHome = getSdkHome();
		}
		return sdkHome;
	}

	public Sdk getSdk()
	{
		if(myUseModuleSdk)
		{
			return PythonSdkType.findPythonSdk(getModule());
		}
		else
		{
			return PythonSdkType.findSdkByPath(getSdkHome());
		}
	}

	@Override
    public void readExternal(Element element) throws InvalidDataException
	{
		super.readExternal(element);
		myInterpreterOptions = JDOMExternalizerUtil.readField(element, "INTERPRETER_OPTIONS");
		readEnvs(element);
		mySdkHome = JDOMExternalizerUtil.readField(element, "SDK_HOME");
		myWorkingDirectory = JDOMExternalizerUtil.readField(element, "WORKING_DIRECTORY");
		myUseModuleSdk = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, "IS_MODULE_SDK"));
		String addContentRoots = JDOMExternalizerUtil.readField(element, "ADD_CONTENT_ROOTS");
		myAddContentRoots = addContentRoots == null || Boolean.parseBoolean(addContentRoots);
		String addSourceRoots = JDOMExternalizerUtil.readField(element, "ADD_SOURCE_ROOTS");
		myAddSourceRoots = addSourceRoots == null || Boolean.parseBoolean(addSourceRoots);
		if(!mySkipModuleSerialization)
		{
			getConfigurationModule().readExternal(element);
		}

		setMappingSettings(PathMappingSettings.readExternal(element));
		// extension settings:
		PythonRunConfigurationExtensionsManager.getInstance().readExternal(this, element);
	}

	protected void readEnvs(Element element)
	{
		String parentEnvs = JDOMExternalizerUtil.readField(element, "PARENT_ENVS");
		if(parentEnvs != null)
		{
			setPassParentEnvs(Boolean.parseBoolean(parentEnvs));
		}
		EnvironmentVariablesComponent.readExternal(element, getEnvs());
	}

	@Override
    public void writeExternal(Element element) throws WriteExternalException
	{
		super.writeExternal(element);
		JDOMExternalizerUtil.writeField(element, "INTERPRETER_OPTIONS", myInterpreterOptions);
		writeEnvs(element);
		JDOMExternalizerUtil.writeField(element, "SDK_HOME", mySdkHome);
		JDOMExternalizerUtil.writeField(element, "WORKING_DIRECTORY", myWorkingDirectory);
		JDOMExternalizerUtil.writeField(element, "IS_MODULE_SDK", Boolean.toString(myUseModuleSdk));
		JDOMExternalizerUtil.writeField(element, "ADD_CONTENT_ROOTS", Boolean.toString(myAddContentRoots));
		JDOMExternalizerUtil.writeField(element, "ADD_SOURCE_ROOTS", Boolean.toString(myAddSourceRoots));
		if(!mySkipModuleSerialization)
		{
			getConfigurationModule().writeExternal(element);
		}

		// extension settings:
		PythonRunConfigurationExtensionsManager.getInstance().writeExternal(this, element);

		PathMappingSettings.writeExternal(element, getMappingSettings());
	}

	protected void writeEnvs(Element element)
	{
		JDOMExternalizerUtil.writeField(element, "PARENT_ENVS", Boolean.toString(isPassParentEnvs()));
		EnvironmentVariablesComponent.writeExternal(element, getEnvs());
	}

	@Override
    public String getInterpreterOptions()
	{
		return myInterpreterOptions;
	}

	@Override
    public void setInterpreterOptions(String interpreterOptions)
	{
		myInterpreterOptions = interpreterOptions;
	}

	@Override
    public String getWorkingDirectory()
	{
		return myWorkingDirectory;
	}

	@Override
    public void setWorkingDirectory(String workingDirectory)
	{
		myWorkingDirectory = workingDirectory;
	}

	@Override
    public void setSdkHome(String sdkHome)
	{
		mySdkHome = sdkHome;
	}

	@Nullable
    @Override
	public Module getModule()
	{
		return getConfigurationModule().getModule();
	}

	@Override
    public boolean isUseModuleSdk()
	{
		return myUseModuleSdk;
	}

	@Override
    public void setUseModuleSdk(boolean useModuleSdk)
	{
		myUseModuleSdk = useModuleSdk;
	}

	@Override
	public boolean shouldAddContentRoots()
	{
		return myAddContentRoots;
	}

	@Override
	public boolean shouldAddSourceRoots()
	{
		return myAddSourceRoots;
	}

	@Override
	public void setAddSourceRoots(boolean flag)
	{
		myAddSourceRoots = flag;
	}

	@Override
	public void setAddContentRoots(boolean flag)
	{
		myAddContentRoots = flag;
	}

	public static void copyParams(AbstractPythonRunConfigurationParams source, AbstractPythonRunConfigurationParams target)
	{
		target.setEnvs(new HashMap<>(source.getEnvs()));
		target.setInterpreterOptions(source.getInterpreterOptions());
		target.setPassParentEnvs(source.isPassParentEnvs());
		target.setSdkHome(source.getSdkHome());
		target.setWorkingDirectory(source.getWorkingDirectory());
		target.setModule(source.getModule());
		target.setUseModuleSdk(source.isUseModuleSdk());
		target.setMappingSettings(source.getMappingSettings());
		target.setAddContentRoots(source.shouldAddContentRoots());
		target.setAddSourceRoots(source.shouldAddSourceRoots());
	}

	/**
	 * Some setups (e.g. virtualenv) provide a script that alters environment variables before running a python interpreter or other tools.
	 * Such settings are not directly stored but applied right before running using this method.
	 *
	 * @param commandLine what to patch
	 */
	@Override
    public void patchCommandLine(GeneralCommandLine commandLine)
	{
		String interpreterPath = getInterpreterPath();
		Sdk sdk = getSdk();
		if(sdk != null && interpreterPath != null)
		{
			patchCommandLineFirst(commandLine, interpreterPath);
			patchCommandLineForVirtualenv(commandLine, interpreterPath);
			patchCommandLineForBuildout(commandLine, interpreterPath);
			patchCommandLineLast(commandLine, interpreterPath);
		}
	}

	/**
	 * Patches command line before virtualenv and buildout patchers.
	 * Default implementation does nothing.
	 *
	 * @param commandLine
	 * @param sdkHome
	 */
	protected void patchCommandLineFirst(GeneralCommandLine commandLine, String sdkHome)
	{
		// override
	}

	/**
	 * Patches command line after virtualenv and buildout patchers.
	 * Default implementation does nothing.
	 *
	 * @param commandLine
	 * @param sdkHome
	 */
	protected void patchCommandLineLast(GeneralCommandLine commandLine, String sdkHome)
	{
		// override
	}

	/**
	 * Gets called after {@link #patchCommandLineForVirtualenv(SdkType, SdkType)}
	 * Does nothing here, real implementations should use alter running script name or use engulfer.
	 *
	 * @param commandLine
	 * @param sdkHome
	 */
	protected void patchCommandLineForBuildout(GeneralCommandLine commandLine, String sdkHome)
	{
	}

	/**
	 * Alters PATH so that a virtualenv is activated, if present.
	 *
	 * @param commandLine
	 * @param sdkHome
	 */
	protected void patchCommandLineForVirtualenv(GeneralCommandLine commandLine, String sdkHome)
	{
		PythonSdkType.patchCommandLineForVirtualenv(commandLine, sdkHome, isPassParentEnvs());
	}

	protected void setUnbufferedEnv()
	{
		Map<String, String> envs = getEnvs();
		// unbuffered I/O is easier for IDE to handle
		PythonEnvUtil.setPythonUnbuffered(envs);
	}

	@Override
	public boolean excludeCompileBeforeLaunchOption()
	{
		Module module = getModule();
		return module != null && ModuleUtilCore.getExtension(module, PyModuleExtension.class) != null;
	}

	public boolean canRunWithCoverage()
	{
		return true;
	}

	/**
	 * Create test spec (string to be passed to runner, probably glued with {@link #TEST_NAME_PARTS_SPLITTER})
	 *
	 * @param location   test location as reported by runner
	 * @param failedTest failed test
	 * @return string spec or null if spec calculation is impossible
	 */
    @Nullable
    @RequiredReadAction
	public String getTestSpec(Location<?> location, AbstractTestProxy failedTest)
	{
		PsiElement element = location.getPsiElement();
		PyClass pyClass = PsiTreeUtil.getParentOfType(element, PyClass.class, false);
		if(location instanceof PyPsiLocationWithFixedClass)
		{
			pyClass = ((PyPsiLocationWithFixedClass) location).getFixedClass();
		}
		PyFunction pyFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class, false);
		VirtualFile virtualFile = location.getVirtualFile();
		if(virtualFile != null)
		{
			String path = virtualFile.getCanonicalPath();
			if(pyClass != null)
			{
				path += TEST_NAME_PARTS_SPLITTER + pyClass.getName();
			}
			if(pyFunction != null)
			{
				path += TEST_NAME_PARTS_SPLITTER + pyFunction.getName();
			}
			return path;
		}
		return null;
	}

	/**
	 * Note to inheritors: Always check {@link #getWorkingDirectory()} first. You should return it, if it is not empty since
	 * user should be able to set dir explicitly. Then, do your guess and return super as last resort.
	 *
	 * @return working directory to run, never null, does its best to guess which dir to use.
	 * Unlike {@link #getWorkingDirectory()} it does not simply take directory from config.
	 */
	public String getWorkingDirectorySafe()
	{
		String result = StringUtil.isEmpty(myWorkingDirectory) ? getProject().getBasePath() : myWorkingDirectory;
		if(result != null)
		{
			return result;
		}

		String firstModuleRoot = getFirstModuleRoot();
		if(firstModuleRoot != null)
		{
			return firstModuleRoot;
		}
		return new File(".").getAbsolutePath();
	}

	@Nullable
	private String getFirstModuleRoot()
	{
		Module module = getModule();
		if(module == null)
		{
			return null;
		}
		VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
		return roots.length > 0 ? roots[0].getPath() : null;
	}

	@Override
	public String getModuleName()
	{
		Module module = getModule();
		return module != null ? module.getName() : null;
	}

	@Override
	public boolean isCompileBeforeLaunchAddedByDefault()
	{
		return false;
	}

	/**
	 * Adds test specs (like method, class, script, etc) to list of runner parameters.
	 */
	public void addTestSpecsAsParameters(ParamsGroup paramsGroup, List<String> testSpecs)
	{
		// By default we simply add them as arguments
		paramsGroup.addParameters(testSpecs);
	}
}
