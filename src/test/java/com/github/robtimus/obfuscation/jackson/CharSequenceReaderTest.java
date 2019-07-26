/*
 * CharSequenceReaderTest.java
 * Copyright 2019 Rob Spoor
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@SuppressWarnings({ "javadoc", "nls" })
public class CharSequenceReaderTest {

    @Test
    @DisplayName("read()")
    public void testRead() throws IOException {
        final String source = "Lorem ipsum dolor sit amet, consectetuer adipiscing elit. Aenean commodo ligula eget dolor. Aenean massa.";
        Writer writer = new StringWriter();
        try (Reader reader = new CharSequenceReader(source, 1, source.length() - 1)) {
            int c;
            while ((c = reader.read()) != -1) {
                writer.write(c);
            }
        }
        assertEquals(source.substring(1, source.length() - 1), writer.toString());
    }

    @Test
    @DisplayName("read(char[], int, int)")
    public void testReadBulk() throws IOException {
        final String source = "Lorem ipsum dolor sit amet, consectetuer adipiscing elit. Aenean commodo ligula eget dolor. Aenean massa.";
        Writer writer = new StringWriter();
        try (Reader reader = new CharSequenceReader(source, 1, source.length() - 1)) {
            assertEquals(0, reader.read(new char[5], 0, 0));
            assertEquals("", writer.toString());
            copy(reader, writer, 5);
        }
        assertEquals(source.substring(1, source.length() - 1), writer.toString());
    }

    @Test
    @DisplayName("skip(long)")
    public void testSkip() throws IOException {
        final String source = "Lorem ipsum dolor sit amet, consectetuer adipiscing elit. Aenean commodo ligula eget dolor. Aenean massa.";
        Writer writer = new StringWriter();
        try (Reader reader = new CharSequenceReader(source, 1, source.length() - 1)) {
            char[] data = new char[10];
            int len = reader.read(data);
            assertEquals(data.length, len);
            writer.write(data);
            assertEquals(0, reader.skip(0));
            assertThrows(IllegalArgumentException.class, () -> reader.skip(-1));
            assertEquals(10, reader.skip(10));
            copy(reader, writer);
        }
        assertEquals(source.substring(1, 11) + source.substring(21, source.length() - 1), writer.toString());
    }

    @Test
    @DisplayName("ready()")
    public void testReady() throws IOException {
        final String source = "Lorem ipsum dolor sit amet, consectetuer adipiscing elit. Aenean commodo ligula eget dolor. Aenean massa.";
        try (Reader reader = new CharSequenceReader(source, 1, source.length() - 1)) {
            for (int i = 1; i < source.length() - 1; i++) {
                assertTrue(reader.ready());
                assertNotEquals(-1, reader.read());
            }
            assertFalse(reader.ready());
            assertEquals(-1, reader.read());
        }
    }

    @Test
    @DisplayName("mark(int) and reset()")
    public void testMarkReset() throws IOException {
        final String source = "Lorem ipsum dolor sit amet, consectetuer adipiscing elit. Aenean commodo ligula eget dolor. Aenean massa.";
        Writer writer = new StringWriter();
        try (Reader reader = new CharSequenceReader(source, 1, source.length() - 1)) {
            assertTrue(reader.markSupported());
            reader.mark(5);
            copy(reader, writer);
            reader.reset();
            copy(reader, writer);
        }
        String singleExpected = source.substring(1, source.length() - 1);
        assertEquals(singleExpected + singleExpected, writer.toString());
    }

    private void copy(Reader reader, Writer writer) throws IOException {
        copy(reader, writer, 4096);
    }

    private void copy(Reader reader, Writer writer, int bufferSize) throws IOException {
        char[] buffer = new char[bufferSize];
        int len;
        while ((len = reader.read(buffer)) != -1) {
            writer.write(buffer, 0, len);
        }
    }
}
