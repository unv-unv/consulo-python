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
package com.jetbrains.python.jython.sdk.flavors;

import com.jetbrains.python.impl.sdk.flavors.PythonSdkFlavor;
import consulo.annotation.component.ExtensionImpl;
import consulo.process.ExecutionException;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.cmd.ParamsGroup;
import consulo.process.ProcessHandler;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import com.jetbrains.python.impl.PythonHelpersLocator;
import com.jetbrains.python.impl.run.JythonProcessHandler;
import com.jetbrains.python.impl.run.PythonCommandLineState;
import consulo.jython.icon.JythonIconGroup;
import consulo.ui.image.Image;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
@ExtensionImpl
public class JythonSdkFlavor extends PythonSdkFlavor
{
	private static final String JYTHONPATH = "JYTHONPATH";
	private static final String PYTHON_PATH_PREFIX = "-Dpython.path=";

	public static String getPythonPathCmdLineArgument(Collection<String> path)
	{
		return PYTHON_PATH_PREFIX + StringUtil.join(appendSystemEnvPaths(path, JYTHONPATH), File.pathSeparator);
	}

	@Override
    public boolean isValidSdkPath(File file)
	{
		return FileUtil.getNameWithoutExtension(file).toLowerCase().startsWith("jython");
	}

	@Override
	public String getVersionRegexp()
	{
		return "(Jython \\S+)( on .*)?";
	}

	@Override
	public String getVersionOption()
	{
		return "--version";
	}

	@Override
	public void initPythonPath(GeneralCommandLine cmd, Collection<String> path)
	{
		initPythonPath(path, cmd.getEnvironment());
		ParamsGroup paramGroup = cmd.getParametersList().getParamsGroup(PythonCommandLineState.GROUP_EXE_OPTIONS);
		assert paramGroup != null;
		for(String param : paramGroup.getParameters())
		{
			if(param.startsWith(PYTHON_PATH_PREFIX))
			{
				return;
			}
		}
		paramGroup.addParameter(getPythonPathCmdLineArgument(path));
	}

	@Override
	public void initPythonPath(Collection<String> path, Map<String, String> env)
	{
		path = appendSystemEnvPaths(path, JYTHONPATH);
		String jythonPath = StringUtil.join(path, File.pathSeparator);
		addToEnv(JYTHONPATH, jythonPath, env);
	}

	@Override
	public ProcessHandler createProcessHandler(GeneralCommandLine commandLine, boolean withMediator) throws ExecutionException
	{
		return JythonProcessHandler.createProcessHandler(commandLine);
	}

	@Override
	public Collection<String> collectDebugPythonPath()
	{
		List<String> list = new ArrayList<>(2);
		//that fixes Jython problem changing sys.argv on execfile, see PY-8164
		list.add(PythonHelpersLocator.getHelperPath("pycharm"));
		list.add(PythonHelpersLocator.getHelperPath("pydev"));
		return list;
	}

	@Override
	public String getName()
	{
		return "Jython";
	}

	@Override
	public Image getIcon()
	{
		return JythonIconGroup.jython();
	}
}
