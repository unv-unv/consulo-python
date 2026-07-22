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
package com.jetbrains.python.impl.psi.impl;

import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.*;

import org.jspecify.annotations.Nullable;

/**
 * @author yole
 */
public class PyBreakStatementImpl extends PyElementImpl implements PyBreakStatement {
    public PyBreakStatementImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyBreakStatement(this);
    }

    @Nullable
    @Override
    public PyLoopStatement getLoopStatement() {
        return getLoopStatement(this);
    }

    @Nullable
    private static PyLoopStatement getLoopStatement(PsiElement element) {
        PyLoopStatement loop = PsiTreeUtil.getParentOfType(element, PyLoopStatement.class);
        if (loop instanceof PyStatementWithElse stmt && PsiTreeUtil.isAncestor(stmt.getElsePart(), element, true)) {
            return getLoopStatement(loop);
        }
        return loop;
    }
}
