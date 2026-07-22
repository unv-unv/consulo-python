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
package com.jetbrains.python.impl.debugger.dataframe;

import consulo.project.Project;
import com.jetbrains.python.debugger.ArrayChunk;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.impl.debugger.array.AsyncArrayTableModel;
import com.jetbrains.python.impl.debugger.array.TableChunkDatasource;
import com.jetbrains.python.impl.debugger.containerview.ColoredCellRenderer;
import com.jetbrains.python.impl.debugger.containerview.NumericContainerViewTable;
import com.jetbrains.python.impl.debugger.containerview.ViewNumericContainerDialog;

/**
 * A bunch of this is copied from NumpyArrayTable.
 *
 * @author amarch
 */
public class DataFrameTable extends NumericContainerViewTable implements TableChunkDatasource {
    private Project myProject;

    public DataFrameTable(Project project, ViewNumericContainerDialog dialog, PyDebugValue value) {
        super(project, dialog, value);
    }

    @Override
    protected AsyncArrayTableModel createTableModel(int rowCount, int columnCount) {
        return new DataFrameTableModel(rowCount, columnCount, this);
    }

    @Override
    protected ColoredCellRenderer createCellRenderer(double minValue, double maxValue, ArrayChunk chunk) {
        return new DataFrameTableCellRenderer();
    }

    @Override
    public boolean isNumeric() {
        return true;
    }

    @Override
    protected final String getTitlePresentation(String slice) {
        return "DataFrame View: " + slice;
    }
}
