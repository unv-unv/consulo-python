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

package com.jetbrains.python.impl.codeInsight.liveTemplates;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.template.Expression;
import consulo.language.editor.template.ExpressionContext;
import consulo.language.editor.template.Result;
import consulo.language.editor.template.TextResult;
import consulo.language.editor.template.macro.Macro;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
@ExtensionImpl
public class CollectionElementNameMacro extends Macro
{
  public String getName() {
    return "collectionElementName";
  }

  public String getPresentableName() {
    return "collectionElementName()";
  }

  @Nonnull
  public String getDefaultValue() {
    return "a";
  }

  public Result calculateResult(@Nonnull Expression[] params, ExpressionContext context) {
    if (params.length != 1) {
      return null;
    }
    final Result paramResult = params[0].calculateResult(context);
    if (paramResult == null) {
      return null;
    }
    String param = paramResult.toString();
    int lastDot = param.lastIndexOf('.');
    if (lastDot >= 0) {
      param = param.substring(lastDot+1);
    }
    if (param.endsWith(")")) {
      int lastParen = param.lastIndexOf('(');
      if (lastParen > 0) {
        param = param.substring(0, lastParen);
      }
    }
    final String result = smartUnpluralize(param);
    return new TextResult(result);
  }

  private static String smartUnpluralize(String param) {
    if (param.endsWith("_list")) {
      return param.substring(0, param.length()-5);
    }
    final String result = StringUtil.unpluralize(param);
    return result == null ? param : result;
  }

  public LookupElement[] calculateLookupItems(@Nonnull Expression[] params, ExpressionContext context) {
    Result result = calculateResult(params, context);
    if (result == null) {
      return null;
    }
    final String[] words = result.toString().split("_");
    if (words.length > 1) {
      List<LookupElement> lookup = new ArrayList<LookupElement>();
      for(int i=0; i<words.length; i++) {
        String element = StringUtil.join(words, i, words.length, "_");
        lookup.add(LookupElementBuilder.create(element));
      }
      return lookup.toArray(new LookupElement[lookup.size()]);
    }
    return null;
  }
}
