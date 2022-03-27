/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.batch.scan.filesystem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.CoreProperties;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.DeprecatedDefaultInputFile;
import org.sonar.api.config.Settings;
import org.sonar.api.scan.filesystem.PathResolver;
import org.sonar.batch.bootstrap.DefaultAnalysisMode;

import javax.annotation.CheckForNull;

import java.io.File;

class InputFileBuilder {

  private static final Logger LOG = LoggerFactory.getLogger(InputFileBuilder.class);

  private final String moduleKey;
  private final PathResolver pathResolver;
  private final LanguageDetection langDetection;
  private final StatusDetection statusDetection;
  private final DefaultModuleFileSystem fs;
  private final DefaultAnalysisMode analysisMode;
  private final Settings settings;
  private final FileMetadata fileMetadata;

  InputFileBuilder(String moduleKey, PathResolver pathResolver, LanguageDetection langDetection,
    StatusDetection statusDetection, DefaultModuleFileSystem fs, DefaultAnalysisMode analysisMode, Settings settings, FileMetadata fileMetadata) {
    this.moduleKey = moduleKey;
    this.pathResolver = pathResolver;
    this.langDetection = langDetection;
    this.statusDetection = statusDetection;
    this.fs = fs;
    this.analysisMode = analysisMode;
    this.settings = settings;
    this.fileMetadata = fileMetadata;
  }

  String moduleKey() {
    return moduleKey;
  }

  PathResolver pathResolver() {
    return pathResolver;
  }

  LanguageDetection langDetection() {
    return langDetection;
  }

  StatusDetection statusDetection() {
    return statusDetection;
  }

  FileSystem fs() {
    return fs;
  }

  @CheckForNull
  DeprecatedDefaultInputFile create(File file) {
    String relativePath = pathResolver.relativePath(fs.baseDir(), file);
    if (relativePath == null) {
      LOG.warn("File '{}' is ignored. It is not located in module basedir '{}'.", file.getAbsolutePath(), fs.baseDir());
      return null;
    }
    return new DeprecatedDefaultInputFile(moduleKey, relativePath);
  }

  /**
   * Optimization to not compute InputFile metadata if the file is excluded from analysis.
   */
  @CheckForNull
  InputFileMetadata completeAndComputeMetadata(DeprecatedDefaultInputFile inputFile, InputFile.Type type) {
    inputFile.setType(type);
    inputFile.setModuleBaseDir(fs.baseDir().toPath());
    inputFile.setCharset(fs.encoding());

    String lang = langDetection.language(inputFile);
    if (lang == null && !settings.getBoolean(CoreProperties.IMPORT_UNKNOWN_FILES_KEY)) {
      return null;
    }
    inputFile.setLanguage(lang);

    InputFileMetadata result = new InputFileMetadata();

    FileMetadata.Metadata metadata = fileMetadata.read(inputFile.file(), fs.encoding());
    inputFile.setLines(metadata.lines);

    result.setNonBlankLines(metadata.nonBlankLines);
    result.setHash(metadata.hash);
    result.setOriginalLineOffsets(metadata.originalLineOffsets);
    result.setEmpty(metadata.empty);

    inputFile.setStatus(statusDetection.status(inputFile.moduleKey(), inputFile.relativePath(), metadata.hash));
    if (analysisMode.isIncremental() && inputFile.status() == InputFile.Status.SAME) {
      return null;
    }
    return result;
  }

}
