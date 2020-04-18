/*
 * JSONObfuscator.java
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

import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.checkStartAndEnd;
import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.copyTo;
import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.discardAll;
import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.reader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.github.robtimus.obfuscation.Obfuscator;
import com.github.robtimus.obfuscation.support.CachingObfuscatingWriter;
import com.github.robtimus.obfuscation.support.CaseSensitivity;
import com.github.robtimus.obfuscation.support.MapBuilder;

/**
 * An obfuscator that obfuscates JSON properties in {@link CharSequence CharSequences} or the contents of {@link Reader Readers}.
 *
 * @author Rob Spoor
 */
public final class JSONObfuscator extends Obfuscator {

    private static final Logger LOGGER = LoggerFactory.getLogger(JSONObfuscator.class);

    private final Map<String, PropertyConfig> properties;

    private final JsonFactory jsonFactory;

    private final String malformedJSONWarning;

    private JSONObfuscator(ObfuscatorBuilder builder) {
        properties = builder.properties();

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
        // closing parser will not close input because it's considered to be unmanaged and Feature.AUTO_CLOSE_SOURCE is disabled explicitly
        try (JsonParser parser = jsonFactory.createParser(input)) {
            Context context = new Context(parser, s, start, end, destination);
            try {
                JsonToken token;
                while ((token = context.nextToken()) != null) {
                    if (token == JsonToken.FIELD_NAME) {
                        String property = context.currentFieldName();
                        PropertyConfig propertyConfig = properties.get(property);
                        if (propertyConfig != null) {
                            obfuscateProperty(propertyConfig, context);
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
    }

    private void obfuscateProperty(PropertyConfig propertyConfig, Context context) throws IOException {
        JsonToken token = context.nextToken();
        switch (token) {
        case START_ARRAY:
            if (!propertyConfig.obfuscateArrays) {
                // there is an obfuscator for the array property, but the obfuscation mode prohibits obfuscating arrays;
                // abort and continue with the next property
                return;
            }
            context.appendUntilToken(token);
            obfuscateNested(propertyConfig.obfuscator, context, JsonToken.START_ARRAY, JsonToken.END_ARRAY);
            break;
        case START_OBJECT:
            if (!propertyConfig.obfuscateObjects) {
                // there is an obfuscator for the object property, but the obfuscation mode prohibits obfuscating objects;
                // abort and continue with the next property
                return;
            }
            context.appendUntilToken(token);
            obfuscateNested(propertyConfig.obfuscator, context, JsonToken.START_OBJECT, JsonToken.END_OBJECT);
            break;
        case VALUE_STRING:
        case VALUE_NUMBER_INT:
        case VALUE_NUMBER_FLOAT:
        case VALUE_TRUE:
        case VALUE_FALSE:
        case VALUE_NULL:
            context.appendUntilToken(token);
            obfuscateScalar(propertyConfig.obfuscator, context);
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
            textIndex = tokenStart;
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
            textIndex = end;
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
        return properties.equals(other.properties)
                && Objects.equals(malformedJSONWarning, other.malformedJSONWarning);
    }

    @Override
    public int hashCode() {
        return properties.hashCode() ^ Objects.hashCode(malformedJSONWarning);
    }

    @Override
    @SuppressWarnings("nls")
    public String toString() {
        return getClass().getName()
                + "[properties=" + properties
                + ",malformedJSONWarning=" + malformedJSONWarning
                + "]";
    }

    /**
     * Returns a builder that will create {@code JSONObfuscators}.
     *
     * @return A builder that will create {@code JSONObfuscators}.
     */
    public static Builder builder() {
        return new ObfuscatorBuilder();
    }

    /**
     * A builder for {@link JSONObfuscator JSONObfuscators}.
     *
     * @author Rob Spoor
     */
    public abstract static class Builder {

        /**
         * Adds a property to obfuscate.
         * This method is an alias for {@link #withProperty(String, Obfuscator, CaseSensitivity)} with the last specified default case sensitivity
         * using {@link #caseSensitiveByDefault()} or {@link #caseInsensitiveByDefault()}. The default is {@link CaseSensitivity#CASE_SENSITIVE}.
         *
         * @param property The name of the property.
         * @param obfuscator The obfuscator to use for obfuscating the property.
         * @return An object that can be used to configure the property, or continue building {@link JSONObfuscator JSONObfuscators}.
         * @throws NullPointerException If the given property name or obfuscator is {@code null}.
         * @throws IllegalArgumentException If a property with the same name and the same case sensitivity was already added.
         */
        public abstract PropertyConfigurer withProperty(String property, Obfuscator obfuscator);

        /**
         * Adds a property to obfuscate.
         *
         * @param property The name of the property.
         * @param obfuscator The obfuscator to use for obfuscating the property.
         * @param caseSensitivity The case sensitivity for the property.
         * @return An object that can be used to configure the property, or continue building {@link JSONObfuscator JSONObfuscators}.
         * @throws NullPointerException If the given property name, obfuscator or case sensitivity is {@code null}.
         * @throws IllegalArgumentException If a property with the same name and the same case sensitivity was already added.
         */
        public abstract PropertyConfigurer withProperty(String property, Obfuscator obfuscator, CaseSensitivity caseSensitivity);

        /**
         * Sets the default case sensitivity for new properties to {@link CaseSensitivity#CASE_SENSITIVE}. This is the default setting.
         * <p>
         * Note that this will not change the case sensitivity of any property that was already added.
         *
         * @return This object.
         */
        public abstract Builder caseSensitiveByDefault();

        /**
         * Sets the default case sensitivity for new entries to {@link CaseSensitivity#CASE_INSENSITIVE}.
         * <p>
         * Note that this will not change the case sensitivity of any entry that was already added.
         *
         * @return This object.
         */
        public abstract Builder caseInsensitiveByDefault();

        /**
         * Indicates that by default properties will not be obfuscated if they are JSON objects or arrays.
         * This method is shorthand for calling both {@link #excludeObjectsByDefault()} and {@link #excludeArraysByDefault()}.
         * <p>
         * Note that this will not change what will be obfuscated for any property that was already added.
         *
         * @return This object.
         */
        public Builder scalarsOnlyByDefault() {
            return excludeObjectsByDefault()
                    .excludeArraysByDefault();
        }

        /**
         * Indicates that by default properties will not be obfuscated if they are JSON objects.
         * This can be overridden per property using {@link PropertyConfigurer#excludeObjects()}
         * <p>
         * Note that this will not change what will be obfuscated for any property that was already added.
         *
         * @return This object.
         */
        public abstract Builder excludeObjectsByDefault();

        /**
         * Indicates that by default properties will not be obfuscated if they are JSON arrays.
         * This can be overridden per property using {@link PropertyConfigurer#excludeArrays()}
         * <p>
         * Note that this will not change what will be obfuscated for any property that was already added.
         *
         * @return This object.
         */
        public abstract Builder excludeArraysByDefault();

        /**
         * Indicates that by default properties will be obfuscated if they are JSON objects or arrays (default).
         * This method is shorthand for calling both {@link #includeObjectsByDefault()} and {@link #includeArraysByDefault()}.
         * <p>
         * Note that this will not change what will be obfuscated for any property that was already added.
         *
         * @return This object.
         */
        public Builder allByDefault() {
            return includeObjectsByDefault()
                    .includeArraysByDefault();
        }

        /**
         * Indicates that by default properties will be obfuscated if they are JSON objects (default).
         * This can be overridden per property using {@link PropertyConfigurer#excludeObjects()}
         * <p>
         * Note that this will not change what will be obfuscated for any property that was already added.
         *
         * @return This object.
         */
        public abstract Builder includeObjectsByDefault();

        /**
         * Indicates that by default properties will be obfuscated if they are JSON arrays (default).
         * This can be overridden per property using {@link PropertyConfigurer#excludeArrays()}
         * <p>
         * Note that this will not change what will be obfuscated for any property that was already added.
         *
         * @return This object.
         */
        public abstract Builder includeArraysByDefault();

        /**
         * Sets the warning to include if a {@link JsonParseException} is thrown.
         * This can be used to override the default message. Use {@code null} to omit the warning.
         *
         * @param warning The warning to include.
         * @return This object.
         */
        public abstract Builder withMalformedJSONWarning(String warning);

        /**
         * This method allows the application of a function to this builder.
         * <p>
         * Any exception thrown by the function will be propagated to the caller.
         *
         * @param <R> The type of the result of the function.
         * @param f The function to apply.
         * @return The result of applying the function to this builder.
         */
        public <R> R transform(Function<? super Builder, ? extends R> f) {
            return f.apply(this);
        }

        /**
         * Creates a new {@code JSONObfuscator} with the properties and obfuscators added to this builder.
         *
         * @return The created {@code JSONObfuscator}.
         */
        public abstract JSONObfuscator build();
    }

    /**
     * An object that can be used to configure a property that should be obfuscated.
     *
     * @author Rob Spoor
     */
    public abstract static class PropertyConfigurer extends Builder {

        /**
         * Indicates that properties with the current name will not be obfuscated if they are JSON objects or arrays.
         * This method is shorthand for calling both {@link #excludeObjects()} and {@link #excludeArrays()}.
         *
         * @return An object that can be used to configure the property, or continue building {@link JSONObfuscator JSONObfuscators}.
         */
        public PropertyConfigurer scalarsOnly() {
            return excludeObjects()
                    .excludeArrays();
        }

        /**
         * Indicates that properties with the current name will not be obfuscated if they are JSON objects.
         *
         * @return An object that can be used to configure the property, or continue building {@link JSONObfuscator JSONObfuscators}.
         */
        public abstract PropertyConfigurer excludeObjects();

        /**
         * Indicates that properties with the current name will not be obfuscated if they are JSON arrays.
         *
         * @return An object that can be used to configure the property, or continue building {@link JSONObfuscator JSONObfuscators}.
         */
        public abstract PropertyConfigurer excludeArrays();

        /**
         * Indicates that properties with the current name will be obfuscated if they are JSON objects or arrays.
         * This method is shorthand for calling both {@link #includeObjects()} and {@link #includeArrays()}.
         *
         * @return An object that can be used to configure the property, or continue building {@link JSONObfuscator JSONObfuscators}.
         */
        public PropertyConfigurer all() {
            return includeObjects()
                    .includeArrays();
        }

        /**
         * Indicates that properties with the current name will be obfuscated if they are JSON objects.
         *
         * @return An object that can be used to configure the property, or continue building {@link JSONObfuscator JSONObfuscators}.
         */
        public abstract PropertyConfigurer includeObjects();

        /**
         * Indicates that properties with the current name will be obfuscated if they are JSON arrays.
         *
         * @return An object that can be used to configure the property, or continue building {@link JSONObfuscator JSONObfuscators}.
         */
        public abstract PropertyConfigurer includeArrays();
    }

    private static final class ObfuscatorBuilder extends PropertyConfigurer {

        private final MapBuilder<PropertyConfig> properties;

        private String malformedJSONWarning;

        // default settings
        private boolean obfuscateObjectsByDefault;
        private boolean obfuscateArraysByDefault;

        // per property settings
        private String property;
        private Obfuscator obfuscator;
        private CaseSensitivity caseSensitivity;
        private boolean obfuscateObjects;
        private boolean obfuscateArrays;

        private ObfuscatorBuilder() {
            properties = new MapBuilder<>();
            malformedJSONWarning = Messages.JSONObfuscator.malformedJSON.text.get();

            obfuscateObjectsByDefault = true;
            obfuscateArraysByDefault = true;
        }

        @Override
        public PropertyConfigurer withProperty(String property, Obfuscator obfuscator) {
            addLastProperty();

            properties.testEntry(property);

            this.property = property;
            this.obfuscator = obfuscator;
            this.caseSensitivity = null;
            this.obfuscateObjects = obfuscateObjectsByDefault;
            this.obfuscateArrays = obfuscateArraysByDefault;

            return this;
        }

        @Override
        public PropertyConfigurer withProperty(String property, Obfuscator obfuscator, CaseSensitivity caseSensitivity) {
            addLastProperty();

            properties.testEntry(property, caseSensitivity);

            this.property = property;
            this.obfuscator = obfuscator;
            this.caseSensitivity = caseSensitivity;
            this.obfuscateObjects = obfuscateObjectsByDefault;
            this.obfuscateArrays = obfuscateArraysByDefault;

            return this;
        }

        @Override
        public Builder caseSensitiveByDefault() {
            properties.caseSensitiveByDefault();
            return this;
        }

        @Override
        public Builder caseInsensitiveByDefault() {
            properties.caseInsensitiveByDefault();
            return this;
        }

        @Override
        public Builder excludeObjectsByDefault() {
            obfuscateObjectsByDefault = false;
            return this;
        }

        @Override
        public Builder excludeArraysByDefault() {
            obfuscateArraysByDefault = false;
            return this;
        }

        @Override
        public Builder includeObjectsByDefault() {
            obfuscateObjectsByDefault = true;
            return this;
        }

        @Override
        public Builder includeArraysByDefault() {
            obfuscateArraysByDefault = true;
            return this;
        }

        @Override
        public PropertyConfigurer excludeObjects() {
            obfuscateObjects = false;
            return this;
        }

        @Override
        public PropertyConfigurer excludeArrays() {
            obfuscateArrays = false;
            return this;
        }

        @Override
        public PropertyConfigurer includeObjects() {
            obfuscateObjects = true;
            return this;
        }

        @Override
        public PropertyConfigurer includeArrays() {
            obfuscateArrays = true;
            return this;
        }

        @Override
        public Builder withMalformedJSONWarning(String warning) {
            malformedJSONWarning = warning;
            return this;
        }

        private Map<String, PropertyConfig> properties() {
            return properties.build();
        }

        private void addLastProperty() {
            if (property != null) {
                PropertyConfig propertyConfig = new PropertyConfig(obfuscator, obfuscateObjects, obfuscateArrays);
                if (caseSensitivity != null) {
                    properties.withEntry(property, propertyConfig, caseSensitivity);
                } else {
                    properties.withEntry(property, propertyConfig);
                }
            }

            property = null;
            obfuscator = null;
            caseSensitivity = null;
            obfuscateObjects = obfuscateObjectsByDefault;
            obfuscateArrays = obfuscateArraysByDefault;
        }

        @Override
        public JSONObfuscator build() {
            addLastProperty();

            return new JSONObfuscator(this);
        }
    }

    private static final class PropertyConfig {

        private final Obfuscator obfuscator;
        private final boolean obfuscateObjects;
        private final boolean obfuscateArrays;

        private PropertyConfig(Obfuscator obfuscator, boolean obfuscateObjects, boolean obfuscateArrays) {
            this.obfuscator = Objects.requireNonNull(obfuscator);
            this.obfuscateObjects = obfuscateObjects;
            this.obfuscateArrays = obfuscateArrays;
        }

        @Override
        public boolean equals(Object o) {
            // null and different types should not occur
            PropertyConfig other = (PropertyConfig) o;
            return obfuscator.equals(other.obfuscator)
                    && obfuscateObjects == other.obfuscateObjects
                    && obfuscateArrays == other.obfuscateArrays;
        }

        @Override
        public int hashCode() {
            return obfuscator.hashCode() ^ Boolean.hashCode(obfuscateObjects) ^ Boolean.hashCode(obfuscateArrays);
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return "[obfuscator=" + obfuscator
                    + ",obfuscateObjects=" + obfuscateObjects
                    + ",obfuscateArrays=" + obfuscateArrays
                    + "]";
        }
    }
}
