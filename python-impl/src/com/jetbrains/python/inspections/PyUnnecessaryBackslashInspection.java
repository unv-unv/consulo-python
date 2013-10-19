package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.Stack;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.inspections.quickfix.RemoveUnnecessaryBackslashQuickFix;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: catherine
 * <p/>
 * Inspection to highlight backslashes in places where line continuation is implicit (inside (), [], {}).
 */
public class PyUnnecessaryBackslashInspection extends PyInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.unnecessary.backslash");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  public static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyParameterList(final PyParameterList list) {
      findProblem(list);
    }

    @Override
    public void visitPyArgumentList(final PyArgumentList list) {
      findProblem(list);
    }

    @Override
    public void visitPyTupleExpression(PyTupleExpression node) {
      if (node.getParent() instanceof PyParenthesizedExpression)
        findProblem(node);
    }

    @Override
    public void visitPyParenthesizedExpression(final PyParenthesizedExpression expression) {
      final Stack<PsiElement> stack = new Stack<PsiElement>();
      stack.push(expression);
      while (!stack.isEmpty()) {
        PsiElement element = stack.pop();
        if (!(element instanceof PyTupleExpression)) {
          findProblem(element);
          if (element != null) {
            for (PsiElement psiElement : element.getChildren()) {
              stack.push(psiElement);
            }
          }
        }
      }
    }

    @Override
    public void visitPyDictLiteralExpression(final PyDictLiteralExpression expression) {
      findProblem(expression);
    }

    @Override
    public void visitPyListLiteralExpression(final PyListLiteralExpression expression) {
      findProblem(expression);
    }

    @Override
    public void visitPySetLiteralExpression(final PySetLiteralExpression expression) {
      findProblem(expression);
    }

    @Override
    public void visitPyStringLiteralExpression(final PyStringLiteralExpression stringLiteralExpression) {
      PsiElement parent = stringLiteralExpression.getParent();
      if (parent instanceof PyListLiteralExpression || parent instanceof PyParenthesizedExpression ||
          parent instanceof PySetLiteralExpression || parent instanceof PyKeyValueExpression ||
          parent instanceof PyNamedParameter || parent instanceof PyArgumentList) {
        findProblem(stringLiteralExpression);
      }
    }

    private void findProblem(@Nullable final PsiElement expression) {
      final PsiWhiteSpace[] children = PsiTreeUtil.getChildrenOfType(expression, PsiWhiteSpace.class);
      if (children != null) {
        for (PsiWhiteSpace ws : children) {
          if (ws.getText().contains("\\")) {
            registerProblem(ws, "Unnecessary backslash in expression.", new RemoveUnnecessaryBackslashQuickFix());
          }
        }
      }
    }

  }
}