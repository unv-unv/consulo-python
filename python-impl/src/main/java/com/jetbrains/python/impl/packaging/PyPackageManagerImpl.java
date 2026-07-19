/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.impl.packaging;

import com.google.common.collect.Lists;
import com.jetbrains.python.impl.PythonHelpersLocator;
import com.jetbrains.python.impl.sdk.PythonEnvUtil;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.packaging.PyRequirement;
import com.jetbrains.python.psi.LanguageLevel;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.util.TempFileService;
import consulo.component.messagebus.MessageBusConnection;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkModificator;
import consulo.content.bundle.SdkTable;
import consulo.execution.RunCanceledByUserException;
import consulo.http.HttpProxyManager;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.platform.Platform;
import consulo.process.*;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.event.ProcessEvent;
import consulo.process.event.ProcessListener;
import consulo.process.util.CapturingProcessRunner;
import consulo.process.util.ProcessOutput;
import consulo.util.dataholder.Key;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.event.BulkFileListener;
import consulo.virtualFileSystem.event.VFileEvent;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author vlan
 */
public class PyPackageManagerImpl extends PyPackageManager
{
	// Python 2.4-2.5 compatible versions
	private static final String SETUPTOOLS_PRE_26_VERSION = "1.4.2";
	private static final String PIP_PRE_26_VERSION = "1.1";
	private static final String VIRTUALENV_PRE_26_VERSION = "1.7.2";

	private static final String SETUPTOOLS_VERSION = "18.1";
	private static final String PIP_VERSION = "7.1.0";
	private static final String VIRTUALENV_VERSION = "13.1.0";

	private static final int ERROR_NO_SETUPTOOLS = 3;

	private static final Logger LOG = Logger.getInstance(PyPackageManagerImpl.class);

	private static final String PACKAGING_TOOL = "packaging_tool.py";
	private static final int TIMEOUT = 10 * 60 * 1000;

	private static final String BUILD_DIR_OPTION = "--build-dir";

	private static final String INSTALL = "install";
	private static final String UNINSTALL = "uninstall";
	private static final String UNTAR = "untar";

	@Nullable
	private volatile List<PyPackage> myPackagesCache = null;
	private final AtomicBoolean myUpdatingCache = new AtomicBoolean(false);

	final private Sdk mySdk;

	@Override
	public void refresh()
	{
		LOG.debug("Refreshing SDK roots and packages cache");
		Application application = ApplicationManager.getApplication();
		application.invokeLater(() -> {
			Sdk sdk = getSdk();
			application.runWriteAction(() -> {
				VirtualFile[] files = sdk.getRootProvider().getFiles(BinariesOrderRootType.ID);
				VirtualFileUtil.markDirtyAndRefresh(true, true, true, files);
			});
			PythonSdkType.getInstance().setupSdkPaths(sdk);
		});
	}

	@Override
	public void installManagement() throws ExecutionException
	{
		Sdk sdk = getSdk();
		boolean pre26 = PythonSdkType.getLanguageLevelForSdk(sdk).isOlderThan(LanguageLevel.PYTHON26);
		if(!refreshAndCheckForSetuptools())
		{
			String name = PyPackageUtil.SETUPTOOLS + "-" + (pre26 ? SETUPTOOLS_PRE_26_VERSION : SETUPTOOLS_VERSION);
			installManagement(name);
		}
		if(PyPackageUtil.findPackage(refreshAndGetPackages(false), PyPackageUtil.PIP) == null)
		{
			String name = PyPackageUtil.PIP + "-" + (pre26 ? PIP_PRE_26_VERSION : PIP_VERSION);
			installManagement(name);
		}
	}

	@Override
	public boolean hasManagement() throws ExecutionException
	{
		return refreshAndCheckForSetuptools() && PyPackageUtil.findPackage(refreshAndGetPackages(false), PyPackageUtil.PIP) != null;
	}

