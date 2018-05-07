/*
 * Copyright 2013-2016 must-be.org
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

import java.awt.Color;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorLinePainter;
import com.intellij.openapi.editor.LineExtensionInfo;
import com.intellij.openapi.editor.TextAnnotationGutterProvider;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.console.parsing.PythonConsoleData;

/**
 * @author VISTALL
 * @since 08-Nov-16
 */
public class ConsolePromptDecorator extends EditorLinePainter implements TextAnnotationGutterProvider
{
	private static ColorKey promptColor = ColorKey.createColorKey("CONSOLE_PROMPT_COLOR");

	private static String extend(String s, int len)
	{
		String res = s;
		while(res.length() < len)
		{
			res = " " + res;
		}

		return res;
	}

	private ConsoleViewContentType promptAttributes = ConsoleViewContentType.USER_INPUT;

	public void setPromptAttributes(ConsoleViewContentType promptAttributes)
	{
		this.promptAttributes = promptAttributes;
		myEditorEx.getColorsScheme().setColor(promptColor, promptAttributes.getAttributes().getForegroundColor());

		UIUtil.invokeLaterIfNeeded(() -> {
			myEditorEx.getGutterComponentEx().revalidateMarkup();
		});
	}

	public ConsoleViewContentType getPromptAttributes()
	{
		return promptAttributes;
	}

	private String indentPrompt;

	public void setIndentPrompt(String indentPrompt)
	{
		this.indentPrompt = indentPrompt;

		extend(myConsoleData.isIPythonEnabled() ? PyConsoleUtil.IPYTHON_INDENT_PROMPT : PyConsoleUtil.INDENT_PROMPT, mainPrompt.length());
	}

	public String getIndentPrompt()
	{
		return indentPrompt;
	}

	private String mainPrompt;

	public void setMainPrompt(String mainPrompt)
	{
		if(!Comparing.equal(this.mainPrompt, mainPrompt))
		{
			this.mainPrompt = mainPrompt;

			UIUtil.invokeLaterIfNeeded(() -> {
				myEditorEx.getGutterComponentEx().revalidateMarkup();
			});
		}
	}

	public String getMainPrompt()
	{
		return mainPrompt;
	}

	private EditorEx myEditorEx;
	private PythonConsoleData myConsoleData;

	public ConsolePromptDecorator(EditorEx editorEx, PythonConsoleData consoleData)
	{
		myEditorEx = editorEx;
		myConsoleData = consoleData;
	}

	@Nullable
	@Override
	public Collection<LineExtensionInfo> getLineExtensions(@Nonnull Project project, @Nonnull VirtualFile virtualFile, int i)
	{
		return null;
	}

	@Nullable
	@Override
	public String getLineText(int line, Editor editor)
	{
		if(line == 0)
		{
			return mainPrompt;
		}
		else if(line > 0)
		{
			return indentPrompt;
		}
		else
		{
			return null;
		}
	}

	@Nullable
	@Override
	public String getToolTip(int i, Editor editor)
	{
		return null;
	}

	@Override
	public EditorFontType getStyle(int i, Editor editor)
	{
		return EditorFontType.CONSOLE_PLAIN;
	}

	@Nullable
	@Override
	public ColorKey getColor(int i, Editor editor)
	{
		return promptColor;
	}

	@Nullable
	@Override
	public Color getBgColor(int i, Editor editor)
	{
		Color backgroundColor = this.promptAttributes.getAttributes().getBackgroundColor();
		if(backgroundColor == null)
		{
			backgroundColor = myEditorEx.getBackgroundColor();
		}
		return backgroundColor;
	}

	@Override
	public List<AnAction> getPopupActions(int i, Editor editor)
	{
		return null;
	}

	@Override
	public void gutterClosed()
	{

	}
}
