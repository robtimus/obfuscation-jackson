/*
 * Jackson2Obfuscator.java
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
import java.io.Reader;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.github.robtimus.obfuscation.support.LimitAppendable;

@SuppressWarnings("squid:S2160")
final class Jackson2Obfuscator extends JSONObfuscator {

    // Note: the following are declared as Set<String> for backwards compatibility with older Jackson versions
    // They are verified through unit tests for correctness and completeness

    // Allow most non-deprecated features, to be as lenient as possible
    @SuppressWarnings("nls")
    static final Set<String> ENABLED_STREAM_READ_FEATURES = Set.of(
            "IGNORE_UNDEFINED",
            "CLEAR_CURRENT_TOKEN_ON_CLOSE"
    );

    // Disable explicitly
    @SuppressWarnings("nls")
    static final Set<String> DISABLED_STREAM_READ_FEATURES = Set.of(
            // the source is not ours to close
            "AUTO_CLOSE_SOURCE",
            // don't fail if there are duplicates, to be as lenient as possible
            "STRICT_DUPLICATE_DETECTION",
            // the source is not unnecessary
            "INCLUDE_SOURCE_IN_LOCATION",
            // use Double.parseDouble
            "USE_FAST_DOUBLE_PARSER",
            // Use built-in parsing for BigDecimal and BigInteger
            "USE_FAST_BIG_NUMBER_PARSER"
    );

    // Allow all features, to be as lenient as possible
    @SuppressWarnings("nls")
    static final Set<String> ENABLED_JSON_READ_FEATURES = Set.of(
            "ALLOW_JAVA_COMMENTS",
            "ALLOW_YAML_COMMENTS",
            "ALLOW_SINGLE_QUOTES",
            "ALLOW_UNQUOTED_FIELD_NAMES",
            "ALLOW_UNESCAPED_CONTROL_CHARS",
            "ALLOW_RS_CONTROL_CHAR",
            "ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER",
            "ALLOW_LEADING_ZEROS_FOR_NUMBERS",
            "ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS",
            "ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS",
            "ALLOW_TRAILING_DECIMAL_POINT_FOR_NUMBERS",
            "ALLOW_NON_NUMERIC_NUMBERS",
            "ALLOW_MISSING_VALUES",
            "ALLOW_TRAILING_COMMA"
    );

    private final JsonFactory jsonFactory;

    Jackson2Obfuscator(ObfuscatorBuilder builder) {
        super(builder);

        jsonFactory = createJsonFactory();
    }

    private static JsonFactory createJsonFactory() {
        JsonFactoryBuilder builder = new JsonFactoryBuilder();
        for (JsonReadFeature feature : JsonReadFeature.values()) {
            String featureName = feature.name();
            if (ENABLED_JSON_READ_FEATURES.contains(featureName)) {
                builder = builder.enable(feature);
            }
        }
        for (StreamReadFeature feature : StreamReadFeature.values()) {
            String featureName = feature.name();
            if (ENABLED_STREAM_READ_FEATURES.contains(featureName)) {
                builder = builder.enable(feature);
            } else if (DISABLED_STREAM_READ_FEATURES.contains(featureName)) {
                builder = builder.disable(feature);
            }
        }
        return builder.build();
    }

    @Override
    void obfuscateText(Reader input, Source source, int start, int end, LimitAppendable destination) throws IOException {
        // closing parser will not close input because it's considered to be unmanaged and Feature.AUTO_CLOSE_SOURCE is disabled explicitly
        try (JsonParser jsonParser = jsonFactory.createParser(input)) {
            Appender appender = new Appender(jsonParser, source, start, end, destination, properties);

            JsonToken token;
            while ((token = jsonParser.nextToken()) != null && !destination.limitExceeded()) {
                switch (token) {
                    case START_OBJECT -> appender.startObject();
                    case END_OBJECT -> appender.endObject();
                    case START_ARRAY -> appender.startArray();
                    case END_ARRAY -> appender.endArray();
                    case FIELD_NAME -> appender.propertyName();
                    case VALUE_STRING -> appender.valueString();
                    case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> appender.valueNumber();
                    case VALUE_TRUE, VALUE_FALSE, VALUE_NULL -> appender.valueOther(token);
                    default -> {
                        // do nothing
                    }
                }
            }
            appender.appendRemainder();
        } catch (StreamReadException e) {
            LOGGER.warn(Messages.JSONObfuscator.malformedJSON.warning(), e);
            if (malformedJSONWarning != null) {
                destination.append(malformedJSONWarning);
            }
        }
    }

    @Override
    JacksonVersion jacksonVersion() {
        return JacksonVersion.JACKSON2;
    }

    private static final class Appender extends ObfuscatingAppender<JsonToken> {

        private final JsonParser jsonParser;

        private Appender(JsonParser jsonParser, Source source, int start, int end, Appendable destination, Map<String, PropertyConfig> properties) {
            super(source, start, end, destination, properties);

            this.jsonParser = jsonParser;
        }

        @Override
        JsonToken startObjectToken() {
            return JsonToken.START_OBJECT;
        }

        @Override
        JsonToken endObjectToken() {
            return JsonToken.END_OBJECT;
        }

        @Override
        JsonToken startArrayToken() {
            return JsonToken.START_ARRAY;
        }

        @Override
        JsonToken endArrayToken() {
            return JsonToken.END_ARRAY;
        }

        @Override
        JsonToken propertyNameToken() {
            return JsonToken.FIELD_NAME;
        }

        @Override
        String tokenToString(JsonToken token) {
            return token.asString();
        }

        @Override
        String currentPropertyName() throws IOException {
            return jsonParser.currentName();
        }

        @Override
        String currentValue() throws IOException {
            return jsonParser.getValueAsString();
        }

        @Override
        int currentTokenLocation() {
            return (int) jsonParser.currentTokenLocation().getCharOffset();
        }

        @Override
        int currentLocation() {
            return (int) jsonParser.currentLocation().getCharOffset();
        }
    }
}
