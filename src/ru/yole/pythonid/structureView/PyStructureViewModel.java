/*
 * Copyright 2006 Dmitry Jemerov (yole)
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

package ru.yole.pythonid.structureView;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.TextEditorBasedStructureViewModel;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.Grouper;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.psi.PsiFile;
import ru.yole.pythonid.psi.PyClass;
import ru.yole.pythonid.psi.PyElement;
import ru.yole.pythonid.psi.PyFunction;

public class PyStructureViewModel extends TextEditorBasedStructureViewModel {
	private PyElement _root;

	public PyStructureViewModel(PyElement root) {
		super(root.getContainingFile());
		this._root = root;
	}

	public StructureViewTreeElement getRoot() {
		return new PyStructureViewElement(this._root);
	}

	public Grouper[] getGroupers() {
		return new Grouper[0];
	}

	public Sorter[] getSorters() {
		return new Sorter[]{Sorter.ALPHA_SORTER};
	}

	public Filter[] getFilters() {
		return new Filter[0];
	}

	protected PsiFile getPsiFile() {
		return this._root.getContainingFile();
	}

	protected Class[] getSuitableClasses() {
		return new Class[]{PyFunction.class, PyClass.class};
	}
}