	private boolean refreshAndCheckForSetuptools() throws ExecutionException
	{
		try
		{
			List<PyPackage> packages = refreshAndGetPackages(false);
			return PyPackageUtil.findPackage(packages, PyPackageUtil.SETUPTOOLS) != null || PyPackageUtil.findPackage(packages,
					PyPackageUtil.DISTRIBUTE) != null;
		}
		catch(PyExecutionException e)
		{
			if(e.getExitCode() == ERROR_NO_SETUPTOOLS)
			{
				return false;
			}
			throw e;
		}
	}

	protected void installManagement(String name) throws ExecutionException
	{
		String dirName = extractHelper(name + ".tar.gz");
		try
		{
			String fileName = dirName + name + File.separatorChar + "setup.py";
			getPythonProcessResult(fileName, Collections.singletonList(INSTALL), true, true, dirName + name);
		}
		finally
		{
			FileUtil.delete(new File(dirName));
		}
	}

	private String extractHelper(String name) throws ExecutionException
	{
		String helperPath = getHelperPath(name);
		ArrayList<String> args = Lists.newArrayList(UNTAR, helperPath);
		String result = getHelperResult(PACKAGING_TOOL, args, false, false, null);
		String dirName = FileUtil.toSystemDependentName(result.trim());
		if(!dirName.endsWith(File.separator))
		{
			dirName += File.separator;
		}
		return dirName;
	}

	PyPackageManagerImpl(Sdk sdk)
	{
		mySdk = sdk;
		subscribeToLocalChanges();
	}

	protected void subscribeToLocalChanges()
	{
		Application app = ApplicationManager.getApplication();
		MessageBusConnection connection = app.getMessageBus().connect();
		connection.subscribe(BulkFileListener.class, new MySdkRootWatcher());
	}

	public Sdk getSdk()
	{
		return mySdk;
	}

	@Override
	public void install(String requirementString) throws ExecutionException
	{
		installManagement();
		install(Collections.singletonList(PyRequirement.fromLine(requirementString)), Collections.emptyList());
	}

	@Override
	public void install(List<PyRequirement> requirements, List<String> extraArgs) throws ExecutionException
	{
		List<String> args = new ArrayList<>();
		args.add(INSTALL);
		File buildDir;
		try
		{
			TempFileService tempFileService = Application.get().getInstance(TempFileService.class);
			buildDir = tempFileService.createTempDirectory("pycharm-packaging", null).toFile();
		}
		catch(IOException e)
		{
			throw new ExecutionException("Cannot create temporary build directory");
		}
		if(!extraArgs.contains(BUILD_DIR_OPTION))
		{
			args.addAll(Arrays.asList(BUILD_DIR_OPTION, buildDir.getAbsolutePath()));
		}

		boolean useUserSite = extraArgs.contains(USE_USER_SITE);

		String proxyString = getProxyString();
		if(proxyString != null)
		{
			args.add("--proxy");
			args.add(proxyString);
		}
		args.addAll(extraArgs);
		for(PyRequirement req : requirements)
		{
			args.addAll(req.getInstallOptions());
		}
		try
		{
			getHelperResult(PACKAGING_TOOL, args, !useUserSite, true, null);
		}
		catch(PyExecutionException e)
		{
			List<String> simplifiedArgs = new ArrayList<>();
			simplifiedArgs.add("install");
			if(proxyString != null)
			{
				simplifiedArgs.add("--proxy");
				simplifiedArgs.add(proxyString);
			}
			simplifiedArgs.addAll(extraArgs);
			for(PyRequirement req : requirements)
			{
				simplifiedArgs.addAll(req.getInstallOptions());
			}
			throw new PyExecutionException(e.getMessage(), "pip", simplifiedArgs, e.getStdout(), e.getStderr(), e.getExitCode(), e.getFixes());
		}
		finally
		{
			LOG.debug("Packages cache is about to be refreshed because these requirements were installed: " + requirements);
			refreshPackagesSynchronously();
			FileUtil.delete(buildDir);
		}
	}

