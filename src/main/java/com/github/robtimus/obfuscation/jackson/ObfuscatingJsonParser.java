/*
 * ObfuscatingJsonParser.java
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.function.Function;
import com.github.robtimus.obfuscation.jackson.JSONObfuscator.PropertyConfigurer.ObfuscationMode;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.util.JsonParserDelegate;

final class ObfuscatingJsonParser extends JsonParserDelegate {

    private final Source source;
    private final Appendable destination;

    private final Map<String, PropertyConfig> properties;

    private final int textOffset;
    private final int textEnd;
    private int textIndex;

    private String tokenValue;
    private int tokenStart;
    private int tokenEnd;

    private final Deque<ObfuscatedProperty> currentProperties = new ArrayDeque<>();

    ObfuscatingJsonParser(JsonParser parser, Source source, int start, int end, Appendable destination, Map<String, PropertyConfig> properties) {
        super(parser);

        this.source = source;
        this.textOffset = start;
        this.textEnd = end;
        this.textIndex = start;
        this.destination = destination;
        this.properties = properties;
    }

    @Override
    public JsonToken nextToken() {
        JsonToken token = super.nextToken();
        if (token == null) {
            return token;
        }
        try {
            switch (token) {
                case START_OBJECT -> startObject();
                case END_OBJECT -> endObject();
                case START_ARRAY -> startArray();
                case END_ARRAY -> endArray();
                case PROPERTY_NAME -> fieldName();
                case VALUE_STRING -> valueString();
                case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> valueNumber();
                case VALUE_TRUE, VALUE_FALSE, VALUE_NULL -> valueOther(token);
                default -> {
                    // do nothing
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return token;
    }

    private void startObject() throws IOException {
        startStructure(JsonToken.START_OBJECT, p -> p.forObjects);
    }

    private void endObject() throws IOException {
        endStructure(JsonToken.START_OBJECT, JsonToken.END_OBJECT);
    }

    private void startArray() throws IOException {
        startStructure(JsonToken.START_ARRAY, p -> p.forArrays);
    }

    private void endArray() throws IOException {
        endStructure(JsonToken.START_ARRAY, JsonToken.END_ARRAY);
    }

    private void startStructure(JsonToken startToken, Function<PropertyConfig, ObfuscationMode> getObfuscationMode) throws IOException {
        ObfuscatedProperty currentProperty = currentProperties.peekLast();
        if (currentProperty != null) {
            if (currentProperty.depth == 0) {
                // The start of the structure that's being obfuscated
                ObfuscationMode obfuscationMode = getObfuscationMode.apply(currentProperty.config);
                if (obfuscationMode == ObfuscationMode.EXCLUDE) {
                    // There is an obfuscator for the structure property, but the obfuscation mode prohibits handling it, so discard the property
                    currentProperties.removeLast();
                } else {
                    updateOtherTokenFields(startToken);
                    appendUntilToken();

                    currentProperty.structure = startToken;
                    currentProperty.obfuscationMode = obfuscationMode;
                    currentProperty.depth++;
                }
            } else if (currentProperty.structure == startToken) {
                // In a nested structure that's being obfuscated; do nothing
                currentProperty.depth++;
            }
            // else in a nested structure that's being obfuscated; do nothing
        }
        // else not obfuscating anything
    }

    private void endStructure(JsonToken startToken, JsonToken endToken) throws IOException {
        ObfuscatedProperty currentProperty = currentProperties.peekLast();
        if (currentProperty != null && currentProperty.structure == startToken) {
            currentProperty.depth--;
            if (currentProperty.depth == 0) {
                if (currentProperty.obfuscateStructure()) {
                    int originalTokenStart = tokenStart;
                    updateOtherTokenFields(endToken);
                    obfuscateUntilToken(currentProperty, originalTokenStart);
                }
                // else the obfuscator is Obfuscator.none(), which means we don't need to obfuscate,
                // or the structure itself should not be obfuscated

                currentProperties.removeLast();
            }
            // else still in a nested structure array that's being obfuscated
        }
        // else currently no structure is being obfuscated
    }

    private void fieldName() throws IOException {
        ObfuscatedProperty currentProperty = currentProperties.peekLast();
        if (currentProperty == null || currentProperty.allowsOverriding()) {
            PropertyConfig config = properties.get(currentName());
            if (config != null) {
                currentProperty = new ObfuscatedProperty(config);
                currentProperties.addLast(currentProperty);
            }

            if (source.needsTruncating()) {
                updateOtherTokenFields(JsonToken.PROPERTY_NAME);
                appendUntilToken();
                source.truncate();
            }
        } else if (!currentProperty.config.performObfuscation && source.needsTruncating()) {
            // in a nested object or array that's being obfuscated using Obfuscator.none(), which means we can just append data already
            updateOtherTokenFields(JsonToken.PROPERTY_NAME);
            appendUntilToken();
            source.truncate();
        }
        // else in a nested object or array that's being obfuscated; do nothing
    }

    private void valueString() throws IOException {
        ObfuscatedProperty currentProperty = currentProperties.peekLast();
        if (currentProperty != null && currentProperty.obfuscateScalar()) {
            updateStringTokenFields();
            appendUntilToken();
            obfuscateCurrentToken(currentProperty);

            if (currentProperty.depth == 0) {
                currentProperties.removeLast();
            }
        }
        // else not obfuscating, or using Obfuscator.none(), or in a nested object or or array that's being obfuscated; do nothing
    }

    private void valueNumber() throws IOException {
        ObfuscatedProperty currentProperty = currentProperties.peekLast();
        if (currentProperty != null && currentProperty.obfuscateScalar()) {
            updateNumberTokenFields();
            appendUntilToken();
            obfuscateCurrentToken(currentProperty);

            if (currentProperty.depth == 0) {
                currentProperties.removeLast();
            }
        }
        // else not obfuscating, or using Obfuscator.none(), or in a nested object or or array that's being obfuscated; do nothing
    }

    private void valueOther(JsonToken token) throws IOException {
        ObfuscatedProperty currentProperty = currentProperties.peekLast();
        if (currentProperty != null && currentProperty.obfuscateScalar()) {
            updateOtherTokenFields(token);
            appendUntilToken();
            obfuscateCurrentToken(currentProperty);

            if (currentProperty.depth == 0) {
                currentProperties.removeLast();
            }
        }
        // else not obfuscating, or using Obfuscator.none(), or in a nested object or or array that's being obfuscated; do nothing
    }

    private void updateStringTokenFields() {
        tokenValue = getValueAsString();
        tokenStart = stringValueStart();
        tokenEnd = stringValueEnd();
    }

    private void updateNumberTokenFields() {
        tokenValue = getValueAsString();
        tokenStart = tokenStart();
        tokenEnd = tokenEnd();
    }

    private void updateOtherTokenFields(JsonToken token) {
        tokenValue = token.asString();
        tokenStart = tokenStart();
        tokenEnd = tokenEnd();
    }

    private int tokenStart() {
        return textOffset + (int) currentTokenLocation().getCharOffset();
    }

    private int tokenEnd() {
        return textOffset + (int) currentLocation().getCharOffset();
    }

    private int stringValueStart() {
        int start = tokenStart();
        // start points to the opening ", skip past it
        if (source.charAt(start) == '"') {
            start++;
        }
        return start;
    }

    private int stringValueEnd() {
        int end = tokenEnd();
        // end points to the closing ", skip back past it
        if (source.charAt(end - 1) == '"') {
            end--;
        }
        return end;
    }

    private void appendUntilToken() throws IOException {
        source.appendTo(textIndex, tokenStart, destination);
        textIndex = tokenStart;
    }

    private void obfuscateUntilToken(ObfuscatedProperty currentProperty, int originalTokenStart) throws IOException {
        source.obfuscateText(originalTokenStart, tokenEnd, currentProperty.config.obfuscator, destination);
        textIndex = tokenEnd;
    }

    private void obfuscateCurrentToken(ObfuscatedProperty currentProperty) throws IOException {
        currentProperty.config.obfuscator.obfuscateText(tokenValue, destination);
        textIndex = tokenEnd;
    }

    void appendRemainder() throws IOException {
        textIndex = source.appendRemainder(textIndex, textEnd, destination);
    }

    private static final class ObfuscatedProperty {

        private final PropertyConfig config;
        private JsonToken structure;
        private ObfuscationMode obfuscationMode;
        private int depth = 0;

        private ObfuscatedProperty(PropertyConfig config) {
            this.config = config;
        }

        private boolean allowsOverriding() {
            // OBFUSCATE and INHERITED do not allow overriding
            // No need to include EXCLUDE; if that occurs the ObfuscatedProperty is discarded
            return obfuscationMode == ObfuscationMode.INHERIT_OVERRIDABLE;
        }

        private boolean obfuscateStructure() {
            // Don't obfuscate the entire structure if Obfuscator.none() is used
            return config.performObfuscation && obfuscationMode == ObfuscationMode.OBFUSCATE;
        }

        private boolean obfuscateScalar() {
            // Don't obfuscate the scalar if Obfuscator.none() is used
            // Obfuscate if depth == 0 (the property is for the scalar itself),
            // or if the obfuscation mode is INHERITED or INHERITED_OVERRIDABLE (EXCLUDE is discarded)
            return config.performObfuscation
                    && (depth == 0 || obfuscationMode != ObfuscationMode.OBFUSCATE);
        }
    }
}
