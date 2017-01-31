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
package com.jetbrains.python.console;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;

/**
 * @author traff
 */
public class PythonConsoleToolWindowFactory implements ToolWindowFactory, DumbAware
{
	public static final String ID = "Python Console";

	@Override
	public void createToolWindowContent(final @NotNull Project project, final @NotNull ToolWindow toolWindow)
	{
		PydevConsoleRunner runner = PythonConsoleRunnerFactory.getInstance().createConsoleRunner(project, null);
		TransactionGuard.submitTransaction(project, runner::runSync);
	}
}