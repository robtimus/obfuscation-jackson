# Migrating from version 2.x to 3.0

## Builder

`JSONObfuscator.Builder` is no longer an interface but instead a final class. If you are creating mocks or implementing it directly you need to use actual instances created through `JSONObfucsator.builder()`.

### withProperty

`JSONObfuscator.Builder.withProperty` no longer returns a `PropertyConfigurer`. Instead it is overloaded to take a `Consumer<PropertyConfigurer>`. If you called any `PropertyConfigurer` methods you need to provide a lambda instead. For example:

```java
/* old:
JSONObfuscator.builder()
        .withProperty("foo", obfuscator)
                .forObjects(ObfuscationMode.INHERIT)
                .forArrays(ObfuscationMode.INHERIT)
 */
JSONObfuscator.builder()
        .withProperty("foo", obfuscator, property -> property
                .forObjects(ObfuscationMode.INHERIT)
                .forArrays(ObfuscationMode.INHERIT))
```

#### Case sensitivity

`JSONObfuscator.withProperty` no longer accepts a `CaseSensitivity` argument. You need to use new `PropertyConfigurer` methods `caseSensitive()` and `caseInsensitive()` instead. For example:

```java
/* old:
JSONObfuscator.builder()
        .withProperty("foo", obfuscator, CaseSensitivity.CASE_INSENSITIVE)
 */
JSONObfuscator.builder()
        .withProperty("foo", obfuscator, PropertyConfigurer::caseInsensitive)
```

### scalarsOnlyByDefault, excludeObjectsByDefault, excludeArraysByDefault, all

`JSONObfuscator.scalarsOnlyByDefault`, `JSONObfuscator.excludeObjectsByDefault`, `JSONObfuscator.excludeArraysByDefault` and `JSONObfuscator.allByDefault` have been removed. You need to use new method `withValueTypesByDefault` instead. For example:

```java
/*
JSONObfuscator.builder()
        .scalarsOnlyByDefault()
 */
JSONObfuscator.builder()
        .withValueTypesByDefault(ValueType.SCALAR, ValueType.NULL)
```

```java
/*
JSONObfuscator.builder()
        .excludeObjectsByDefault()
 */
JSONObfuscator.builder()
        .withValueTypesByDefault(ValueType.SCALAR, ValueType.ARRAY, ValueType.NULL)
```

```java
/*
JSONObfuscator.builder()
        .excludeArraysByDefault()
 */
JSONObfuscator.builder()
        .withValueTypesByDefault(ValueType.SCALAR, ValueType.OBJECT, ValueType.NULL)
```

```java
/*
JSONObfuscator.builder()
        .excludeObjectsByDefault()
        .excludeArraysByDefault()
 */
JSONObfuscator.builder()
        .withValueTypesByDefault(ValueType.SCALAR, ValueType.NULL)
```

```java
/*
JSONObfuscator.builder()
        .allByDefault()
 */
JSONObfuscator.builder()
        .withValueTypesByDefault(ValueType.ALL)
```

Note that `ValueType.SCALAR` does not include `null` values, so you need to add both `ValueType.SCALAR` and `ValueType.NULL` if you want to keep obfuscating `null` values.

### includeObjectsByDefault, includeArraysByDefault

`JSONObfuscator.includeObjectsByDefault` and `JSONObfuscator.includeArraysByDefault` have been removed. You need to combine methods `forObjectsByDefault` and/or `forArraysByDefault` with new method `withValueTypesByDefault` instead. For example:

```java
/*
JSONObfuscator.builder()
        .includeObjectsByDefault()
 */
JSONObfuscator.builder()
        .withValueTypesByDefault(ValueType.SCALAR, ValueType.OBJECT, ValueType.NULL)
        // or .withValueTypesByDefault(ValueType.ALL) to also include arrays
        .forObjectsByDefault(ObfuscationMode.OBFUSCATE)
```

