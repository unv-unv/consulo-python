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
package com.jetbrains.python.run;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.execution.Location;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.types.TypeEvalContext;

/**
 * Filters out Python scripts for which it doesn't make sense to run the standard Python configuration,
 * and which are (possibly) run by other configurations instead.
 *
 * @author yole
 */
public interface RunnableScriptFilter
{
	ExtensionPointName<RunnableScriptFilter> EP_NAME = ExtensionPointName.create("consulo.python.runnableScriptFilter");

	boolean isRunnableScript(PsiFile script, @NotNull Module module, Location location, @Nullable TypeEvalContext context);
}
