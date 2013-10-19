package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.stubs.PyAnnotationStub;

/**
 * @author yole
 */
public class PyAnnotationImpl extends PyBaseElementImpl<PyAnnotationStub> implements PyAnnotation {
  public PyAnnotationImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyAnnotationImpl(PyAnnotationStub stub) {
    super(stub, PyElementTypes.ANNOTATION);
  }

  @Override
  public PyExpression getValue() {
    return findChildByClass(PyExpression.class);
  }

  @Override
  public PyClass resolveToClass() {
    PyExpression expr = getValue();
    if (expr instanceof PyReferenceExpression) {
      final PsiPolyVariantReference reference = ((PyReferenceExpression)expr).getReference();
      final PsiElement result = reference.resolve();
      if (result instanceof PyClass) {
        return (PyClass) result;
      }
    }
    return null;
  }
}