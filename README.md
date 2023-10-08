# obfuscation-jackson
[![Maven Central](https://img.shields.io/maven-central/v/com.github.robtimus/obfuscation-jackson)](https://search.maven.org/artifact/com.github.robtimus/obfuscation-jackson)
[![Build Status](https://github.com/robtimus/obfuscation-jackson/actions/workflows/build.yml/badge.svg)](https://github.com/robtimus/obfuscation-jackson/actions/workflows/build.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=com.github.robtimus%3Aobfuscation-jackson&metric=alert_status)](https://sonarcloud.io/summary/overall?id=com.github.robtimus%3Aobfuscation-jackson)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=com.github.robtimus%3Aobfuscation-jackson&metric=coverage)](https://sonarcloud.io/summary/overall?id=com.github.robtimus%3Aobfuscation-jackson)
[![Known Vulnerabilities](https://snyk.io/test/github/robtimus/obfuscation-jackson/badge.svg)](https://snyk.io/test/github/robtimus/obfuscation-jackson)

Provides functionality for obfuscating JSON documents. This can be useful for logging such documents, e.g. as part of request/response logging, where sensitive properties like passwords should not be logged as-is.

To create a JSON obfuscator, simply create a builder, add properties to it, and let it build the final obfuscator:

    Obfuscator obfuscator = JSONObfuscator.builder()
            .withProperty("password", Obfuscator.fixedLength(3))
            .build();

## Obfuscation for objects and/or arrays

By default, a JSON obfuscator will obfuscate all properties; for object and array properties, their contents in the document including opening and closing characters will be obfuscated. This can be turned on or off for all properties, or per property. For example:

    Obfuscator obfuscator = JSONObfuscator.builder()
            .scalarsOnlyByDefault()
            // .scalarsOnlyByDefault() is equivalent to:
            // .forObjectsByDefault(ObfuscationMode.EXCLUDE)
            // .forArraysByDefault(ObfuscationMode.EXCLUDE)
            .withProperty("password", Obfuscator.fixedLength(3))
            .withProperty("complex", Obfuscator.fixedLength(3))
                    .forObjects(ObfuscationMode.OBFUSCATE) // override the default setting
            .withProperty("arrayOfComplex", Obfuscator.fixedLength(3))
                    .forArrays(ObfuscationMode.INHERIT_OVERRIDABLE) // override the default setting
            .build();

The four possible modes for both objects and arrays are:
* `EXCLUDE`: don't obfuscate nested objects or arrays, but instead traverse into them.
* `OBFUSCATE`: obfuscate nested objects and arrays completely (default).
* `INHERIT`: don't obfuscate nested objects or arrays, but use the obfuscator for all nested scalar properties.
* `INHERIT_OVERRIDABLE`: don't obfuscate nested objects or arrays, but use the obfuscator for all nested scalar properties. If a nested property has its own obfuscator defined this will be used instead.

## Handling malformed JSON

If malformed JSON is encountered, obfuscation aborts. It will add a message to the result indicating that obfuscation was aborted. This message can be changed or turned off when creating JSON obfuscators:

    Obfuscator obfuscator = JSONObfuscator.builder()
            .withProperty("password", Obfuscator.fixedLength(3))
            // use null to turn it off
            .withMalformedJSONWarning("<invalid JSON>")
            .build();
