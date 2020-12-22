# obfuscation-jackson

Provides functionality for obfuscating JSON documents. This can be useful for logging such documents, e.g. as part of request/response logging, where sensitive properties like passwords should not be logged as-is.

To create a JSON obfuscator, simply create a builder, add properties to it, and let it build the final obfuscator:

    Obfuscator obfuscator = JSONObfuscator.builder()
            .withProperty("password", Obfuscator.fixedLength(3))
            .build();

## Disabling obfuscation for objects and/or arrays

By default, a JSON obfuscator will obfuscate all properties; for object and array properties, their contents in the document including opening and closing characters will be obfuscated. This can be turned on or off for all properties, or per property. For example:

    Obfuscator obfuscator = JSONObfuscator.builder()
            .scalarsOnlyByDefault()
            .withProperty("password", Obfuscator.fixedLength(3))
            .withProperty("complex", Obfuscator.fixedLength(3))
                    .includeObjects() // override the default setting
            .build();

## Handling malformed JSON

If malformed JSON is encountered, obfuscation aborts. It will add a message to the result indicating that obfuscation was aborted. This message can be changed or turned off when creating JSON obfuscators:

    Obfuscator obfuscator = JSONObfuscator.builder()
            .withProperty("password", Obfuscator.fixedLength(3))
            // use null to turn it off
            .withMalformedJSONWarning("<invalid JSON>")
            .build();
