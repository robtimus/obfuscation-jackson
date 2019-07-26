/*
 * JSONObfuscatorTest.java
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

import static com.github.robtimus.obfuscation.Obfuscator.fixedLength;
import static com.github.robtimus.obfuscation.PropertyObfuscator.ofType;
import static com.github.robtimus.obfuscation.jackson.JSONObfuscator.builder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import com.github.robtimus.obfuscation.Obfuscator;
import com.github.robtimus.obfuscation.PropertyObfuscator.Builder;
import com.github.robtimus.obfuscation.jackson.JSONObfuscator.JSONBuilder;
import com.github.robtimus.obfuscation.jackson.JSONObfuscator.MalformedJSONStrategy;

@SuppressWarnings({ "javadoc", "nls" })
@TestInstance(Lifecycle.PER_CLASS)
public class JSONObfuscatorTest {

    @Nested
    @DisplayName("valid JSON")
    @TestInstance(Lifecycle.PER_CLASS)
    public class ValidJSON extends ObfuscatorTest {

        public ValidJSON() {
            super("JSONObfuscator.input.valid.json", "JSONObfuscator.expected.valid", () -> createObfuscator());
        }
    }

    @Nested
    @DisplayName("invalid JSON")
    @TestInstance(Lifecycle.PER_CLASS)
    public class InvalidJSON extends ObfuscatorTest {

        public InvalidJSON() {
            super("JSONObfuscator.input.invalid", "JSONObfuscator.expected.invalid", () -> createObfuscator());
        }
    }

    @Nested
    @DisplayName("truncated JSON")
    @TestInstance(Lifecycle.PER_CLASS)
    public class TruncatedJSON {

        @Nested
        @DisplayName("discard remainder")
        @TestInstance(Lifecycle.PER_CLASS)
        public class DiscardRemainder {

            @Nested
            @DisplayName("with warning")
            public class WithWarning extends TruncatedJSONTest {

                public WithWarning() {
                    super("JSONObfuscator.expected.truncated.discard-remainder", true, MalformedJSONStrategy.DISCARD_REMAINDER);
                }
            }

            @Nested
            @DisplayName("without warning")
            public class WithoutWarning extends TruncatedJSONTest {

                public WithoutWarning() {
                    super("JSONObfuscator.expected.truncated.discard-remainder.no-warning", false, MalformedJSONStrategy.DISCARD_REMAINDER);
                }
            }
        }

        @Nested
        @DisplayName("include remainder")
        @TestInstance(Lifecycle.PER_CLASS)
        public class IncludeRemainder {

            @Nested
            @DisplayName("with warning")
            public class WithWarning extends TruncatedJSONTest {

                public WithWarning() {
                    super("JSONObfuscator.expected.truncated.include-remainder", true, MalformedJSONStrategy.INCLUDE_REMAINDER);
                }
            }

            @Nested
            @DisplayName("without warning")
            public class WithoutWarning extends TruncatedJSONTest {

                public WithoutWarning() {
                    super("JSONObfuscator.expected.truncated.include-remainder.no-warning", false, MalformedJSONStrategy.INCLUDE_REMAINDER);
                }
            }
        }

        private class TruncatedJSONTest extends ObfuscatorTest {

            private TruncatedJSONTest(String expectedResource, boolean includeWarning, MalformedJSONStrategy strategy) {
                super("JSONObfuscator.input.truncated", expectedResource, () -> createObfuscator(includeWarning, strategy));
            }
        }
    }

    private static class ObfuscatorTest {

        private final String input;
        private final String expected;
        private final Supplier<Obfuscator> obfuscatorSupplier;

        private ObfuscatorTest(String inputResource, String expectedResource, Supplier<Obfuscator> obfuscatorSupplier) {
            this.input = readResource(inputResource);
            this.expected = readResource(expectedResource);
            this.obfuscatorSupplier = obfuscatorSupplier;
        }

        @Test
        @DisplayName("obfuscateText(CharSequence, int, int)")
        public void testObfuscateTextCharSequence() {
            Obfuscator obfuscator = obfuscatorSupplier.get();

            assertEquals(expected, obfuscator.obfuscateText("x" + input + "x", 1, 1 + input.length()).toString());
        }

        @Test
        @DisplayName("obfuscateText(CharSequence, int, int, Appendable)")
        public void testObfuscateTextCharSequenceToAppendable() throws IOException {
            Obfuscator obfuscator = obfuscatorSupplier.get();

            StringBuilder destination = new StringBuilder();
            obfuscator.obfuscateText("x" + input + "x", 1, 1 + input.length(), destination);
            assertEquals(expected, destination.toString());
        }

        @Test
        @DisplayName("obfuscateText(Reader, Appendable)")
        public void testObfuscateTextReaderToAppendable() throws IOException {
            Obfuscator obfuscator = obfuscatorSupplier.get();

            StringBuilder destination = new StringBuilder();
            obfuscator.obfuscateText(new StringReader(input), destination);
            assertEquals(expected, destination.toString());

            destination.delete(0, destination.length());
            obfuscator.obfuscateText(new BufferedReader(new StringReader(input)), destination);
            assertEquals(expected, destination.toString());
        }

        @Test
        @DisplayName("streamTo(Appendable")
        public void testStreamTo() throws IOException {
            Obfuscator obfuscator = obfuscatorSupplier.get();

            Writer writer = new StringWriter();
            try (Writer w = obfuscator.streamTo(writer)) {
                int index = 0;
                while (index < input.length()) {
                    int to = Math.min(index + 5, input.length());
                    w.write(input, index, to - index);
                    index = to;
                }
            }
            assertEquals(expected, writer.toString());
        }
    }

    private static Obfuscator createObfuscator() {
        return createObfuscator(ofType(JSONObfuscator.TYPE));
    }

    private static Obfuscator createObfuscator(boolean includeWarning, MalformedJSONStrategy strategy) {
        JSONBuilder builder = builder()
                .includeMalformedJSONWarning(includeWarning)
                .withMalformedJSONStrategy(strategy);
        return createObfuscator(builder);
    }

    private static Obfuscator createObfuscator(Builder builder) {
        Obfuscator obfuscator = fixedLength(3);
        return builder
                .withProperty("string", obfuscator)
                .withProperty("int", obfuscator)
                .withProperty("float", obfuscator)
                .withProperty("boolean", obfuscator)
                .withProperty("object", obfuscator)
                .withProperty("array", obfuscator)
                .withProperty("null", obfuscator)
                .build();
    }

    private static String readResource(String name) {
        StringBuilder sb = new StringBuilder();
        try (Reader input = new InputStreamReader(JSONObfuscatorTest.class.getResourceAsStream(name), StandardCharsets.UTF_8)) {
            char[] buffer = new char[4096];
            int len;
            while ((len = input.read(buffer)) != -1) {
                sb.append(buffer, 0, len);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return sb.toString();
    }
}