```java
/*
JSONObfuscator.builder()
        .includeArraysByDefault()
 */
JSONObfuscator.builder()
        .withValueTypesByDefault(ValueType.SCALAR, ValueType.ARRAY, ValueType.NULL)
        // or .withValueTypesByDefault(ValueType.ALL) to also include objects
        .forArraysByDefault(ObfuscationMode.OBFUSCATE)
```

```java
/*
JSONObfuscator.builder()
        .includeObjectsByDefault()
        .includeArraysByDefault()
 */
JSONObfuscator.builder()
        .withValueTypesByDefault(ValueType.ALL)
        .forArraysByDefault(ObfuscationMode.OBFUSCATE)
```

Note that the defaults already use `ValueType.ALL` and `ObfuscationMode.OBFUSCATE` for both objects and arrays.

### limitTo

`JSONObfuscator.Builder.limitTo` no longer returns a `LimitConfigurer`. Instead it is overloaded to take a `Consumer<LimitConfigurer>`. If you called any `LimitConfigurer` methods you need to provide a lambda instead. For example:

```java
/* old:
JSONObfuscator.builder()
        .limitTo(1024)
                .withTruncatedIndicator("<truncated>")
 */
JSONObfuscator.builder()
        .limitTo(1024, limit -> limit
                .withTruncatedIndicator("<truncated>"))
```

## PropertyConfigurer

`JSONObfuscator.PropertyConfigurer` is no longer an interface but instead a final class. If you are creating mocks or implementing it directly you need to use actual instances passed to the `Consumer` argument of `JSONObfuscator.Builder.withProperty`.

### scalarsOnly, excludeObjects, excludeArrays, all

`JSONObfuscator.PropertyConfigurer.scalarsOnly`, `JSONObfuscator.PropertyConfigurer.excludeObjects`, `JSONObfuscator.PropertyConfigurer.excludeArrays` and `JSONObfuscator.all` have been removed. You need to use new method `withValueTypes` instead. For example:

```java
/*
JSONObfuscator.builder()
        .withProperty("foo", obfuscator)
                .scalarsOnlyByDefault()
 */
JSONObfuscator.builder()
        .withProperty("foo", obfuscator, property -> property
                .withValueTypes(ValueType.SCALAR, ValueType.NULL))
```

```java
/*
JSONObfuscator.builder()
        .withProperty("foo", obfuscator)
                .excludeObjects()
 */
JSONObfuscator.builder()
        .withProperty("foo", obfuscator, property -> property
                .withValueTypes(ValueType.SCALAR, ValueType.ARRAY, ValueType.NULL))
```

```java
/*
JSONObfuscator.builder()
        .withProperty("foo", obfuscator)
                .excludeArrays()
 */
JSONObfuscator.builder()
        .withProperty("foo", obfuscator, property -> property
                .withValueTypes(ValueType.SCALAR, ValueType.OBJECT, ValueType.NULL))
```

```java
/*
JSONObfuscator.builder()
        .withProperty("foo", obfuscator)
                .excludeObjects()
                .excludeArrays()
 */
JSONObfuscator.builder()
        .withProperty("foo", obfuscator, property -> property
                .withValueTypes(ValueType.SCALAR, ValueType.NULL))
```

```java
/*
JSONObfuscator.builder()
        .withProperty("foo", obfuscator)
               .all()
 */
JSONObfuscator.builder()
        .withProperty("foo", obfuscator, property -> property
                .withValueTypes(ValueType.ALL))
```

Note that `ValueType.SCALAR` does not include `null` values, so you need to add both `ValueType.SCALAR` and `ValueType.NULL` if you want to keep obfuscating `null` values.

### includeObjects, includeArrays

`JSONObfuscator.PropertyConfigurer.includeObjects` and `JSONObfuscator.PropertyConfigurer.includeArrays` have been removed. You need to combine methods `forObjects` and/or `forArrays` with new method `withValueTypes` instead. For example:

```java
/*
JSONObfuscator.builder()
        .withProperty("foo", obfuscator)
                .includeObjects())
 */
JSONObfuscator.builder()
        .withProperty("foo", obfuscator, property -> property
                .withValueTypes(ValueType.SCALAR, ValueType.OBJECT, ValueType.NULL)
                // or .withValueTypes(ValueType.ALL) to also include arrays
                .forObjects(ObfuscationMode.OBFUSCATE))
```

```java
/*
JSONObfuscator.builder()
        .withProperty("foo", obfuscator)
                .includeArrays()
 */
JSONObfuscator.builder()
        .withProperty("foo", obfuscator, property -> property
                .withValueTypes(ValueType.SCALAR, ValueType.ARRAY, ValueType.NULL)
                // or .withValueTypes(ValueType.ALL) to also include objects
                .forArrays(ObfuscationMode.OBFUSCATE))
```

```java
/*
JSONObfuscator.builder()
        .withProperty("foo", obfuscator)
                .includeObjects())
                .includeArrays()
 */
JSONObfuscator.builder()
        .withProperty("foo", obfuscator, property -> property
                .withValueTypes(ValueType.ALL)
                .forArrays(ObfuscationMode.OBFUSCATE))
```

## LimitConfigurer

`JSONObfuscator.LimitConfigurer` is no longer an interface but instead a final class. If you are creating mocks or implementing it directly you need to use actual instances passed to the `Consumer` argument of `JSONObfuscator.Builder.limitTo`.

## ObfuscationMode

Class `ObfuscationMode` is no longer nested in `PropertyConfigurer` but directly in `JSONObfuscator`. You need to replace any occurrence of `JSONObfuscator.PropertyConfigurer.ObfuscationMode` to `JSONObfuscator.ObfuscationMode` in import statements, method arguments, etc.

### EXCLUDE

Constant `ObfuscationMode.EXCLUDE` has been removed. You need to use new method `withValueTypesByDefault` and/or `withValueTypes` as documented above instead. For example:

```java
/* old
JSONObfuscator.builder()
        .forObjectsByDefault(ObfuscationMode.EXCLUDE)
 */
JSONObfuscator.builder()
        .withValueTypesByDefault(ValueType.SCALAR, ValueType.ARRAY, ValueType.NULL)
```

```java
/* old
JSONObfuscator.builder()
        .forArraysByDefault(ObfuscationMode.EXCLUDE)
 */
JSONObfuscator.builder()
        .withValueTypesByDefault(ValueType.SCALAR, ValueType.OBJECT, ValueType.NULL)
```

```java
/* old
JSONObfuscator.builder()
        .forObjectsByDefault(ObfuscationMode.EXCLUDE)
        .forArraysByDefault(ObfuscationMode.EXCLUDE)
 */
JSONObfuscator.builder()
        .withValueTypesByDefault(ValueType.SCALAR, ValueType.NULL)
```

```java
/*
JSONObfuscator.builder()
        .withProperty("foo", obfuscator)
                .forObjects(ObfuscationMode.EXCLUDE)
 */
JSONObfuscator.builder()
        .withProperty("foo", obfuscator, property -> property
                .withValueTypes(ValueType.SCALAR, ValueType.ARRAY, ValueType.NULL))
```

```java
/*
JSONObfuscator.builder()
        .withProperty("foo", obfuscator)
                .forArrays(ObfuscationMode.EXCLUDE)
 */
JSONObfuscator.builder()
        .withProperty("foo", obfuscator, property -> property
                .withValueTypes(ValueType.SCALAR, ValueType.OBJECT, ValueType.NULL))
```

```java
/*
JSONObfuscator.builder()
        .withProperty("foo", obfuscator)
                .forObjects(ObfuscationMode.EXCLUDE)
                .forArrays(ObfuscationMode.EXCLUDE)
 */
JSONObfuscator.builder()
        .withProperty("foo", obfuscator, property -> property
                .withValueTypes(ValueType.SCALAR, ValueType.NULL))
```
