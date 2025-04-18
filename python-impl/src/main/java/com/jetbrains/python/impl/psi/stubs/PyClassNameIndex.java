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

/*
 * @author max
 */
package com.jetbrains.python.impl.psi.stubs;

import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.impl.psi.search.PyProjectScopeBuilder;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.stub.StringStubIndexExtension;
import consulo.language.psi.stub.StubIndex;
import consulo.language.psi.stub.StubIndexKey;
import consulo.project.Project;
import consulo.project.content.scope.ProjectScopes;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collection;

@ExtensionImpl
public class PyClassNameIndex extends StringStubIndexExtension<PyClass> {
  public static final StubIndexKey<String,PyClass> KEY = StubIndexKey.createIndexKey("Py.class.shortName");

  @Nonnull
  public StubIndexKey<String, PyClass> getKey() {
    return KEY;
  }

  public static Collection<PyClass> find(String name, Project project, GlobalSearchScope scope) {
    return StubIndex.getInstance().get(KEY, name, project, scope);
  }

  public static Collection<PyClass> find(String name, Project project, boolean includeNonProjectItems) {
    final GlobalSearchScope scope = includeNonProjectItems
                                    ? PyProjectScopeBuilder.excludeSdkTestsScope(project)
                                    : GlobalSearchScope.projectScope(project);
    return find(name, project, scope);
  }


  @Nullable
  public static PyClass findClass(@Nonnull String qName, Project project, GlobalSearchScope scope) {
    int pos = qName.lastIndexOf(".");
    String shortName = pos > 0 ? qName.substring(pos+1) : qName;
    for (PyClass pyClass : find(shortName, project, scope)) {
      if (pyClass.getQualifiedName().equals(qName)) {
        return pyClass;
      }
    }
    return null;
  }

  @Nullable
  public static PyClass findClass(@Nullable String qName, Project project) {
    if (qName == null) {
      return null;
    }
    return findClass(qName, project, (GlobalSearchScope) ProjectScopes.getAllScope(project));
  }

  public static Collection<String> allKeys(Project project) {
    return StubIndex.getInstance().getAllKeys(KEY, project);
  }
}