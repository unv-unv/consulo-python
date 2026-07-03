/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.impl.console;

import com.google.common.base.CharMatcher;
import com.google.common.collect.Lists;
import com.jetbrains.python.console.pydev.ConsoleCommunicationListener;
import com.jetbrains.python.impl.PythonHelper;
import com.jetbrains.python.impl.console.actions.ShowVarsAction;
import com.jetbrains.python.impl.debugger.PyDebugRunner;
import com.jetbrains.python.impl.run.PythonCommandLineState;
import com.jetbrains.python.impl.run.PythonProcessHandler;
import com.jetbrains.python.impl.run.PythonTracebackFilter;
import com.jetbrains.python.run.PythonRunParams;
import consulo.annotation.access.RequiredWriteAction;
import consulo.application.Application;
import consulo.application.dumb.DumbAware;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.codeEditor.Caret;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorEx;
import consulo.codeEditor.action.EditorAction;
import consulo.codeEditor.action.EditorWriteActionHandler;
import consulo.content.bundle.Sdk;
import consulo.dataContext.DataContext;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.document.util.TextRange;
import consulo.execution.ExecutionHelper;
import consulo.execution.ExecutionManager;
import consulo.execution.action.CloseAction;
import consulo.execution.debug.XDebugSession;
import consulo.execution.debug.XDebuggerManager;
import consulo.execution.debug.icon.ExecutionDebugIconGroup;
import consulo.execution.executor.DefaultRunExecutor;
import consulo.execution.executor.Executor;
import consulo.execution.process.ProcessTerminatedListener;
import consulo.execution.ui.RunContentDescriptor;
import consulo.execution.ui.console.ConsoleExecuteAction;
import consulo.execution.ui.console.ConsoleHistoryController;
import consulo.execution.ui.console.language.LanguageConsoleView;
import consulo.execution.util.ConsoleTitleGen;
import consulo.ide.impl.idea.execution.configurations.EncodingEnvironmentUtil;
import consulo.ide.impl.idea.execution.console.ConsoleHistoryControllerImpl;
import consulo.ide.impl.idea.openapi.actionSystem.ex.ActionImplUtil;
import consulo.ide.impl.idea.util.PathMappingSettings;
import consulo.language.editor.completion.lookup.LookupManager;
import consulo.language.file.light.LightVirtualFile;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.process.ExecutionException;
import consulo.process.KillableProcessHandler;
import consulo.process.ProcessHandler;
import consulo.process.ProcessOutputTypes;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.cmd.ParamsGroup;
import consulo.process.event.ProcessAdapter;
import consulo.process.event.ProcessEvent;
import consulo.process.event.ProcessListener;
import consulo.project.Project;
import consulo.python.psi.icon.PythonPsiIconGroup;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.MessageCategory;
import consulo.ui.ex.action.*;
import consulo.ui.ex.awt.IJSwingUtilities;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.SideBorder;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.content.Content;
import consulo.ui.ex.errorTreeView.NewErrorTreeViewPanel;
import consulo.ui.ex.errorTreeView.NewErrorTreeViewPanelFactory;
import consulo.ui.ex.toolWindow.ToolWindow;
import consulo.undoRedo.CommandProcessor;
import consulo.util.collection.ArrayUtil;
import consulo.util.dataholder.Key;
import consulo.util.io.NetUtil;
import consulo.util.lang.EmptyRunnable;
import consulo.util.lang.StringUtil;
import consulo.util.lang.TimeoutUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.apache.xmlrpc.XmlRpcException;
import org.jspecify.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static consulo.execution.ui.console.language.AbstractConsoleRunnerWithHistory.registerActionShortcuts;

/**
 * @author traff
 * @author oleg
 */
