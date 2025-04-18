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

package com.jetbrains.python.impl;

import com.jetbrains.python.PythonDialectsTokenSetContributorBase;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.TokenSet;
import jakarta.annotation.Nonnull;

import static com.jetbrains.python.impl.PyElementTypes.*;
import static com.jetbrains.python.PyTokenTypes.*;

/**
 * @author vlan
 */
@ExtensionImpl
public class PythonTokenSetContributor extends PythonDialectsTokenSetContributorBase
{
  @Nonnull
  @Override
  public TokenSet getStatementTokens() {
    return TokenSet.create(EXPRESSION_STATEMENT, ASSIGNMENT_STATEMENT, AUG_ASSIGNMENT_STATEMENT, ASSERT_STATEMENT,
                           BREAK_STATEMENT, CONTINUE_STATEMENT, DEL_STATEMENT, EXEC_STATEMENT, FOR_STATEMENT,
                           FROM_IMPORT_STATEMENT, GLOBAL_STATEMENT, IMPORT_STATEMENT, IF_STATEMENT, PASS_STATEMENT,
                           PRINT_STATEMENT, RAISE_STATEMENT, RETURN_STATEMENT, TRY_EXCEPT_STATEMENT, WITH_STATEMENT,
                           WHILE_STATEMENT, NONLOCAL_STATEMENT, CLASS_DECLARATION, FUNCTION_DECLARATION);
  }

  @Nonnull
  @Override
  public TokenSet getExpressionTokens() {
    return TokenSet.create(EMPTY_EXPRESSION, REFERENCE_EXPRESSION, INTEGER_LITERAL_EXPRESSION, FLOAT_LITERAL_EXPRESSION,
                           IMAGINARY_LITERAL_EXPRESSION, STRING_LITERAL_EXPRESSION, PARENTHESIZED_EXPRESSION,
                           SUBSCRIPTION_EXPRESSION, SLICE_EXPRESSION, BINARY_EXPRESSION, PREFIX_EXPRESSION, CALL_EXPRESSION,
                           LIST_LITERAL_EXPRESSION, TUPLE_EXPRESSION, KEYWORD_ARGUMENT_EXPRESSION, STAR_ARGUMENT_EXPRESSION,
                           LAMBDA_EXPRESSION, LIST_COMP_EXPRESSION, DICT_LITERAL_EXPRESSION, KEY_VALUE_EXPRESSION,
                           REPR_EXPRESSION, GENERATOR_EXPRESSION, CONDITIONAL_EXPRESSION, YIELD_EXPRESSION,
                           TARGET_EXPRESSION, NONE_LITERAL_EXPRESSION, BOOL_LITERAL_EXPRESSION,
                           SET_LITERAL_EXPRESSION, SET_COMP_EXPRESSION, DICT_COMP_EXPRESSION, STAR_EXPRESSION);
  }

  @Nonnull
  @Override
  public TokenSet getNameDefinerTokens() {
    // FROM_IMPORT_STATEMENT is not exactly a NameDefiner but needed anyway in mypackage/__init__.py, 'from mypackage.foo import bar' makes
    // 'foo' name visible
    return TokenSet.create(STAR_IMPORT_ELEMENT, IMPORT_ELEMENT, CLASS_DECLARATION,
                           GLOBAL_STATEMENT, GENERATOR_EXPRESSION, DICT_COMP_EXPRESSION,
                           LIST_COMP_EXPRESSION, SET_COMP_EXPRESSION, WITH_STATEMENT,
                           FUNCTION_DECLARATION, ASSIGNMENT_STATEMENT, EXCEPT_PART,
                           FOR_STATEMENT,
                           FROM_IMPORT_STATEMENT);
  }

  @Nonnull
  @Override
  public TokenSet getKeywordTokens() {
    return TokenSet.create(
      AND_KEYWORD, AS_KEYWORD, ASSERT_KEYWORD, BREAK_KEYWORD, CLASS_KEYWORD,
      CONTINUE_KEYWORD, DEF_KEYWORD, DEL_KEYWORD, ELIF_KEYWORD, ELSE_KEYWORD,
      EXCEPT_KEYWORD, EXEC_KEYWORD, FINALLY_KEYWORD, FOR_KEYWORD,
      FROM_KEYWORD,
      GLOBAL_KEYWORD, IF_KEYWORD, IMPORT_KEYWORD, IN_KEYWORD, IS_KEYWORD,
      LAMBDA_KEYWORD, NOT_KEYWORD, OR_KEYWORD, PASS_KEYWORD, PRINT_KEYWORD,
      RAISE_KEYWORD, RETURN_KEYWORD, TRY_KEYWORD, WITH_KEYWORD, WHILE_KEYWORD,
      YIELD_KEYWORD,
      NONE_KEYWORD, TRUE_KEYWORD, FALSE_KEYWORD, NONLOCAL_KEYWORD, DEBUG_KEYWORD);
  }

  @Nonnull
  @Override
  public TokenSet getParameterTokens() {
    return TokenSet.create(NAMED_PARAMETER, TUPLE_PARAMETER, SINGLE_STAR_PARAMETER);
  }

  @Nonnull
  @Override
  public TokenSet getFunctionDeclarationTokens() {
    return TokenSet.create(FUNCTION_DECLARATION);
  }

  @Nonnull
  @Override
  public TokenSet getUnbalancedBracesRecoveryTokens() {
    return TokenSet.create(DEF_KEYWORD, CLASS_KEYWORD, RETURN_KEYWORD, WITH_KEYWORD, WHILE_KEYWORD, BREAK_KEYWORD, CONTINUE_KEYWORD,
                           RAISE_KEYWORD, TRY_KEYWORD, EXCEPT_KEYWORD, FINALLY_KEYWORD);
  }

  @Nonnull
  @Override
  public TokenSet getReferenceExpressionTokens() {
    return TokenSet.create(REFERENCE_EXPRESSION);
  }
}