	@Override
	public void uninstall(List<PyPackage> packages) throws ExecutionException
	{
		List<String> args = new ArrayList<>();
		try
		{
			args.add(UNINSTALL);
			boolean canModify = true;
			for(PyPackage pkg : packages)
			{
				if(canModify)
				{
					String location = pkg.getLocation();
					if(location != null)
					{
						canModify = FileUtil.ensureCanCreateFile(new File(location));
					}
				}
				args.add(pkg.getName());
			}
			getHelperResult(PACKAGING_TOOL, args, !canModify, true, null);
		}
		catch(PyExecutionException e)
		{
			throw new PyExecutionException(e.getMessage(), "pip", args, e.getStdout(), e.getStderr(), e.getExitCode(), e.getFixes());
		}
		finally
		{
			LOG.debug("Packages cache is about to be refreshed because these packages were uninstalled: " + packages);
			refreshPackagesSynchronously();
		}
	}


	@Nullable
	@Override
	public List<PyPackage> getPackages()
	{
		List<PyPackage> packages = myPackagesCache;
		return packages != null ? Collections.unmodifiableList(packages) : null;
	}

	protected List<PyPackage> collectPackages() throws ExecutionException
	{
		String output;
		try
		{
			LOG.debug("Collecting installed packages for the SDK " + mySdk.getName(), new Throwable());
			output = getHelperResult(PACKAGING_TOOL, Collections.singletonList("list"), false, false, null);
		}
		catch(ProcessNotCreatedException ex)
		{
			if(ApplicationManager.getApplication().isUnitTestMode())
			{
				LOG.info("Not-env unit test mode, will return mock packages");
				return Lists.newArrayList(new PyPackage(PyPackageUtil.PIP, PIP_VERSION, null, Collections.emptyList()),
						new PyPackage(PyPackageUtil.SETUPTOOLS, SETUPTOOLS_VERSION, null,
								Collections.emptyList()));
			}
			else
			{
				throw ex;
			}
		}

		return parsePackagingToolOutput(output);
	}

	@Override
	public Set<PyPackage> getDependents(PyPackage pkg) throws ExecutionException
	{
		List<PyPackage> packages = refreshAndGetPackages(false);
		Set<PyPackage> dependents = new HashSet<>();
		for(PyPackage p : packages)
		{
			List<PyRequirement> requirements = p.getRequirements();
			for(PyRequirement requirement : requirements)
			{
				if(requirement.getName().equals(pkg.getName()))
				{
					dependents.add(p);
				}
			}
		}
		return dependents;
	}

	@Override
	public String createVirtualEnv(String destinationDir, boolean useGlobalSite) throws ExecutionException
	{
		List<String> args = new ArrayList<>();
		Sdk sdk = getSdk();
		LanguageLevel languageLevel = PythonSdkType.getLanguageLevelForSdk(sdk);
		boolean usePyVenv = languageLevel.isAtLeast(LanguageLevel.PYTHON33);
		if(usePyVenv)
		{
			args.add("pyvenv");
			if(useGlobalSite)
			{
				args.add("--system-site-packages");
			}
			args.add(destinationDir);
			getHelperResult(PACKAGING_TOOL, args, false, true, null);
		}
		else
		{
			if(useGlobalSite)
			{
				args.add("--system-site-packages");
			}
			args.add(destinationDir);
			boolean pre26 = languageLevel.isOlderThan(LanguageLevel.PYTHON26);
			String name = "virtualenv-" + (pre26 ? VIRTUALENV_PRE_26_VERSION : VIRTUALENV_VERSION);
			String dirName = extractHelper(name + ".tar.gz");
			try
			{
				String fileName = dirName + name + File.separatorChar + "virtualenv.py";
				getPythonProcessResult(fileName, args, false, true, dirName + name);
			}
			finally
			{
				FileUtil.delete(new File(dirName));
			}
		}

		String binary = PythonSdkType.getPythonExecutable(destinationDir);
		String binaryFallback = destinationDir + File.separator + "bin" + File.separator + "python";
		String path = (binary != null) ? binary : binaryFallback;

		if(usePyVenv)
		{
			// Still no 'packaging' and 'pysetup3' for Python 3.3rc1, see PEP 405
			VirtualFile binaryFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
			if(binaryFile != null)
			{
				Sdk tmpSdk = SdkTable.getInstance().createSdk("", PythonSdkType.getInstance());
				SdkModificator modificator = tmpSdk.getSdkModificator();
				modificator.setHomePath(path);
				modificator.commitChanges();

				PyPackageManager manager = PyPackageManager.getInstance(tmpSdk);
				manager.installManagement();
			}
		}
		return path;
	}

