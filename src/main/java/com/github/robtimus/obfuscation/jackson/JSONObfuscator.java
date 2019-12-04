/*
 * JSONObfuscator.java
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

import static com.github.robtimus.obfuscation.ObfuscatorUtils.checkStartAndEnd;
import static com.github.robtimus.obfuscation.ObfuscatorUtils.copyTo;
import static com.github.robtimus.obfuscation.ObfuscatorUtils.discardAll;
import static com.github.robtimus.obfuscation.ObfuscatorUtils.reader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.github.robtimus.obfuscation.CachingObfuscatingWriter;
import com.github.robtimus.obfuscation.Obfuscator;
import com.github.robtimus.obfuscation.PropertyAwareBuilder;

/**
 * An obfuscator that obfuscates JSON properties in {@link CharSequence CharSequences} or the contents of {@link Reader Readers}.
 *
 * @author Rob Spoor
 */
public final class JSONObfuscator extends Obfuscator {

    private static final Logger LOGGER = LoggerFactory.getLogger(JSONObfuscator.class);

    /**
     * The possible obfuscation modes.
     *
     * @author Rob Spoor
     */
    public enum ObfuscationMode {
        /** Indicates only scalar properties (strings, numbers, booleans, nulls) will be obfuscated, not arrays or objects. */
        SCALAR(false, false),

        /** Indicates all properties will be obfuscated, including arrays and objects. */
        ALL(true, true),
        ;

        private final boolean obfuscateArrays;
        private final boolean obfuscateObjects;

        ObfuscationMode(boolean obfuscateArrays, boolean obfuscateObjects) {
            this.obfuscateArrays = obfuscateArrays;
            this.obfuscateObjects = obfuscateObjects;
        }
    }

    private final Map<String, Obfuscator> obfuscators;
    private final boolean caseInsensitivePropertyNames;
    private final ObfuscationMode obfuscationMode;

    private final JsonFactory jsonFactory;

    private final String malformedJSONWarning;

    private JSONObfuscator(Builder builder) {
        obfuscators = builder.obfuscators();
        caseInsensitivePropertyNames = builder.caseInsensitivePropertyNames();
        obfuscationMode = builder.obfuscationMode;

        jsonFactory = new JsonFactory();
        for (JsonParser.Feature feature : JsonParser.Feature.values()) {
            jsonFactory.enable(feature);
        }
        jsonFactory.disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);
        jsonFactory.disable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

