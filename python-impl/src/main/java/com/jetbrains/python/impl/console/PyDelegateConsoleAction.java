package com.jetbrains.python.impl.console;

import consulo.ui.ex.action.*;
import consulo.util.concurrent.coroutine.Coroutine;
import consulo.util.concurrent.coroutine.step.CodeExecution;

/**
 * @author VISTALL
 * @since 2026-07-03
 */
public abstract class PyDelegateConsoleAction extends DumbAwareAction implements AnActionWithAsyncUpdate {
    private final AnAction myDelegate;

    public PyDelegateConsoleAction(AnAction delegate) {
        myDelegate = delegate;
    }

    @Override
    public Coroutine<?, ?> updateAsync(AnActionEvent e) {
        if (myDelegate instanceof AnActionWithAsyncUpdate asyncUpdate) {
            return asyncUpdate.updateAsync(e);
        }

        if (myDelegate instanceof AnActionWithSyncUpdate syncUpdate) {
            return CodeExecution.run(() -> syncUpdate.update(e)).toCoroutine();
        }

        return CodeExecution.run(() -> {
        }).toCoroutine();
    }
}
