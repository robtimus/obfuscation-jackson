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
import java.util.Map;
import java.util.function.Predicate;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.util.JsonParserDelegate;

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

    private PropertyConfig currentProperty;
    private JsonToken currentStructure;
    private int depth = 0;

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
    public JsonToken nextToken() throws IOException {
        JsonToken token = super.nextToken();
        if (token == null) {
            return token;
        }
        switch (token) {
            case START_OBJECT:
                startObject();
                break;
            case END_OBJECT:
                endObject();
                break;
            case START_ARRAY:
                startArray();
                break;
            case END_ARRAY:
                endArray();
                break;
            case FIELD_NAME:
                fieldName();
                break;
            case VALUE_STRING:
                valueString();
                break;
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                valueNumber();
                break;
            case VALUE_TRUE:
                valueBoolean(token);
                break;
            case VALUE_FALSE:
                valueBoolean(token);
                break;
            case VALUE_NULL:
                valueNull();
                break;
            default:
                break;
        }
        return token;
    }

    private void startObject() throws IOException {
        startStructure(JsonToken.START_OBJECT, p -> p.obfuscateObjects);
    }

    private void endObject() throws IOException {
        endStructure(JsonToken.START_OBJECT, JsonToken.END_OBJECT);
    }

    private void startArray() throws IOException {
        startStructure(JsonToken.START_ARRAY, p -> p.obfuscateArrays);
    }

    private void endArray() throws IOException {
        endStructure(JsonToken.START_ARRAY, JsonToken.END_ARRAY);
    }

    private void startStructure(JsonToken startToken, Predicate<PropertyConfig> doValidate) throws IOException {
        if (currentProperty != null) {
            if (depth == 0) {
                if (doValidate.test(currentProperty)) {
                    updateOtherTokenFields(startToken);
                    appendUntilToken();

                    currentStructure = startToken;
                    depth++;
                } else {
                    // There is an obfuscator for the structure property, but the obfuscation mode prohibits obfuscating it; reset the obfuscation
                    currentProperty = null;
                }
            } else if (currentStructure == startToken) {
                // In a nested structure that's being obfuscated; do nothing
                depth++;
            }
            // else in a nested structure that's being obfuscated; do nothing
        }
        // else not obfuscating
    }

    private void endStructure(JsonToken startToken, JsonToken endToken) throws IOException {
        if (currentStructure == startToken) {
            depth--;
            if (depth == 0) {
                if (currentProperty.performObfuscation) {
                    int originalTokenStart = tokenStart;
                    updateOtherTokenFields(endToken);
                    obfuscateUntilToken(originalTokenStart);
                }
                // else the obfuscator is Obfuscator.none(), which means we don't need to obfuscate

                currentProperty = null;
                currentStructure = null;
            }
            // else still in a nested structure array that's being obfuscated
        }
        // else currently no structure is being obfuscated
    }

    private void fieldName() throws IOException {
        if (currentProperty == null) {
            currentProperty = properties.get(getCurrentName());

            if (source.needsTruncating()) {
                updateOtherTokenFields(JsonToken.FIELD_NAME);
                appendUntilToken();
                source.truncate();
            }
        } else if (!currentProperty.performObfuscation && source.needsTruncating()) {
            // in a nested object or array that's being obfuscated using Obfuscator.none(), which means we can just append data already
            updateOtherTokenFields(JsonToken.FIELD_NAME);
            appendUntilToken();
            source.truncate();
        }
        // else in a nested object or array that's being obfuscated; do nothing
    }

    private void valueString() throws IOException {
        if (currentProperty != null && depth == 0) {
            updateStringTokenFields();
            appendUntilToken();
            // obfuscate even if the obfuscator is Obfuscator.none(), as that will already append the original value
            obfuscateCurrentToken();

            currentProperty = null;
        }
        // else not obfuscating, or in a nested object or or array that's being obfuscated; do nothing
    }

    private void valueNumber() throws IOException {
        if (currentProperty != null && depth == 0) {
            updateNumberTokenFields();
            appendUntilToken();
            // obfuscate even if the obfuscator is Obfuscator.none(), as that will already append the original value
            obfuscateCurrentToken();

            currentProperty = null;
        }
        // else not obfuscating, or in a nested object or or array that's being obfuscated; do nothing
    }

    private void valueBoolean(JsonToken token) throws IOException {
        if (currentProperty != null && depth == 0) {
            updateOtherTokenFields(token);
            appendUntilToken();
            // obfuscate even if the obfuscator is Obfuscator.none(), as that will already append the original value
            obfuscateCurrentToken();

            currentProperty = null;
        }
        // else not obfuscating, or in a nested object or or array that's being obfuscated; do nothing
    }

    private void valueNull() throws IOException {
        if (currentProperty != null && depth == 0) {
            updateOtherTokenFields(JsonToken.VALUE_NULL);
            appendUntilToken();
            // obfuscate even if the obfuscator is Obfuscator.none(), as that will already append the original value
            obfuscateCurrentToken();

            currentProperty = null;
        }
        // else not obfuscating, or in a nested object or or array that's being obfuscated; do nothing
    }

    private void updateStringTokenFields() throws IOException {
        tokenValue = getValueAsString();
        tokenStart = stringValueStart();
        tokenEnd = stringValueEnd();
    }

    private void updateNumberTokenFields() throws IOException {
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
        return textOffset + (int) getTokenLocation().getCharOffset();
    }

    private int tokenEnd() {
        return textOffset + (int) getCurrentLocation().getCharOffset();
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

    private void obfuscateUntilToken(int originalTokenStart) throws IOException {
        source.obfuscateText(originalTokenStart, tokenEnd, currentProperty.obfuscator, destination);
        textIndex = tokenEnd;
    }

    private void obfuscateCurrentToken() throws IOException {
        currentProperty.obfuscator.obfuscateText(tokenValue, destination);
        textIndex = tokenEnd;
    }

    void appendRemainder() throws IOException {
        textIndex = source.appendRemainder(textIndex, textEnd, destination);
    }
}
