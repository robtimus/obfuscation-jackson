/*
 * PropertyConfig.java
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

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import com.github.robtimus.obfuscation.Obfuscator;
import com.github.robtimus.obfuscation.jackson.JSONObfuscator.PropertyConfigurer.ObfuscationMode;
import com.github.robtimus.obfuscation.jackson.JSONObfuscator.PropertyConfigurer.ValueType;
import com.github.robtimus.obfuscation.support.CaseSensitivity;

record PropertyConfig(
        Obfuscator obfuscator,
        ObfuscationMode forObjects,
        ObfuscationMode forArrays,
        boolean performObfuscation
) {

    PropertyConfig {
        Objects.requireNonNull(obfuscator);
        Objects.requireNonNull(forObjects);
        Objects.requireNonNull(forArrays);
    }

    PropertyConfig(Obfuscator obfuscator, ObfuscationMode forObjects, ObfuscationMode forArrays) {
        this(obfuscator, forObjects, forArrays, !obfuscator.equals(Obfuscator.none()));
    }

    static final class Lookup {

        private final Map<String, Map<ValueType, PropertyConfig>> caseSensitiveProperties;
        private final Map<String, Map<ValueType, PropertyConfig>> caseInsensitiveProperties;

        private Lookup(Builder builder) {
            caseSensitiveProperties = copy(builder.caseSensitiveProperties, new HashMap<>());
            caseInsensitiveProperties = copy(builder.caseInsensitiveProperties, new TreeMap<>(String.CASE_INSENSITIVE_ORDER));
        }

        private static Map<String, Map<ValueType, PropertyConfig>> copy(Map<String, Map<ValueType, PropertyConfig>> map,
                                                                        Map<String, Map<ValueType, PropertyConfig>> target) {

            map.forEach((property, subMap) -> target.put(property, new EnumMap<>(subMap)));
            return Collections.unmodifiableMap(target);
        }

        PropertyConfig find(String property, ValueType valueType) {
            PropertyConfig config = find(caseSensitiveProperties, property, valueType);
            if (config != null) {
                return config;
            }
            return find(caseInsensitiveProperties, property, valueType);
        }

        private PropertyConfig find(Map<String, Map<ValueType, PropertyConfig>> properties, String property, ValueType valueType) {
            Map<ValueType, PropertyConfig> configs = properties.get(property);
            return configs != null
                    ? configs.get(valueType)
                    : null;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Lookup other
                    && equals(other);
        }

        private boolean equals(Lookup other) {
            return caseSensitiveProperties.equals(other.caseSensitiveProperties)
                    && caseInsensitiveProperties.equals(other.caseInsensitiveProperties);
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + caseSensitiveProperties.hashCode();
            result = prime * result + caseInsensitiveProperties.hashCode();
            return result;
        }

        static Builder builder() {
            return new Builder();
        }

        static final class Builder {

            private final Map<String, Map<ValueType, PropertyConfig>> caseSensitiveProperties;
            private final Map<String, Map<ValueType, PropertyConfig>> caseInsensitiveProperties;

            private Builder() {
                caseSensitiveProperties = new HashMap<>();
                caseInsensitiveProperties = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            }

            Builder add(String property, ValueType valueType, CaseSensitivity caseSensitivity, PropertyConfig config) {
                Map<String, Map<ValueType, PropertyConfig>> properties = switch (caseSensitivity) {
                    case CASE_SENSITIVE -> caseSensitiveProperties;
                    case CASE_INSENSITIVE -> caseInsensitiveProperties;
                };
                properties.computeIfAbsent(property, k -> new EnumMap<>(ValueType.class)).merge(valueType, config, (a, b) -> {
                    throw new IllegalArgumentException(Messages.JSONObfuscator.duplicateProperty(caseSensitivity, property, valueType));
                });
                return this;
            }

            Lookup build() {
                return new Lookup(this);
            }
        }
    }
}
