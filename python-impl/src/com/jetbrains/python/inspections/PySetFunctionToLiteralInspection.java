package com.jetbrains.python.inspections;

import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.inspections.quickfix.ReplaceFunctionWithSetLiteralQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: catherine
 *
 * Inspection to find set built-in function and replace it with set literal
 * available if the selected language level supports set literals.
 */
public class PySetFunctionToLiteralInspection extends PyInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.set.function.to.literal");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  private static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyCallExpression(final PyCallExpression node) {
      if (!isAvailable(node)) return;
      PyExpression callee = node.getCallee();
      if (node.isCalleeText(PyNames.SET) && isInBuiltins(callee)) {
        PyExpression[] arguments = node.getArguments();
        if (arguments.length == 1) {
          PyElement[] elements = getSetCallArguments(node);
          if (elements.length != 0)
              registerProblem(node, PyBundle.message("INSP.NAME.set.function.to.literal"),
                                               new ReplaceFunctionWithSetLiteralQuickFix());
        }
      }
    }

    private static boolean isAvailable(PyCallExpression node) {
      final InspectionProfile profile = InspectionProjectProfileManager.getInstance(node.getProject()).getInspectionProfile();
      final InspectionToolWrapper inspectionTool = profile.getInspectionTool("PyCompatibilityInspection", node.getProject());
      if (inspectionTool != null) {
        final InspectionProfileEntry inspection = inspectionTool.getTool();
        if (inspection instanceof PyCompatibilityInspection) {
          final JDOMExternalizableStringList versions = ((PyCompatibilityInspection)inspection).ourVersions;
          for (String s : versions) {
            if (!LanguageLevel.fromPythonVersion(s).supportsSetLiterals()) return false;
          }
        }
      }
      return LanguageLevel.forElement(node).supportsSetLiterals();
    }

    private static boolean isInBuiltins(PyExpression callee) {
      if (callee instanceof PyQualifiedExpression && (((PyQualifiedExpression)callee).getQualifier() != null)) {
        return false;
      }
      PsiReference reference = callee.getReference();
      if (reference != null) {
        PsiElement resolved = reference.resolve();
        if (resolved != null && PyBuiltinCache.getInstance(callee).hasInBuiltins(resolved)) {
          return true;
        }
      }
      return false;
    }
  }

  public static PyElement[] getSetCallArguments(PyCallExpression node) {
    PyExpression argument = node.getArguments()[0];
    if (argument instanceof PyStringLiteralExpression) {
      return PyElement.EMPTY_ARRAY;
    }
    if ((argument instanceof PySequenceExpression || (argument instanceof PyParenthesizedExpression &&
                  ((PyParenthesizedExpression)argument).getContainedExpression() instanceof PyTupleExpression))) {

      if (argument instanceof PySequenceExpression)
        return ((PySequenceExpression)argument).getElements();
      PyExpression tuple = ((PyParenthesizedExpression)argument).getContainedExpression();
      if (tuple instanceof PyTupleExpression)
        return ((PyTupleExpression)(tuple)).getElements();
    }
    return PyElement.EMPTY_ARRAY;
  }
}