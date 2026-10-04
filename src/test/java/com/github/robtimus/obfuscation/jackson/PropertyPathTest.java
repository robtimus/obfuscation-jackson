/*
 * PropertyPathTest.java
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.github.robtimus.junit.support.test.collections.IteratorTests;
import com.github.robtimus.junit.support.test.collections.UnmodifiableIteratorTests;
import com.github.robtimus.obfuscation.jackson.JSONObfuscator.PropertyPath;

@SuppressWarnings("nls")
class PropertyPathTest {

    @Test
    @DisplayName("length()")
    void testLength() {
        PropertyPath path = newPath("a", "b", "c");

        assertEquals(3, path.length());
    }

    @Test
    @DisplayName("propertyAt(int)")
    void testPropertyAt() {
        PropertyPath path = newPath("a", "b", "c");

        assertThrows(IndexOutOfBoundsException.class, () -> path.propertyAt(-1));
        assertEquals("a", path.propertyAt(0));
        assertEquals("b", path.propertyAt(1));
        assertEquals("c", path.propertyAt(2));
        assertThrows(IndexOutOfBoundsException.class, () -> path.propertyAt(3));
    }

    @Test
    @DisplayName("lastProperty()")
    void testLastProperty() {
        PropertyPath path = newPath("a", "b", "c");

        assertEquals("c", path.lastProperty());
    }

    @Nested
    @DisplayName("iterator()")
    class IteratorTest implements IteratorTests.IterationTests<String>, UnmodifiableIteratorTests.RemoveTests<String> {

        @Override
        public Iterable<String> iterable() {
            return newPath("a", "b", "c");
        }

        @Override
        public Collection<String> expectedElements() {
            return List.of("a", "b", "c");
        }

        @Override
        public boolean fixedOrder() {
            return true;
        }
    }

    @Test
    void testToString() {
        PropertyPath path = newPath("a", "b", "c.d");

        assertEquals("a.b.\"c.d\"", path.toString());
    }

    private static PropertyPath newPath(String... properties) {
        PropertyPath path = new PropertyPath();
        for (String property : properties) {
            path.push(property);
        }
        return path;
    }
}
