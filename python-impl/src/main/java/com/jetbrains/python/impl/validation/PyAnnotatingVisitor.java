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

package com.jetbrains.python.impl.validation;

import com.jetbrains.python.impl.psi.impl.PyFileImpl;
import consulo.language.editor.annotation.AnnotationHolder;
import consulo.language.editor.annotation.Annotator;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.logging.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PyAnnotatingVisitor implements Annotator {
  private static final Logger LOGGER = Logger.getInstance(PyAnnotatingVisitor.class);

  private final List<PyAnnotator> myAnnotators = new ArrayList<PyAnnotator>();

  private final Class[] ANNOTATOR_CLASSES = new Class[]{
    AssignTargetAnnotator.class,
    ParameterListAnnotator.class,
    HighlightingAnnotator.class,
    ReturnAnnotator.class,
    TryExceptAnnotator.class,
    BreakContinueAnnotator.class,
    GlobalAnnotator.class,
    ImportAnnotator.class,
    StringLiteralQuotesAnnotator.class,
    PyBuiltinAnnotator.class,
    UnsupportedFeatures.class
  };

  public PyAnnotatingVisitor() {
    for (Class cls : ANNOTATOR_CLASSES) {
      PyAnnotator annotator;
      try {
        annotator = (PyAnnotator)cls.newInstance();
      }
      catch (InstantiationException e) {
        LOGGER.error(e);
        continue;
      }
      catch (IllegalAccessException e) {
        LOGGER.error(e);
        continue;
      }
      myAnnotators.add(annotator);
    }
  }

  @Override
  public void annotate(PsiElement psiElement, AnnotationHolder holder) {
    PsiFile file = psiElement.getContainingFile();
    for (PyAnnotator annotator : myAnnotators) {
      if (file instanceof PyFileImpl fileImpl && !fileImpl.isAcceptedFor(annotator.getClass())) continue;
      annotator.annotateElement(psiElement, holder);
    }
  }
}
