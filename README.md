# Java JSON RecordMapper

A simple Java interface for mapping `java21.util.json.JsonObject` instances to Java record instances, and back.
By default, the JSON member names are the names of the record components; a naming strategy or the
`@JsonName` annotation allows other names. Only records are mapped (and sealed interfaces of
records); other classes such as JavaBeans are not supported.

This project is based on the idea of Rémi Forax and his repository
[forax/json-object-mapper](https://github.com/forax/json-object-mapper).

## Key Features

*   **Type-Safe Mapping:** Converts `JsonObject` to a specific record class, and records back to JSON.
*   **Nested Record Support:** Capable of mapping JSON objects that contain nested JSON objects to records with components that are themselves records.
*   **Recursive Records:** Records may refer to themselves, directly (`record Node(String value, Node next)`) or through other records. The nesting depth of the mapped JSON is limited by the thread's stack size.
*   **Mandatory Fields:** By default, all record components are considered mandatory. If a corresponding key is missing in the `JsonObject`, a `JsonValueException` is thrown during mapping, unless the component has a `@JsonDefault` value.
*   **Optional Fields:** Components of type `Optional<T>`, `OptionalInt`, `OptionalLong` or `OptionalDouble` are empty if the key is missing or the value is JSON `null`.
*   **Exact Conversions:** Integral numbers are only converted if no information is lost, and every mapping error is reported as a `JsonValueException`. Its message contains the JSON path and the location of the offending value, also for the checks done by the mapper itself.
*   **JSON Names:** Member names that differ from the component names, with a naming strategy (`SNAKE_CASE`, `KEBAB_CASE`) or the `@JsonName` annotation, which also names enum constants; `@JsonIgnore` excludes a component.
*   **Sealed Interfaces:** Polymorphic values of sealed interfaces of records, with a type member (`{"type": "Circle", ...}`), also at the top level.
*   **Custom Types:** A `JsonDecoder` and a `JsonEncoder` registered for a class read and write its values; `java.time` types, `UUID` and `URI` are predefined.
*   **Reflection-based:** Uses `MethodHandles.Lookup` to access record constructors, enabling mapping to public and non-public records (if the lookup has sufficient access rights).

## Prerequisites

- Java 21
- `java21.util.json` (Maven `net.sourceforge.streamsupport:java21.util.json:0.0.1`), a Java 21 port of the
  `jdk.incubator.json` API of JDK 28. Install it into the local Maven repository with `mvn install`
  (or open both projects in the same Eclipse workspace so that m2e resolves it from the workspace).

## Maven

```xml
<dependency>
    <groupId>net.sourceforge.streamsupport</groupId>
    <artifactId>json-record-mapper</artifactId>
    <version>0.0.1</version>
</dependency>
```

The mapper is the module `dev.json.records`; `requires dev.json.records;` also gives access to
the JSON API `java21.util.json`. The records need not be opened, as they are accessed with the
`Lookup` passed to the mapper.

## Getting Started

Define your record.

```java
// Simple record
record User(String name, int age) {}
```

Create an instance of `RecordMapper` using the `of` factory method.
You need to provide a `MethodHandles.Lookup` instance.
It's recommended to store the mapper as a `static final` constant.

```java
import java.lang.invoke.MethodHandles;
import dev.json.records.RecordMapper;

public class Main {
    static final RecordMapper MAPPER = RecordMapper.of(MethodHandles.lookup());

    public static void main(String[] args) {
        User user = MAPPER.fromJson("""
            { "name": "Alice", "age": 30 }
            """, User.class);
        System.out.println("Mapped User: " + user);

        String json = MAPPER.toJsonText(user);   // {"name":"Alice","age":30}
    }
}
```

`fromJson` and `fromJsonList` parse JSON text; `fromTyped` and `fromTypedList` map an already
parsed `JsonObject` or `JsonArray` (`Json.parse` of `java21.util.json`). A syntax error results in
a `JsonParseException`.

`match` works like `fromTyped` but returns `null` instead of throwing a `JsonValueException`
if the `JsonObject` does not match the record, which makes it usable with record patterns:

```java
JsonObject userJson = (JsonObject) Json.parse(text);
if (MAPPER.match(userJson, User.class) instanceof User(String name, int age)) {
    ...
}
```

`fromJsonList` (or `fromTypedList`) maps a top-level JSON array of objects:

```java
List<User> users = MAPPER.fromJsonList("""
    [ { "name": "Alice", "age": 30 }, { "name": "Bob", "age": 25 } ]
    """, User.class);
```

## Generic Records

Generic records are mapped with their type arguments, which may be any of the supported
reference types (records, `String`, wrappers, collections, ...).

As a record component (or collection element, `Optional`, ...), nothing special is needed:

```java
record Page<T>(List<T> items, int total) {}
record UserResponse(Page<User> users, String cursor) {}

UserResponse response = MAPPER.fromTyped(json, UserResponse.class);
```

At the top level, a `Class` cannot express `Page<User>`, so the type is passed as a `TypeRef`,
usually an anonymous subclass (`fromTypedList` and `match` have the same overloads):

```java
Page<User> page = MAPPER.fromTyped(json, new TypeRef<Page<User>>() {});
```

In generic code, where a type variable is erased at runtime, the type is composed with `TypeRef.of`:

```java
<T extends Record> Page<T> load(JsonObject json, Class<T> type) {
    return MAPPER.fromTyped(json, TypeRef.of(Page.class, type));
}
```

Generic records may be recursive (`record Tree<T>(T value, List<Tree<T>> children)`). Not supported
are a generic record without type arguments (`fromTyped(json, Page.class)` or a raw `Page`
component), wildcards (`Page<?>`), and generic record types that expand infinitely
(`record Weird<T>(Weird<List<T>> next)`); they result in an `UnsupportedOperationException`.
The mapper caches a decoder for each parameterized type, which keeps the involved classes
reachable as long as the mapper is.

## Sealed Interfaces

A sealed interface whose permitted subclasses are records (possibly through nested sealed
interfaces) is mapped polymorphically: its values are JSON objects with a member that holds the
type of the record.

```java
sealed interface Shape permits Circle, Square {}
record Circle(double radius) implements Shape {}
record Square(double side) implements Shape {}

record Drawing(List<Shape> shapes) {}
// {"shapes": [{"type": "Circle", "radius": 1.0}, {"type": "Square", "side": 2.0}]}
```

A sealed interface can be used wherever a record can, also at the top level, which works well with
record patterns:

```java
if (MAPPER.match(json, Shape.class) instanceof Shape shape) {
    double area = switch (shape) {
        case Circle(double radius) -> Math.PI * radius * radius;
        case Square(double side) -> side * side;
    };
}
```

- The member is named `type` unless the sealed interface is annotated with
  `@JsonDiscriminator("kind")`; nested sealed interfaces must use the same name.
- The type is the simple name of the record class unless the record is annotated with
  `@JsonTypeName("circle")`.
- A record of a sealed interface is always written with its type, as the first member, even where
  its own class is expected. When it is read as its own class, the type member is ignored.
- A missing or unknown type results in a `JsonValueException`. Duplicate type names, and a type
  member that is also the JSON name of a component, result in an `IllegalArgumentException`.
- Generic sealed interfaces (`sealed interface Result<T>`), generic records of a sealed interface
  and permitted subclasses that are neither records nor sealed interfaces (e.g. enums) are not
  supported.

## Configuration

`RecordMapper.of(lookup)` returns a mapper with the default configuration. A builder configures
the naming strategy, decoders and encoders, whether unknown members are an error, and whether null
components are left out; the mappers it builds are not affected by later changes of the builder:

```java
static final RecordMapper MAPPER = RecordMapper.builder(MethodHandles.lookup())
        .naming(JsonNaming.SNAKE_CASE)
        .decoder(Currency.class, value -> Currency.getInstance(value.asString()))
        .encoder(Currency.class, currency -> JsonString.of(currency.getCurrencyCode()))
        .failOnUnknownMembers(true)
        .omitNulls(true)
        .build();
```

By default, a JSON object member that does not correspond to a component of the record is ignored.
With `failOnUnknownMembers(true)`, it results in a `JsonValueException` (and `match` returns `null`),
which detects typos in member names. The type member of a record of a sealed interface is never
unknown, and the keys of a `Map` are not checked.

By default, a component that is `null` is written as JSON `null`. With `omitNulls(true)`, it is left
out, and a missing member of a component of a reference type is read as `null`, so the result can
be read back; a missing member of a primitive component is still an error. Elements of collections
and values of maps are not affected.

## Default Values and Ignored Components

`@JsonDefault` gives the value of a component, as JSON text, if its member is missing. The value is
converted like a value read from JSON, so it works for any component type (also records,
collections and types with a decoder), and it is converted on every use. Only a missing member is
replaced, JSON `null` is mapped as usual. `@JsonIgnore` excludes a component from the JSON form: it
is neither written nor read, and gets its default value, or `null`, zero, `false` or an empty
optional:

```java
record User(String name,
            @JsonDefault("\"user\"") String role,
            @JsonDefault("[]") List<String> tags,
            @JsonIgnore String password) {}

MAPPER.fromJson("""
    { "name": "Alice", "password": "secret" }
    """, User.class);                                        // User[name=Alice, role=user, tags=[], password=null]
MAPPER.toJsonText(new User("Bob", "admin", List.of(), "secret"));
                                                             // {"name":"Bob","role":"admin","tags":[]}
```

A `@JsonDefault` value that cannot be parsed or converted, and `@JsonDefault` on an optional
component, are programming errors (`IllegalArgumentException`). In the strict mode, the member of an
ignored component is unknown.

## JSON Names

By default, a record component is mapped from the JSON object member with the same name.
A naming strategy derives the member names from the component names:

| Strategy               | `firstName`  | `userID`  | `URLValue`  |
|------------------------|--------------|-----------|-------------|
| `IDENTITY` (default)   | `firstName`  | `userID`  | `URLValue`  |
| `SNAKE_CASE`           | `first_name` | `user_id` | `url_value` |
| `KEBAB_CASE`           | `first-name` | `user-id` | `url-value` |

The `@JsonName` annotation specifies the member name of a single component and takes precedence
over the strategy:

```java
record User(@JsonName("ID") long id, String firstName) {}
```

Two components of a record must not be mapped from the same member name (`IllegalArgumentException`).
The keys of a `Map` are not affected by the strategy.

Enum constants are read and written with their name, unless they have a `@JsonName`; this also
holds for enum keys of a `Map`:

```java
enum Status { @JsonName("active") ACTIVE, @JsonName("on-hold") ON_HOLD, CLOSED }
```

## Custom Types

A `JsonDecoder` converts JSON values to a class that the mapper does not support itself, or
converts them differently. It is registered for exactly one class and used wherever values of that
class occur: record components, elements of collections and arrays, map values, optionals, type
arguments of generic records and the top level. It takes precedence over the conversion of the
mapper, so it can also replace the predefined decoders, e.g. to read an `Instant` from epoch
milliseconds:

```java
static final RecordMapper MAPPER = RecordMapper.builder(MethodHandles.lookup())
        .decoder(Currency.class, value -> Currency.getInstance(value.asString()))
        .decoder(Instant.class, value -> Instant.ofEpochMilli(value.asLong()))
        .build();
```

A decoder never gets JSON null; the mapper maps it to `null` or to an empty optional as described
below. If the value cannot be converted, the decoder should throw a `JsonValueException`, which the
conversion methods of `JsonValue` (`asString()`, `asLong()`, ...) already do for a value of the wrong
JSON type. Any other runtime exception is wrapped in a `JsonValueException` whose cause is the
original exception, so `match` returns `null` in that case as well.

For writing JSON, a `JsonEncoder` is the counterpart of a decoder. It is used for values whose
runtime class is exactly the registered class, is never called with `null` and must not return
`null`:

```java
static final RecordMapper MAPPER = RecordMapper.builder(MethodHandles.lookup())
        .decoder(Currency.class, value -> Currency.getInstance(value.asString()))
        .encoder(Currency.class, currency -> JsonString.of(currency.getCurrencyCode()))
        .build();
```

## Writing JSON

`toJson` writes a record as a `JsonObject`, and `toJsonList` a list of records as a `JsonArray`.
The result can be read back into equal records (except for array components, which `equals` of a
record compares by identity). `toJsonText` writes the compact JSON text; an indented text is
obtained with `Json.toDisplayString(MAPPER.toJson(value), "  ")`:

```java
record User(String name, int age, Optional<String> nickname) {}

String text = MAPPER.toJsonText(new User("Alice", 30, Optional.empty()));   // {"name":"Alice","age":30}
```

`toJson` takes any value that is written as a JSON object, so a value whose static type is a sealed
interface needs no cast; other values, e.g. a `String`, result in an `IllegalArgumentException`.

- The members are written in the order of the record components, with the same names as for
  reading (`JsonNaming`, `@JsonName`).
- `null` is written as JSON `null` (a `null` component not at all with `omitNulls(true)`); an empty
  `Optional` component (or `OptionalInt`, ...) is left out, and as an element of a collection or a
  map value written as JSON `null`.
- Components with `@JsonIgnore` are not written, enum constants with their `@JsonName` if they have
  one.
- Values are written according to their runtime type, so generic records such as `Page<User>` need
  no `TypeRef`.
- Numbers keep their form: `0.1f` is written as `0.1`, a `BigDecimal` keeps its scale (`1.50`).
  NaN and infinite values cannot be written (`IllegalArgumentException`).
- `byte[]` is written as a Base64 string, `java.time` types, `UUID` and `URI` as strings in their
  standard form, values with a registered `JsonEncoder` with that encoder.
- JSON values (`JsonValue` and its subtypes) are written unchanged, a `JsonNull` as JSON `null`.
- Values that could not be read back (e.g. `String[]`, maps with `Integer` keys, other classes)
  result in an `UnsupportedOperationException`.
- As records are immutable, a cycle can only occur through a mutable collection or map; a
  collection or map that contains itself results in an `IllegalArgumentException`.
- Errors below the top level contain the JSON path of the offending value, e.g.
  `NaN cannot be written as a JSON number. Path: "{items[1{price".`; exceptions of encoders and
  record accessors are wrapped in an `IllegalArgumentException` with that path.

## How It Works

1.  **Component Discovery:** Retrieves the record components of the target `recordClass`.
2.  **Key-Value Matching:** For each component, it extracts the corresponding value from the `JsonObject`
    using the component's name as the key (case-sensitive).
3.  **Type Conversion:** Converts JSON values to the types of the record components, using the
    conversion methods of `JsonValue` (`asString()`, `asInt()`, ...).
4.  **Constructor Invocation:** Invokes the canonical constructor of the `recordClass`
    with the extracted and converted values.

## Supported Data Types for Record Components

The mapper implementation directly supports:

- `String` from JSON string
- `boolean` from JSON boolean
- `byte`, `short`, `int` or `long` from JSON number, if it can be converted exactly (`30` or `30.0`,
  but not `30.5` or a value out of range)
- `float` or `double` from JSON number, rounded to the nearest value (`5` is accepted as well);
  a number beyond the finite range is rejected
- `char` from a JSON string consisting of exactly one UTF-16 code unit (`"x"`)
- the wrapper types `Boolean`, `Byte`, `Short`, `Character`, `Integer`, `Long`, `Float` and `Double`,
  converted like the corresponding primitive type
- `BigDecimal` from JSON number, exactly as written (`1.50` keeps its scale of 2; `-0.0` becomes `0.0`)
- `BigInteger` from JSON number, if it is a whole number (`1.0` or `1e2`, but not `1.5`)
- enums from a JSON string with the exact name of a constant (`"RED"`, but not `"red"`), or its
  `@JsonName` (see [JSON Names](#json-names))
- `LocalDate`, `LocalTime`, `LocalDateTime`, `OffsetDateTime`, `ZonedDateTime`, `Instant`, `Duration`
  and `Period` from a JSON string in ISO-8601 format (`"2026-09-24"`, `"PT1H30M"`, ...), and `UUID`
  and `URI` from a JSON string; these are predefined decoders, which a registered decoder replaces
  (see [Custom Types](#custom-types))
- primitive arrays from JSON array (`int[]`, `double[]`, ...), without boxing; the elements are
  converted like the corresponding primitive components. Exceptions: `char[]` is read from an array
  of single character strings (`["a", "b"]`), and `byte[]` from a Base64 string (`"AQL/"`, padding
  optional). Multidimensional arrays of them (`double[][]`, `int[][][]`) may be ragged and contain
  `null` rows. Each mapping creates new arrays; note that `equals` of a record compares array
  components by identity.
- other `Record` types from nested JSON objects, mapped recursively
- sealed interfaces of records from JSON objects with a type member (see
  [Sealed Interfaces](#sealed-interfaces))
- `List<T>` and `Set<T>` from JSON array and `Map<K, T>` from JSON object, with `String` or enum keys
  `K`, where `T` is one of the reference types listed here (including `Optional` and nested
  collections). The collections are unmodifiable, keep the order of the JSON text and may contain
  `null` elements. Duplicate elements of a `Set` are dropped, keeping the first occurrence.
- `Optional<T>`, where `T` is one of the reference types above except `Optional`
  (e.g. `Optional<List<String>>` for an optional array), and `OptionalInt`, `OptionalLong`,
  `OptionalDouble`
- the JSON types `JsonValue`, `JsonObject`, `JsonArray`, `JsonString`, `JsonNumber` and
  `JsonBoolean`, for parts of a document whose structure is open. The value is passed on unchanged
  (and written unchanged); a value of another JSON type is rejected:

  ```java
  record Event(String type, JsonObject payload, Map<String, JsonValue> extras) {}
  ```

  As JSON values have no value semantics, `equals` of a record compares such components by
  identity, and a parsed value refers to the text of its whole document.
- any other class, with a registered decoder (see [Custom Types](#custom-types))

JSON `null` and missing keys are handled as follows:

| Component type                          | JSON `null`        | missing key        |
|-----------------------------------------|--------------------|--------------------|
| primitive                               | error              | error              |
| `String`, wrapper, record, collection, array | `null`        | error              |
| `JsonObject`, `JsonArray`, ...          | `null`             | error              |
| `JsonValue`                             | `JsonNull`         | error              |
| `Optional`, `OptionalInt`, ...          | empty              | empty              |
| collection element / map value          | `null` (empty for an optional element type) | -  |
| element of a primitive array            | error              | -                  |
| row of a multidimensional array         | `null`             | -                  |

A missing key is not an error for a component with `@JsonDefault` (its default value) and, with
`omitNulls(true)`, for a component of a reference type (`null`).

JSON `null` also terminates recursive records (`{ "value": "c", "next": null }`), which may also
recurse through collections (`record Tree(String value, List<Tree> children)`).

A JSON value of the wrong type (also inside an `Optional`) or a number that cannot be converted
results in a `JsonValueException`, as does a missing key or JSON `null` where the table says error.
If a record component type is not supported, an `UnsupportedOperationException` is thrown.

To protect against denial of service, the text of a JSON number mapped to `BigDecimal` or
`BigInteger` is limited to 1000 characters, and a `BigInteger` to 1000 digits (`1e999` is accepted,
`1e1000` is not).

Every mapping error contains the JSON path and the location of the offending value, e.g.
`128 cannot be represented as a byte. Path: "{items[2{qty". Location: line 5, position 14.`,
including the errors detected by the mapper itself (range checks, unknown enum constants and type
names, the number limits, invalid Base64, unknown members) and exceptions of decoders. As the path
is computed from the JSON text, values that were not parsed (e.g. created with `JsonObject.of`)
have none.

Exceptions of record constructors, e.g. of a validation in a compact constructor, are reported as a
`JsonValueException` as well, with the path of the JSON object and the original exception as the
cause, so `match` returns `null` for invalid values:

```java
record Age(int value) {
    Age {
        if (value < 0) throw new IllegalArgumentException("negative age");
    }
}
record Person(String name, Age age) {}

MAPPER.fromJson("""
    { "name": "Bob", "age": { "value": -1 } }
    """, Person.class);
// JsonValueException: Cannot create record Age: negative age. Path: "{age". Location: line 0, position 24.
```

## Benchmarks

The JMH benchmark `RecordMapperBench` is part of the test sources, so JMH is not a dependency of the
library. It is not run by the unit tests; to run it:

- with Maven: `mvn -Pbench test-compile exec:exec`, JMH options can be passed with
  `-Djmh.args="RecordMapperBench -f 1 -wi 1 -i 1"`
- from the IDE: run its `main` method as a Java application, after `mvn test-compile` (or with
  annotation processing enabled in the IDE), because JMH generates code from the benchmark

## What's missing

It's just a prototype so

- only records are supported: no other classes (e.g. JavaBeans), no interfaces other than sealed
  interfaces of records, and no components of type `Object`
- arrays of reference types (`String[]`, `Point[]`, ...), other collection types (`Collection<T>`,
  `ArrayList<T>`, ...) and maps with keys other than `String` or an enum are not supported
- decoders and encoders can only be registered for classes, not for parameterized types
  (`Money<EUR>`) or map keys, and are not discovered automatically (e.g. with `ServiceLoader`)
- generic sealed interfaces (`sealed interface Result<T> permits Ok, Err`) are not supported

## License

BSD 2-Clause License (see LICENSE).