	@Override
	@Nullable
	public List<PyRequirement> getRequirements(Module module)
	{
		return Optional.ofNullable(PyPackageUtil.getRequirementsFromTxt(module)).orElseGet(() -> PyPackageUtil.findSetupPyRequires(module));
	}


	//   public List<PyPackage> refreshAndGetPackagesIfNotInProgress(boolean alwaysRefresh) throws ExecutionException

	@Override
	public List<PyPackage> refreshAndGetPackages(boolean alwaysRefresh) throws ExecutionException
	{
		List<PyPackage> currentPackages = myPackagesCache;
		if(alwaysRefresh || currentPackages == null)
		{
			try
			{
				List<PyPackage> packages = collectPackages();
				LOG.debug("Packages installed in " + mySdk.getName() + ": " + packages);
				myPackagesCache = packages;
				return Collections.unmodifiableList(packages);
			}
			catch(ExecutionException e)
			{
				myPackagesCache = Collections.emptyList();
				throw e;
			}
		}
		return Collections.unmodifiableList(currentPackages);
	}

	private void refreshPackagesSynchronously()
	{
		PyPackageUtil.updatePackagesSynchronouslyWithGuard(this, myUpdatingCache);
	}

	@Nullable
	private static String getProxyString()
	{
		HttpProxyManager settings = HttpProxyManager.getInstance();
		if(settings.isHttpProxyEnabled())
		{
			String credentials;
			if(settings.isProxyAuthenticationEnabled())
			{
				credentials = String.format("%s:%s@", settings.getProxyLogin(), settings.getPlainProxyPassword());
			}
			else
			{
				credentials = "";
			}
			return "http://" + credentials + String.format("%s:%d", settings.getProxyHost(), settings.getProxyPort());
		}
		return null;
	}

	private String getHelperResult(String helper,
								   List<String> args,
								   boolean askForSudo,
								   boolean showProgress,
								   @Nullable String parentDir) throws ExecutionException
	{
		String helperPath = getHelperPath(helper);
		if(helperPath == null)
		{
			throw new ExecutionException("Cannot find external tool: " + helper);
		}
		return getPythonProcessResult(helperPath, args, askForSudo, showProgress, parentDir);
	}

	@Nullable
	protected String getHelperPath(String helper) throws ExecutionException
	{
		return PythonHelpersLocator.getHelperPath(helper);
	}

	private String getPythonProcessResult(String path,
										  List<String> args,
										  boolean askForSudo,
										  boolean showProgress,
										  @Nullable String workingDir) throws ExecutionException
	{
		ProcessOutput output = getPythonProcessOutput(path, args, askForSudo, showProgress, workingDir);
		int exitCode = output.getExitCode();
		if(output.isTimeout())
		{
			throw new PyExecutionException("Timed out", path, args, output);
		}
		else if(exitCode != 0)
		{
			throw new PyExecutionException("Non-zero exit code (" + exitCode + ")", path, args, output);
		}
		return output.getStdout();
	}

