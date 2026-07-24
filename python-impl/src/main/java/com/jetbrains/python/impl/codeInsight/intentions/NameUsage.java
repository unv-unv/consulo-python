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
package com.jetbrains.python.impl.codeInsight.intentions;

import consulo.annotation.access.RequiredReadAction;
import consulo.colorScheme.TextAttributes;
import consulo.document.Document;
import consulo.fileEditor.FileEditorLocation;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.EditSourceUtil;
import consulo.navigation.Navigatable;
import consulo.ui.image.Image;
import consulo.usage.TextChunk;
import consulo.usage.UsagePresentation;
import consulo.usage.rule.PsiElementUsage;

import org.jspecify.annotations.Nullable;
import java.awt.*;

/**
 * Simplistic usage object for demonstration of name clashes, etc.
 * @author dcheryasov
 * @since 2009-10-11
 */
class NameUsage implements PsiElementUsage {
  private final PsiElement myElement;
  private final PsiElement myCulprit;

  static final TextAttributes SLANTED;
  private final String myName;
  private final boolean myIsPrefix;

  static {
    SLANTED = TextAttributes.ERASE_MARKER.clone();
    SLANTED.setFontType(Font.ITALIC);
  }

  /**
   * Creates a conflict search panel usage.
   * @param element where conflict happens.
   * @param culprit where name is redefined; usages with the same culprit are grouped.
   * @param name redefinition of it is what the conflict is about.
   * @param prefix if true, show name as a prefix to element's name in "would be" part.
   */
  public NameUsage(PsiElement element, PsiElement culprit, String name, boolean prefix) {
    myElement = element;
    myCulprit = culprit;
    myName = name;
    myIsPrefix = prefix;
  }

  @Override
  public FileEditorLocation getLocation() {
    return null;
  }

  @Override
  public UsagePresentation getPresentation() {
    return new UsagePresentation() {
      @Nullable
      @Override
      @RequiredReadAction
      public Image getIcon() {
        return myElement.isValid() ? IconDescriptorUpdaters.getIcon(myElement, 0) : null;
      }

      @Override
      @RequiredReadAction
      public TextChunk[] getText() {
        if (myElement.isValid()) {
          TextChunk[] chunks = new TextChunk[3];
          PsiFile file = myElement.getContainingFile();
          String line_id = "...";
          Document document = file.getViewProvider().getDocument();
          if (document != null) {
            line_id = String.valueOf(document.getLineNumber(myElement.getTextOffset()));
          }
          chunks[0] = new TextChunk(SLANTED, "(" + line_id + ") ");
          chunks[1] = new TextChunk(TextAttributes.ERASE_MARKER, myElement.getText());
          StringBuilder sb = new StringBuilder(" would become ").append(myName);
          if (myIsPrefix) sb.append(".").append(myElement.getText());
          chunks[2] = new TextChunk(SLANTED, sb.toString());
          return chunks;
        }
        else return new TextChunk[]{new TextChunk(SLANTED, "?")}; 
      }

      @Override
      @RequiredReadAction
      public String getPlainText() {
        return myElement.getText();
      }

      @Override
      @RequiredReadAction
      public String getTooltipText() {
        return myElement.getText();
      }
    };
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public boolean isReadOnly() {
    return false;
  }

  @Override
  public void selectInEditor() { }

  @Override
  public void highlightInEditor() { }

  @Override
  @RequiredReadAction
  public void navigate(boolean requestFocus) {
    Navigatable descr = EditSourceUtil.getDescriptor(myElement);
    if (descr != null) descr.navigate(requestFocus);
  }

  @Override
  @RequiredReadAction
  public boolean canNavigate() {
    return EditSourceUtil.canNavigate(myElement);
  }

  @Override
  @RequiredReadAction
  public boolean canNavigateToSource() {
    return false;
  }

  @Override
  public PsiElement getElement() {
    return myCulprit;
  }

  @Override
  public boolean isNonCodeUsage() {
    return false;
  }
}
