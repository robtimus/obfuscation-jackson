/*
 * JSONObfuscatorTest.java
 * Copyright 2020 Rob Spoor
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
import static com.github.robtimus.obfuscation.Obfuscator.none;
import static com.github.robtimus.obfuscation.jackson.JSONObfuscator.builder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.github.robtimus.obfuscation.Obfuscator;
import com.github.robtimus.obfuscation.jackson.JSONObfuscator.Builder;
import com.github.robtimus.obfuscation.jackson.JSONObfuscator.ObfuscationMode;

@SuppressWarnings({ "javadoc", "nls" })
@TestInstance(Lifecycle.PER_CLASS)
public class JSONObfuscatorTest {

    @ParameterizedTest(name = "{1}")
    @MethodSource
    @DisplayName("equals(Object)")
    public void testEquals(Obfuscator obfuscator, Object object, boolean expected) {
        assertEquals(expected, obfuscator.equals(object));
    }

    Arguments[] testEquals() {
        Obfuscator obfuscator = createObfuscator();
        return new Arguments[] {
                arguments(obfuscator, obfuscator, true),
                arguments(obfuscator, null, false),
                arguments(obfuscator, createObfuscator(), true),
                arguments(obfuscator, builder().build(), false),
                arguments(obfuscator, createObfuscator(builder().withObfuscationMode(ObfuscationMode.SCALAR)), false),
                arguments(obfuscator, createObfuscator(builder().withMalformedJSONWarning(null)), false),
                arguments(obfuscator, "foo", false),
        };
    }

    @Test
    @DisplayName("hashCode()")
    public void testHashCode() {
        Obfuscator obfuscator = createObfuscator();
        assertEquals(obfuscator.hashCode(), obfuscator.hashCode());
        assertEquals(obfuscator.hashCode(), createObfuscator().hashCode());
    }

    @Nested
    @DisplayName("valid JSON")
    @TestInstance(Lifecycle.PER_CLASS)
    public class ValidJSON {

        @Nested
        @DisplayName("ObfuscationMode.ALL")
        @TestInstance(Lifecycle.PER_CLASS)
        public class ObfuscatingAll extends ObfuscatorTest {

            public ObfuscatingAll() {
                super("JSONObfuscator.input.valid.json", "JSONObfuscator.expected.valid.all", () -> createObfuscator());
            }
        }

        @Nested
        @DisplayName("ObfuscationMode.SCALAR")
        @TestInstance(Lifecycle.PER_CLASS)
        public class ObfuscatingScalars extends ObfuscatorTest {

            public ObfuscatingScalars() {
                super("JSONObfuscator.input.valid.json", "JSONObfuscator.expected.valid.scalar",
                        () -> createObfuscator(builder().withObfuscationMode(ObfuscationMode.SCALAR)));
            }
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
        @DisplayName("with warning")
        public class WithWarning extends TruncatedJSONTest {

            public WithWarning() {
                super("JSONObfuscator.expected.truncated", true);
            }
        }

        @Nested
        @DisplayName("without warning")
        public class WithoutWarning extends TruncatedJSONTest {

            public WithoutWarning() {
                super("JSONObfuscator.expected.truncated.no-warning", false);
            }
        }

        private class TruncatedJSONTest extends ObfuscatorTest {

            protected TruncatedJSONTest(String expectedResource, boolean includeWarning) {
                super("JSONObfuscator.input.truncated", expectedResource, () -> createObfuscator(includeWarning));
            }
        }
    }

    private static class ObfuscatorTest {

        private final String input;
        private final String expected;
        private final Supplier<Obfuscator> obfuscatorSupplier;

        protected ObfuscatorTest(String inputResource, String expectedResource, Supplier<Obfuscator> obfuscatorSupplier) {
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
            obfuscator.obfuscateText("x" + input + "x", 1, 1 + input.length(), (Appendable) destination);
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
        return builder()
                .transform(JSONObfuscatorTest::createObfuscator);
    }

    private static Obfuscator createObfuscator(boolean includeWarning) {
        Builder builder = builder();
        if (!includeWarning) {
            builder = builder.withMalformedJSONWarning(null);
        }
        return builder.transform(JSONObfuscatorTest::createObfuscator);
    }

    private static Obfuscator createObfuscator(Builder builder) {
        Obfuscator obfuscator = fixedLength(3);
        return builder
                .withProperty("string", obfuscator)
                .withProperty("int", obfuscator)
                .withProperty("float", obfuscator)
                .withProperty("booleanTrue", obfuscator)
                .withProperty("booleanFalse", obfuscator)
                .withProperty("object", obfuscator)
                .withProperty("array", obfuscator)
                .withProperty("null", obfuscator)
                .withProperty("notObfuscated", none())
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