        malformedJSONWarning = builder.malformedJSONWarning;
    }

    @Override
    public CharSequence obfuscateText(CharSequence s, int start, int end) {
        checkStartAndEnd(s, start, end);
        StringBuilder sb = new StringBuilder(end - start);
        obfuscateText(s, start, end, sb);
        return sb.toString();
    }

    @Override
    public void obfuscateText(CharSequence s, int start, int end, Appendable destination) throws IOException {
        checkStartAndEnd(s, start, end);
        @SuppressWarnings("resource")
        Reader input = reader(s, start, end);
        obfuscateText(input, s, start, end, destination);
    }

    @Override
    public void obfuscateText(Reader input, Appendable destination) throws IOException {
        StringBuilder contents = new StringBuilder();
        @SuppressWarnings("resource")
        Reader reader = copyTo(input, contents);
        obfuscateText(reader, contents, 0, -1, destination);
    }

    private void obfuscateText(Reader input, CharSequence s, int start, int end, Appendable destination) throws IOException {
        @SuppressWarnings("resource")
        JsonParser parser = jsonFactory.createParser(input);
        Context context = new Context(parser, s, start, end, destination);
        try {
            JsonToken token;
            while ((token = context.nextToken()) != null) {
                if (token == JsonToken.FIELD_NAME) {
                    String property = context.currentFieldName();
                    Obfuscator obfuscator = obfuscators.get(property);
                    if (obfuscator != null) {
                        obfuscateProperty(obfuscator, context);
                    }
                }
            }
            // read the remainder so the final append will include all text
            discardAll(input);
            context.appendRemainder();
        } catch (JsonParseException e) {
            LOGGER.warn(Messages.JSONObfuscator.malformedJSON.warning.get(), e);
            if (malformedJSONWarning != null) {
                destination.append(malformedJSONWarning);
            }
        }
    }

    private void obfuscateProperty(Obfuscator obfuscator, Context context) throws IOException {
        JsonToken token = context.nextToken();
        switch (token) {
        case START_ARRAY:
            if (!obfuscationMode.obfuscateArrays) {
                // there is an obfuscator for the array property, but the obfuscation mode prohibits obfuscating arrays;
                // abort and continue with the next property
                return;
            }
            context.appendUntilToken(token);
            obfuscateNested(obfuscator, context, JsonToken.START_ARRAY, JsonToken.END_ARRAY);
            break;
        case START_OBJECT:
            if (!obfuscationMode.obfuscateObjects) {
                // there is an obfuscator for the object property, but the obfuscation mode prohibits obfuscating objects;
                // abort and continue with the next property
                return;
            }
            context.appendUntilToken(token);
            obfuscateNested(obfuscator, context, JsonToken.START_OBJECT, JsonToken.END_OBJECT);
            break;
        case VALUE_STRING:
        case VALUE_NUMBER_INT:
        case VALUE_NUMBER_FLOAT:
        case VALUE_TRUE:
        case VALUE_FALSE:
        case VALUE_NULL:
            context.appendUntilToken(token);
            obfuscateScalar(obfuscator, context);
            break;
        default:
            // do nothing
        }
    }

    private void obfuscateNested(Obfuscator obfuscator, Context context, JsonToken beginToken, JsonToken endToken) throws IOException {
        int depth = 1;
        JsonToken token = null;
        while (depth > 0) {
            token = context.nextToken();
            if (token == beginToken) {
                depth++;
            } else if (token == endToken) {
                depth--;
            }
        }
        context.obfuscateUntilToken(token, obfuscator);
    }

    private void obfuscateScalar(Obfuscator obfuscator, Context context) throws IOException {
        context.obfuscateCurrentToken(obfuscator);
    }

    private static final class Context {
        private final JsonParser parser;
        private final CharSequence text;
        private final Appendable destination;

        private final int textOffset;
        private final int textEnd;
        private int textIndex;

        private String tokenValue;
        private int tokenStart;
        private int tokenEnd;

        private Context(JsonParser parser, CharSequence source, int start, int end, Appendable destination) {
            this.parser = parser;
            this.text = source;
            this.textOffset = start;
            this.textEnd = end;
            this.textIndex = start;
            this.tokenEnd = start;
            this.destination = destination;
        }

        private JsonToken nextToken() throws IOException {
            return parser.nextToken();
        }

        private String currentFieldName() throws IOException {
            return parser.getCurrentName();
        }

        private void appendUntilToken(JsonToken token) throws IOException {
            updateTokenFields(token);
            destination.append(text, textIndex, tokenStart);
        }

        private void obfuscateUntilToken(JsonToken token, Obfuscator obfuscator) throws IOException {
            int originalTokenStart = tokenStart;
            updateTokenFields(token);
            obfuscator.obfuscateText(text, originalTokenStart, tokenEnd, destination);
            textIndex = tokenEnd;
        }

        private void obfuscateCurrentToken(Obfuscator obfuscator) throws IOException {
            obfuscator.obfuscateText(tokenValue, destination);
            textIndex = tokenEnd;
        }

        private void appendRemainder() throws IOException {
            int end = textEnd == -1 ? text.length() : textEnd;
            destination.append(text, textIndex, end);
        }

        private void updateTokenFields(JsonToken token) throws IOException {
            switch (token) {
            case START_ARRAY:
            case END_ARRAY:
            case START_OBJECT:
            case END_OBJECT:
            case VALUE_TRUE:
            case VALUE_FALSE:
            case VALUE_NULL:
                tokenValue = token.asString();
                tokenStart = tokenStart();
                tokenEnd = tokenEnd();
                break;
            case VALUE_STRING:
                tokenValue = parser.getValueAsString();
                tokenStart = stringValueStart();
                tokenEnd = stringValueEnd();
                break;
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                tokenValue = parser.getValueAsString();
                tokenStart = tokenStart();
                tokenEnd = tokenEnd();
                break;
            default:
                throw new IllegalStateException(Messages.JSONObfuscator.unexpectedToken.get(token));
            }
        }

        private int tokenStart() {
            return textOffset + (int) parser.getTokenLocation().getCharOffset();
        }

        private int tokenEnd() {
            return textOffset + (int) parser.getCurrentLocation().getCharOffset();
        }

        private int stringValueStart() {
            int start = tokenStart();
            // start points to the opening ", skip past it
            if (text.charAt(start) == '"') {
                start++;
            }
            return start;
        }

        private int stringValueEnd() {
            int end = tokenEnd();
            // end points to the closing ", skip back past it
            if (text.charAt(end - 1) == '"') {
                end--;
            }
            return end;
        }
    }

    @Override
    public Writer streamTo(Appendable destination) {
        return new CachingObfuscatingWriter(this, destination);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || o.getClass() != getClass()) {
            return false;
        }
        JSONObfuscator other = (JSONObfuscator) o;
        return obfuscators.equals(other.obfuscators)
                && caseInsensitivePropertyNames == other.caseInsensitivePropertyNames
                && obfuscationMode == other.obfuscationMode
                && Objects.equals(malformedJSONWarning, other.malformedJSONWarning);
    }

    @Override
    public int hashCode() {
        return obfuscators.hashCode() ^ obfuscationMode.hashCode() ^ Objects.hashCode(malformedJSONWarning);
    }

    @Override
    @SuppressWarnings("nls")
    public String toString() {
        return getClass().getName()
                + "[obfuscators=" + obfuscators
                + ",caseInsensitivePropertyNames=" + caseInsensitivePropertyNames
                + ",obfuscationMode=" + obfuscationMode
                + ",malformedJSONWarning=" + malformedJSONWarning
                + "]";
    }

    /**
     * Returns a builder that will create {@code JSONObfuscators}.
     *
     * @return A builder that will create {@code JSONObfuscators}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A builder for {@link JSONObfuscator JSONObfuscators}.
     *
     * @author Rob Spoor
     */
    public static final class Builder extends PropertyAwareBuilder<Builder, JSONObfuscator> {

        private ObfuscationMode obfuscationMode = ObfuscationMode.ALL;

        private String malformedJSONWarning = Messages.JSONObfuscator.malformedJSON.text.get();

        private Builder() {
            super();
        }

        /**
         * Sets the obfuscation mode. The default is {@link ObfuscationMode#ALL}.
         *
         * @param obfuscationMode The obfuscation mode.
         * @return This object.
         * @throws NullPointerException If the givne obfuscation mode is {@code null}.
         */
        public Builder withObfuscationMode(ObfuscationMode obfuscationMode) {
            this.obfuscationMode = Objects.requireNonNull(obfuscationMode);
            return this;
        }

        /**
         * Sets the warning to include if a {@link JsonParseException} is thrown.
         * This can be used to override the default message. Use {@code null} to omit the warning.
         *
         * @param warning The warning to include.
         * @return This object.
         */
        public Builder withMalformedJSONWarning(String warning) {
            malformedJSONWarning = warning;
            return this;
        }

        @Override
        public JSONObfuscator build() {
            return new JSONObfuscator(this);
        }
    }
}
