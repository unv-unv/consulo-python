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
package com.jetbrains.python.impl.facet;

import consulo.application.Application;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.library.Library;
import consulo.content.library.LibraryTable;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.ProjectRootManager;
import consulo.module.content.layer.ModifiableRootModel;
import consulo.module.content.layer.orderEntry.LibraryOrderEntry;
import consulo.module.content.layer.orderEntry.OrderEntry;
import consulo.module.content.util.OrderEntryUtil;
import consulo.project.Project;
import consulo.project.content.library.ProjectLibraryTable;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.archive.ArchiveFileType;
import consulo.virtualFileSystem.archive.ArchiveVfsUtil;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author yole
 */
public class FacetLibraryConfigurator {
  private FacetLibraryConfigurator() {
  }

  @RequiredUIAccess
  public static void attachLibrary(
      Module module,
      @Nullable ModifiableRootModel existingModel,
      String libraryName,
      List<String> paths
  ) {
    Application.get().runWriteAction(() -> {
      // add all paths to library
      ModifiableRootModel model =
        existingModel != null ? existingModel : ModuleRootManager.getInstance(module).getModifiableModel();
      LibraryOrderEntry orderEntry = OrderEntryUtil.findLibraryOrderEntry(model, libraryName);
      if (orderEntry != null) {
        // update existing
        Library lib = orderEntry.getLibrary();
        if (lib != null) {
          fillLibrary(module.getProject(), lib, paths);
          if (existingModel == null) {
            model.commit();
          }
          return;
        }
      }
      // create new
      LibraryTable.ModifiableModel projectLibrariesModel = ProjectLibraryTable.getInstance(model.getProject()).getModifiableModel();
      Library lib = projectLibrariesModel.createLibrary(libraryName);
      fillLibrary(module.getProject(), lib, paths);
      projectLibrariesModel.commit();
      model.addLibraryEntry(lib);
      if (existingModel == null) {
        model.commit();
      }
    });
  }

  private static void fillLibrary(Project project, Library lib, List<String> paths) {
    Library.ModifiableModel modifiableModel = lib.getModifiableModel();
    for (String root : lib.getUrls(BinariesOrderRootType.ID)) {
      modifiableModel.removeRoot(root, BinariesOrderRootType.ID);
    }
    Set<VirtualFile> roots = new HashSet<>();
    ProjectRootManager rootManager = ProjectRootManager.getInstance(project);
    Collections.addAll(roots, rootManager.getContentRoots());
    Collections.addAll(roots, rootManager.getContentSourceRoots());
    if (paths != null) {
      for (String dir : paths) {
        VirtualFile pathEntry = LocalFileSystem.getInstance().findFileByPath(dir);
        if (pathEntry != null && !pathEntry.isDirectory() && pathEntry.getFileType() instanceof ArchiveFileType) {
          pathEntry = ArchiveVfsUtil.getJarRootForLocalFile(pathEntry);
        }
        // buildout includes source root of project in paths; don't add it as library home
        if (pathEntry != null && roots.contains(pathEntry)) {
          continue;
        }
        if (pathEntry != null) {
          modifiableModel.addRoot(pathEntry, BinariesOrderRootType.ID);
        }
        else {
          modifiableModel.addRoot("file://" + dir, BinariesOrderRootType.ID);
        }
      }
    }
    modifiableModel.commit();
  }

  @RequiredUIAccess
  public static void detachLibrary(Module module, String libraryName) {
    Application.get().runWriteAction(() -> {
      // remove the library
      ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
      OrderEntry entry = OrderEntryUtil.findLibraryOrderEntry(model, libraryName);
      if (entry == null) {
        model.dispose();
      }
      else {
        model.removeOrderEntry(entry);
        model.commit();
      }
    });
  }
}
