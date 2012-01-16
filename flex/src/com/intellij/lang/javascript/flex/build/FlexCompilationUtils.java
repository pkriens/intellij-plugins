package com.intellij.lang.javascript.flex.build;

import com.intellij.lang.javascript.flex.FlexBundle;
import com.intellij.lang.javascript.flex.FlexFacet;
import com.intellij.lang.javascript.flex.FlexUtils;
import com.intellij.lang.javascript.flex.actions.airdescriptor.AirDescriptorParameters;
import com.intellij.lang.javascript.flex.actions.airdescriptor.CreateAirDescriptorAction;
import com.intellij.lang.javascript.flex.actions.htmlwrapper.CreateHtmlWrapperAction;
import com.intellij.lang.javascript.flex.projectStructure.FlexProjectLevelCompilerOptionsHolder;
import com.intellij.lang.javascript.flex.projectStructure.model.*;
import com.intellij.lang.javascript.flex.projectStructure.options.BCUtils;
import com.intellij.lang.javascript.flex.sdk.AirMobileSdkType;
import com.intellij.lang.javascript.flex.sdk.AirSdkType;
import com.intellij.lang.javascript.flex.sdk.FlexSdkUtils;
import com.intellij.lang.javascript.flex.sdk.FlexmojosSdkType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.util.SystemProperties;
import com.intellij.util.text.StringTokenizer;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;

import static com.intellij.lang.javascript.flex.projectStructure.ui.CreateHtmlWrapperTemplateDialog.*;

public class FlexCompilationUtils {

  public static final String SWF_MACRO = "${swf}";

  private static final String[] MACROS_TO_REPLACE =
    {SWF_MACRO, "${title}", "${application}", "${bgcolor}", "${width}", "${height}", VERSION_MAJOR_MACRO, VERSION_MINOR_MACRO,
      VERSION_REVISION_MACRO};

  private FlexCompilationUtils() {
  }

