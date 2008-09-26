/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

package org.jetbrains.plugins.scala.config.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.icons.Icons;

import javax.swing.*;

/**
 * Pointer to added but not registered Scala SDK
 *
 * @author ilyas
 */
public class ScalaSDKPointer implements AbstractSDK {

  private final String myScalaLibraryName;
  private final String myPathToLibrary;
  private final String myVersion;
  private final boolean myProjectLib;

  public ScalaSDKPointer(@NotNull String name, @NotNull String path, String version, final boolean isProjectLib) {
    myScalaLibraryName = name;
    myPathToLibrary = path;
    myVersion = version;
    myProjectLib = isProjectLib;
  }

  public String getLibraryName() {
    return myScalaLibraryName;
  }

  public boolean isProjectLib() {
    return myProjectLib;
  }

  public String getPresentation() {
    return " (Scala version \"" + getVersion() + "\")";
  }

  public String getPath() {
    return myPathToLibrary;
  }

  public Icon getIcon() {
    return Icons.SCALA_SDK;
  }

  public String getVersion() {
    return myVersion;
  }
}