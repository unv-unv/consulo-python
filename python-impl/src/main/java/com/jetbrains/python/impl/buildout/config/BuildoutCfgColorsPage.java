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
package com.jetbrains.python.impl.buildout.config;

import consulo.annotation.component.ExtensionImpl;
import consulo.colorScheme.TextAttributesKey;
import consulo.colorScheme.setting.AttributesDescriptor;
import consulo.colorScheme.setting.ColorDescriptor;
import consulo.language.editor.colorScheme.setting.ColorSettingsPage;
import consulo.language.editor.highlight.SyntaxHighlighter;
import consulo.language.editor.highlight.SyntaxHighlighterFactory;
import consulo.localize.LocalizeValue;

import java.util.HashMap;
import java.util.Map;

/**
 * @author traff
 */
@ExtensionImpl
public class BuildoutCfgColorsPage implements ColorSettingsPage {
  private static final AttributesDescriptor[] ATTRS = new AttributesDescriptor[]{
    new AttributesDescriptor("Section name", BuildoutCfgSyntaxHighlighter.BUILDOUT_SECTION_NAME),
    new AttributesDescriptor("Key", BuildoutCfgSyntaxHighlighter.BUILDOUT_KEY),
    new AttributesDescriptor("Value", BuildoutCfgSyntaxHighlighter.BUILDOUT_VALUE),
    new AttributesDescriptor("Key value separator", BuildoutCfgSyntaxHighlighter.BUILDOUT_KEY_VALUE_SEPARATOR),
    new AttributesDescriptor("Comment", BuildoutCfgSyntaxHighlighter.BUILDOUT_COMMENT)
  };

  private static final HashMap<String, TextAttributesKey> ourTagToDescriptorMap = new HashMap<>();

  static {
    //ourTagToDescriptorMap.put("comment", DjangoTemplateHighlighterColors.DJANGO_COMMENT);
  }

  @Override
  public LocalizeValue getDisplayName() {
    return LocalizeValue.localizeTODO("Buildout config");
  }

  @Override
  public AttributesDescriptor[] getAttributeDescriptors() {
    return ATTRS;
  }

  @Override
  public ColorDescriptor[] getColorDescriptors() {
    return ColorDescriptor.EMPTY_ARRAY;
  }

  @Override
  public SyntaxHighlighter getHighlighter() {
    SyntaxHighlighter highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(BuildoutCfgFileType.INSTANCE, null, null);
    assert highlighter != null;
    return highlighter;
  }

  @Override
  public String getDemoText() {
    return
      "; Buildout config\n" +
        "[buildout]\n" +
        "parts = python\n" +
        "develop = .\n" +
        "eggs = django-shorturls\n" +
        "\n" +
        "[python]\n" +
        "recipe = zc.recipe.egg\n" +
        "interpreter = python\n" +
        "eggs = ${buildout:eggs}";
  }

  @Override
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return ourTagToDescriptorMap;
  }
}
