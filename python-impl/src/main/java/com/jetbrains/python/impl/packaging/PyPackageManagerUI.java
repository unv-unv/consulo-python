/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.impl.packaging;

import com.jetbrains.python.impl.packaging.ui.PyPackageManagementService;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.packaging.PyPackageManagers;
import com.jetbrains.python.packaging.PyRequirement;
import consulo.application.AllIcons;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.content.bundle.Sdk;
import consulo.execution.RunCanceledByUserException;
import consulo.logging.Logger;
import consulo.process.ExecutionException;
import consulo.project.Project;
import consulo.project.ui.notification.Notification;
import consulo.project.ui.notification.NotificationGroup;
import consulo.project.ui.notification.NotificationType;
import consulo.project.ui.notification.NotificationsManager;
import consulo.project.ui.notification.event.NotificationListener;
import consulo.repository.ui.PackageManagementService;
import consulo.ui.ex.awt.Messages;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.Ref;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.swing.event.HyperlinkEvent;
import java.util.*;

/**
 * @author vlan
 */
public class PyPackageManagerUI {
  @Nonnull
  private static final Logger LOG = Logger.getInstance(PyPackageManagerUI.class);

  @Nullable
  private Listener myListener;
  @Nonnull
  private Project myProject;
  @Nonnull
  private Sdk mySdk;

  public interface Listener {
    void started();

    void finished(List<ExecutionException> exceptions);
  }

  public PyPackageManagerUI(@Nonnull Project project, @Nonnull Sdk sdk, @Nullable Listener listener) {
    myProject = project;
    mySdk = sdk;
    myListener = listener;
  }

  public void installManagement() {
    ProgressManager.getInstance().run(new InstallManagementTask(myProject, mySdk, myListener));
  }

  public void install(@Nonnull final List<PyRequirement> requirements, @Nonnull final List<String> extraArgs) {
    ProgressManager.getInstance().run(new InstallTask(myProject, mySdk, requirements, extraArgs, myListener));
  }

  public void uninstall(@Nonnull final List<PyPackage> packages) {
    if (checkDependents(packages)) {
      return;
    }
    ProgressManager.getInstance().run(new UninstallTask(myProject, mySdk, myListener, packages));
  }

  private boolean checkDependents(@Nonnull final List<PyPackage> packages) {
    try {
      final Map<String, Set<PyPackage>> dependentPackages = collectDependents(packages, mySdk);
      final int[] warning = {0};
      if (!dependentPackages.isEmpty()) {
        Application application = ApplicationManager.getApplication();
        application.invokeAndWait(() -> {
          if (dependentPackages.size() == 1) {
            String message = "You are attempting to uninstall ";
            List<String> dep = new ArrayList<>();
            int size = 1;
            for (Map.Entry<String, Set<PyPackage>> entry : dependentPackages.entrySet()) {
              final Set<PyPackage> value = entry.getValue();
              size = value.size();
              dep.add(entry.getKey() + " package which is required for " + StringUtil.join(value, ", "));
            }
            message += StringUtil.join(dep, "\n");
            message += size == 1 ? " package" : " packages";
            message += "\n\nDo you want to proceed?";
            warning[0] = Messages.showYesNoDialog(message, "Warning", AllIcons.General.BalloonWarning);
          }
          else {
            String message = "You are attempting to uninstall packages which are required for another packages.\n\n";
            List<String> dep = new ArrayList<>();
            for (Map.Entry<String, Set<PyPackage>> entry : dependentPackages.entrySet()) {
              dep.add(entry.getKey() + " -> " + StringUtil.join(entry.getValue(), ", "));
            }
            message += StringUtil.join(dep, "\n");
            message += "\n\nDo you want to proceed?";
            warning[0] = Messages.showYesNoDialog(message, "Warning", AllIcons.General.BalloonWarning);
          }
        }, application.getCurrentModalityState());
      }
      if (warning[0] != Messages.YES) {
        return true;
      }
    }
    catch (ExecutionException e) {
      LOG.info("Error loading packages dependents: " + e.getMessage(), e);
    }
    return false;
  }

