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
package com.jetbrains.python.impl.refactoring.classes.ui;

import java.awt.Component;

import consulo.annotation.access.RequiredReadAction;
import org.jspecify.annotations.Nullable;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JList;

import consulo.component.util.Iconable;
import com.jetbrains.python.psi.PyClass;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.language.icon.IconDescriptorUpdaters;

/**
 * @author Dennis.Ushakov
 */
public class PyClassCellRenderer extends DefaultListCellRenderer
{
	private final boolean myShowReadOnly;

	public PyClassCellRenderer()
	{
		setOpaque(true);
		myShowReadOnly = true;
	}

	public PyClassCellRenderer(boolean showReadOnly)
	{
		setOpaque(true);
		myShowReadOnly = showReadOnly;
	}

	@Override
    @RequiredReadAction
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus)
	{
		super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

		return customizeRenderer(value, myShowReadOnly);
	}

	@RequiredReadAction
    public JLabel customizeRenderer(Object value, boolean showReadOnly)
	{
		PyClass aClass = (PyClass) value;
		setText(getClassText(aClass));

		int flags = Iconable.ICON_FLAG_VISIBILITY;
		if(showReadOnly)
		{
			flags |= Iconable.ICON_FLAG_READ_STATUS;
		}
		Icon icon = TargetAWT.to(IconDescriptorUpdaters.getIcon(aClass, flags));
		if(icon != null)
		{
			setIcon(icon);
		}
		return this;
	}

	@Nullable
    @RequiredReadAction
	public static String getClassText(PyClass aClass)
	{
		return aClass.getName();
	}
}