public class PydevConsoleRunnerImpl implements PydevConsoleRunner {
    public static final String WORKING_DIR_ENV = "WORKING_DIR_AND_PYTHON_PATHS";
    public static final String CONSOLE_START_COMMAND =
        "import sys; print('Python %s on %s' % (sys.version, sys.platform))\n" + "sys.path.extend([" + WORKING_DIR_ENV + "])\n";
    private static final Logger LOG = Logger.getInstance(PydevConsoleRunnerImpl.class);
    @SuppressWarnings("SpellCheckingInspection")
    public static final String PYDEV_PYDEVCONSOLE_PY = "pydev/pydevconsole.py";
    public static final int PORTS_WAITING_TIMEOUT = 20000;
    private static final String CONSOLE_FEATURE = "python.console";
    private final Project myProject;
    private final String myTitle;
    private final String myWorkingDir;
    private final Consumer<String> myRerunAction;
    private Sdk mySdk;
    private GeneralCommandLine myGeneralCommandLine;
    protected int[] myPorts;
    private PydevConsoleCommunication myPydevConsoleCommunication;
    private ProcessHandler myProcessHandler;
    protected PydevConsoleExecuteActionHandler myConsoleExecuteActionHandler;
    private List<ConsoleListener> myConsoleListeners = consulo.util.collection.Lists.newLockFreeCopyOnWriteList();
    private final PyConsoleType myConsoleType;
    private Map<String, String> myEnvironmentVariables;
    private String myCommandLine;
    private final PyConsoleOptions.PyConsoleSettings myConsoleSettings;
    private String[] myStatementsToExecute = ArrayUtil.EMPTY_STRING_ARRAY;

    private static final long APPROPRIATE_TO_WAIT = 60000;

    private String myConsoleTitle = null;
    private PythonConsoleView myConsoleView;

    public PydevConsoleRunnerImpl(
        Project project,
        Sdk sdk,
        PyConsoleType consoleType,
        @Nullable String workingDir,
        Map<String, String> environmentVariables,
        PyConsoleOptions.PyConsoleSettings settingsProvider,
        Consumer<String> rerunAction,
        String... statementsToExecute
    ) {
        myProject = project;
        mySdk = sdk;
        myTitle = consoleType.getTitle();
        myWorkingDir = workingDir;
        myConsoleType = consoleType;
        myEnvironmentVariables = environmentVariables;
        myConsoleSettings = settingsProvider;
        myStatementsToExecute = statementsToExecute;
        myRerunAction = rerunAction;
    }

    public void setConsoleTitle(String consoleTitle) {
        myConsoleTitle = consoleTitle;
    }

    private List<AnAction> fillToolBarActions(DefaultActionGroup toolbarActions, RunContentDescriptor contentDescriptor) {
        //toolbarActions.add(backspaceHandlingAction);

        toolbarActions.add(createRerunAction());

        List<AnAction> actions = new ArrayList<>();

        //stop
        actions.add(createStopAction());

        //close
        actions.add(createCloseAction(contentDescriptor));

        // run action
        actions.add(new ConsoleExecuteAction(
            myConsoleView,
            myConsoleExecuteActionHandler,
            myConsoleExecuteActionHandler.getEmptyExecuteAction(),
            myConsoleExecuteActionHandler
        ));

        // Help
        actions.add(CommonActionsManager.getInstance().createHelpAction("interactive_console"));

        toolbarActions.addAll(actions);


        actions.add(0, createRerunAction());

        actions.add(createInterruptAction());
        actions.add(createTabCompletionAction());

        actions.add(createSplitLineAction());

        toolbarActions.add(new ShowVarsAction(myConsoleView, myPydevConsoleCommunication));
        toolbarActions.add(ConsoleHistoryController.getController(myConsoleView).getBrowseHistory());

        toolbarActions.add(new ConnectDebuggerAction());

        toolbarActions.add(new NewConsoleAction());

        return actions;
    }

    @Override
    @RequiredUIAccess
    public void open() {
        ToolWindow toolWindow = PythonConsoleToolWindow.getInstance(myProject).getToolWindow();
        if (toolWindow != null) {
            toolWindow.activate(EmptyRunnable.getInstance(), true);
        }
        else {
            runSync();
        }
    }

    @Override
    public void runSync() {
        myPorts = findAvailablePorts(myProject, myConsoleType);

        assert myPorts != null;

        myGeneralCommandLine = createCommandLine(mySdk, myEnvironmentVariables, myWorkingDir, myPorts);
        myCommandLine = myGeneralCommandLine.getCommandLineString();

        try {
            initAndRun();
        }
        catch (ExecutionException e) {
            LOG.warn("Error running console", e);
            ExecutionHelper.showErrors(myProject, Collections.<Exception>singletonList(e), "Python Console", null);
        }

        ProgressManager.getInstance().run(new Task.Backgroundable(myProject, LocalizeValue.localizeTODO("Connecting to Console"), false) {
            @Override
            public void run(ProgressIndicator indicator) {
                indicator.setText(LocalizeValue.localizeTODO("Connecting to console..."));
                connect(myStatementsToExecute);
            }
        });
    }

