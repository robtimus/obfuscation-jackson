/*
 * JacksonVersion.java
 * Copyright 2026 Rob Spoor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.robtimus.obfuscation.jackson;

/**
 * The supported Jackson versions.
 *
 * @author Rob Spoor
 * @since 2.0
 */
public enum JacksonVersion {

    /** Jackson 2. */
    @SuppressWarnings("nls")
    JACKSON2("com.fasterxml.jackson.core.JsonFactory"),

    /** Jackson 3. */
    @SuppressWarnings("nls")
    JACKSON3("tools.jackson.core.JsonParser"),
    ;

    private final boolean isAvailable;

    JacksonVersion(String jsonFactoryClassName) {
        isAvailable = classExists(jsonFactoryClassName);
    }

    /**
     * Returns whether this Jackson version is available as a runtime dependency.
     *
     * @return {@code true} if this Jackson version is available as a runtime dependency, or {@code false} otherwise.
     */
    public boolean isAvailable() {
        return isAvailable;
    }

    static boolean classExists(String className) {
        ClassLoader classLoader = JacksonVersion.class.getClassLoader();
        try {
            Class.forName(className, false, classLoader);
            return true;
        } catch (@SuppressWarnings("unused") ClassNotFoundException e) {
            return false;
        }
    }
}
