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

import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.appendAtMost;
import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.checkStartAndEnd;
import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.copyTo;
import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.counting;
import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.reader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.github.robtimus.obfuscation.Obfuscator;
import com.github.robtimus.obfuscation.jackson.JSONObfuscator.PropertyConfigurer.ObfuscationMode;
import com.github.robtimus.obfuscation.support.CachingObfuscatingWriter;
import com.github.robtimus.obfuscation.support.CaseSensitivity;
import com.github.robtimus.obfuscation.support.CountingReader;
import com.github.robtimus.obfuscation.support.LimitAppendable;
import com.github.robtimus.obfuscation.support.MapBuilder;

/**
 * An obfuscator that obfuscates JSON properties in {@link CharSequence CharSequences} or the contents of {@link Reader Readers}.
 *
 * @author Rob Spoor
 */
public final class JSONObfuscator extends Obfuscator {

    private static final Logger LOGGER = LoggerFactory.getLogger(JSONObfuscator.class);

    // Note: the following are declared as Set<String> for backwards compatibility with older Jackson versions
    // They are verified through unit tests for correctness and completeness

    // Allow most non-deprecated features, to be as lenient as possible
    @SuppressWarnings("nls")
    static final Set<String> ENABLED_JSON_PARSER_FEATURES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "ALLOW_COMMENTS",
            "ALLOW_YAML_COMMENTS",
            "ALLOW_UNQUOTED_FIELD_NAMES",
            "ALLOW_SINGLE_QUOTES",
            "IGNORE_UNDEFINED"
    )));

    // Disable explicitly
    @SuppressWarnings("nls")
    static final Set<String> DISABLED_JSON_PARSER_FEATURES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            // the source is not ours to close
            "AUTO_CLOSE_SOURCE",
            // don't fail if there are duplicates, to be as lenient as possible
            "STRICT_DUPLICATE_DETECTION",
            // the source is not unnecessary
            "INCLUDE_SOURCE_IN_LOCATION",
            // use Double.parseDouble
            "USE_FAST_DOUBLE_PARSER"
    )));

    // Allow all features, to be as lenient as possible
    @SuppressWarnings("nls")
    static final Set<String> ENABLED_JSON_READ_FEATURES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "ALLOW_JAVA_COMMENTS",
            "ALLOW_YAML_COMMENTS",
            "ALLOW_SINGLE_QUOTES",
            "ALLOW_UNQUOTED_FIELD_NAMES",
            "ALLOW_UNESCAPED_CONTROL_CHARS",
            "ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER",
            "ALLOW_LEADING_ZEROS_FOR_NUMBERS",
            "ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS",
            "ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS",
            "ALLOW_TRAILING_DECIMAL_POINT_FOR_NUMBERS",
            "ALLOW_NON_NUMERIC_NUMBERS",
            "ALLOW_MISSING_VALUES",
            "ALLOW_TRAILING_COMMA"
    )));

    private final Map<String, PropertyConfig> properties;

    private final JsonFactory jsonFactory;

    private final String malformedJSONWarning;

    private final long limit;
    private final String truncatedIndicator;

    private JSONObfuscator(ObfuscatorBuilder builder) {
        properties = builder.properties();

        jsonFactory = createJsonFactory();

        malformedJSONWarning = builder.malformedJSONWarning;

        limit = builder.limit;
        truncatedIndicator = builder.truncatedIndicator;
    }

    private static JsonFactory createJsonFactory() {
        JsonFactoryBuilder builder = new JsonFactoryBuilder();
        for (JsonReadFeature feature : JsonReadFeature.values()) {
            String featureName = feature.name();
            if (ENABLED_JSON_READ_FEATURES.contains(featureName)) {
                builder = builder.enable(feature);
            }
        }

        JsonFactory factory = builder.build();
        for (JsonParser.Feature feature : JsonParser.Feature.values()) {
            String featureName = feature.name();
            if (ENABLED_JSON_PARSER_FEATURES.contains(featureName)) {
                factory.enable(feature);
            } else if (DISABLED_JSON_PARSER_FEATURES.contains(featureName)) {
                factory.disable(feature);
            }
        }
        return factory;
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
        Reader reader = reader(s, start, end);
        LimitAppendable appendable = appendAtMost(destination, limit);
        obfuscateText(reader, new Source.OfCharSequence(s), start, end, appendable);
        if (appendable.limitExceeded() && truncatedIndicator != null) {
            destination.append(String.format(truncatedIndicator, end - start));
        }
    }

    @Override
    public void obfuscateText(Reader input, Appendable destination) throws IOException {
        @SuppressWarnings("resource")
        CountingReader countingReader = counting(input);
        Source.OfReader source = new Source.OfReader(countingReader, LOGGER);
        @SuppressWarnings("resource")
        Reader reader = copyTo(countingReader, source);
        LimitAppendable appendable = appendAtMost(destination, limit);
        obfuscateText(reader, source, 0, -1, appendable);
        if (appendable.limitExceeded() && truncatedIndicator != null) {
            destination.append(String.format(truncatedIndicator, countingReader.count()));
        }
    }

    private void obfuscateText(Reader input, Source source, int start, int end, LimitAppendable destination) throws IOException {
        // closing parser will not close input because it's considered to be unmanaged and Feature.AUTO_CLOSE_SOURCE is disabled explicitly
        try (ObfuscatingJsonParser parser = new ObfuscatingJsonParser(jsonFactory.createParser(input), source, start, end, destination, properties)) {
            try {
                while (parser.nextToken() != null && !destination.limitExceeded()) {
                    // do nothing; the parser will take care of obfuscation
                }
                parser.appendRemainder();
            } catch (JsonParseException e) {
                LOGGER.warn(Messages.JSONObfuscator.malformedJSON.warning(), e);
                if (malformedJSONWarning != null) {
                    destination.append(malformedJSONWarning);
                }
            }
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
                && Objects.equals(malformedJSONWarning, other.malformedJSONWarning)
                && limit == other.limit
                && Objects.equals(truncatedIndicator, other.truncatedIndicator);
    }

    @Override
    public int hashCode() {
        return properties.hashCode() ^ Objects.hashCode(malformedJSONWarning) ^ Long.hashCode(limit) ^ Objects.hashCode(truncatedIndicator);
    }

    @Override
    @SuppressWarnings("nls")
    public String toString() {
        return getClass().getName()
                + "[properties=" + properties
                + ",malformedJSONWarning=" + malformedJSONWarning
                + ",limit=" + limit
                + ",truncatedIndicator=" + truncatedIndicator
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
    public interface Builder {

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
        PropertyConfigurer withProperty(String property, Obfuscator obfuscator);

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
        PropertyConfigurer withProperty(String property, Obfuscator obfuscator, CaseSensitivity caseSensitivity);

        /**
         * Sets the default case sensitivity for new properties to {@link CaseSensitivity#CASE_SENSITIVE}. This is the default setting.
         * <p>
         * Note that this will not change the case sensitivity of any property that was already added.
         *
         * @return This object.
         */
        Builder caseSensitiveByDefault();

        /**
         * Sets the default case sensitivity for new properties to {@link CaseSensitivity#CASE_INSENSITIVE}.
         * <p>
         * Note that this will not change the case sensitivity of any property that was already added.
         *
         * @return This object.
         */
        Builder caseInsensitiveByDefault();

        /**
         * Indicates that by default properties will not be obfuscated if they are JSON objects or arrays.
         * This method is shorthand for calling both {@link #excludeObjectsByDefault()} and {@link #excludeArraysByDefault()}.
         * <p>
         * Note that this will not change what will be obfuscated for any property that was already added.
         *
         * @return This object.
         */
        default Builder scalarsOnlyByDefault() {
            return excludeObjectsByDefault()
                    .excludeArraysByDefault();
        }

        /**
         * Indicates that by default properties will not be obfuscated if they are JSON objects.
         * This method is an alias for {@link #forObjectsByDefault(ObfuscationMode)} in combination with {@link ObfuscationMode#EXCLUDE}.
         * <p>
         * Note that this will not change what will be obfuscated for any property that was already added.
         *
         * @return This object.
         */
        default Builder excludeObjectsByDefault() {
            return forObjectsByDefault(ObfuscationMode.EXCLUDE);
        }

        /**
         * Indicates that by default properties will not be obfuscated if they are JSON arrays.
         * This method is an alias for {@link #forArraysByDefault(ObfuscationMode)} in combination with {@link ObfuscationMode#EXCLUDE}.
         * <p>
         * Note that this will not change what will be obfuscated for any property that was already added.
         *
         * @return This object.
         */
        default Builder excludeArraysByDefault() {
            return forArraysByDefault(ObfuscationMode.EXCLUDE);
        }

        /**
         * Indicates that by default properties will be obfuscated if they are JSON objects or arrays (default).
         * This method is shorthand for calling both {@link #includeObjectsByDefault()} and {@link #includeArraysByDefault()}.
         * <p>
         * Note that this will not change what will be obfuscated for any property that was already added.
         *
         * @return This object.
         */
        default Builder allByDefault() {
            return includeObjectsByDefault()
                    .includeArraysByDefault();
        }

        /**
         * Indicates that by default properties will be obfuscated if they are JSON objects (default).
         * This method is an alias for {@link #forObjectsByDefault(ObfuscationMode)} in combination with {@link ObfuscationMode#OBFUSCATE}.
         * <p>
         * Note that this will not change what will be obfuscated for any property that was already added.
         *
         * @return This object.
         */
        default Builder includeObjectsByDefault() {
            return forObjectsByDefault(ObfuscationMode.OBFUSCATE);
        }

        /**
         * Indicates that by default properties will be obfuscated if they are JSON arrays (default).
         * This method is an alias for {@link #forArraysByDefault(ObfuscationMode)} in combination with {@link ObfuscationMode#OBFUSCATE}.
         * <p>
         * Note that this will not change what will be obfuscated for any property that was already added.
         *
         * @return This object.
         */
        default Builder includeArraysByDefault() {
            return forArraysByDefault(ObfuscationMode.OBFUSCATE);
        }

        /**
         * Indicates how to handle properties if they are JSON objects. The default is {@link ObfuscationMode#OBFUSCATE}.
         * This can be overridden per property using {@link PropertyConfigurer#forObjects(ObfuscationMode)}
         * <p>
         * Note that this will not change what will be obfuscated for any property that was already added.
         *
         * @param obfuscationMode The obfuscation mode that determines how to handle properties.
         * @return This object.
         * @throws NullPointerException If the given obfuscation mode is {@code null}.
         * @since 1.3
         */
        Builder forObjectsByDefault(ObfuscationMode obfuscationMode);

        /**
         * Indicates how to handle properties if they are JSON arrays. The default is {@link ObfuscationMode#OBFUSCATE}.
         * This can be overridden per property using {@link PropertyConfigurer#forArrays(ObfuscationMode)}
         * <p>
         * Note that this will not change what will be obfuscated for any property that was already added.
         *
         * @param obfuscationMode The obfuscation mode that determines how to handle properties.
         * @return This object.
         * @throws NullPointerException If the given obfuscation mode is {@code null}.
         * @since 1.3
         */
        Builder forArraysByDefault(ObfuscationMode obfuscationMode);

        /**
         * Sets the warning to include if a {@link JsonParseException} is thrown.
         * This can be used to override the default message. Use {@code null} to omit the warning.
         *
         * @param warning The warning to include.
         * @return This object.
         */
        Builder withMalformedJSONWarning(String warning);

        /**
         * Sets the limit for the obfuscated result.
         *
         * @param limit The limit to use.
         * @return An object that can be used to configure the handling when the obfuscated result exceeds a pre-defined limit,
         *         or continue building {@link JSONObfuscator JSONObfuscators}.
         * @throws IllegalArgumentException If the given limit is negative.
         * @since 1.1
         */
        LimitConfigurer limitTo(long limit);

        /**
         * This method allows the application of a function to this builder.
         * <p>
         * Any exception thrown by the function will be propagated to the caller.
         *
         * @param <R> The type of the result of the function.
         * @param f The function to apply.
         * @return The result of applying the function to this builder.
         */
        default <R> R transform(Function<? super Builder, ? extends R> f) {
            return f.apply(this);
        }

        /**
         * Creates a new {@code JSONObfuscator} with the properties and obfuscators added to this builder.
         *
         * @return The created {@code JSONObfuscator}.
         */
        JSONObfuscator build();
    }

    /**
     * An object that can be used to configure a property that should be obfuscated.
     *
     * @author Rob Spoor
     */
    public interface PropertyConfigurer extends Builder {

        /**
         * Indicates that properties with the current name will not be obfuscated if they are JSON objects or arrays.
         * This method is shorthand for calling both {@link #excludeObjects()} and {@link #excludeArrays()}.
         *
         * @return This object.
         */
        default PropertyConfigurer scalarsOnly() {
            return excludeObjects()
                    .excludeArrays();
        }

        /**
         * Indicates that properties with the current name will not be obfuscated if they are JSON objects.
         * This method is an alias for {@link #forObjects(ObfuscationMode)} in combination with {@link ObfuscationMode#EXCLUDE}.
         *
         * @return This object.
         */
        default PropertyConfigurer excludeObjects() {
            return forObjects(ObfuscationMode.EXCLUDE);
        }

        /**
         * Indicates that properties with the current name will not be obfuscated if they are JSON arrays.
         * This method is an alias for {@link #forArrays(ObfuscationMode)} in combination with {@link ObfuscationMode#EXCLUDE}.
         *
         * @return This object.
         */
        default PropertyConfigurer excludeArrays() {
            return forArrays(ObfuscationMode.EXCLUDE);
        }

        /**
         * Indicates that properties with the current name will be obfuscated if they are JSON objects or arrays.
         * This method is shorthand for calling both {@link #includeObjects()} and {@link #includeArrays()}.
         *
         * @return This object.
         */
        default PropertyConfigurer all() {
            return includeObjects()
                    .includeArrays();
        }

        /**
         * Indicates that properties with the current name will be obfuscated if they are JSON objects.
         * This method is an alias for {@link #forObjects(ObfuscationMode)} in combination with {@link ObfuscationMode#OBFUSCATE}.
         *
         * @return This object.
         */
        default PropertyConfigurer includeObjects() {
            return forObjects(ObfuscationMode.OBFUSCATE);
        }

        /**
         * Indicates that properties with the current name will be obfuscated if they are JSON arrays.
         * This method is an alias for {@link #forArrays(ObfuscationMode)} in combination with {@link ObfuscationMode#OBFUSCATE}.
         *
         * @return This object.
         */
        default PropertyConfigurer includeArrays() {
            return forArrays(ObfuscationMode.OBFUSCATE);
        }

        /**
         * Indicates how to handle properties if they are JSON objects. The default is {@link ObfuscationMode#OBFUSCATE}.
         *
         * @param obfuscationMode The obfuscation mode that determines how to handle properties.
         * @return This object.
         * @throws NullPointerException If the given obfuscation mode is {@code null}.
         * @since 1.3
         */
        PropertyConfigurer forObjects(ObfuscationMode obfuscationMode);

        /**
         * Indicates how to handle properties if they are JSON arrays. The default is {@link ObfuscationMode#OBFUSCATE}.
         *
         * @param obfuscationMode The obfuscation mode that determines how to handle properties.
         * @return This object.
         * @throws NullPointerException If the given obfuscation mode is {@code null}.
         * @since 1.3
         */
        PropertyConfigurer forArrays(ObfuscationMode obfuscationMode);

        /**
         * The possible ways to deal with nested objects and arrays.
         *
         * @author Rob Spoor
         * @since 1.3
         */
        enum ObfuscationMode {
            /** Don't obfuscate nested objects or arrays, but instead traverse into them. **/
            EXCLUDE,

            /** Obfuscate nested objects and arrays completely. **/
            OBFUSCATE,

            /** Don't obfuscate nested objects or arrays, but use the obfuscator for all nested scalar properties. **/
            INHERIT,

            /**
             * Don't obfuscate nested objects or arrays, but use the obfuscator for all nested scalar properties.
             * If a nested property has its own obfuscator defined this will be used instead.
             **/
            INHERIT_OVERRIDABLE,
        }
    }

    /**
     * An object that can be used to configure handling when the obfuscated result exceeds a pre-defined limit.
     *
     * @author Rob Spoor
     * @since 1.1
     */
    public interface LimitConfigurer extends Builder {

        /**
         * Sets the indicator to use when the obfuscated result is truncated due to the limit being exceeded.
         * There can be one place holder for the total number of characters. Defaults to {@code ... (total: %d)}.
         * Use {@code null} to omit the indicator.
         *
         * @param pattern The pattern to use as indicator.
         * @return This object.
         */
        LimitConfigurer withTruncatedIndicator(String pattern);
    }

    private static final class ObfuscatorBuilder implements PropertyConfigurer, LimitConfigurer {

        private final MapBuilder<PropertyConfig> properties;

        private String malformedJSONWarning;

        private long limit;
        private String truncatedIndicator;

        // default settings
        private ObfuscationMode forObjectsByDefault;
        private ObfuscationMode forArraysByDefault;

        // per property settings
        private String property;
        private Obfuscator obfuscator;
        private CaseSensitivity caseSensitivity;
        private ObfuscationMode forObjects;
        private ObfuscationMode forArrays;

        private ObfuscatorBuilder() {
            properties = new MapBuilder<>();

            malformedJSONWarning = Messages.JSONObfuscator.malformedJSON.text();

            limit = Long.MAX_VALUE;
            truncatedIndicator = "... (total: %d)"; //$NON-NLS-1$

            forObjectsByDefault = ObfuscationMode.OBFUSCATE;
            forArraysByDefault = ObfuscationMode.OBFUSCATE;
        }

        @Override
        public PropertyConfigurer withProperty(String property, Obfuscator obfuscator) {
            addLastProperty();

            properties.testEntry(property);

            this.property = property;
            this.obfuscator = obfuscator;
            this.caseSensitivity = null;
            this.forObjects = forObjectsByDefault;
            this.forArrays = forArraysByDefault;

            return this;
        }

        @Override
        public PropertyConfigurer withProperty(String property, Obfuscator obfuscator, CaseSensitivity caseSensitivity) {
            addLastProperty();

            properties.testEntry(property, caseSensitivity);

            this.property = property;
            this.obfuscator = obfuscator;
            this.caseSensitivity = caseSensitivity;
            this.forObjects = forObjectsByDefault;
            this.forArrays = forArraysByDefault;

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
        public Builder forObjectsByDefault(ObfuscationMode obfuscationMode) {
            forObjectsByDefault = Objects.requireNonNull(obfuscationMode);
            return this;
        }

        @Override
        public Builder forArraysByDefault(ObfuscationMode obfuscationMode) {
            forArraysByDefault = Objects.requireNonNull(obfuscationMode);
            return this;
        }

        @Override
        public PropertyConfigurer forObjects(ObfuscationMode obfuscationMode) {
            forObjects = Objects.requireNonNull(obfuscationMode);
            return this;
        }

        @Override
        public PropertyConfigurer forArrays(ObfuscationMode obfuscationMode) {
            forArrays = Objects.requireNonNull(obfuscationMode);
            return this;
        }

        @Override
        public Builder withMalformedJSONWarning(String warning) {
            malformedJSONWarning = warning;
            return this;
        }

        @Override
        public LimitConfigurer limitTo(long limit) {
            if (limit < 0) {
                throw new IllegalArgumentException(limit + " < 0"); //$NON-NLS-1$
            }
            this.limit = limit;
            return this;
        }

        @Override
        public LimitConfigurer withTruncatedIndicator(String pattern) {
            this.truncatedIndicator = pattern;
            return this;
        }

        private Map<String, PropertyConfig> properties() {
            return properties.build();
        }

        private void addLastProperty() {
            if (property != null) {
                PropertyConfig propertyConfig = new PropertyConfig(obfuscator, forObjects, forArrays);
                if (caseSensitivity != null) {
                    properties.withEntry(property, propertyConfig, caseSensitivity);
                } else {
                    properties.withEntry(property, propertyConfig);
                }
            }

            property = null;
            obfuscator = null;
            caseSensitivity = null;
            forObjects = forObjectsByDefault;
            forArrays = forArraysByDefault;
        }

        @Override
        public JSONObfuscator build() {
            addLastProperty();

            return new JSONObfuscator(this);
        }
    }
}
