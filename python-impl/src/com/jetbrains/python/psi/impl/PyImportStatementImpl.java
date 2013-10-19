package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.ArrayFactory;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyImportStatement;
import com.jetbrains.python.psi.stubs.PyImportStatementStub;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyImportStatementImpl extends PyBaseElementImpl<PyImportStatementStub> implements PyImportStatement {
  public PyImportStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyImportStatementImpl(PyImportStatementStub stub) {
    this(stub, PyElementTypes.IMPORT_STATEMENT);
  }

  public PyImportStatementImpl(PyImportStatementStub stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }

  @NotNull
  public PyImportElement[] getImportElements() {
    final PyImportStatementStub stub = getStub();
    if (stub != null) {
      return stub.getChildrenByType(PyElementTypes.IMPORT_ELEMENT, new ArrayFactory<PyImportElement>() {
        @NotNull
        public PyImportElement[] create(int count) {
          return new PyImportElement[count];
        }
      });
    }
    return childrenToPsi(TokenSet.create(PyElementTypes.IMPORT_ELEMENT), new PyImportElement[0]);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyImportStatement(this);
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    PyPsiUtils.deleteAdjacentComma(this, child, getImportElements());
    super.deleteChildInternal(child);
  }
}