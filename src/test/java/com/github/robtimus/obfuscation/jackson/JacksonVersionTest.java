/*
 * JacksonVersionTest.java
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.json.JsonFactory;

@SuppressWarnings("nls")
class JacksonVersionTest {

    @Nested
    @DisplayName("classExists(String)")
    class ClassExists {

        @ParameterizedTest(name = "class: {0}")
        @ValueSource(classes = { String.class, JsonFactory.class })
        @DisplayName("class exists")
        void testClassExists(Class<?> c) {
            assertTrue(JacksonVersion.classExists(c.getName()));
        }

        @Test
        @DisplayName("class does not exist")
        void testClassDoesNotExist() {
            assertFalse(JacksonVersion.classExists("java.lang.ClassThatDoesNotExist"));
        }
    }
}
