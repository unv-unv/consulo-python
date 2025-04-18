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
package com.jetbrains.python.impl.testing.attest;

import com.google.common.collect.Lists;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import com.jetbrains.python.impl.testing.*;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.content.bundle.Sdk;
import consulo.execution.action.Location;
import consulo.language.psi.PsiElement;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;

/**
 * User: catherine
 */
@ExtensionImpl
public class PythonAtTestConfigurationProducer extends PythonTestConfigurationProducer {
    public PythonAtTestConfigurationProducer() {
        super(PythonTestConfigurationType.getInstance().PY_ATTEST_FACTORY);
    }

    @Override
    @RequiredReadAction
    protected boolean isAvailable(@Nonnull Location location) {
        PsiElement element = location.getPsiElement();
        Module module = location.getModule();
        if (module == null) {
            module = ModuleUtilCore.findModuleForPsiElement(element);
        }

        Sdk sdk = PythonSdkType.findPythonSdk(module);
        return module != null && TestRunnerService.getInstance(module)
            .getProjectConfiguration()
            .equals(PythonTestConfigurationsModel.PYTHONS_ATTEST_NAME) && sdk != null;
    }

    @Override
    protected boolean isTestClass(
        @Nonnull PyClass pyClass,
        @Nullable AbstractPythonTestRunConfiguration configuration,
        @Nullable TypeEvalContext context
    ) {
        for (PyClassLikeType type : pyClass.getAncestorTypes(TypeEvalContext.codeInsightFallback(pyClass.getProject()))) {
            if (type != null && "TestBase".equals(type.getName()) && hasTestFunction(pyClass)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasTestFunction(@Nonnull PyClass pyClass) {
        PyFunction[] methods = pyClass.getMethods();
        for (PyFunction function : methods) {
            PyDecoratorList decorators = function.getDecoratorList();
            if (decorators == null) {
                continue;
            }
            for (PyDecorator decorator : decorators.getDecorators()) {
                if ("test".equals(decorator.getName()) || "test_if".equals(decorator.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected boolean isTestFunction(@Nonnull PyFunction pyFunction, @Nullable AbstractPythonTestRunConfiguration configuration) {
        PyDecoratorList decorators = pyFunction.getDecoratorList();
        if (decorators == null) {
            return false;
        }
        for (PyDecorator decorator : decorators.getDecorators()) {
            if ("test".equals(decorator.getName()) || "test_if".equals(decorator.getName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected List<PyStatement> getTestCaseClassesFromFile(@Nonnull PyFile file) {
        List<PyStatement> result = Lists.newArrayList();
        for (PyClass cls : file.getTopLevelClasses()) {
            if (isTestClass(cls, null, null)) {
                result.add(cls);
            }
        }

        for (PyFunction cls : file.getTopLevelFunctions()) {
            if (isTestFunction(cls, null)) {
                result.add(cls);
            }
        }
        return result;
    }
}