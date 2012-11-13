package org.jetbrains.jps.incremental.scala;

import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.incremental.scala.model.FacetSettings;
import org.jetbrains.jps.incremental.scala.model.LibraryLevel;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryCollection;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.util.Collection;

import static com.intellij.openapi.util.text.StringUtil.join;

/**
 * @author Pavel Fatin
 */
public class CompilationSettings {
  private Collection<File> myScalaCompilerClasspath;
  private File myOutputDirectory;
  private Collection<File> myCompilationClasspath;

  public CompilationSettings(Collection<File> scalaCompilerClasspath,
                             File outputDirectory,
                             Collection<File> compilationClasspath) {
    myScalaCompilerClasspath = scalaCompilerClasspath;
    myOutputDirectory = outputDirectory;
    myCompilationClasspath = compilationClasspath;
  }

  public Collection<File> getScalaCompilerClasspath() {
    return myScalaCompilerClasspath;
  }

  public File getOutputDirectory() {
    return myOutputDirectory;
  }

  public Collection<File> getCompilationClasspath() {
    return myCompilationClasspath;
  }

  public static CompilationSettings create(CompileContext context, ModuleChunk chunk) {
    JpsModule module = chunk.representativeTarget().getModule();
    JpsModel model = context.getProjectDescriptor().getModel();

    // Find a Scala compiler library that is configured in a Scala facet
    JpsLibrary compilerLibrary = getCompilerLibraryIn(module, model);

    // Collect all files in the compiler library
    Collection<File> compilerClasspath = compilerLibrary.getFiles(JpsOrderRootType.COMPILED);
    if (compilerClasspath.isEmpty()) throw new ConfigurationException(
        "Scala compiler library is empty: " + compilerLibrary.getName());

    ModuleBuildTarget target = chunk.representativeTarget();

    // Get an output directory
    File outputDirectory = target.getOutputDir();
    if (outputDirectory == null) throw new ConfigurationException(
        "Output directory not specified for module " + target.getModuleName());

    // Get compilation classpath files
    Collection<File> chunkClasspath = context.getProjectPaths()
        .getCompilationClasspathFiles(chunk, chunk.containsTests(), false, false);

    return new CompilationSettings(compilerClasspath, outputDirectory, chunkClasspath);
  }

  private static JpsLibrary getCompilerLibraryIn(JpsModule module, JpsModel model) {
    FacetSettings settings = SettingsManager.getFacetSettings(module);

    if (settings == null) throw new ConfigurationException(
        "No Scala facet in module: " + module.getName());

    LibraryLevel compilerLibraryLevel = settings.getCompilerLibraryLevel();

    if (compilerLibraryLevel == null) throw new ConfigurationException(
        "No compiler library level set in module: " + module.getName());

    JpsLibraryCollection libraryCollection = getLibraryCollection(compilerLibraryLevel, model, module);

    String compilerLibraryName = settings.getCompilerLibraryName();

    if (compilerLibraryName == null) throw new ConfigurationException(
        "No compiler library name set in module: " + module.getName());

    JpsLibrary library = libraryCollection.findLibrary(compilerLibraryName);

    if (library == null) throw new ConfigurationException(
        String.format("Сompiler library for module %s not found: %s / %s ",
            module.getName(), compilerLibraryLevel, compilerLibraryName));

    return library;
  }

  private static JpsLibraryCollection getLibraryCollection(LibraryLevel level, JpsModel model, JpsModule module) {
    switch (level) {
      case Global:
        return model.getGlobal().getLibraryCollection();
      case Project:
        return model.getProject().getLibraryCollection();
      case Module:
        return module.getLibraryCollection();
      default:
        throw new ConfigurationException("Unknown library level: " + level);
    }
  }
}
