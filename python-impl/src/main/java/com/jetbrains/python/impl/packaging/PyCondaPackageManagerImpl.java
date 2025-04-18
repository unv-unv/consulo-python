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
package com.jetbrains.python.impl.packaging;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyRequirement;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.util.SystemInfo;
import consulo.content.bundle.Sdk;
import consulo.execution.RunCanceledByUserException;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.ProcessHandlerBuilder;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.util.CapturingProcessRunner;
import consulo.process.util.CapturingProcessUtil;
import consulo.process.util.ProcessOutput;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class PyCondaPackageManagerImpl extends PyPackageManagerImpl
{
	public static final String PYTHON = "python";

	PyCondaPackageManagerImpl(@Nonnull final Sdk sdk)
	{
		super(sdk);
	}

	@Override
	public void installManagement() throws ExecutionException
	{
	}

	@Override
	public boolean hasManagement() throws ExecutionException
	{
		final Sdk sdk = getSdk();
		return isCondaVEnv(sdk);
	}

	@Override
	protected void installManagement(@Nonnull String name) throws ExecutionException
	{
	}

	@Override
	public void install(@Nonnull List<PyRequirement> requirements, @Nonnull List<String> extraArgs) throws ExecutionException
	{
		final ArrayList<String> arguments = new ArrayList<>();
		for(PyRequirement requirement : requirements)
		{
			arguments.add(requirement.toString());
		}
		arguments.add("-y");
		if(extraArgs.contains("-U"))
		{
			getCondaOutput("update", arguments);
		}
		else
		{
			arguments.addAll(extraArgs);
			getCondaOutput("install", arguments);
		}
	}

	private ProcessOutput getCondaOutput(@Nonnull final String command, List<String> arguments) throws ExecutionException
	{
		final Sdk sdk = getSdk();

		final String condaExecutable = PyCondaPackageService.getCondaExecutable(sdk.getHomeDirectory());
		if(condaExecutable == null)
		{
			throw new PyExecutionException("Cannot find conda", "Conda", Collections.<String>emptyList(), new ProcessOutput());
		}

		final String path = getCondaDirectory();
		if(path == null)
		{
			throw new PyExecutionException("Empty conda name for " + sdk.getHomePath(), command, arguments);
		}

		final ArrayList<String> parameters = Lists.newArrayList(condaExecutable, command, "-p", path);
		parameters.addAll(arguments);

		final GeneralCommandLine commandLine = new GeneralCommandLine(parameters);
		final ProcessOutput result = CapturingProcessUtil.execAndGetOutput(commandLine);
		final int exitCode = result.getExitCode();
		if(exitCode != 0)
		{
			final String message =
					StringUtil.isEmptyOrSpaces(result.getStdout()) && StringUtil.isEmptyOrSpaces(result.getStderr()) ? "Permission denied" : "Non-zero exit code";
			throw new PyExecutionException(message, "Conda", parameters, result);
		}
		return result;
	}

	@Nullable
	private String getCondaDirectory()
	{
		final VirtualFile homeDirectory = getSdk().getHomeDirectory();
		if(homeDirectory == null)
		{
			return null;
		}
		if(SystemInfo.isWindows)
		{
			return homeDirectory.getParent().getPath();
		}
		return homeDirectory.getParent().getParent().getPath();
	}

	@Override
	public void install(@Nonnull String requirementString) throws ExecutionException
	{
		getCondaOutput("install", Lists.newArrayList(requirementString, "-y"));
	}

	@Override
	public void uninstall(@Nonnull List<PyPackage> packages) throws ExecutionException
	{
		final ArrayList<String> arguments = new ArrayList<>();
		for(PyPackage aPackage : packages)
		{
			arguments.add(aPackage.getName());
		}
		arguments.add("-y");

		getCondaOutput("remove", arguments);
	}

	@Nonnull
	@Override
	protected List<PyPackage> collectPackages() throws ExecutionException
	{
		final ProcessOutput output = getCondaOutput("list", Lists.newArrayList("-e"));
		final Set<PyPackage> packages = Sets.newConcurrentHashSet(parseCondaToolOutput(output.getStdout()));
		packages.addAll(super.collectPackages());
		return Lists.newArrayList(packages);
	}

	@Nonnull
	protected static List<PyPackage> parseCondaToolOutput(@Nonnull String s) throws ExecutionException
	{
		final String[] lines = StringUtil.splitByLines(s);
		final List<PyPackage> packages = new ArrayList<>();
		for(String line : lines)
		{
			if(line.startsWith("#"))
			{
				continue;
			}
			final List<String> fields = StringUtil.split(line, "=");
			if(fields.size() < 3)
			{
				throw new PyExecutionException("Invalid conda output format", "conda", Collections.<String>emptyList());
			}
			final String name = fields.get(0);
			final String version = fields.get(1);
			final List<PyRequirement> requirements = new ArrayList<>();
			if(fields.size() >= 4)
			{
				final String requiresLine = fields.get(3);
				final String requiresSpec = StringUtil.join(StringUtil.split(requiresLine, ":"), "\n");
				requirements.addAll(PyRequirement.fromText(requiresSpec));
			}
			if(!"Python".equals(name))
			{
				packages.add(new PyPackage(name, version, "", requirements));
			}
		}
		return packages;
	}

	public static boolean isCondaVEnv(@Nonnull final Sdk sdk)
	{
		final String condaName = "conda-meta";
		final VirtualFile homeDirectory = sdk.getHomeDirectory();
		if(homeDirectory == null)
		{
			return false;
		}
		final VirtualFile condaMeta =
				SystemInfo.isWindows ? homeDirectory.getParent().findChild(condaName) : homeDirectory.getParent().getParent().findChild(condaName);
		return condaMeta != null;
	}

	@Nonnull
	public static String createVirtualEnv(@Nonnull String destinationDir, String version) throws ExecutionException
	{
		final String condaExecutable = PyCondaPackageService.getSystemCondaExecutable();
		if(condaExecutable == null)
		{
			throw new PyExecutionException("Cannot find conda", "Conda", Collections.<String>emptyList(), new ProcessOutput());
		}

		final ArrayList<String> parameters = Lists.newArrayList(condaExecutable, "create", "-p", destinationDir, "python=" + version, "-y");

		final GeneralCommandLine commandLine = new GeneralCommandLine(parameters);
		final ProcessHandler handler = ProcessHandlerBuilder.create(commandLine).build();
		final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
		final ProcessOutput result = new CapturingProcessRunner(handler).runProcess(indicator);
		if(result.isCancelled())
		{
			throw new RunCanceledByUserException();
		}
		final int exitCode = result.getExitCode();
		if(exitCode != 0)
		{
			final String message =
					StringUtil.isEmptyOrSpaces(result.getStdout()) && StringUtil.isEmptyOrSpaces(result.getStderr()) ? "Permission denied" : "Non-zero exit code";
			throw new PyExecutionException(message, "Conda", parameters, result);
		}
		final String binary = PythonSdkType.getPythonExecutable(destinationDir);
		final String binaryFallback = destinationDir + File.separator + "bin" + File.separator + "python";
		return (binary != null) ? binary : binaryFallback;
	}

}