  private static Map<String, Set<PyPackage>> collectDependents(@Nonnull final List<PyPackage> packages, Sdk sdk) throws ExecutionException {
    Map<String, Set<PyPackage>> dependentPackages = new HashMap<>();
    for (PyPackage pkg : packages) {
      final Set<PyPackage> dependents = PyPackageManager.getInstance(sdk).getDependents(pkg);
      if (dependents != null && !dependents.isEmpty()) {
        for (PyPackage dependent : dependents) {
          if (!packages.contains(dependent)) {
            dependentPackages.put(pkg.getName(), dependents);
          }
        }
      }
    }
    return dependentPackages;
  }

  private abstract static class PackagingTask extends Task.Backgroundable {
    private static final NotificationGroup PACKAGING_GROUP_ID = NotificationGroup.balloonGroup("Python Packaging");

    @Nonnull
    protected final Sdk mySdk;
    @Nullable
    protected final Listener myListener;

    public PackagingTask(@Nullable Project project, @Nonnull Sdk sdk, @Nonnull String title, @Nullable Listener listener) {
      super(project, title);
      mySdk = sdk;
      myListener = listener;
    }

    @Override
    public void run(@Nonnull ProgressIndicator indicator) {
      taskStarted(indicator);
      taskFinished(runTask(indicator));
    }

    @Nonnull
    protected abstract List<ExecutionException> runTask(@Nonnull ProgressIndicator indicator);

    @Nonnull
    protected abstract String getSuccessTitle();

    @Nonnull
    protected abstract String getSuccessDescription();

    @Nonnull
    protected abstract String getFailureTitle();

    protected void taskStarted(@Nonnull ProgressIndicator indicator) {
      final PackagingNotification[] notifications =
        NotificationsManager.getNotificationsManager().getNotificationsOfType(PackagingNotification.class, (Project)getProject());
      for (PackagingNotification notification : notifications) {
        notification.expire();
      }
      indicator.setText(getTitle() + "...");
      if (myListener != null) {
        ApplicationManager.getApplication().invokeLater(() -> myListener.started());
      }
    }

    protected void taskFinished(@Nonnull final List<ExecutionException> exceptions) {
      final Ref<Notification> notificationRef = new Ref<>(null);
      if (exceptions.isEmpty()) {
        notificationRef.set(new PackagingNotification(PACKAGING_GROUP_ID,
                                                      getSuccessTitle(),
                                                      getSuccessDescription(),
                                                      NotificationType.INFORMATION,
                                                      null));
      }
      else {
        final PackageManagementService.ErrorDescription description = PyPackageManagementService.toErrorDescription(exceptions, mySdk);
        if (description != null) {
          final String firstLine = getTitle() + ": error occurred.";
          final NotificationListener listener = new NotificationListener() {
            @Override
            public void hyperlinkUpdate(@Nonnull Notification notification, @Nonnull HyperlinkEvent event) {
              assert myProject != null;
              final String title = StringUtil.capitalizeWords(getFailureTitle(), true);
              consulo.ide.impl.idea.webcore.packaging.PackagesNotificationPanel.showError(title, description);
            }
          };
          notificationRef.set(new PackagingNotification(PACKAGING_GROUP_ID,
                                                        getFailureTitle(),
                                                        firstLine + " <a href=\"xxx\">Details...</a>",
                                                        consulo.project.ui.notification.NotificationType.ERROR,
                                                        listener));
        }
      }
      ApplicationManager.getApplication().invokeLater(() -> {
        if (myListener != null) {
          myListener.finished(exceptions);
        }
        final Notification notification = notificationRef.get();
        if (notification != null) {
          notification.notify((Project)myProject);
        }
      });
    }

    private static class PackagingNotification extends Notification {

      public PackagingNotification(@Nonnull NotificationGroup groupDisplayId,
                                   @Nonnull String title,
                                   @Nonnull String content,
                                   @Nonnull consulo.project.ui.notification.NotificationType type,
                                   @Nullable NotificationListener listener) {
        super(groupDisplayId, title, content, type, listener);
      }
    }
  }

