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
import static com.github.robtimus.obfuscation.jackson.JSONObfuscator.DISABLED_JSON_PARSER_FEATURES;
import static com.github.robtimus.obfuscation.jackson.JSONObfuscator.ENABLED_JSON_PARSER_FEATURES;
import static com.github.robtimus.obfuscation.jackson.JSONObfuscator.ENABLED_JSON_READ_FEATURES;
import static com.github.robtimus.obfuscation.jackson.JSONObfuscator.builder;
import static com.github.robtimus.obfuscation.support.CaseSensitivity.CASE_SENSITIVE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.log4j.Appender;
import org.apache.log4j.Level;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.github.robtimus.junit.support.extension.testlogger.Reload4jLoggerContext;
import com.github.robtimus.junit.support.extension.testlogger.TestLogger;
import com.github.robtimus.obfuscation.Obfuscator;
import com.github.robtimus.obfuscation.jackson.JSONObfuscator.Builder;

@SuppressWarnings("nls")
@TestInstance(Lifecycle.PER_CLASS)
class JSONObfuscatorTest {

    @Nested
    @DisplayName("features")
    class Features {

        @ParameterizedTest(name = "{0}")
        @EnumSource(JsonParser.Feature.class)
        @DisplayName("JsonParser.Feature completeness")
        void testJsonParserFeatureCompleteness(JsonParser.Feature feature) {
            int enabled = ENABLED_JSON_PARSER_FEATURES.contains(feature) ? 1 : 0;
            int disabled = DISABLED_JSON_PARSER_FEATURES.contains(feature) ? 1 : 0;
            int deprecated = assertDoesNotThrow(() -> {
                Field field = JsonParser.Feature.class.getDeclaredField(feature.name());
                return field.isAnnotationPresent(Deprecated.class) ? 1 : 0;
            });
            assertEquals(1, enabled + disabled + deprecated, "Each JsonParser.Feature should either be enabled, disabled, or deprecated");
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(JsonReadFeature.class)
        @DisplayName("JsonReadFeature completeness")
        void testJsonReadFeatureCompleteness(JsonReadFeature feature) {
            assertTrue(ENABLED_JSON_READ_FEATURES.contains(feature), "Each JsonReadFeature should eitherbe enabled, disabled, or deprecated");
        }
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource
    @DisplayName("equals(Object)")
    void testEquals(Obfuscator obfuscator, Object object, boolean expected) {
        assertEquals(expected, obfuscator.equals(object));
    }

    Arguments[] testEquals() {
        Obfuscator obfuscator = createObfuscator(builder().withProperty("test", none()));
        return new Arguments[] {
                arguments(obfuscator, obfuscator, true),
                arguments(obfuscator, null, false),
                arguments(obfuscator, createObfuscator(builder().withProperty("test", none())), true),
                arguments(obfuscator, createObfuscator(builder().withProperty("test", none(), CASE_SENSITIVE)), true),
                arguments(obfuscator, createObfuscator(builder().withProperty("test", fixedLength(3))), false),
                arguments(obfuscator, createObfuscator(builder().withProperty("test", none()).excludeObjects()), false),
                arguments(obfuscator, createObfuscator(builder().withProperty("test", none()).excludeArrays()), false),
                arguments(obfuscator, createObfuscator(builder().withProperty("test", none()).limitTo(Long.MAX_VALUE)), true),
                arguments(obfuscator, createObfuscator(builder().withProperty("test", none()).limitTo(1024)), false),
                arguments(obfuscator, createObfuscator(builder().withProperty("test", none()).limitTo(Long.MAX_VALUE).withTruncatedIndicator(null)),
                        false),
                arguments(obfuscator, builder().build(), false),
                arguments(obfuscator, createObfuscator(builder().withProperty("test", none()).withMalformedJSONWarning(null)), false),
                arguments(obfuscator, "foo", false),
        };
    }

    @Test
    @DisplayName("hashCode()")
    void testHashCode() {
        Obfuscator obfuscator = createObfuscator();
        assertEquals(obfuscator.hashCode(), obfuscator.hashCode());
        assertEquals(obfuscator.hashCode(), createObfuscator().hashCode());
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTest {

        @Nested
        @DisplayName("limitTo")
        class LimitTo {

            @Test
            @DisplayName("negative limit")
            void testNegativeLimit() {
                Builder builder = builder();
                assertThrows(IllegalArgumentException.class, () -> builder.limitTo(-1));
            }
        }
    }

    @Nested
    @DisplayName("valid JSON")
    @TestInstance(Lifecycle.PER_CLASS)
    class ValidJSON {

        @Nested
        @DisplayName("caseSensitiveByDefault()")
        @TestInstance(Lifecycle.PER_CLASS)
        class ObfuscatingCaseSensitively extends ObfuscatorTest {

            ObfuscatingCaseSensitively() {
                super("JSONObfuscator.input.valid.json", "JSONObfuscator.expected.valid.all",
                        () -> createObfuscator(builder().caseSensitiveByDefault()));
            }
        }

        @Nested
        @DisplayName("caseInsensitiveByDefault()")
        @TestInstance(Lifecycle.PER_CLASS)
        class ObfuscatingCaseInsensitively extends ObfuscatorTest {

            ObfuscatingCaseInsensitively() {
                super("JSONObfuscator.input.valid.json", "JSONObfuscator.expected.valid.all",
                        () -> createObfuscatorCaseInsensitive(builder().caseInsensitiveByDefault()));
            }
        }

        @Nested
        @DisplayName("obfuscating all (default)")
        @TestInstance(Lifecycle.PER_CLASS)
        class ObfuscatingAll extends ObfuscatorTest {

            ObfuscatingAll() {
                super("JSONObfuscator.input.valid.json", "JSONObfuscator.expected.valid.all", () -> createObfuscator());
            }
        }

        @Nested
        @DisplayName("obfuscating all, overriding scalars only by default")
        @TestInstance(Lifecycle.PER_CLASS)
        class ObfuscatingAllOverridden extends ObfuscatorTest {

            ObfuscatingAllOverridden() {
                super("JSONObfuscator.input.valid.json", "JSONObfuscator.expected.valid.all",
                        () -> createObfuscatorObfuscatingAll(builder().scalarsOnlyByDefault()));
            }
        }

        @Nested
        @DisplayName("obfuscating scalars only by default")
        @TestInstance(Lifecycle.PER_CLASS)
        class ObfuscatingScalars extends ObfuscatorTest {

            ObfuscatingScalars() {
                super("JSONObfuscator.input.valid.json", "JSONObfuscator.expected.valid.scalar",
                        () -> createObfuscator(builder().scalarsOnlyByDefault()));
            }
        }

        @Nested
        @DisplayName("obfuscating scalars only, overriding all by default")
        @TestInstance(Lifecycle.PER_CLASS)
        class ObfuscatingScalarsOverridden extends ObfuscatorTest {

            ObfuscatingScalarsOverridden() {
                super("JSONObfuscator.input.valid.json", "JSONObfuscator.expected.valid.scalar",
                        () -> createObfuscatorObfuscatingScalarsOnly(builder().allByDefault()));
            }
        }

        @Nested
        @DisplayName("limited")
        @TestInstance(Lifecycle.PER_CLASS)
        class Limited {

            @Nested
            @DisplayName("with truncated indicator")
            @TestInstance(Lifecycle.PER_CLASS)
            class WithTruncatedIndicator extends ObfuscatorTest {

                WithTruncatedIndicator() {
                    super("JSONObfuscator.input.valid.json", "JSONObfuscator.expected.valid.limited.with-indicator",
                            () -> createObfuscator(builder().limitTo(583)));
                }
            }

            @Nested
            @DisplayName("without truncated indicator")
            @TestInstance(Lifecycle.PER_CLASS)
            class WithoutTruncatedIndicator extends ObfuscatorTest {

                WithoutTruncatedIndicator() {
                    super("JSONObfuscator.input.valid.json", "JSONObfuscator.expected.valid.limited.without-indicator",
                            () -> createObfuscator(builder().limitTo(583).withTruncatedIndicator(null)));
                }
            }
        }
    }

    @Nested
    @DisplayName("invalid JSON")
    @TestInstance(Lifecycle.PER_CLASS)
    class InvalidJSON extends ObfuscatorTest {

        InvalidJSON() {
            super("JSONObfuscator.input.invalid", "JSONObfuscator.expected.invalid", () -> createObfuscator());
        }
    }

    @Nested
    @DisplayName("truncated JSON")
    @TestInstance(Lifecycle.PER_CLASS)
    class TruncatedJSON {

        @Nested
        @DisplayName("with warning")
        class WithWarning extends TruncatedJSONTest {

            WithWarning() {
                super("JSONObfuscator.expected.truncated", true);
            }
        }

        @Nested
        @DisplayName("without warning")
        class WithoutWarning extends TruncatedJSONTest {

            WithoutWarning() {
                super("JSONObfuscator.expected.truncated.no-warning", false);
            }
        }

        private class TruncatedJSONTest extends ObfuscatorTest {

            TruncatedJSONTest(String expectedResource, boolean includeWarning) {
                super("JSONObfuscator.input.truncated", expectedResource, () -> createObfuscator(includeWarning));
            }
        }
    }

    abstract static class ObfuscatorTest {

        private final String input;
        private final String expected;
        private final String inputWithLargeValues;
        private final String expectedWithLargeValues;
        private final Supplier<Obfuscator> obfuscatorSupplier;

        @TestLogger.ForClass(JSONObfuscator.class)
        private Reload4jLoggerContext logger;

        private Appender appender;

        ObfuscatorTest(String inputResource, String expectedResource, Supplier<Obfuscator> obfuscatorSupplier) {
            this.input = readResource(inputResource);
            this.expected = readResource(expectedResource);
            this.obfuscatorSupplier = obfuscatorSupplier;

            String largeValue = createLargeValue();
            inputWithLargeValues = input.replace("string\\\"int", largeValue);
            expectedWithLargeValues = expected
                    .replace("string\\\"int", largeValue)
                    .replace("(total: " + input.length(), "(total: " + inputWithLargeValues.length());
        }

        @BeforeEach
        void configureLogger() {
            appender = mock(Appender.class);
            logger.setLevel(Level.TRACE)
                    .setAppender(appender)
                    .useParentAppenders(false);
        }

        @Test
        @DisplayName("obfuscateText(CharSequence, int, int)")
        void testObfuscateTextCharSequence() {
            Obfuscator obfuscator = obfuscatorSupplier.get();

            assertEquals(expected, obfuscator.obfuscateText("x" + input + "x", 1, 1 + input.length()).toString());

            assertNoTruncationLogging(appender);
        }

        @Test
        @DisplayName("obfuscateText(CharSequence, int, int) with large values")
        void testObfuscateTextCharSequenceWithLargeValues() {
            Obfuscator obfuscator = obfuscatorSupplier.get();

            assertEquals(expectedWithLargeValues,
                    obfuscator.obfuscateText("x" + inputWithLargeValues + "x", 1, 1 + inputWithLargeValues.length()).toString());

            assertNoTruncationLogging(appender);
        }

        @Test
        @DisplayName("obfuscateText(CharSequence, int, int) with large values")
        void testObfuscateTextCharSequenceWithLargeValuesLoggingDisabled() {
            Obfuscator obfuscator = obfuscatorSupplier.get();

            assertEquals(expectedWithLargeValues,
                    obfuscator.obfuscateText("x" + inputWithLargeValues + "x", 1, 1 + inputWithLargeValues.length()).toString());

            assertNoTruncationLogging(appender);
        }

        @Test
        @DisplayName("obfuscateText(CharSequence, int, int, Appendable)")
        void testObfuscateTextCharSequenceToAppendable() throws IOException {
            Obfuscator obfuscator = obfuscatorSupplier.get();

            StringWriter destination = spy(new StringWriter());
            obfuscator.obfuscateText("x" + input + "x", 1, 1 + input.length(), destination);
            assertEquals(expected, destination.toString());
            verify(destination, never()).close();

            assertNoTruncationLogging(appender);
        }

        @Test
        @DisplayName("obfuscateText(CharSequence, int, int, Appendable) with large values")
        void testObfuscateTextCharSequenceToAppendableWithLargeValues() throws IOException {
            Obfuscator obfuscator = obfuscatorSupplier.get();

            StringWriter destination = spy(new StringWriter());
            destination.getBuffer().delete(0, destination.getBuffer().length());
            obfuscator.obfuscateText("x" + inputWithLargeValues + "x", 1, 1 + inputWithLargeValues.length(), destination);
            assertEquals(expectedWithLargeValues, destination.toString());
            verify(destination, never()).close();

            assertNoTruncationLogging(appender);
        }

        @Test
        @DisplayName("obfuscateText(Reader, Appendable)")
        @SuppressWarnings("resource")
        void testObfuscateTextReaderToAppendable() throws IOException {
            Obfuscator obfuscator = obfuscatorSupplier.get();

            StringWriter destination = spy(new StringWriter());
            Reader reader = spy(new StringReader(input));
            obfuscator.obfuscateText(reader, destination);
            assertEquals(expected, destination.toString());
            verify(reader, never()).close();
            verify(destination, never()).close();

            destination.getBuffer().delete(0, destination.getBuffer().length());
            reader = spy(new BufferedReader(new StringReader(input)));
            obfuscator.obfuscateText(reader, destination);
            assertEquals(expected, destination.toString());
            verify(reader, never()).close();
            verify(destination, never()).close();

            assertNoTruncationLogging(appender);
        }

        @Test
        @DisplayName("obfuscateText(Reader, Appendable) with large values")
        @SuppressWarnings("resource")
        void testObfuscateTextReaderToAppendableWithLargeValues() throws IOException {
            Obfuscator obfuscator = obfuscatorSupplier.get();

            StringWriter destination = spy(new StringWriter());
            Reader reader = spy(new StringReader(inputWithLargeValues));
            obfuscator.obfuscateText(reader, destination);
            assertEquals(expectedWithLargeValues, destination.toString());
            verify(reader, never()).close();
            verify(destination, never()).close();

            destination.getBuffer().delete(0, destination.getBuffer().length());
            reader = spy(new BufferedReader(new StringReader(inputWithLargeValues)));
            obfuscator.obfuscateText(reader, destination);
            assertEquals(expectedWithLargeValues, destination.toString());
            verify(reader, never()).close();
            verify(destination, never()).close();

            assertTruncationLogging(appender);
        }

        @Test
        @DisplayName("obfuscateText(Reader, Appendable) with large values - logging disabled")
        @SuppressWarnings("resource")
        void testObfuscateTextReaderToAppendableWithLargeValuesLoggingDisabled() throws IOException {
            logger.setLevel(Level.DEBUG);

            Obfuscator obfuscator = obfuscatorSupplier.get();

            StringWriter destination = spy(new StringWriter());
            Reader reader = spy(new StringReader(inputWithLargeValues));
            obfuscator.obfuscateText(reader, destination);
            assertEquals(expectedWithLargeValues, destination.toString());
            verify(reader, never()).close();
            verify(destination, never()).close();

            destination.getBuffer().delete(0, destination.getBuffer().length());
            reader = spy(new BufferedReader(new StringReader(inputWithLargeValues)));
            obfuscator.obfuscateText(reader, destination);
            assertEquals(expectedWithLargeValues, destination.toString());
            verify(reader, never()).close();
            verify(destination, never()).close();

            assertNoTruncationLogging(appender);
        }

        @Test
        @DisplayName("streamTo(Appendable)")
        void testStreamTo() throws IOException {
            Obfuscator obfuscator = obfuscatorSupplier.get();

            StringWriter writer = spy(new StringWriter());
            try (Writer w = obfuscator.streamTo(writer)) {
                int index = 0;
                while (index < input.length()) {
                    int to = Math.min(index + 5, input.length());
                    w.write(input, index, to - index);
                    index = to;
                }
            }
            assertEquals(expected, writer.toString());
            verify(writer, never()).close();

            assertNoTruncationLogging(appender);
        }

        @Test
        @DisplayName("streamTo(Appendable) with large values")
        void testStreamToWithLargeValues() throws IOException {
            Obfuscator obfuscator = obfuscatorSupplier.get();

            StringWriter writer = spy(new StringWriter());
            try (Writer w = obfuscator.streamTo(writer)) {
                int index = 0;
                while (index < inputWithLargeValues.length()) {
                    int to = Math.min(index + 5, inputWithLargeValues.length());
                    w.write(inputWithLargeValues, index, to - index);
                    index = to;
                }
            }
            assertEquals(expectedWithLargeValues, writer.toString());
            verify(writer, never()).close();

            // streamTo caches the entire results, then obfuscate the cached contents as a CharSequence
            assertNoTruncationLogging(appender);
        }

        private String createLargeValue() {
            char[] chars = new char[Source.OfReader.PREFERRED_MAX_BUFFER_SIZE];
            for (int i = 0; i < chars.length; i += 10) {
                for (int j = 0; j < 10 && i + j < chars.length; j++) {
                    chars[i + j] = (char) ('0' + j);
                }
            }
            return new String(chars);
        }

        private void assertNoTruncationLogging(Appender appender) {
            ArgumentCaptor<LoggingEvent> loggingEvents = ArgumentCaptor.forClass(LoggingEvent.class);

            verify(appender, atLeast(0)).doAppend(loggingEvents.capture());

            List<String> traceMessages = loggingEvents.getAllValues().stream()
                    .filter(event -> event.getLevel() == Level.TRACE)
                    .map(LoggingEvent::getRenderedMessage)
                    .collect(Collectors.toList());

            assertThat(traceMessages, hasSize(0));
        }

        private void assertTruncationLogging(Appender appender) {
            ArgumentCaptor<LoggingEvent> loggingEvents = ArgumentCaptor.forClass(LoggingEvent.class);

            verify(appender, atLeast(1)).doAppend(loggingEvents.capture());

            List<String> traceMessages = loggingEvents.getAllValues().stream()
                    .filter(event -> event.getLevel() == Level.TRACE)
                    .map(LoggingEvent::getRenderedMessage)
                    .collect(Collectors.toList());

            assertThat(traceMessages, hasSize(greaterThanOrEqualTo(1)));

            Pattern pattern = Pattern.compile(".*: (\\d+)");
            int expectedMax = (int) (Source.OfReader.PREFERRED_MAX_BUFFER_SIZE * 1.25D);
            List<Integer> sizes = traceMessages.stream()
                    .map(message -> extractSize(message, pattern))
                    .collect(Collectors.toList());
            assertThat(sizes, everyItem(lessThanOrEqualTo(expectedMax)));
        }

        private int extractSize(String message, Pattern pattern) {
            Matcher matcher = pattern.matcher(message);
            assertTrue(matcher.find());
            return Integer.parseInt(matcher.group(1));
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

    private static Obfuscator createObfuscatorCaseInsensitive(Builder builder) {
        Obfuscator obfuscator = fixedLength(3);
        return builder
                .withProperty("STRING", obfuscator)
                .withProperty("INT", obfuscator)
                .withProperty("FLOAT", obfuscator)
                .withProperty("BOOLEANTRUE", obfuscator)
                .withProperty("BOOLEANFALSE", obfuscator)
                .withProperty("OBJECT", obfuscator)
                .withProperty("ARRAY", obfuscator)
                .withProperty("NULL", obfuscator)
                .withProperty("NOTOBFUSCATED", none())
                .build();
    }

    private static Obfuscator createObfuscatorObfuscatingAll(Builder builder) {
        Obfuscator obfuscator = fixedLength(3);
        return builder
                .withProperty("string", obfuscator).all()
                .withProperty("int", obfuscator).all()
                .withProperty("float", obfuscator).all()
                .withProperty("booleanTrue", obfuscator).all()
                .withProperty("booleanFalse", obfuscator).all()
                .withProperty("object", obfuscator).all()
                .withProperty("array", obfuscator).all()
                .withProperty("null", obfuscator).all()
                .withProperty("notObfuscated", none()).all()
                .build();
    }

    private static Obfuscator createObfuscatorObfuscatingScalarsOnly(Builder builder) {
        Obfuscator obfuscator = fixedLength(3);
        return builder
                .withProperty("string", obfuscator).scalarsOnly()
                .withProperty("int", obfuscator).scalarsOnly()
                .withProperty("float", obfuscator).scalarsOnly()
                .withProperty("booleanTrue", obfuscator).scalarsOnly()
                .withProperty("booleanFalse", obfuscator).scalarsOnly()
                .withProperty("object", obfuscator).scalarsOnly()
                .withProperty("array", obfuscator).scalarsOnly()
                .withProperty("null", obfuscator).scalarsOnly()
                .withProperty("notObfuscated", none()).scalarsOnly()
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
        return sb.toString().replace("\r", "");
    }
}