    @Override
    public void run() {
        FileDocumentManager.getInstance().saveAllDocuments();

        myPorts = findAvailablePorts(myProject, myConsoleType);

        assert myPorts != null;

        myGeneralCommandLine = createCommandLine(mySdk, myEnvironmentVariables, myWorkingDir, myPorts);
        myCommandLine = myGeneralCommandLine.getCommandLineString();

        UIUtil.invokeLaterIfNeeded(() -> ProgressManager.getInstance().run(
            new Task.Backgroundable(myProject, LocalizeValue.localizeTODO("Connecting to Console"), false) {
                @Override
                public void run(ProgressIndicator indicator) {
                    indicator.setText(LocalizeValue.localizeTODO("Connecting to console..."));
                    try {
                        initAndRun();
                        connect(myStatementsToExecute);
                    }
                    catch (Exception e) {
                        LOG.warn("Error running console", e);
                        UIUtil.invokeAndWaitIfNeeded((Runnable) () -> showErrorsInConsole(e));
                    }
                }
            }
        ));
    }

    private void showErrorsInConsole(Exception e) {
        DefaultActionGroup actionGroup = new DefaultActionGroup(createRerunAction());

        ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, actionGroup, false);

        // Runner creating
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(actionToolbar.getComponent(), BorderLayout.WEST);

        NewErrorTreeViewPanelFactory panelFactory = myProject.getApplication().getInstance(NewErrorTreeViewPanelFactory.class);
        NewErrorTreeViewPanel errorViewPanel = panelFactory.createPanel(myProject, null, false, false, null);

        String[] messages = StringUtil.isNotEmpty(e.getMessage()) ? StringUtil.splitByLines(e.getMessage()) : ArrayUtil.EMPTY_STRING_ARRAY;
        if (messages.length == 0) {
            messages = new String[]{"Unknown error"};
        }

        errorViewPanel.addMessage(MessageCategory.ERROR, messages, null, -1, -1, null);
        panel.add(errorViewPanel.getComponent(), BorderLayout.CENTER);


        RunContentDescriptor contentDescriptor = new RunContentDescriptor(null, myProcessHandler, panel, "Error running console");

        actionGroup.add(createCloseAction(contentDescriptor));