	protected ProcessOutput getPythonProcessOutput(String helperPath,
												   List<String> args,
												   boolean askForSudo,
												   boolean showProgress,
												   @Nullable String workingDir) throws ExecutionException
	{
		String homePath = getSdk().getHomePath();
		if(homePath == null)
		{
			throw new ExecutionException("Cannot find Python interpreter for SDK " + mySdk.getName());
		}
		if(workingDir == null)
		{
			workingDir = new File(homePath).getParent();
		}
		List<String> cmdline = new ArrayList<>();
		cmdline.add(homePath);
		cmdline.add(helperPath);
		cmdline.addAll(args);
		LOG.info("Running packaging tool: " + StringUtil.join(cmdline, " "));

		boolean canCreate = FileUtil.ensureCanCreateFile(new File(homePath));
		boolean useSudo = !canCreate && !Platform.current().os().isWindows() && askForSudo;

		try
		{
			 GeneralCommandLine commandLine = new GeneralCommandLine(cmdline).withWorkDirectory(workingDir);
			Map<String, String> environment = commandLine.getEnvironment();
			PythonEnvUtil.setPythonUnbuffered(environment);
			PythonEnvUtil.setPythonDontWriteBytecode(environment);
			PythonEnvUtil.resetHomePathChanges(homePath, environment);
			if(useSudo)
			{
				commandLine = commandLine.withSudo("Please enter your password to make changes in system packages: ");
			}

			ProcessHandler handler = ProcessHandlerBuilder.create(commandLine).build();
			CapturingProcessRunner runner = new CapturingProcessRunner(handler);
			final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
			ProcessOutput result;
			if(showProgress && indicator != null)
			{
				handler.addProcessListener(new ProcessListener()
				{
					@Override
					public void onTextAvailable(ProcessEvent event, Key outputType)
					{
						if(outputType == ProcessOutputTypes.STDOUT || outputType == ProcessOutputTypes.STDERR)
						{
							for(String line : StringUtil.splitByLines(event.getText()))
							{
								String trimmed = line.trim();
								if(isMeaningfulOutput(trimmed))
								{
									indicator.setText2(trimmed);
								}
							}
						}
					}

					private boolean isMeaningfulOutput(String trimmed)
					{
						return trimmed.length() > 3;
					}
				});
				result = runner.runProcess(indicator);
			}
			else
			{
				result = runner.runProcess(TIMEOUT);
			}
			if(result.isCancelled())
			{
				throw new RunCanceledByUserException();
			}
			int exitCode = result.getExitCode();
			if(exitCode != 0)
			{
				String message =
						StringUtil.isEmptyOrSpaces(result.getStdout()) && StringUtil.isEmptyOrSpaces(result.getStderr()) ? "Permission denied" : "Non-zero exit code (" + exitCode +
								")";
				throw new PyExecutionException(message, helperPath, args, result);
			}
			return result;
		}
		catch(ExecutionException e)
		{
			throw new PyExecutionException(e.getMessage(), helperPath, args);
		}
	}

	private static List<PyPackage> parsePackagingToolOutput(String s) throws ExecutionException
	{
		String[] lines = StringUtil.splitByLines(s);
		List<PyPackage> packages = new ArrayList<>();
		for(String line : lines)
		{
			List<String> fields = StringUtil.split(line, "\t");
			if(fields.size() < 3)
			{
				throw new PyExecutionException("Invalid output format", PACKAGING_TOOL, Collections.emptyList());
			}
			String name = fields.get(0);
			String version = fields.get(1);
			String location = fields.get(2);
			List<PyRequirement> requirements = new ArrayList<>();
			if(fields.size() >= 4)
			{
				String requiresLine = fields.get(3);
				String requiresSpec = StringUtil.join(StringUtil.split(requiresLine, ":"), "\n");
				requirements.addAll(PyRequirement.fromText(requiresSpec));
			}
			if(!"Python".equals(name))
			{
				packages.add(new PyPackage(name, version, location, requirements));
			}
		}
		return packages;
	}

	private class MySdkRootWatcher implements BulkFileListener
	{
		@Override
		public void after(List<? extends VFileEvent> events)
		{
			Sdk sdk = getSdk();
			VirtualFile[] roots = sdk.getRootProvider().getFiles(BinariesOrderRootType.ID);
			for(VFileEvent event : events)
			{
				VirtualFile file = event.getFile();
				if(file != null)
				{
					for(VirtualFile root : roots)
					{
						if(VirtualFileUtil.isAncestor(root, file, false))
						{
							LOG.debug("Refreshing packages cache on SDK change");
							ApplicationManager.getApplication().executeOnPooledThread(PyPackageManagerImpl.this::refreshPackagesSynchronously);
							return;
						}
					}
				}
			}
		}
	}
}