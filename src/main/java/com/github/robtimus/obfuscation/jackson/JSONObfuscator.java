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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.robtimus.obfuscation.Obfuscator;
import com.github.robtimus.obfuscation.jackson.JSONObfuscator.PropertyConfigurer.ObfuscationMode;
import com.github.robtimus.obfuscation.jackson.JSONObfuscator.PropertyConfigurer.ValueType;
import com.github.robtimus.obfuscation.jackson.JSONObfuscator.PropertyPath.Matcher;
import com.github.robtimus.obfuscation.jackson.PropertyPaths.AndMatcher;
import com.github.robtimus.obfuscation.jackson.PropertyPaths.ContainsAtIgnoreCaseMatcher;
import com.github.robtimus.obfuscation.jackson.PropertyPaths.ContainsAtMatcher;
import com.github.robtimus.obfuscation.jackson.PropertyPaths.EndsWithIgnoreCaseMatcher;
import com.github.robtimus.obfuscation.jackson.PropertyPaths.EndsWithMatcher;
import com.github.robtimus.obfuscation.jackson.PropertyPaths.HasLengthAtLeastMatcher;
import com.github.robtimus.obfuscation.jackson.PropertyPaths.HasLengthAtMostMatcher;
import com.github.robtimus.obfuscation.jackson.PropertyPaths.HasLengthGreaterThanMatcher;
import com.github.robtimus.obfuscation.jackson.PropertyPaths.HasLengthLessThanMatcher;
import com.github.robtimus.obfuscation.jackson.PropertyPaths.HasLengthMatcher;
import com.github.robtimus.obfuscation.jackson.PropertyPaths.IsIgnoreCaseMatcher;
import com.github.robtimus.obfuscation.jackson.PropertyPaths.IsMatcher;
import com.github.robtimus.obfuscation.jackson.PropertyPaths.NotMatcher;
import com.github.robtimus.obfuscation.jackson.PropertyPaths.OrMatcher;
import com.github.robtimus.obfuscation.jackson.PropertyPaths.StartsWithIgnoreCaseMatcher;
import com.github.robtimus.obfuscation.jackson.PropertyPaths.StartsWithMatcher;
import com.github.robtimus.obfuscation.support.CachingObfuscatingWriter;
import com.github.robtimus.obfuscation.support.CaseSensitivity;
import com.github.robtimus.obfuscation.support.CountingReader;
import com.github.robtimus.obfuscation.support.LimitAppendable;
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

    final PropertyConfig.Lookup properties;
    private final String propertiesRepresentation;
    private final String propertyPathsRepresentation;

    final String malformedJSONWarning;

    final long limit;
    final String truncatedIndicator;

    JSONObfuscator(Builder builder) {
        properties = builder.properties.build();
        propertiesRepresentation = builder.propertiesRepresentation();
        propertyPathsRepresentation = builder.propertyPathsRepresentation();

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
                + "[properties=" + propertiesRepresentation
                + ",propertyPaths=" + propertyPathsRepresentation
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
        return new Builder();
    }

    /**
     * A builder for {@link JSONObfuscator JSONObfuscators}.
     *
     * @author Rob Spoor
     */
    public static final class Builder {

        private final PropertyConfig.Lookup.Builder properties;
        private final StringBuilder propertiesRepresentation;
        private final StringBuilder propertyPathsRepresentation;

        private CaseSensitivity defaultCaseSensitivity;
        private Set<ValueType> defaultValueTypes;

        private String malformedJSONWarning;

        private long limit;
        private String truncatedIndicator;

        private JacksonVersion jacksonVersion;

        // default settings
        private ObfuscationMode forObjectsByDefault;
        private ObfuscationMode forArraysByDefault;

        private final PropertyNameConfigurer propertyNameConfigurer;
        private final PropertyPathConfigurer propertyPathConfigurer;
        private final LimitConfigurer limitConfigurer;

        private Builder() {
            properties = PropertyConfig.Lookup.builder();
            propertiesRepresentation = new StringBuilder().append('{');
            propertyPathsRepresentation = new StringBuilder().append('{');

            defaultCaseSensitivity = CaseSensitivity.CASE_SENSITIVE;
            defaultValueTypes = EnumSet.of(ValueType.ALL);

            malformedJSONWarning = Messages.JSONObfuscator.malformedJSON.text();

            limit = Long.MAX_VALUE;
            truncatedIndicator = "... (total: %d)"; //$NON-NLS-1$

            forObjectsByDefault = ObfuscationMode.OBFUSCATE;
            forArraysByDefault = ObfuscationMode.OBFUSCATE;

            propertyNameConfigurer = new PropertyNameConfigurer();
            propertyPathConfigurer = new PropertyPathConfigurer();
            limitConfigurer = new LimitConfigurer();
        }

        /**
         * Adds a property to obfuscate.
         * This method is equivalent to calling for {@link #withProperty(String, Obfuscator, Consumer)} with a {@link Consumer} that does nothing.
         *
         * @param property The name of the property.
         * @param obfuscator The obfuscator to use for obfuscating the property.
         * @return This object.
         * @throws NullPointerException If the given property name or obfuscator is {@code null}.
         * @throws IllegalArgumentException If a property with the same name and the same case sensitivity was already added for the property's value
         *                                  types.
         */
        public Builder withProperty(String property, Obfuscator obfuscator) {
            addProperty(property, obfuscator, null);
            return this;
        }

        /**
         * Adds a property to obfuscate.
         * This property will use the defaults set using {@link #caseSensitiveByDefault()}, {@link #caseInsensitiveByDefault()},
         * {@link #withValueTypesByDefault(ValueType, ValueType...)},
         * {@link #forObjectsByDefault(ObfuscationMode)} and {@link #forArraysByDefault(ObfuscationMode)}, unless explicitly replaced by the given
         * {@link Consumer}.
         *
         * @param property The name of the property.
         * @param obfuscator The obfuscator to use for obfuscating the property.
         * @param configurer A {@link Consumer} that can be used to update its argument, to override any setting for the property.
         * @return This object.
         * @throws NullPointerException If the given property name, obfuscator or {@link Consumer} is {@code null}.
         * @throws IllegalArgumentException If a property with the same name and the same case sensitivity was already added for the property's value
         *                                  types.
         * @since 3.0
         */
        public Builder withProperty(String property, Obfuscator obfuscator, Consumer<PropertyNameConfigurer> configurer) {
            Objects.requireNonNull(configurer);
            addProperty(property, obfuscator, configurer);
            return this;
        }

        private void addProperty(String property, Obfuscator obfuscator, Consumer<PropertyNameConfigurer> configurer) {
            Objects.requireNonNull(property);
            Objects.requireNonNull(obfuscator);
            try {
                propertyNameConfigurer.initialize(this);
                if (configurer != null) {
                    configurer.accept(propertyNameConfigurer);
                }

                PropertyConfig propertyConfig = propertyNameConfigurer.newConfig(obfuscator);

                propertyNameConfigurer.valueTypes()
                        .forEach(valueType -> properties.add(property, valueType, propertyNameConfigurer.caseSensitivity, propertyConfig));

                addPropertyRepresenation(property, obfuscator);
            } finally {
                propertyNameConfigurer.reset();
            }
        }

        private void addPropertyRepresenation(String property, Obfuscator obfuscator) {
            addPropertyRepresentation(property, obfuscator, propertyNameConfigurer, propertiesRepresentation);
        }

        /**
         * Adds a matcher for property paths to obfuscate.
         * This method is equivalent to calling for {@link #withPropertyPath(Matcher, Obfuscator, Consumer)} with a {@link Consumer} that
         * does nothing.
         *
         * @param matcher The matcher for property paths.
         * @param obfuscator The obfuscator to use for obfuscating the property.
         * @return This object.
         * @throws NullPointerException If the given matcher or obfuscator is {@code null}.
         * @throws IllegalArgumentException If an equal matcher was already added for the property's value types.
         * @since 3.0
         */
        public Builder withPropertyPath(PropertyPath.Matcher matcher, Obfuscator obfuscator) {
            addPropertyPath(matcher, obfuscator, null);
            return this;
        }

        /**
         * Adds a matcher for property paths to obfuscate.
         * This property will use the defaults set using {@link #withValueTypesByDefault(ValueType, ValueType...)},
         * {@link #forObjectsByDefault(ObfuscationMode)} and {@link #forArraysByDefault(ObfuscationMode)}, unless explicitly replaced by the given
         * {@link Consumer}.
         * Any matcher added using this method will take precedence over properties added using {@link #withProperty(String, Obfuscator)} or
         * {@link #withProperty(String, Obfuscator, Consumer)}.
         *
         * @param matcher The matcher for property paths.
         * @param obfuscator The obfuscator to use for obfuscating the property.
         * @param configurer A {@link Consumer} that can be used to update its argument, to override any setting for matched properties.
         * @return This object.
         * @throws NullPointerException If the given matcher, obfuscator or {@link Consumer} is {@code null}.
         * @throws IllegalArgumentException If an equal matcher was already added for the property's value types.
         * @since 3.0
         */
        public Builder withPropertyPath(PropertyPath.Matcher matcher, Obfuscator obfuscator, Consumer<PropertyPathConfigurer> configurer) {
            Objects.requireNonNull(configurer);
            addPropertyPath(matcher, obfuscator, configurer);
            return this;
        }

        private void addPropertyPath(PropertyPath.Matcher matcher, Obfuscator obfuscator, Consumer<PropertyPathConfigurer> configurer) {
            Objects.requireNonNull(matcher);
            Objects.requireNonNull(obfuscator);
            try {
                propertyPathConfigurer.initialize(this);
                if (configurer != null) {
                    configurer.accept(propertyPathConfigurer);
                }

                PropertyConfig propertyConfig = propertyPathConfigurer.newConfig(obfuscator);

                propertyPathConfigurer.valueTypes()
                        .forEach(valueType -> properties.add(matcher, valueType, propertyConfig));

                addPropertyPathRepresenation(matcher, obfuscator);
            } finally {
                propertyPathConfigurer.reset();
            }
        }

        private void addPropertyPathRepresenation(PropertyPath.Matcher property, Obfuscator obfuscator) {
            addPropertyRepresentation(property, obfuscator, propertyPathConfigurer, propertiesRepresentation);
        }

        @SuppressWarnings("nls")
        private void addPropertyRepresentation(Object property, Obfuscator obfuscator, PropertyConfigurer<?> propertyConfigurer,
                                               StringBuilder target) {
            if (target.length() > 1) {
                target.append(", ");
            }
            target.append(property).append("=[");
            propertyConfigurer.addPropertyRepresentaton(target, obfuscator);
            target.append("]");
        }

        /**
         * Sets the default case sensitivity for new properties to {@link CaseSensitivity#CASE_SENSITIVE}. This is the default setting.
         * <p>
         * Note that this will not change the case sensitivity of any property that was already added.
         *
         * @return This object.
         */
        public Builder caseSensitiveByDefault() {
            defaultCaseSensitivity = CaseSensitivity.CASE_SENSITIVE;
            return this;
        }

        /**
         * Sets the default case sensitivity for new properties to {@link CaseSensitivity#CASE_INSENSITIVE}.
         * <p>
         * Note that this will not change the case sensitivity of any property that was already added.
         *
         * @return This object.
         */
        public Builder caseInsensitiveByDefault() {
            defaultCaseSensitivity = CaseSensitivity.CASE_INSENSITIVE;
            return this;
        }

        /**
         * Sets several value types for which properties should be obfuscated by default.
         * <p>
         * Note that this will not change what will be obfuscated for any property that was already added.
         *
         * @param valueType The first value type to set.
         * @param additionalValueTypes Additional value types to set.
         * @return This object.
         * @throws NullPointerException If any of the given value types is {@code null}.
         * @since 3.0
         */
        public Builder withValueTypesByDefault(ValueType valueType, ValueType... additionalValueTypes) {
            defaultValueTypes.clear();
            defaultValueTypes.add(valueType);
            Collections.addAll(defaultValueTypes, additionalValueTypes);
            return this;
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
        public Builder forObjectsByDefault(ObfuscationMode obfuscationMode) {
            forObjectsByDefault = Objects.requireNonNull(obfuscationMode);
            return this;
        }

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
        public Builder forArraysByDefault(ObfuscationMode obfuscationMode) {
            forArraysByDefault = Objects.requireNonNull(obfuscationMode);
            return this;
        }

        /**
         * Sets the warning to include if a {@link StreamReadException} is thrown.
         * This can be used to override the default message. Use {@code null} to omit the warning.
         *
         * @param warning The warning to include.
         * @return This object.
         */
        public Builder withMalformedJSONWarning(String warning) {
            malformedJSONWarning = warning;
            return this;
        }

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
        public Builder withJacksonVersion(JacksonVersion jacksonVersion) {
            this.jacksonVersion = Objects.requireNonNull(jacksonVersion);
            return this;
        }

        /**
         * Sets the limit for the obfuscated result.
         * This method is equivalent to calling for {@link #limitTo(long, Consumer)} with a {@link Consumer} that does nothing.
         *
         * @param limit The limit to use.
         * @return This object.
         * @throws IllegalArgumentException If the given limit is negative.
         * @since 1.1
         */
        public Builder limitTo(long limit) {
            setLimit(limit, null);
            return this;
        }

        /**
         * Sets the limit for the obfuscated result.
         *
         * @param limit The limit to use.
         * @param configurer A {@link Consumer} that can be used to update its argument, to set any limit-specific properties.
         * @return This object.
         * @throws IllegalArgumentException If the given limit is negative.
         * @since 3.0
         */
        public Builder limitTo(long limit, Consumer<LimitConfigurer> configurer) {
            Objects.requireNonNull(configurer);
            setLimit(limit, configurer);
            return this;
        }

        private void setLimit(long limit, Consumer<LimitConfigurer> configurer) {
            if (limit < 0) {
                throw new IllegalArgumentException(limit + " < 0"); //$NON-NLS-1$
            }
            try {
                limitConfigurer.truncatedIndicator = truncatedIndicator;
                if (configurer != null) {
                    configurer.accept(limitConfigurer);
                }

                this.limit = limit;
                this.truncatedIndicator = limitConfigurer.truncatedIndicator;
            } finally {
                limitConfigurer.reset();
            }
        }

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

        private String propertiesRepresentation() {
            return finishRepresentation(propertiesRepresentation);
        }

        private String propertyPathsRepresentation() {
            return finishRepresentation(propertyPathsRepresentation);
        }

        private String finishRepresentation(StringBuilder representation) {
            representation.append('}');
            String result = representation.toString();
            representation.deleteCharAt(representation.length() - 1);
            return result;
        }

        /**
         * Creates a new {@code JSONObfuscator} with the properties and obfuscators added to this builder.
         *
         * @return The created {@code JSONObfuscator}.
         */
        public JSONObfuscator build() {
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

    /**
     * An object that can be used to configure a property that should be obfuscated.
     *
     * @author Rob Spoor
     */
    public abstract static sealed class PropertyConfigurer<C extends PropertyConfigurer<C>> {

        private final Set<ValueType> valueTypes = EnumSet.noneOf(ValueType.class);

        private ObfuscationMode forObjects;
        private ObfuscationMode forArrays;

        private PropertyConfigurer() {
        }

        /**
         * Sets several value types for which the property should be obfuscated.
         *
         * @param valueType The first value type to set.
         * @param additionalValueTypes Additional value types to set.
         * @return This object.
         * @throws NullPointerException If any of the given value types is {@code null}.
         * @since 3.0
         */
        public C withValueTypes(ValueType valueType, ValueType... additionalValueTypes) {
            valueTypes.clear();
            valueTypes.add(valueType);
            Collections.addAll(valueTypes, additionalValueTypes);
            return self();
        }

        /**
         * Indicates how to handle properties if they are JSON objects. The default is {@link ObfuscationMode#OBFUSCATE}.
         *
         * @param obfuscationMode The obfuscation mode that determines how to handle properties.
         * @return This object.
         * @throws NullPointerException If the given obfuscation mode is {@code null}.
         * @since 1.3
         */
        public C forObjects(ObfuscationMode obfuscationMode) {
            forObjects = Objects.requireNonNull(obfuscationMode);
            return self();
        }

        /**
         * Indicates how to handle properties if they are JSON arrays. The default is {@link ObfuscationMode#OBFUSCATE}.
         *
         * @param obfuscationMode The obfuscation mode that determines how to handle properties.
         * @return This object.
         * @throws NullPointerException If the given obfuscation mode is {@code null}.
         * @since 1.3
         */
        public C forArrays(ObfuscationMode obfuscationMode) {
            forArrays = Objects.requireNonNull(obfuscationMode);
            return self();
        }

        @SuppressWarnings("unchecked")
        private C self() {
            return (C) this;
        }

        PropertyConfig newConfig(Obfuscator obfuscator) {
            return new PropertyConfig(obfuscator, forObjects, forArrays);
        }

        Stream<ValueType> valueTypes() {
            return valueTypes.stream()
                    .flatMap(valueType -> ValueType.DE_ALIASED_TYPES.get(valueType).stream())
                    .distinct();
        }

        void initialize(Builder builder) {
            valueTypes.clear();
            valueTypes.addAll(builder.defaultValueTypes);
            forObjects = builder.forObjectsByDefault;
            forArrays = builder.forArraysByDefault;
        }

        @SuppressWarnings("nls")
        void addPropertyRepresentaton(StringBuilder target, Obfuscator obfuscator) {
            target.append("valueTypes=").append(valueTypes);
            target.append(",obfuscator=").append(obfuscator);
            target.append(",forObjects=").append(forObjects);
            target.append(",forArrays=").append(forArrays);
        }

        void reset() {
            valueTypes.clear();
            forObjects = null;
            forArrays = null;
        }

        /**
         * The possible value types.
         *
         * @author Rob Spoor
         * @since 3.0
         */
        public enum ValueType {
            /**
             * Represents string values.
             */
            STRING,
            /**
             * Represent numeric values.
             */
            NUMBER,
            /**
             * Represents boolean values.
             */
            BOOLEAN,
            /**
             * Represents object values.
             */
            OBJECT,
            /**
             * Represents array values.
             */
            ARRAY,
            /**
             * Represents {@code null} values.
             */
            NULL,
            /**
             * Represents scalar values: strings, numbers and booleans.
             * This is an alias for combining {@link #STRING}, {@link #NUMBER} and {@link #BOOLEAN}.
             */
            SCALAR,
            /**
             * Represents all possible values.
             * This is an alias for combining {@link #STRING}, {@link #NUMBER} {@link #BOOLEAN}, {@link #OBJECT} and {@link #ARRAY} but not
             * {@link #NULL}.
             */
            NON_NULL,
            /**
             * Represents all possible values.
             * This is an alias for combining {@link #STRING}, {@link #NUMBER} {@link #BOOLEAN}, {@link #OBJECT}, {@link #ARRAY} and {@link #NULL}.
             */
            ALL,
            ;

            private static final Map<ValueType, Set<ValueType>> DE_ALIASED_TYPES = deAliasedTypes();

            private static Map<ValueType, Set<ValueType>> deAliasedTypes() {
                Map<ValueType, Set<ValueType>> result = new EnumMap<>(ValueType.class);
                result.put(STRING, EnumSet.of(STRING));
                result.put(NUMBER, EnumSet.of(NUMBER));
                result.put(BOOLEAN, EnumSet.of(BOOLEAN));
                result.put(OBJECT, EnumSet.of(OBJECT));
                result.put(ARRAY, EnumSet.of(ARRAY));
                result.put(NULL, EnumSet.of(NULL));

                result.put(SCALAR, EnumSet.of(STRING, NUMBER, BOOLEAN));
                result.put(NON_NULL, EnumSet.of(STRING, NUMBER, BOOLEAN, OBJECT, ARRAY));
                result.put(ALL, EnumSet.of(STRING, NUMBER, BOOLEAN, OBJECT, ARRAY, NULL));

                return result;
            }
        }

        /**
         * The possible ways to deal with nested objects and arrays.
         *
         * @author Rob Spoor
         * @since 1.3
         */
        public enum ObfuscationMode {
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
     * An object that can be used to configure a property that should be obfuscated based on its name.
     *
     * @author Rob Spoor
     * @since 3.0
     */
    public static final class PropertyNameConfigurer extends PropertyConfigurer<PropertyNameConfigurer> {

        private CaseSensitivity caseSensitivity;

        private PropertyNameConfigurer() {
        }

        /**
         * Sets the case sensitivity for the property to {@link CaseSensitivity#CASE_SENSITIVE}.
         *
         * @return This object.
         */
        public PropertyNameConfigurer caseSensitive() {
            caseSensitivity = CaseSensitivity.CASE_SENSITIVE;
            return this;
        }

        /**
         * Sets the case sensitivity for the property to {@link CaseSensitivity#CASE_INSENSITIVE}.
         *
         * @return This object.
         */
        public PropertyNameConfigurer caseInsensitive() {
            caseSensitivity = CaseSensitivity.CASE_INSENSITIVE;
            return this;
        }

        @Override
        void initialize(Builder builder) {
            super.initialize(builder);
            caseSensitivity = builder.defaultCaseSensitivity;
        }

        @Override
        @SuppressWarnings("nls")
        void addPropertyRepresentaton(StringBuilder target, Obfuscator obfuscator) {
            if (caseSensitivity == CaseSensitivity.CASE_INSENSITIVE) {
                target.append("caseInsensitive,");
            }
            super.addPropertyRepresentaton(target, obfuscator);
        }

        @Override
        void reset() {
            super.reset();
            caseSensitivity = null;
        }
    }

    /**
     * An object that can be used to configure a property that should be obfuscated based on their {@linkplain PropertyPath paths}.
     *
     * @author Rob Spoor
     * @since 3.0
     */
    public static final class PropertyPathConfigurer extends PropertyConfigurer<PropertyPathConfigurer> {

        private PropertyPathConfigurer() {
        }
    }

    /**
     * An object that can be used to configure handling when the obfuscated result exceeds a pre-defined limit.
     *
     * @author Rob Spoor
     * @since 1.1
     */
    public static final class LimitConfigurer {

        private String truncatedIndicator;

        private LimitConfigurer() {
        }

        /**
         * Sets the indicator to use when the obfuscated result is truncated due to the limit being exceeded.
         * There can be one place holder for the total number of characters. Defaults to {@code ... (total: %d)}.
         * Use {@code null} to omit the indicator.
         *
         * @param pattern The pattern to use as indicator.
         * @return This object.
         */
        public LimitConfigurer withTruncatedIndicator(String pattern) {
            this.truncatedIndicator = pattern;
            return this;
        }

        private void reset() {
            this.truncatedIndicator = null;
        }
    }

    /**
     * A representation of the path to the current property that is obfuscated by a {@link JSONObfuscator}.
     * This path will only contain property names, not array indexes.
     * <p>
     * A property path should only be considered valid while obfuscating. Using it outside a matcher configured with
     * {@link JSONObfuscator.Builder#withPropertyPath(Matcher, Obfuscator)} or
     * {@link JSONObfuscator.Builder#withPropertyPath(Matcher, Obfuscator, Consumer)} may yield unexpected results.
     * A property path may be updated several times while obfuscating. It should therefore not escape the matcher or stored in any structure.
     *
     * @author Rob Spoor
     * @since 3.0
     */
    public static final class PropertyPath implements Iterable<String> {

        private final List<String> properties = new ArrayList<>();
        private final List<String> readOnlyProperties = Collections.unmodifiableList(properties);

        void push(String property) {
            properties.add(property);
        }

        void pop() {
            // Empty may occur if pop is called for the outer-most object object
            if (!properties.isEmpty()) {
                properties.remove(properties.size() - 1);
            }
        }

        List<String> properties() {
            return readOnlyProperties;
        }

        /**
         * Returns the length of the property path. This is the number of properties in the property path.
         *
         * @return The length of the property path.
         */
        public int length() {
            return properties.size();
        }

        /**
         * Returns a specific property in the property path.
         *
         * @param index The index of the property to return.
         * @return The property at the given index.
         * @throws IndexOutOfBoundsException If the index is negative or not smaller than the {@linkplain #length() length}.
         */
        public String propertyAt(int index) {
            return properties.get(index);
        }

        /**
         * Returns the last property in the property path.
         *
         * @return The last property in the property path.
         */
        public String lastProperty() {
            return properties.get(properties.size() - 1);
        }

        /**
         * Returns an iterator over the properties in the property path. This iterator does not allow removal of elements.
         */
        @Override
        public Iterator<String> iterator() {
            return readOnlyProperties.iterator();
        }

        /**
         * Returns a string representation of the property path. This representation is the properties of the property path joined by dots.
         * If any property contains any dots then it will be quoted in the result.
         *
         * @return The properties of the property path joined by dots.
         */
        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return PropertyPaths.join(properties, "", "");
        }

        /**
         * Returns a matcher that checks whether a property path contains exactly one or more properties.
         *
         * @param property The first property to check for.
         * @param additionalProperties Additional properties to check for.
         * @return A matcher that checks whether a property path contains exactly the given properties.
         * @throws NullPointerException If any of the given properties is {@code null}.
         */
        public static Matcher is(String property, String... additionalProperties) {
            return new IsMatcher(toList(property, additionalProperties));
        }

        /**
         * Returns a matcher that checks whether a property path case insensitively contains exactly one or more properties.
         *
         * @param property The first property to check for.
         * @param additionalProperties Additional properties to check for.
         * @return A matcher that checks whether a property path case insensitively contains exactly the given properties.
         * @throws NullPointerException If any of the given properties is {@code null}.
         */
        public static Matcher isIgnoreCase(String property, String... additionalProperties) {
            return new IsIgnoreCaseMatcher(toList(property, additionalProperties));
        }

        /**
         * Returns a matcher that checks whether a property path starts with a specific prefix.
         *
         * @param property The first property of the prefix to check for.
         * @param additionalProperties Additional properties of the prefix to check for.
         * @return A matcher that checks whether a property path starts with the given properties.
         * @throws NullPointerException If any of the given properties is {@code null}.
         */
        public static Matcher startsWith(String property, String... additionalProperties) {
            return new StartsWithMatcher(toList(property, additionalProperties));
        }

        /**
         * Returns a matcher that checks whether a property path case insensitively starts with a specific prefix.
         *
         * @param property The first property of the prefix to check for.
         * @param additionalProperties Additional properties of the prefix to check for.
         * @return A matcher that checks whether a property path case insensitively starts with the given properties.
         * @throws NullPointerException If any of the given properties is {@code null}.
         */
        public static Matcher startsWithIgnoreCase(String property, String... additionalProperties) {
            return new StartsWithIgnoreCaseMatcher(toList(property, additionalProperties));
        }

        /**
         * Returns a matcher that checks whether a property path ends with a specific postfix.
         *
         * @param property The first property of the postfix to check for.
         * @param additionalProperties Additional properties of the postfix to check for.
         * @return A matcher that checks whether a property path ends with the given properties.
         * @throws NullPointerException If any of the given properties is {@code null}.
         */
        public static Matcher endsWith(String property, String... additionalProperties) {
            return new EndsWithMatcher(toList(property, additionalProperties));
        }

        /**
         * Returns a matcher that checks whether a property path case insensitively ends with a specific postfix.
         *
         * @param property The first property of the postfix to check for.
         * @param additionalProperties Additional properties of the postfix to check for.
         * @return A matcher that checks whether a property path case insensitively ends with the given properties.
         * @throws NullPointerException If any of the given properties is {@code null}.
         */
        public static Matcher endsWithIgnoreCase(String property, String... additionalProperties) {
            return new EndsWithIgnoreCaseMatcher(toList(property, additionalProperties));
        }

        /**
         * Returns a matcher that checks whether a property path contains one or more properties at a specific index.
         *
         * @param index The index where the first property should occur.
         *              If it is negative it will be treated as the number of properties from the end of the property path.
         * @param property The first property to check for.
         * @param additionalProperties Additional properties to check for.
         * @return A matcher that checks whether a property path contains the given properties at the given index.
         * @throws NullPointerException If any of the given properties is {@code null}.
         */
        public static Matcher containsAt(int index, String property, String... additionalProperties) {
            return new ContainsAtMatcher(index, toList(property, additionalProperties));
        }

        /**
         * Returns a matcher that checks whether a property path case insensitively contains one or more properties at a specific index.
         *
         * @param index The index where the first property should occur.
         *              If it is negative it will be treated as the number of properties from the end of the property path.
         * @param property The first property to check for.
         * @param additionalProperties Additional properties to check for.
         * @return A matcher that checks whether a property path case insensitively contains the given properties at the given index.
         * @throws NullPointerException If any of the given properties is {@code null}.
         */
        public static Matcher containsAtIgnoreCase(int index, String property, String... additionalProperties) {
            return new ContainsAtIgnoreCaseMatcher(index, toList(property, additionalProperties));
        }

        /**
         * Returns a matcher that checks whether a property path has a specific length.
         *
         * @param length The length to check for.
         * @return A matcher that checks whether a property path has the given length.
         * @throws IllegalArgumentException If the given length is not at least 1.
         */
        public static Matcher hasLength(int length) {
            if (length < 1) {
                throw new IllegalArgumentException(length + " < 1"); //$NON-NLS-1$
            }
            return new HasLengthMatcher(length);
        }

        /**
         * Returns a matcher that checks whether a property path has a length that is greater than or equal to a specific minimum.
         *
         * @param min The minimum length to check for, inclusive.
         * @return A matcher that checks whether a property path has a length that is greater than or equal to the given minimum.
         * @throws IllegalArgumentException If the given minimum is not at least 1.
         */
        public static Matcher hasLengthAtLeast(int min) {
            if (min < 1) {
                throw new IllegalArgumentException(min + " < 1"); //$NON-NLS-1$
            }
            return new HasLengthAtLeastMatcher(min);
        }

        /**
         * Returns a matcher that checks whether a property path has a length that is greater than a specific minimum.
         *
         * @param min The minimum length to check for, exclusive.
         * @return A matcher that checks whether a property path has a length that is greater than the given minimum.
         * @throws IllegalArgumentException If the given minimum is not at least 1.
         */
        public static Matcher hasLengthGreaterThan(int min) {
            if (min < 1) {
                throw new IllegalArgumentException(min + " < 1"); //$NON-NLS-1$
            }
            return new HasLengthGreaterThanMatcher(min);
        }

        /**
         * Returns a matcher that checks whether a property path has a length that is less than or equal to a specific maximum.
         *
         * @param max The maximum length to check for, inclusive.
         * @return A matcher that checks whether a property path has a length that is less than or equal to the given maximum.
         * @throws IllegalArgumentException If the given maximum is not at least 1.
         */
        public static Matcher hasLengthAtMost(int max) {
            if (max < 1) {
                throw new IllegalArgumentException(max + " < 1"); //$NON-NLS-1$
            }
            return new HasLengthAtMostMatcher(max);
        }

        /**
         * Returns a matcher that checks whether a property path has a length that is less than a specific maximum.
         *
         * @param max The maximum length to check for, exclusive.
         * @return A matcher that checks whether a property path has a length that is less than the given maximum.
         * @throws IllegalArgumentException If the given maximum is not at least 1.
         */
        public static Matcher hasLengthLessThan(int max) {
            if (max < 1) {
                throw new IllegalArgumentException(max + " < 1"); //$NON-NLS-1$
            }
            return new HasLengthLessThanMatcher(max);
        }

        private static List<String> toList(String property, String... additionalProperties) {
            List<String> properties = new ArrayList<>(additionalProperties.length + 1);
            properties.add(Objects.requireNonNull(property));
            for (String additionalProperty : additionalProperties) {
                properties.add(Objects.requireNonNull(additionalProperty));
            }
            return properties;
        }

        /**
         * A matcher for property paths. This extension of {@link Predicate} provides default implementations of {@link Predicate#and(Predicate)},
         * {@link Predicate#or(Predicate)} and {@link Predicate#negate()} that return objects that define equality, making them more suitable to be
         * used with {@link JSONObfuscator.Builder#withPropertyPath(Matcher, Obfuscator)} and
         * {@link JSONObfuscator.Builder#withPropertyPath(Matcher, Obfuscator, Consumer)}.
         * <p>
         * While this is a functional interface, using lambdas or method references may not define equality as is recommended for use with
         * {@link JSONObfuscator.Builder#withPropertyPath(Matcher, Obfuscator)} and
         * {@link JSONObfuscator.Builder#withPropertyPath(Matcher, Obfuscator, Consumer)}. Implementations should preferably be provided through
         * custom classes or records instead.
         *
         * @author Rob Spoor
         * @since 3.0
         */
        public interface Matcher extends Predicate<PropertyPath> {

            @Override
            default Matcher and(Predicate<? super PropertyPath> other) {
                Objects.requireNonNull(other);
                return new AndMatcher(this, other);
            }

            @Override
            default Matcher or(Predicate<? super PropertyPath> other) {
                Objects.requireNonNull(other);
                return new OrMatcher(this, other);
            }

            @Override
            default Matcher negate() {
                return new NotMatcher(this);
            }
        }
    }
}