        showContentDescriptor(contentDescriptor);
    }


    private void showContentDescriptor(RunContentDescriptor contentDescriptor) {
        ToolWindow toolwindow = PythonConsoleToolWindow.getToolWindow(myProject);
        if (toolwindow != null) {
            PythonConsoleToolWindow.getInstance(myProject).init(toolwindow, contentDescriptor);
        }
        else {
            ExecutionManager.getInstance(myProject).getContentManager().showRunContent(getExecutor(), contentDescriptor);
        }
    }

    private static Executor getExecutor() {
        return DefaultRunExecutor.getRunExecutorInstance();
    }

    private static int[] findAvailablePorts(Project project, PyConsoleType consoleType) {
        int[] ports;
        try {
            // File "pydev/console/pydevconsole.py", line 223, in <module>
            // port, client_port = sys.argv[1:3]
            ports = NetUtil.findAvailableSocketPorts(2);
        }
        catch (IOException e) {
            ExecutionHelper.showErrors(project, Collections.<Exception>singletonList(e), consoleType.getTitle(), null);
            return null;
        }
        return ports;
    }

    protected GeneralCommandLine createCommandLine(
        Sdk sdk,
        Map<String, String> environmentVariables,
        String workingDir,
        int[] ports
    ) {
        return doCreateConsoleCmdLine(sdk, environmentVariables, workingDir, ports, PythonHelper.CONSOLE);
    }

    protected GeneralCommandLine doCreateConsoleCmdLine(
        Sdk sdk,
        Map<String, String> environmentVariables,
        String workingDir,
        int[] ports,
        PythonHelper helper
    ) {
        GeneralCommandLine cmd = PythonCommandLineState.createPythonCommandLine(
            myProject,
            new PythonConsoleRunParams(
                myConsoleSettings,
                workingDir,
                sdk,
                environmentVariables
            ),
            false
        );
        cmd.withWorkDirectory(myWorkingDir);

        ParamsGroup group = cmd.getParametersList().getParamsGroup(PythonCommandLineState.GROUP_SCRIPT);
        helper.addToGroup(group, cmd);

        for (int port : ports) {
            group.addParameter(String.valueOf(port));
        }

        return cmd;
    }

    private PythonConsoleView createConsoleView() {
        PythonConsoleView consoleView = new PythonConsoleView(myProject, myTitle, mySdk);
        myPydevConsoleCommunication.setConsoleFile(consoleView.getVirtualFile());
        consoleView.addMessageFilter(new PythonTracebackFilter(myProject));
        return consoleView;
    }

    private ProcessHandler createProcess() throws ExecutionException {
        myCommandLine = myGeneralCommandLine.getCommandLineString();
        Map<String, String> envs = myGeneralCommandLine.getEnvironment();
        EncodingEnvironmentUtil.setLocaleEnvironmentIfMac(envs, myGeneralCommandLine.getCharset());

        ProcessHandler handler = PythonProcessHandler.createProcessHandler(myGeneralCommandLine);
        try {
            myPydevConsoleCommunication = new PydevConsoleCommunication(myProject, myPorts[0], handler, myPorts[1]);
        }
        catch (Exception e) {
            throw new ExecutionException(e.getMessage());
        }
        return handler;
    }

    private ProcessHandler createProcessHandler(ProcessHandler process) {
        myProcessHandler = new PyConsoleProcessHandler(process, myConsoleView, myPydevConsoleCommunication);
        return myProcessHandler;
    }

    private void initAndRun() throws ExecutionException {
        // Create Server process
        ProcessHandler process = createProcess();
        UIUtil.invokeLaterIfNeeded(() -> {
            // Init console view
            myConsoleView = createConsoleView();
            if (myConsoleView != null) {
                myConsoleView.setBorder(new SideBorder(JBColor.border(), SideBorder.LEFT));
            }
            myProcessHandler = createProcessHandler(process);

            myConsoleExecuteActionHandler = createExecuteActionHandler();

            ProcessTerminatedListener.attach(myProcessHandler);

            PythonConsoleView consoleView = myConsoleView;
            myProcessHandler.addProcessListener(new ProcessListener() {
                @Override
                public void processTerminated(ProcessEvent event) {
                    consoleView.setEditable(false);
                }
            });

            // Attach to process
            myConsoleView.attachToProcess(myProcessHandler);
            createContentDescriptorAndActions();

            // Run
            myProcessHandler.startNotify();
        });
    }

    protected void createContentDescriptorAndActions() {
        // Runner creating
        DefaultActionGroup toolbarActions = new DefaultActionGroup();
        ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarActions, false);

        // Runner creating
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(actionToolbar.getComponent(), BorderLayout.WEST);
        panel.add(myConsoleView.getComponent(), BorderLayout.CENTER);

        actionToolbar.setTargetComponent(panel);

        if (myConsoleTitle == null) {
            myConsoleTitle = new ConsoleTitleGen(myProject, myTitle) {
                @Override
                protected List<String> getActiveConsoles(String consoleTitle) {
                    PythonConsoleToolWindow toolWindow = PythonConsoleToolWindow.getInstance(myProject);
                    if (toolWindow.getToolWindow() != null) {
                        return Lists.newArrayList(toolWindow.getToolWindow().getContentManager().getContents())
                            .stream()
                            .map(Content::getDisplayName)
                            .collect(Collectors.toList());
                    }
                    else {
                        return super.getActiveConsoles(consoleTitle);
                    }
                }
            }.makeTitle();
        }

        RunContentDescriptor contentDescriptor = new RunContentDescriptor(myConsoleView, myProcessHandler, panel, myConsoleTitle, null);

        contentDescriptor.setFocusComputable(() -> myConsoleView.getConsoleEditor().getContentComponent());
        contentDescriptor.setAutoFocusContent(true);

        // tool bar actions
        List<AnAction> actions = fillToolBarActions(toolbarActions, contentDescriptor);
        registerActionShortcuts(actions, myConsoleView.getConsoleEditor().getComponent());
        registerActionShortcuts(actions, panel);
        getConsoleView().addConsoleFolding(false);

        showContentDescriptor(contentDescriptor);
    }

    private void connect(String[] statements2execute) {
        if (handshake()) {
            Application.get().invokeLater(() -> {
                // Propagate console communication to language console
                final PythonConsoleView consoleView = myConsoleView;

                consoleView.setConsoleCommunication(myPydevConsoleCommunication);
                consoleView.setSdk(mySdk);
                consoleView.setExecutionHandler(myConsoleExecuteActionHandler);
                myProcessHandler.addProcessListener(new ProcessAdapter() {
                    @Override
                    public void onTextAvailable(ProcessEvent event, Key outputType) {
                        consoleView.print(event.getText(), outputType);
                    }
                });

                enableConsoleExecuteAction();

                for (String statement : statements2execute) {
                    consoleView.executeStatement(statement + "\n", ProcessOutputTypes.SYSTEM);
                }

                fireConsoleInitializedEvent(consoleView);
                consoleView.initialized();
            });
        }
        else {
            myConsoleView.print("Couldn't connect to console process.", ProcessOutputTypes.STDERR);
            myProcessHandler.destroyProcess();
            myConsoleView.setEditable(false);
        }
    }


    protected AnAction createRerunAction() {
        return new RestartAction(this);
    }

    private AnAction createInterruptAction() {
        AnAction anAction = new LegacyAnAction() {
            @Override
            @RequiredUIAccess
            public void actionPerformed(AnActionEvent e) {
                if (myPydevConsoleCommunication.isExecuting() || myPydevConsoleCommunication.isWaitingForInput()) {
                    myConsoleView.print("^C", ProcessOutputTypes.SYSTEM);
                    myPydevConsoleCommunication.interrupt();
                }
                else {
                    Document document = myConsoleView.getConsoleEditor().getDocument();
                    if (document.getTextLength() != 0) {
                        Application.get().runWriteAction(() -> CommandProcessor.getInstance().runUndoTransparentAction(
                            () -> document.deleteString(0, document.getLineEndOffset(document.getLineCount() - 1))
                        ));
                    }
                }
            }

            @Override
            public void update(AnActionEvent e) {
                EditorEx consoleEditor = myConsoleView.getConsoleEditor();
                boolean enabled =
                    IJSwingUtilities.hasFocus(consoleEditor.getComponent()) && !consoleEditor.getSelectionModel().hasSelection();
                e.getPresentation().setEnabled(enabled);
            }
        };
        anAction.registerCustomShortcutSet(KeyEvent.VK_C, InputEvent.CTRL_MASK, myConsoleView.getConsoleEditor().getComponent());
        anAction.getTemplatePresentation().setVisible(false);
        return anAction;
    }

    private AnAction createTabCompletionAction() {
        AnAction runCompletions = new LegacyAnAction() {
            @RequiredUIAccess
            @Override
            public void actionPerformed(AnActionEvent e) {

                Editor editor = myConsoleView.getConsoleEditor();
                if (LookupManager.getActiveLookup(editor) != null) {
                    AnAction replace = ActionManager.getInstance().getAction("EditorChooseLookupItemReplace");
                    ActionImplUtil.performActionDumbAware(replace, e);
                    return;
                }
                AnAction completionAction = ActionManager.getInstance().getAction("CodeCompletion");
                if (completionAction == null) {
                    return;
                }
                ActionImplUtil.performActionDumbAware(completionAction, e);
            }

            @Override
            public void update(AnActionEvent e) {
                Editor editor = myConsoleView.getConsoleEditor();
                if (LookupManager.getActiveLookup(editor) != null) {
                    e.getPresentation().setEnabled(false);
                }
                int offset = editor.getCaretModel().getOffset();
                Document document = editor.getDocument();
                int lineStart = document.getLineStartOffset(document.getLineNumber(offset));
                String textToCursor = document.getText(new TextRange(lineStart, offset));
                e.getPresentation().setEnabled(!CharMatcher.whitespace().matchesAllOf(textToCursor));
            }
        };

        runCompletions.registerCustomShortcutSet(KeyEvent.VK_TAB, 0, myConsoleView.getConsoleEditor().getComponent());
        runCompletions.getTemplatePresentation().setVisible(false);
        return runCompletions;
    }

    private boolean isIndentSubstring(String text) {
        int indentSize = myConsoleExecuteActionHandler.getPythonIndent();
        return text.length() >= indentSize && CharMatcher.whitespace().matchesAllOf(text.substring(text.length() - indentSize));
    }

    private void enableConsoleExecuteAction() {
        myConsoleExecuteActionHandler.setEnabled(true);
    }

    private boolean handshake() {
        boolean res;
        long started = System.currentTimeMillis();
        do {
            try {
                res = myPydevConsoleCommunication.handshake();
            }
            catch (XmlRpcException ignored) {
                res = false;
            }
            if (res) {
                break;
            }
            else {
                long now = System.currentTimeMillis();
                if (now - started > APPROPRIATE_TO_WAIT) {
                    break;
                }
                else {
                    TimeoutUtil.sleep(100);
                }
            }
        }
        while (true);
        return res;
    }


    private AnAction createStopAction() {
        AnAction generalStopAction = ActionManager.getInstance().getAction(IdeActions.ACTION_STOP_PROGRAM);
        PyDelegateConsoleAction stopAction = new PyDelegateConsoleAction(generalStopAction) {
            @Override
            @RequiredUIAccess
            public void actionPerformed(AnActionEvent e) {
                e = stopConsole(e);

                generalStopAction.actionPerformed(e);
            }
        };
        stopAction.copyFrom(generalStopAction);
        return stopAction;
    }

    private AnAction createCloseAction(final RunContentDescriptor descriptor) {
        final AnAction generalCloseAction = new CloseAction(getExecutor(), descriptor, myProject);

        PyDelegateConsoleAction stopAction = new PyDelegateConsoleAction(generalCloseAction) {
            @Override
            @RequiredUIAccess
            public void actionPerformed(AnActionEvent e) {
                e = stopConsole(e);

                clearContent(descriptor);

                generalCloseAction.actionPerformed(e);
            }
        };
        stopAction.copyFrom(generalCloseAction);
        return stopAction;
    }

    protected void clearContent(RunContentDescriptor descriptor) {
        PythonConsoleToolWindow toolWindow = PythonConsoleToolWindow.getInstance(myProject);
        if (toolWindow.getToolWindow() != null) {
            Content content = toolWindow.getToolWindow().getContentManager().findContent(descriptor.getDisplayName());
            assert content != null;
            toolWindow.getToolWindow().getContentManager().removeContent(content, true);
        }
    }

    private AnActionEvent stopConsole(AnActionEvent e) {
        if (myPydevConsoleCommunication != null) {
            e =
                new AnActionEvent(
                    e.getInputEvent(),
                    e.getDataContext(),
                    e.getPlace(),
                    e.getPresentation(),
                    e.getActionManager(),
                    e.getModifiers()
                );
            try {
                closeCommunication();
                // waiting for REPL communication before destroying process handler
                Thread.sleep(300);
            }
            catch (Exception ignored) {
                // Ignore
            }
        }
        return e;
    }

    protected AnAction createSplitLineAction() {

        class ConsoleSplitLineAction extends EditorAction {

            private static final String CONSOLE_SPLIT_LINE_ACTION_ID = "Console.SplitLine";

            public ConsoleSplitLineAction() {
                super(new EditorWriteActionHandler() {
                    private final EditorAction mySplitLineAction =
                        (EditorAction) ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_SPLIT);

                    @Override
                    public boolean isEnabled(Editor editor, DataContext dataContext) {
                        return mySplitLineAction.getHandler().isEnabled(editor, dataContext);
                    }

                    @Override
                    @RequiredWriteAction
                    public void executeWriteAction(Editor editor, @Nullable Caret caret, DataContext dataContext) {
                        ((EditorWriteActionHandler) mySplitLineAction.getHandler()).executeWriteAction(editor, caret, dataContext);
                        editor.getCaretModel().getCurrentCaret().moveCaretRelatively(0, 1, false, true);
                    }
                });
            }

            public void setup() {
                EmptyAction.setupAction(this, CONSOLE_SPLIT_LINE_ACTION_ID, null);
            }
        }

        ConsoleSplitLineAction action = new ConsoleSplitLineAction();
        action.setup();
        return action;
    }

    private void closeCommunication() {
        if (!myProcessHandler.isProcessTerminated()) {
            myPydevConsoleCommunication.close();
        }
    }

    @RequiredUIAccess
    protected PydevConsoleExecuteActionHandler createExecuteActionHandler() {
        myConsoleExecuteActionHandler = new PydevConsoleExecuteActionHandler(myConsoleView, myProcessHandler, myPydevConsoleCommunication);
        myConsoleExecuteActionHandler.setEnabled(false);
        new ConsoleHistoryControllerImpl(myConsoleType.getTypeId(), "", myConsoleView).install();
        return myConsoleExecuteActionHandler;
    }

    @Override
    public PydevConsoleCommunication getPydevConsoleCommunication() {
        return myPydevConsoleCommunication;
    }

    static VirtualFile getConsoleFile(PsiFile psiFile) {
        VirtualFile file = psiFile.getViewProvider().getVirtualFile();
        if (file instanceof LightVirtualFile lightVirtualFile) {
            file = lightVirtualFile.getOriginalFile();
        }
        return file;
    }

    @Override
    public void addConsoleListener(ConsoleListener consoleListener) {
        myConsoleListeners.add(consoleListener);
    }

    private void fireConsoleInitializedEvent(LanguageConsoleView consoleView) {
        for (ConsoleListener listener : myConsoleListeners) {
            listener.handleConsoleInitialized(consoleView);
        }
        myConsoleListeners.clear();
    }

    @Override
    public PydevConsoleExecuteActionHandler getConsoleExecuteActionHandler() {
        return myConsoleExecuteActionHandler;
    }

    private static class RestartAction extends AnAction {
        private PydevConsoleRunnerImpl myConsoleRunner;

        private RestartAction(PydevConsoleRunnerImpl runner) {
            copyFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_RERUN));
            getTemplatePresentation().setIcon(PlatformIconGroup.actionsRestart());
            myConsoleRunner = runner;
        }

        @Override
        @RequiredUIAccess
        public void actionPerformed(AnActionEvent e) {
            myConsoleRunner.rerun();
        }
    }

    private void rerun() {
        Application application = myProject.getApplication();
        new Task.Backgroundable(myProject, LocalizeValue.localizeTODO("Restarting Console"), true) {
            @Override
            public void run(ProgressIndicator indicator) {
                if (myProcessHandler instanceof KillableProcessHandler killableProcessHandler) {
                    UIUtil.invokeAndWaitIfNeeded((Runnable) () -> closeCommunication());

                    boolean processStopped = myProcessHandler.waitFor(5000L);
                    if (!processStopped && killableProcessHandler.canKillProcess()) {
                        killableProcessHandler.killProcess();
                    }
                    myProcessHandler.waitFor();
                }

                application.invokeLater(() -> myRerunAction.accept(myConsoleTitle), application.getDefaultModalityState());
            }
        }.queue();
    }

    private class ConnectDebuggerAction extends ToggleAction implements DumbAware {
        private boolean mySelected = false;
        private XDebugSession mySession = null;

        public ConnectDebuggerAction() {
            super(
                LocalizeValue.localizeTODO("Attach Debugger"),
                LocalizeValue.localizeTODO("Enables tracing of code executed in console"),
                ExecutionDebugIconGroup.actionStartdebugger()
            );
        }

        @Override
        public boolean isSelected(AnActionEvent e) {
            return mySelected;
        }

        @Override
        public void update(AnActionEvent e) {
            if (mySession != null) {
                e.getPresentation().setEnabled(false);
            }
            else {
                e.getPresentation().setEnabled(true);
            }
        }

        @Override
        @RequiredUIAccess
        public void setSelected(AnActionEvent e, boolean state) {
            mySelected = state;

            if (mySelected) {
                try {
                    mySession = connectToDebugger();
                }
                catch (Exception e1) {
                    LOG.error(e1);
                    Messages.showErrorDialog("Can't connect to debugger", "Error Connecting Debugger");
                }
            }
            else {
                //TODO: disable debugging
            }
        }
    }

    private static class NewConsoleAction extends AnAction implements DumbAware {
        public NewConsoleAction() {
            super(
                LocalizeValue.localizeTODO("New Console"),
                LocalizeValue.localizeTODO("Creates new python console"),
                PlatformIconGroup.generalAdd()
            );
        }

        @Override
        @RequiredUIAccess
        public void actionPerformed(AnActionEvent e) {
            PydevConsoleRunner runner =
                PythonConsoleRunnerFactory.getInstance().createConsoleRunner(e.getRequiredData(Project.KEY), e.getData(Module.KEY));
            runner.run();
        }
    }

    private XDebugSession connectToDebugger() throws ExecutionException {
        ServerSocket serverSocket = PythonCommandLineState.createServerSocket();

        XDebugSession session = XDebuggerManager.getInstance(myProject).startSessionAndShowTab(
            "Python Console Debugger",
            PythonPsiIconGroup.python(),
            null,
            true,
            xDebugSession -> {
                PythonDebugLanguageConsoleView debugConsoleView = new PythonDebugLanguageConsoleView(myProject, mySdk);

                PyConsoleDebugProcessHandler consoleDebugProcessHandler = new PyConsoleDebugProcessHandler(myProcessHandler);

                PyConsoleDebugProcess consoleDebugProcess = new PyConsoleDebugProcess(
                    xDebugSession,
                    serverSocket,
                    debugConsoleView,
                    consoleDebugProcessHandler
                );

                PythonDebugConsoleCommunication communication = PyDebugRunner.initDebugConsoleView(
                    myProject,
                    consoleDebugProcess,
                    debugConsoleView,
                    consoleDebugProcessHandler,
                    xDebugSession
                );

                communication.addCommunicationListener(new ConsoleCommunicationListener() {
                    @Override
                    public void commandExecuted(boolean more) {
                        xDebugSession.rebuildViews();
                    }

                    @Override
                    public void inputRequested() {
                    }
                });

                myPydevConsoleCommunication.setDebugCommunication(communication);
                debugConsoleView.attachToProcess(consoleDebugProcessHandler);

                consoleDebugProcess.waitForNextConnection();

                try {
                    consoleDebugProcess.connect(myPydevConsoleCommunication);
                }
                catch (Exception e) {
                    LOG.error(e); //TODO
                }

                myProcessHandler.notifyTextAvailable(
                    "\nDebugger connected.\n",
                    ProcessOutputTypes.STDERR
                );

                return consoleDebugProcess;
            }
        );

        return session;
    }

    @Override
    public ProcessHandler getProcessHandler() {
        return myProcessHandler;
    }

    @Override
    public PythonConsoleView getConsoleView() {
        return myConsoleView;
    }

    public static PythonConsoleRunnerFactory factory() {
        return new PydevConsoleRunnerFactory();
    }

    private static class PythonConsoleRunParams implements PythonRunParams {
        private final PyConsoleOptions.PyConsoleSettings myConsoleSettings;
        private String myWorkingDir;
        private Sdk mySdk;
        private Map<String, String> myEnvironmentVariables;

        public PythonConsoleRunParams(
            PyConsoleOptions.PyConsoleSettings consoleSettings,
            String workingDir,
            Sdk sdk,
            Map<String, String> envs
        ) {
            myConsoleSettings = consoleSettings;
            myWorkingDir = workingDir;
            mySdk = sdk;
            myEnvironmentVariables = envs;
            myEnvironmentVariables.putAll(consoleSettings.getEnvs());
        }

        @Override
        public String getInterpreterOptions() {
            return myConsoleSettings.getInterpreterOptions();
        }

        @Override
        public void setInterpreterOptions(String interpreterOptions) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getWorkingDirectory() {
            return myWorkingDir;
        }

        @Override
        public void setWorkingDirectory(String workingDirectory) {
            throw new UnsupportedOperationException();
        }

        @Nullable
        @Override
        public String getSdkHome() {
            return mySdk.getHomePath();
        }

        @Override
        public void setSdkHome(String sdkHome) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setModule(Module module) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getModuleName() {
            return myConsoleSettings.getModuleName();
        }

        @Override
        public boolean isUseModuleSdk() {
            return myConsoleSettings.isUseModuleSdk();
        }

        @Override
        public void setUseModuleSdk(boolean useModuleSdk) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isPassParentEnvs() {
            return myConsoleSettings.isPassParentEnvs();
        }

        @Override
        public void setPassParentEnvs(boolean passParentEnvs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, String> getEnvs() {
            return myEnvironmentVariables;
        }

        @Override
        public void setEnvs(Map<String, String> envs) {
            throw new UnsupportedOperationException();
        }

        @Nullable
        @Override
        public PathMappingSettings getMappingSettings() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setMappingSettings(@Nullable PathMappingSettings mappingSettings) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean shouldAddContentRoots() {
            return myConsoleSettings.shouldAddContentRoots();
        }

        @Override
        public boolean shouldAddSourceRoots() {
            return myConsoleSettings.shouldAddSourceRoots();
        }

        @Override
        public void setAddContentRoots(boolean flag) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setAddSourceRoots(boolean flag) {
            throw new UnsupportedOperationException();
        }
    }
}