  static void deleteCacheForFile(final String filePath) throws IOException {
    final VirtualFile cacheFile = LocalFileSystem.getInstance().findFileByPath(filePath + ".cache");
    if (cacheFile != null) {
      final Ref<IOException> exceptionRef = new Ref<IOException>();

      ApplicationManager.getApplication().invokeAndWait(new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              if (cacheFile.exists()) {
                try {
                  cacheFile.delete(this);
                }
                catch (IOException e) {
                  exceptionRef.set(e);
                }
              }
            }
          });
        }
      }, ProgressManager.getInstance().getProgressIndicator().getModalityState());

      if (!exceptionRef.isNull()) {
        throw exceptionRef.get();
      }
    }
  }

  static List<VirtualFile> getConfigFiles(final FlexBuildConfiguration config,
                                          final @NotNull Module module,
                                          final @Nullable FlexFacet flexFacet,
                                          final @Nullable String cssFilePath) throws IOException {

    final List<VirtualFile> result = new ArrayList<VirtualFile>();

    if (config.USE_CUSTOM_CONFIG_FILE && !FlexCompilerHandler.needToMergeAutogeneratedAndCustomConfigFile(config, cssFilePath != null)) {
      final String customConfigFilePath =
        config.getType() == FlexBuildConfiguration.Type.FlexUnit && config.USE_CUSTOM_CONFIG_FILE_FOR_TESTS
        ? config.CUSTOM_CONFIG_FILE_FOR_TESTS
        : config.CUSTOM_CONFIG_FILE;
      final VirtualFile customConfigFile =
        VfsUtil.findRelativeFile(customConfigFilePath, FlexUtils.getFlexCompilerWorkDir(module.getProject(), null));
      if (customConfigFile != null) {
        result.add(customConfigFile);
      }
    }

    if (!config.USE_CUSTOM_CONFIG_FILE ||
        config.getType() == FlexBuildConfiguration.Type.FlexUnit ||
        config.getType() == FlexBuildConfiguration.Type.OverriddenMainClass ||
        cssFilePath != null) {
      final String cssFileName = cssFilePath == null ? null : cssFilePath.substring(cssFilePath.lastIndexOf('/') + 1);
      final String postfix = cssFileName == null ? null : FileUtil.getNameWithoutExtension(cssFileName);

      final String facetName = flexFacet == null ? null : flexFacet.getName();
      final String name = FlexCompilerHandler.generateConfigFileName(module, facetName, config.getType().getConfigFilePrefix(), postfix);
      final String configText = FlexCompilerHandler.generateConfigFileText(module, config, cssFilePath);
      result.add(getOrCreateConfigFile(module.getProject(), name, configText));
    }
    return result;
  }

  static VirtualFile getOrCreateConfigFile(final Project project, final String name, final String text) throws IOException {
    final VirtualFile existingConfigFile = VfsUtil.findRelativeFile(name, FlexUtils.getFlexCompilerWorkDir(project, null));

    if (existingConfigFile != null && Arrays.equals(text.getBytes(), existingConfigFile.contentsToByteArray())) {
      return existingConfigFile;
    }

    final Ref<VirtualFile> fileRef = new Ref<VirtualFile>();
    final Ref<IOException> error = new Ref<IOException>();
    final Runnable runnable = new Runnable() {
      public void run() {
        fileRef.set(ApplicationManager.getApplication().runWriteAction(new NullableComputable<VirtualFile>() {
          public VirtualFile compute() {
            try {
              final String baseDirPath = FlexUtils.getTempFlexConfigsDirPath();
              final VirtualFile baseDir = VfsUtil.createDirectories(baseDirPath);

              VirtualFile configFile = baseDir.findChild(name);
              if (configFile == null) {
                configFile = baseDir.createChildData(this, name);
              }
              VfsUtil.saveText(configFile, text);
              return configFile;
            }
            catch (IOException ex) {
              error.set(ex);
            }
            return null;
          }
        }));
      }
    };

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      runnable.run();
    }
    else {
      ApplicationManager.getApplication()
        .invokeAndWait(runnable, ProgressManager.getInstance().getProgressIndicator().getModalityState());
    }

    if (!error.isNull()) {
      throw error.get();
    }
    return fileRef.get();
  }

  static List<String> buildCommand(final List<String> compilerCommand,
                                   final List<VirtualFile> configFiles,
                                   final Module module,
                                   final FlexBuildConfiguration config) {
    final Sdk flexSdk = FlexUtils.getFlexSdkForFlexModuleOrItsFlexFacets(module);
    assert flexSdk != null;

    final List<String> command = new ArrayList<String>(compilerCommand);

    if (flexSdk.getSdkType() instanceof AirSdkType) {
      command.add("+configname=air");
    }
    else if (flexSdk.getSdkType() instanceof AirMobileSdkType) {
      command.add("+configname=airmobile");
    }

    final boolean useSdkConfig = config.USE_DEFAULT_SDK_CONFIG_FILE && !(flexSdk.getSdkType() instanceof FlexmojosSdkType);

    for (VirtualFile configFile : configFiles) {
      command.add("-load-config" + (useSdkConfig ? "+=" : "=") + configFile.getPath());
    }

    if (!StringUtil.isEmpty(config.ADDITIONAL_COMPILER_OPTIONS)) {
      // TODO handle -option="path with spaces"
      for (final String s : StringUtil.split(config.ADDITIONAL_COMPILER_OPTIONS, " ")) {
        command.add(FlexUtils.replacePathMacros(s, module, flexSdk.getHomePath()));
      }
    }

    return command;
  }

  static List<String> buildCommand(final List<String> compilerCommand,
                                   final List<VirtualFile> configFiles,
                                   final Module module,
                                   final FlexIdeBuildConfiguration bc) {
    final List<String> command = new ArrayList<String>(compilerCommand);
    for (VirtualFile configFile : configFiles) {
      command.add("-load-config=" + configFile.getPath());
    }

    final SdkEntry sdkEntry = bc.getDependencies().getSdkEntry();
    assert sdkEntry != null;

    final Sdk sdk = sdkEntry.findSdk();
    assert sdk != null;
    addAdditionalOptions(command, module, sdk.getHomePath(),
                         FlexProjectLevelCompilerOptionsHolder.getInstance(module.getProject()).getProjectLevelCompilerOptions()
                           .getAdditionalOptions());
    addAdditionalOptions(command, module, sdk.getHomePath(),
                         FlexBuildConfigurationManager.getInstance(module).getModuleLevelCompilerOptions().getAdditionalOptions());
    addAdditionalOptions(command, module, sdk.getHomePath(), bc.getCompilerOptions().getAdditionalOptions());

    return command;
  }

  private static void addAdditionalOptions(final List<String> command,
                                           final Module module,
                                           final String sdkHome,
                                           final String additionalOptions) {
    if (!StringUtil.isEmpty(additionalOptions)) {
      // TODO handle -option="path with spaces"
      for (final String s : StringUtil.split(additionalOptions, " ")) {
        command.add(FlexUtils.replacePathMacros(s, module, sdkHome));
      }
    }
  }

  static List<String> getMxmlcCompcCommand(final Project project, final Sdk flexSdk, final boolean isApp) {
    final List<String> command = new ArrayList<String>();

    final String className =
      isApp ? (FlexSdkUtils.isFlex4Sdk(flexSdk) ? "flex2.tools.Mxmlc" : "flex2.tools.Compiler") : "flex2.tools.Compc";

    String additionalClasspath = FileUtil.toSystemDependentName(FlexUtils.getPathToBundledJar("idea-flex-compiler-fix.jar"));
    if (!(flexSdk.getSdkType() instanceof FlexmojosSdkType)) {
      additionalClasspath += File.pathSeparator + FileUtil.toSystemDependentName(flexSdk.getHomePath() + "/lib/compc.jar");
    }

    command.addAll(FlexSdkUtils.getCommandLineForSdkTool(project, flexSdk, additionalClasspath, className, null));

    return command;
  }

  /**
   * returns <code>false</code> if compilation error found in output
   */
  static boolean handleCompilerOutput(final FlexCompilationManager compilationManager,
                                      final FlexCompilationTask task,
                                      final String output) {
    boolean failureDetected = false;
    final StringTokenizer tokenizer = new StringTokenizer(output, "\r\n");

    while (tokenizer.hasMoreElements()) {
      final String text = tokenizer.nextElement();
      if (!StringUtil.isEmptyOrSpaces(text)) {

        final Matcher matcher = FlexCompilerHandler.errorPattern.matcher(text);

        if (matcher.matches()) {
          final String file = matcher.group(1);
          final String additionalInfo = matcher.group(2);
          final String line = matcher.group(3);
          final String column = matcher.group(4);
          final String type = matcher.group(5);
          final String message = matcher.group(6);

          final CompilerMessageCategory messageCategory =
            "Warning".equals(type) ? CompilerMessageCategory.WARNING : CompilerMessageCategory.ERROR;
          final VirtualFile relativeFile = VfsUtil.findRelativeFile(file, null);

          final StringBuilder fullMessage = new StringBuilder();
          if (relativeFile == null) fullMessage.append(file).append(": ");
          if (additionalInfo != null) fullMessage.append(additionalInfo).append(' ');
          fullMessage.append(message);

          compilationManager.addMessage(task, messageCategory, fullMessage.toString(), relativeFile != null ? relativeFile.getUrl() : null,
                                        line != null ? Integer.parseInt(line) : 0, column != null ? Integer.parseInt(column) : 0);
          failureDetected |= messageCategory == CompilerMessageCategory.ERROR;
        }
        else if (text.startsWith("Error: ") || text.startsWith("Exception in thread \"main\" ")) {
          final String updatedText = text.startsWith("Error: ") ? text.substring("Error: ".length()) : text;
          compilationManager.addMessage(task, CompilerMessageCategory.ERROR, updatedText, null, -1, -1);
          failureDetected = true;
        }
        else {
          compilationManager.addMessage(task, CompilerMessageCategory.INFORMATION, text, null, -1, -1);
        }
      }
    }

    return !failureDetected;
  }

  private static String getOutputSwfFileNameForCssFile(final Project project, final String cssFilePath) {
    final VirtualFile cssFile = LocalFileSystem.getInstance().findFileByPath(cssFilePath);
    final VirtualFile sourceRoot = cssFile == null
                                   ? null
                                   : ProjectRootManager.getInstance(project).getFileIndex().getSourceRootForFile(cssFile);
    final String relativePath = sourceRoot == null ? null : VfsUtilCore.getRelativePath(cssFile, sourceRoot, '/');
    final String cssFileName = cssFilePath.substring(FileUtil.toSystemIndependentName(cssFilePath).lastIndexOf("/") + 1);
    final String relativeFolder = relativePath == null ? "" : relativePath.substring(0, relativePath.lastIndexOf('/') + 1);
    return relativeFolder + FileUtil.getNameWithoutExtension(cssFileName) + ".swf";
  }

  static FlexBuildConfiguration createCssConfig(final FlexBuildConfiguration config, final String cssFilePath) {
    final FlexBuildConfiguration cssConfig = config.clone();
    cssConfig.setType(FlexBuildConfiguration.Type.Default);
    cssConfig.OUTPUT_FILE_NAME = getOutputSwfFileNameForCssFile(config.getModule().getProject(), cssFilePath);
    cssConfig.OUTPUT_TYPE = FlexBuildConfiguration.APPLICATION;
    cssConfig.CSS_FILES_LIST.clear();
    cssConfig.PATH_TO_SERVICES_CONFIG_XML = "";
    cssConfig.CONTEXT_ROOT = "";
    return cssConfig;
  }

  public static void ensureOutputFileWritable(final Project project, final String filePath) {
    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(filePath);
    if (file != null && !file.isWritable()) {
      ApplicationManager.getApplication().invokeAndWait(new Runnable() {
        public void run() {
          ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(file);
        }
      }, ModalityState.defaultModalityState());
    }
  }

  public static void performPostCompileActions(final @NotNull FlexIdeBuildConfiguration bc) throws FlexCompilerException {
    switch (bc.getTargetPlatform()) {
      case Web:
        if (bc.isUseHtmlWrapper()) {
          handleHtmlWrapper(bc);
        }
        break;
      case Desktop:
        handleAirDescriptor(bc, bc.getAirDesktopPackagingOptions());
        break;
      case Mobile:
        if (bc.getAndroidPackagingOptions().isEnabled()) {
          handleAirDescriptor(bc, bc.getAndroidPackagingOptions());
        }
        if (bc.getIosPackagingOptions().isEnabled()) {
          handleAirDescriptor(bc, bc.getIosPackagingOptions());
        }
        break;
    }
  }

  private static void handleHtmlWrapper(final FlexIdeBuildConfiguration bc) throws FlexCompilerException {
    final VirtualFile templateDir = LocalFileSystem.getInstance().findFileByPath(bc.getWrapperTemplatePath());
    if (templateDir == null || !templateDir.isDirectory()) {
      throw new FlexCompilerException(FlexBundle.message("html.wrapper.dir.not.found", bc.getWrapperTemplatePath()));
    }
    final VirtualFile templateFile = templateDir.findChild(CreateHtmlWrapperAction.HTML_WRAPPER_TEMPLATE_FILE_NAME);
    if (templateFile == null) {
      throw new FlexCompilerException(FlexBundle.message("no.index.template.html.file", bc.getWrapperTemplatePath()));
    }

    final VirtualFile outputDir = LocalFileSystem.getInstance().findFileByPath(bc.getOutputFolder());
    if (outputDir == null || !outputDir.isDirectory()) {
      throw new FlexCompilerException(FlexBundle.message("output.folder.does.not.exist", bc.getOutputFolder()));
    }

    final Ref<FlexCompilerException> exceptionRef = new Ref<FlexCompilerException>();
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      public void run() {
        exceptionRef.set(ApplicationManager.getApplication().runWriteAction(new NullableComputable<FlexCompilerException>() {
          public FlexCompilerException compute() {
            for (VirtualFile file : templateDir.getChildren()) {
              if (file == templateFile) {
                final String wrapperText;
                try {
                  wrapperText = VfsUtil.loadText(file);
                }
                catch (IOException e) {
                  return new FlexCompilerException(FlexBundle.message("failed.to.load.file", file.getPath(), e.getMessage()));
                }

                if (!wrapperText.contains(SWF_MACRO)) {
                  return new FlexCompilerException(FlexBundle.message("no.swf.macro", FileUtil.toSystemDependentName(file.getPath())));
                }

                final String fixedText = replaceMacros(wrapperText, FileUtil.getNameWithoutExtension(bc.getOutputFileName()),
                                                       bc.getDependencies().getTargetPlayer());
                final String wrapperFileName = getWrapperFileName(bc);
                try {
                  FlexUtils.addFileWithContent(wrapperFileName, fixedText, outputDir);
                }
                catch (IOException e) {
                  return new FlexCompilerException(
                    FlexBundle.message("failed.to.create.file", wrapperFileName, outputDir.getPath(), e.getMessage()));
                }
              }
              else {
                try {
                  file.copy(this, outputDir, file.getName());
                }
                catch (IOException e) {
                  return new FlexCompilerException(FlexBundle.message("failed.to.copy.file", file.getName(), templateDir.getPath(),
                                                                      outputDir.getPath(), e.getMessage()));
                }
              }
            }
            return null;
          }
        }));
      }
    }, ModalityState.any());

    if (!exceptionRef.isNull()) {
      throw exceptionRef.get();
    }
  }

  private static String replaceMacros(final String wrapperText, final String outputFileName, final String targetPlayer) {
    final String[] versionParts = targetPlayer.split("[.]");
    final String major = versionParts.length >= 1 ? versionParts[0] : "0";
    final String minor = versionParts.length >= 2 ? versionParts[1] : "0";
    final String revision = versionParts.length >= 3 ? versionParts[2] : "0";
    final String[] replacement = {outputFileName, outputFileName, outputFileName, "#ffffff", "100%", "100%", major, minor, revision};
    return StringUtil.replace(wrapperText, MACROS_TO_REPLACE, replacement);
  }

  public static String getWrapperFileName(final FlexIdeBuildConfiguration bc) {
    return FileUtil.getNameWithoutExtension(bc.getOutputFileName()) + ".html";
  }

  private static void handleAirDescriptor(final FlexIdeBuildConfiguration config,
                                          final AirPackagingOptions packagingOptions) throws FlexCompilerException {
    if (packagingOptions.isUseGeneratedDescriptor()) {
      final boolean android = packagingOptions instanceof AndroidPackagingOptions;
      generateAirDescriptor(config, BCUtils.getGeneratedAirDescriptorName(config, packagingOptions), android);
    }
    else {
      copyAndFixCustomAirDescriptor(config, packagingOptions.getCustomDescriptorPath());
    }
  }

  private static void generateAirDescriptor(final FlexIdeBuildConfiguration config,
                                            final String descriptorFileName,
                                            final boolean addAndroidPermissions) throws FlexCompilerException {
    final Ref<FlexCompilerException> exceptionRef = new Ref<FlexCompilerException>();

    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      public void run() {
        try {
          final SdkEntry sdkEntry = config.getDependencies().getSdkEntry();
          assert sdkEntry != null;
          final Sdk sdk = sdkEntry.findSdk();
          assert sdk != null;
          final String airVersion = FlexSdkUtils.getAirVersion(sdk.getVersionString());
          final String fileName = FileUtil.getNameWithoutExtension(config.getOutputFileName());
          final String appId = fixApplicationId(config.getMainClass());

          CreateAirDescriptorAction.createAirDescriptor(
            new AirDescriptorParameters(descriptorFileName, config.getOutputFolder(), airVersion, appId, fileName, fileName,
                                        "1.0", config.getOutputFileName(), fileName, 400, 300, addAndroidPermissions));
        }
        catch (IOException e) {
          exceptionRef.set(new FlexCompilerException("Failed to generate AIR descriptor: " + e));
        }
      }
    }, ModalityState.any());

    if (!exceptionRef.isNull()) {
      throw exceptionRef.get();
    }
  }

  public static String fixApplicationId(final String appId) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < appId.length(); i++) {
      final char ch = appId.charAt(i);
      if (ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z' || ch >= '0' && ch <= '9' || ch == '-' || ch == '.') {
        builder.append(ch);
      }
    }
    return builder.toString();
  }

  private static void copyAndFixCustomAirDescriptor(final FlexIdeBuildConfiguration config, final String customDescriptorPath)
    throws FlexCompilerException {
    final VirtualFile descriptorTemplateFile = LocalFileSystem.getInstance().findFileByPath(customDescriptorPath);
    if (descriptorTemplateFile == null) {
      throw new FlexCompilerException("Custom AIR descriptor file not found: " + customDescriptorPath);
    }

    final VirtualFile outputFolder = LocalFileSystem.getInstance().findFileByPath(config.getOutputFolder());
    assert outputFolder != null;

    final Ref<FlexCompilerException> exceptionRef = new Ref<FlexCompilerException>();

    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            try {
              final String content = fixInitialContent(descriptorTemplateFile, config.getOutputFileName());
              FlexUtils.addFileWithContent(descriptorTemplateFile.getName(), content, outputFolder);
            }
            catch (FlexCompilerException e) {
              exceptionRef.set(e);
            }
            catch (IOException e) {
              exceptionRef.set(new FlexCompilerException("Failed to copy AIR descriptor to output folder", null, -1, -1));
            }
          }
        });
      }
    }, ModalityState.any());

    if (!exceptionRef.isNull()) {
      throw exceptionRef.get();
    }
  }

  private static String fixInitialContent(final VirtualFile descriptorFile, final String swfName) throws FlexCompilerException {
    try {
      final Document document;
      try {
        document = JDOMUtil.loadDocument(descriptorFile.getInputStream());
      }
      catch (IOException e) {
        throw new FlexCompilerException("Failed to read AIR descriptor content: " + e.getMessage(), descriptorFile.getUrl(), -1, -1);
      }

      final Element rootElement = document.getRootElement();
      if (rootElement == null || !"application".equals(rootElement.getName())) {
        throw new FlexCompilerException("AIR descriptor file has incorrect root tag", descriptorFile.getUrl(), -1, -1);
      }

      Element initialWindowElement = rootElement.getChild("initialWindow", rootElement.getNamespace());
      if (initialWindowElement == null) {
        initialWindowElement = new Element("initialWindow", rootElement.getNamespace());
        rootElement.addContent(initialWindowElement);
      }

      Element contentElement = initialWindowElement.getChild("content", rootElement.getNamespace());
      if (contentElement == null) {
        contentElement = new Element("content", rootElement.getNamespace());
        initialWindowElement.addContent(contentElement);
      }

      contentElement.setText(swfName);

      return JDOMUtil.writeDocument(document, SystemProperties.getLineSeparator());
    }
    catch (JDOMException e) {
      throw new FlexCompilerException("AIR descriptor file has incorrect format: " + e.getMessage(), descriptorFile.getUrl(), -1, -1);
    }
  }
}
