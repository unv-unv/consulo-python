package com.jetbrains.python.testing;

import com.intellij.execution.configurations.*;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.testing.attest.PythonAtTestRunConfiguration;
import com.jetbrains.python.testing.doctest.PythonDocTestRunConfiguration;
import com.jetbrains.python.testing.nosetest.PythonNoseTestRunConfiguration;
import com.jetbrains.python.testing.pytest.PyTestRunConfiguration;
import com.jetbrains.python.testing.unittest.PythonUnitTestRunConfiguration;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * User : catherine
 */
public class PythonTestConfigurationType implements ConfigurationType {
  public static final String ID = "tests";

  public final PythonDocTestConfigurationFactory PY_DOCTEST_FACTORY = new PythonDocTestConfigurationFactory(this);
  public final PythonUnitTestConfigurationFactory PY_UNITTEST_FACTORY = new PythonUnitTestConfigurationFactory(this);
  public final PythonNoseTestConfigurationFactory PY_NOSETEST_FACTORY = new PythonNoseTestConfigurationFactory(this);
  public final PythonPyTestConfigurationFactory PY_PYTEST_FACTORY = new PythonPyTestConfigurationFactory(this);
  public final PythonAtTestConfigurationFactory PY_ATTEST_FACTORY = new PythonAtTestConfigurationFactory(this);

  public static PythonTestConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(PythonTestConfigurationType.class);
  }

  private static class PythonUnitTestConfigurationFactory extends ConfigurationFactory {
    protected PythonUnitTestConfigurationFactory(ConfigurationType configurationType) {
      super(configurationType);
    }

    @Override
    public RunConfiguration createTemplateConfiguration(Project project) {
      return new PythonUnitTestRunConfiguration(project, this);
    }

    @Override
    public String getName() {
      return PyBundle.message("runcfg.unittest.display_name");
    }
  }

  private static class PythonDocTestConfigurationFactory extends ConfigurationFactory {
    protected PythonDocTestConfigurationFactory(ConfigurationType configurationType) {
      super(configurationType);
    }

    @Override
    public RunConfiguration createTemplateConfiguration(Project project) {
      return new PythonDocTestRunConfiguration(project, this);
    }

    @Override
    public String getName() {
      return PyBundle.message("runcfg.doctest.display_name");
    }
  }

  private static class PythonPyTestConfigurationFactory extends ConfigurationFactory {
    protected PythonPyTestConfigurationFactory(ConfigurationType configurationType) {
      super(configurationType);
    }

    @Override
    public RunConfiguration createTemplateConfiguration(Project project) {
      return new PyTestRunConfiguration(project, this);
    }

    @Override
    public String getName() {
      return PyBundle.message("runcfg.pytest.display_name");
    }
  }

  private static class PythonNoseTestConfigurationFactory extends ConfigurationFactory {
    protected PythonNoseTestConfigurationFactory(ConfigurationType configurationType) {
      super(configurationType);
    }

    @Override
    public RunConfiguration createTemplateConfiguration(Project project) {
      return new PythonNoseTestRunConfiguration(project, this);
    }

    @Override
    public String getName() {
      return PyBundle.message("runcfg.nosetests.display_name");
    }
  }

  private static class PythonAtTestConfigurationFactory extends ConfigurationFactory {
    protected PythonAtTestConfigurationFactory(ConfigurationType configurationType) {
      super(configurationType);
    }

    @Override
    public RunConfiguration createTemplateConfiguration(Project project) {
      return new PythonAtTestRunConfiguration(project, this);
    }

    @Override
    public String getName() {
      return PyBundle.message("runcfg.attest.display_name");
    }
  }

  @Override
  public String getDisplayName() {
    return PyBundle.message("runcfg.test.display_name");
  }

  @Override
  public String getConfigurationTypeDescription() {
    return PyBundle.message("runcfg.test.description");
  }

  @Override
  public Icon getIcon() {
    return PythonIcons.Python.PythonTests;
  }

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  @Override
  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[] {PY_UNITTEST_FACTORY, PY_DOCTEST_FACTORY, PY_NOSETEST_FACTORY,
        PY_PYTEST_FACTORY, PY_ATTEST_FACTORY};
  }
}