  private static class InstallTask extends PackagingTask {
    @Nonnull
    private final List<PyRequirement> myRequirements;
    @Nonnull
    private final List<String> myExtraArgs;

    public InstallTask(@Nullable Project project,
                       @Nonnull Sdk sdk,
                       @Nonnull List<PyRequirement> requirements,
                       @Nonnull List<String> extraArgs,
                       @Nullable Listener listener) {
      super(project, sdk, "Installing packages", listener);
      myRequirements = requirements;
      myExtraArgs = extraArgs;
    }

    @Nonnull
    @Override
    protected List<ExecutionException> runTask(@Nonnull ProgressIndicator indicator) {
      final List<ExecutionException> exceptions = new ArrayList<>();
      final int size = myRequirements.size();
      final PyPackageManager manager = PyPackageManagers.getInstance().forSdk(mySdk);
      for (int i = 0; i < size; i++) {
        final PyRequirement requirement = myRequirements.get(i);
        indicator.setText(String.format("Installing package '%s'...", requirement));
        if (i == 0) {
          indicator.setIndeterminate(true);
        }
        else {
          indicator.setIndeterminate(false);
          indicator.setFraction((double)i / size);
        }
        try {
          manager.install(Collections.singletonList(requirement), myExtraArgs);
        }
        catch (RunCanceledByUserException e) {
          exceptions.add(e);
          break;
        }
        catch (ExecutionException e) {
          exceptions.add(e);
        }
      }
      manager.refresh();
      return exceptions;
    }

    @Nonnull
    @Override
    protected String getSuccessTitle() {
      return "Packages installed successfully";
    }

    @Nonnull
    @Override
    protected String getSuccessDescription() {
      return "Installed packages: " + PyPackageUtil.requirementsToString(myRequirements);
    }

    @Nonnull
    @Override
    protected String getFailureTitle() {
      return "Install packages failed";
    }
  }

  private static class InstallManagementTask extends InstallTask {

    public InstallManagementTask(@Nullable Project project, @Nonnull Sdk sdk, @Nullable Listener listener) {
      super(project, sdk, Collections.<PyRequirement>emptyList(), Collections.<String>emptyList(), listener);
    }

    @Nonnull
    @Override
    protected List<ExecutionException> runTask(@Nonnull ProgressIndicator indicator) {
      final List<ExecutionException> exceptions = new ArrayList<>();
      final PyPackageManager manager = PyPackageManagers.getInstance().forSdk(mySdk);
      indicator.setText("Installing packaging tools...");
      indicator.setIndeterminate(true);
      try {
        manager.installManagement();
      }
      catch (ExecutionException e) {
        exceptions.add(e);
      }
      manager.refresh();
      return exceptions;
    }

    @Nonnull
    @Override
    protected String getSuccessDescription() {
      return "Installed Python packaging tools";
    }
  }

  private static class UninstallTask extends PackagingTask {
    @Nonnull
    private final List<PyPackage> myPackages;

    public UninstallTask(@Nullable Project project, @Nonnull Sdk sdk, @Nullable Listener listener, @Nonnull List<PyPackage> packages) {
      super(project, sdk, "Uninstalling packages", listener);
      myPackages = packages;
    }

    @Nonnull
    @Override
    protected List<ExecutionException> runTask(@Nonnull ProgressIndicator indicator) {
      final PyPackageManager manager = PyPackageManagers.getInstance().forSdk(mySdk);
      indicator.setIndeterminate(true);
      try {
        manager.uninstall(myPackages);
        return Collections.emptyList();
      }
      catch (ExecutionException e) {
        return Collections.singletonList(e);
      }
      finally {
        manager.refresh();
      }
    }

    @Nonnull
    @Override
    protected String getSuccessTitle() {
      return "Packages uninstalled successfully";
    }

    @Nonnull
    @Override
    protected String getSuccessDescription() {
      final String packagesString = StringUtil.join(myPackages, pkg -> "'" + pkg.getName() + "'", ", ");
      return "Uninstalled packages: " + packagesString;
    }

    @Nonnull
    @Override
    protected String getFailureTitle() {
      return "Uninstall packages failed";
    }
  }
}
