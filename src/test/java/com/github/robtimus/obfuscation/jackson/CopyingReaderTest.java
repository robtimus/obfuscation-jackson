/*
 * CopyingReaderTest.java
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
import java.io.StringReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@SuppressWarnings({ "javadoc", "nls" })
public class CopyingReaderTest {

    @Test
    @DisplayName("read()")
    public void testReadSingle() throws IOException {
        final String expected = "Lorem ipsum dolor sit amet, consectetuer adipiscing elit.";

        try (CopyingReader in = new CopyingReader(new StringReader(expected))) {
            while (in.read() != -1) {
                // ignore
            }
            assertEquals(expected, in.contents.toString());
        }
    }

    @Test
    @DisplayName("read(char[], int, int)")
    public void testReadBulk() throws IOException {
        final String expected = "Lorem ipsum dolor sit amet, consectetuer adipiscing elit.";

        try (CopyingReader in = new CopyingReader(new StringReader(expected))) {
            char[] buffer = new char[10];
            final int offset = 3;
            while (in.read(buffer, offset, buffer.length - offset) != -1) {
                // ignore
            }
            assertEquals(expected, in.contents.toString());
        }
    }

    @Test
    @DisplayName("skip(long)")
    public void testSkip() throws IOException {
        final String expected = "Lorem ipsum dolor sit amet, consectetuer adipiscing elit.";

        try (CopyingReader in = new CopyingReader(new StringReader(expected))) {
            in.skip(1000);
            assertEquals(-1, in.read());
            assertEquals(expected, in.contents.toString());
        }
    }

    @Test
    @DisplayName("ready()")
    public void testReady() throws IOException {
        final String expected = "Lorem ipsum dolor sit amet, consectetuer adipiscing elit. Aenean commodo ligula eget dolor. Aenean massa.";

        try (CopyingReader reader = new CopyingReader(new CharSequenceReader(expected, 0, expected.length()))) {
            for (int i = 0; i < expected.length(); i++) {
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
        final String expected = "Lorem ipsum dolor sit amet, consectetuer adipiscing elit.";
        try (CopyingReader in = new CopyingReader(new StringReader(expected))) {
            assertTrue(in.markSupported());
            in.read();
            in.mark(5);
            assertEquals(expected.length() - 1, readAll(in));
            in.reset();
            assertEquals(expected.length() - 1, readAll(in));

            assertEquals(expected, in.contents.toString());
        }
        try (CopyingReader in = new CopyingReader(new Reader() {

            @Override
            public int read(char[] cbuf, int off, int len) throws IOException {
                return -1;
            }

            @Override
            public void close() throws IOException {
                // does nothing
            }
        })) {
            assertFalse(in.markSupported());
            assertThrows(IOException.class, () -> in.mark(0));
            assertThrows(IOException.class, () -> in.reset());
        }
    }

    private int readAll(Reader in) throws IOException {
        char[] buffer = new char[4096];
        int len;
        int total = 0;
        while ((len = in.read(buffer)) != -1) {
            total += len;
        }
        return total;
    }
}
