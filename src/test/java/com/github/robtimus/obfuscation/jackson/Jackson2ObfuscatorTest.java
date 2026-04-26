/*
 * Jackson2ObfuscatorTest.java
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

import static com.github.robtimus.obfuscation.jackson.Jackson2Obfuscator.DISABLED_STREAM_READ_FEATURES;
import static com.github.robtimus.obfuscation.jackson.Jackson2Obfuscator.ENABLED_JSON_READ_FEATURES;
import static com.github.robtimus.obfuscation.jackson.Jackson2Obfuscator.ENABLED_STREAM_READ_FEATURES;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.json.JsonReadFeature;

@SuppressWarnings("nls")
class Jackson2ObfuscatorTest {

    @Nested
    @DisplayName("features")
    @TestInstance(Lifecycle.PER_CLASS)
    class Features {

        @ParameterizedTest(name = "{0}")
        @EnumSource(StreamReadFeature.class)
        @DisplayName("StreamReadFeature completeness")
        void testStreamReadFeatureCompleteness(StreamReadFeature feature) {
            int enabled = ENABLED_STREAM_READ_FEATURES.contains(feature.name()) ? 1 : 0;
            int disabled = DISABLED_STREAM_READ_FEATURES.contains(feature.name()) ? 1 : 0;
            int deprecated = assertDoesNotThrow(() -> {
                Field field = StreamReadFeature.class.getDeclaredField(feature.name());
                return field.isAnnotationPresent(Deprecated.class) ? 1 : 0;
            });
            assertEquals(1, enabled + disabled + deprecated, "Each StreamReadFeature should either be enabled, disabled, or deprecated");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("enabledStreamReadFeatures")
        void testEnabledStreamReadFeatureCorrectness(String featureName) {
            assertDoesNotThrow(() -> StreamReadFeature.valueOf(featureName));
        }

        Stream<Arguments> enabledStreamReadFeatures() {
            Set<String> unsupportedFeatures = readUnsupportedFeatures("unsupportedStreamReadFeatures");
            assertAll(unsupportedFeatures.stream()
                    .map(feature -> () -> assertThrows(IllegalArgumentException.class, () -> StreamReadFeature.valueOf(feature))));
            return ENABLED_STREAM_READ_FEATURES.stream()
                    .filter(feature -> !unsupportedFeatures.contains(feature))
                    .map(Arguments::arguments);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("disabledStreamReadFeatures")
        void testDisabledStreamReadFeatureCorrectness(String featureName) {
            assertDoesNotThrow(() -> StreamReadFeature.valueOf(featureName));
        }

        Stream<Arguments> disabledStreamReadFeatures() {
            Set<String> unsupportedFeatures = readUnsupportedFeatures("unsupportedStreamReadFeatures");
            assertAll(unsupportedFeatures.stream()
                    .map(feature -> () -> assertThrows(IllegalArgumentException.class, () -> StreamReadFeature.valueOf(feature))));
            return DISABLED_STREAM_READ_FEATURES.stream()
                    .filter(feature -> !unsupportedFeatures.contains(feature))
                    .map(Arguments::arguments);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(JsonReadFeature.class)
        @DisplayName("JsonReadFeature completeness")
        void testJsonReadFeatureCompleteness(JsonReadFeature feature) {
            assertTrue(ENABLED_JSON_READ_FEATURES.contains(feature.name()),
                    "Each JsonReadFeature should eitherbe enabled, disabled, or deprecated");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("enabledJsonReadFeatures")
        void testEnabledJsonReadFeatureCorrectness(String featureName) {
            assertDoesNotThrow(() -> JsonReadFeature.valueOf(featureName));
        }

        Stream<Arguments> enabledJsonReadFeatures() {
            Set<String> unsupportedFeatures = readUnsupportedFeatures("unsupportedJsonReadFeatures");
            assertAll(unsupportedFeatures.stream()
                    .map(feature -> () -> assertThrows(IllegalArgumentException.class, () -> JsonReadFeature.valueOf(feature))));
            return ENABLED_JSON_READ_FEATURES.stream()
                    .filter(feature -> !unsupportedFeatures.contains(feature))
                    .map(Arguments::arguments);
        }

        private Set<String> readUnsupportedFeatures(String systemPropertyPostfix) {
            String featuresString = System.getProperty("com.github.robtimus.obfuscation.jackson.test.jackson2." + systemPropertyPostfix);
            if (featuresString == null) {
                return Collections.emptySet();
            }
            return Pattern.compile(",").splitAsStream(featuresString)
                    .collect(Collectors.toSet());
        }
    }
}
