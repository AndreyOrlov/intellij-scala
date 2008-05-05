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

package org.jetbrains.plugins.scala.config;

import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.plugins.scala.icons.Icons;
import org.jetbrains.plugins.scala.config.util.AbstractSDK;

import javax.swing.*;

/**
 * @author ilyas
 */
public class ScalaSDK implements AbstractSDK {

  protected Library myLibrary;
  protected String mySdkVersion;

  public ScalaSDK(Library library) {
    myLibrary = library;
    mySdkVersion = ScalaConfigUtils.getScalaLibVersion(library);
  }

  public ScalaSDK() {
  }

  public Library getLibrary() {
    return myLibrary;
  }

  public String getSdkVersion() {
    return mySdkVersion;
  }

  public String getLibraryName(){
    return myLibrary.getName();
  }

  public Icon getIcon(){
    return Icons.SCALA_SDK;
  }
}
