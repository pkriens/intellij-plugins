/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.coldFusion;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

public class CfmlBundle extends DynamicBundle {
  @NonNls private static final String BUNDLE = "messages.CfmlBundle";
  private static final CfmlBundle INSTANCE = new CfmlBundle();

  private CfmlBundle() { super(BUNDLE); }

  @NotNull
  public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }

  @NotNull
  public static Supplier<String> lazyMessage(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getLazyMessage(key, params);
  }

  // TextAttributeKey names must be globally unique.
  // However those in CfmlBundle are not. So mangle them.
  public static String cfmlizeMessage(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key) {
    return "Cfml" + message(key);
  }
}
