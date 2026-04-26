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
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.robtimus.obfuscation.Obfuscator;
import com.github.robtimus.obfuscation.jackson.JSONObfuscator.PropertyConfigurer.ObfuscationMode;
import com.github.robtimus.obfuscation.support.CachingObfuscatingWriter;
import com.github.robtimus.obfuscation.support.CaseSensitivity;
import com.github.robtimus.obfuscation.support.CountingReader;
import com.github.robtimus.obfuscation.support.LimitAppendable;
import com.github.robtimus.obfuscation.support.MapBuilder;
import tools.jackson.core.exc.StreamReadException;

/**
 * An obfuscator that obfuscates JSON properties in {@link CharSequence CharSequences} or the contents of {@link Reader Readers}.
 * <p>
 * This class supports both Jackson 2 and Jackson 3, depending on the available runtime dependencies. It prefers Jackson 3 if both are available.
 * An explicit version can be defined using {@link Builder#withJacksonVersion(JacksonVersion)}.
 *
 * @author Rob Spoor
 */
public abstract class JSONObfuscator extends Obfuscator {

    static final Logger LOGGER = LoggerFactory.getLogger(JSONObfuscator.class);

    final Map<String, PropertyConfig> properties;

    final String malformedJSONWarning;

    final long limit;
    final String truncatedIndicator;

    JSONObfuscator(ObfuscatorBuilder builder) {
        properties = builder.properties();

        malformedJSONWarning = builder.malformedJSONWarning;

        limit = builder.limit;
        truncatedIndicator = builder.truncatedIndicator;
    }

    @Override
    public final CharSequence obfuscateText(CharSequence s, int start, int end) {
        checkStartAndEnd(s, start, end);
        StringBuilder sb = new StringBuilder(end - start);
        obfuscateText(s, start, end, sb);
        return sb.toString();
    }

    @Override
    public final void obfuscateText(CharSequence s, int start, int end, Appendable destination) throws IOException {
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
    public final void obfuscateText(Reader input, Appendable destination) throws IOException {
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

    abstract void obfuscateText(Reader input, Source source, int start, int end, LimitAppendable destination) throws IOException;

    @Override
    public final Writer streamTo(Appendable destination) {
        return new CachingObfuscatingWriter(this, destination);
    }

    @Override
    public final boolean equals(Object o) {
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
    public final int hashCode() {
        return properties.hashCode() ^ Objects.hashCode(malformedJSONWarning) ^ Long.hashCode(limit) ^ Objects.hashCode(truncatedIndicator);
    }

    @Override
    @SuppressWarnings("nls")
    public final String toString() {
        return JSONObfuscator.class.getName()
                + "[properties=" + properties
                + ",malformedJSONWarning=" + malformedJSONWarning
                + ",limit=" + limit
                + ",truncatedIndicator=" + truncatedIndicator
                + ",jacksonVersion=" + jacksonVersion()
                + "]";
    }

    abstract JacksonVersion jacksonVersion();

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
         * Sets the warning to include if a {@link StreamReadException} is thrown.
         * This can be used to override the default message. Use {@code null} to omit the warning.
         *
         * @param warning The warning to include.
         * @return This object.
         */
        Builder withMalformedJSONWarning(String warning);

        /**
         * Sets the Jackson version to use. This method should only be called if more than one Jackson version is available.
         * <p>
         * If a version is set that is not {@linkplain JacksonVersion#isAvailable() available}, calling {@link #build()} will fail.
         *
         * @param jacksonVersion The Jackson version to use.
         * @return This object.
         * @throws NullPointerException If the given Jackson version is {@code null}.
         * @since 2.0
         */
        Builder withJacksonVersion(JacksonVersion jacksonVersion);

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

    static final class ObfuscatorBuilder implements PropertyConfigurer, LimitConfigurer {

        private final MapBuilder<PropertyConfig> properties;

        private String malformedJSONWarning;

        private long limit;
        private String truncatedIndicator;

        private JacksonVersion jacksonVersion;

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
        public Builder withJacksonVersion(JacksonVersion jacksonVersion) {
            this.jacksonVersion = Objects.requireNonNull(jacksonVersion);
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

            return switch (determineJacksonVersion()) {
                case JACKSON2 -> new Jackson2Obfuscator(this);
                case JACKSON3 -> new Jackson3Obfuscator(this);
            };
        }

        private JacksonVersion determineJacksonVersion() {
            if (jacksonVersion != null) {
                return jacksonVersion;
            }
            return Arrays.stream(JacksonVersion.values())
                    .filter(JacksonVersion::isAvailable)
                    .max(Comparator.naturalOrder())
                    .orElseThrow();
        }
    }
}
