package dev.json.records;

import java21.util.json.Json;
import java21.util.json.JsonArray;
import java21.util.json.JsonObject;
import java21.util.json.JsonValue;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The {@code RecordMapper} interface defines a contract for mapping {@link JsonObject}
 * instances to Java {@link Record} instances, and records back to JSON (see Writing JSON
 * below).
 * <p>
 * The mapping process relies on matching keys in the {@code JsonObject} to the
 * component names of the target record (see JSON Names below for other names). It
 * supports basic data types and nested records, and other types with a {@link JsonDecoder}.
 * Only records are mapped, and sealed interfaces of records; other classes (e.g. JavaBeans),
 * other interfaces and components of type {@code Object} are not supported.
 * </p>
 * <p>
 * The mapper is the module {@code dev.json.records}; {@code requires dev.json.records;}
 * also gives access to the JSON API ({@code java21.util.json}). The records of a module need
 * not be opened, as they are accessed with the lookup passed to {@link #of(MethodHandles.Lookup)}.
 * </p>
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li><b>Type-Safe Mapping:</b> Converts {@code JsonObject} to a specific record class.</li>
 *   <li><b>Nested Record Support:</b> Capable of mapping JSON objects that contain
 *       nested JSON objects to records with components that are themselves records.</li>
 *   <li><b>Recursive Records:</b> Records may refer to themselves, directly
 *       ({@code record Node(String value, Node next)}) or through other records.
 *       The nesting depth of the mapped JSON is limited by the thread's stack size.</li>
 *   <li><b>Mandatory Fields:</b> By default, all record components are considered
 *       mandatory. If a corresponding key is missing in the {@code JsonObject},
 *       a {@link java21.util.json.JsonValueException} is thrown during mapping, unless the
 *       component has a {@link JsonDefault} value.</li>
 *   <li><b>Optional Fields:</b> Components of type {@code Optional<T>}, {@code OptionalInt},
 *       {@code OptionalLong} or {@code OptionalDouble} are empty if the key is missing
 *       or the value is JSON null.</li>
 * </ul>
 *
 * <h2>Supported Data Types for Record Components:</h2>
 * <ul>
 *   <li>{@code String} (from JSON string)</li>
 *   <li>{@code boolean} (from JSON boolean)</li>
 *   <li>{@code byte}, {@code short}, {@code int} and {@code long} (from JSON number, if it
 *       can be converted exactly, e.g. {@code 30} or {@code 30.0}, but not {@code 30.5}
 *       or a value out of range)</li>
 *   <li>{@code float} and {@code double} (from JSON number, rounded to the nearest value;
 *       a number beyond the finite range is rejected)</li>
 *   <li>{@code char} (from JSON string consisting of exactly one UTF-16 code unit)</li>
 *   <li>the wrapper types {@code Boolean}, {@code Byte}, {@code Short}, {@code Character},
 *       {@code Integer}, {@code Long}, {@code Float} and {@code Double}, converted like the
 *       corresponding primitive type</li>
 *   <li>{@code BigDecimal} (from JSON number, exactly as written, e.g. {@code 1.50} keeps
 *       its scale of 2; {@code -0.0} becomes {@code 0.0})</li>
 *   <li>{@code BigInteger} (from JSON number, if it is a whole number, e.g. {@code 1.0}
 *       or {@code 1e2}, but not {@code 1.5})</li>
 *   <li>enums (from JSON string with the exact name of a constant, or its {@link JsonName})</li>
 *   <li>{@code LocalDate}, {@code LocalTime}, {@code LocalDateTime}, {@code OffsetDateTime},
 *       {@code ZonedDateTime}, {@code Instant}, {@code Duration} and {@code Period} (from
 *       JSON string in ISO-8601 format, as parsed by their {@code parse} methods), and
 *       {@code UUID} and {@code URI} (from JSON string). These are predefined decoders that
 *       a registered {@link JsonDecoder} replaces.</li>
 *   <li>primitive arrays (from JSON array), e.g. {@code int[]} or {@code double[]}, whose
 *       elements are converted like the corresponding primitive components, except
 *       {@code char[]}, which is read from an array of single character strings, and
 *       {@code byte[]}, which is read from a Base64 string ({@link java.util.Base64},
 *       padding optional). Multidimensional arrays of them, e.g. {@code double[][]}, may
 *       be ragged and contain {@code null} rows. Arrays of reference types such as
 *       {@code String[]} are not supported, use {@code List<T>} instead. Each mapping
 *       creates new arrays; note that {@code equals} of a record compares array
 *       components by identity.</li>
 *   <li>Other {@code Record} types (from nested JSON objects)</li>
 *   <li>sealed interfaces of records (from JSON objects with a type member, see Sealed
 *       Interfaces below)</li>
 *   <li>{@code List<T>} and {@code Set<T>} (from JSON array) and {@code Map<K, T>}
 *       (from JSON object) with {@code String} or enum keys {@code K}, where {@code T}
 *       is one of the reference types listed here, including {@code Optional} and nested
 *       collections. The collections are unmodifiable, keep the order of the JSON text
 *       and may contain {@code null} elements. Duplicate elements of a {@code Set} are
 *       dropped.</li>
 *   <li>{@code Optional<T>} where {@code T} is one of the reference types above except
 *       {@code Optional}, e.g. {@code Optional<List<String>>} for an optional array, and
 *       {@code OptionalInt}, {@code OptionalLong}, {@code OptionalDouble}</li>
 *   <li>the JSON types {@code JsonValue}, {@code JsonObject}, {@code JsonArray},
 *       {@code JsonString}, {@code JsonNumber} and {@code JsonBoolean}, for parts of a
 *       document whose structure is open, e.g. {@code record Event(String type,
 *       JsonObject payload)}. The value is passed on unchanged, and written unchanged; a
 *       value of another JSON type is rejected. As JSON values have no value semantics,
 *       {@code equals} of a record compares such components by identity, and a parsed value
 *       refers to the text of its whole document.</li>
 *   <li>any other class, with a registered {@link JsonDecoder}</li>
 * </ul>
 * JSON null and missing keys are handled as follows:
 * <ul>
 *   <li>primitive components: JSON null and a missing key are rejected with a
 *       {@link java21.util.json.JsonValueException}</li>
 *   <li>{@code String}, wrapper, record, collection, array and JSON type components:
 *       JSON null maps to {@code null}, a missing key is rejected; except for
 *       {@code JsonValue}, which takes JSON null as a {@code JsonNull}</li>
 *   <li>optional components: JSON null and a missing key map to an empty optional;
 *       a value of the wrong type is still rejected</li>
 *   <li>collection elements and map values: JSON null maps to {@code null}, or to an
 *       empty optional for an element of an optional type</li>
 *   <li>elements of a primitive array: JSON null is rejected; rows of a multidimensional
 *       array: JSON null maps to {@code null}</li>
 * </ul>
 * A missing key is not rejected for a component with a {@link JsonDefault} value, and for a
 * component of a reference type with {@link Builder#omitNulls(boolean)}, see below.
 * To protect against denial of service, the text of a JSON number mapped to
 * {@code BigDecimal} or {@code BigInteger} is limited to 1000 characters, and a
 * {@code BigInteger} to 1000 digits (so {@code 1e999} is accepted, {@code 1e1000} is not).
 * <p>
 * Every mapping error is reported as a {@code JsonValueException} whose detail message
 * contains the JSON path and the location of the offending value, e.g.
 * <code>Path: "&#123;items[2&#123;qty". Location: line 5, position 14.</code>;
 * this includes the errors checked by this mapper itself (range errors of {@code byte},
 * {@code short}, {@code char} and {@code float}, unknown enum constants, fractional
 * {@code BigInteger} values, the number limits, invalid Base64, unknown type names and unknown
 * members) and exceptions of decoders. An exception of a record constructor, e.g. of a
 * validation in a compact constructor, is reported as well, with the path of the JSON object
 * and the exception as its cause, so {@link #match(JsonObject, Class)} returns {@code null}:
 * <pre>{@code
 * record Age(int value) {
 *     Age {
 *         if (value < 0) throw new IllegalArgumentException("negative age");
 *     }
 * }
 * // JsonValueException: Cannot create record Age: negative age. Path: ...
 * }</pre>
 * As the path is computed from the JSON text, values that were not parsed, e.g. created with
 * {@code JsonObject.of}, have none.
 *
 * <h2>Configuration:</h2>
 * {@link #of(MethodHandles.Lookup)} returns a mapper with the default configuration;
 * {@link #builder(MethodHandles.Lookup)} configures the naming strategy, decoders and
 * encoders, whether unknown JSON object members are an error, and whether null components
 * are left out:
 * <pre>{@code
 * static final RecordMapper MAPPER = RecordMapper.builder(MethodHandles.lookup())
 *         .naming(JsonNaming.SNAKE_CASE)
 *         .decoder(Currency.class, value -> Currency.getInstance(value.asString()))
 *         .encoder(Currency.class, currency -> JsonString.of(currency.getCurrencyCode()))
 *         .failOnUnknownMembers(true)
 *         .omitNulls(true)
 *         .build();
 * }</pre>
 * By default, a JSON object member that does not correspond to a component of the record
 * is ignored; with {@link Builder#failOnUnknownMembers(boolean)} it is an error. By default,
 * a component that is {@code null} is written as JSON null; with
 * {@link Builder#omitNulls(boolean)} it is left out, and a missing member of a component of
 * a reference type is read as {@code null}.
 *
 * <h2>Default Values and Ignored Components:</h2>
 * A {@link JsonDefault} annotation gives the value of a component, as JSON text, if its
 * member is missing; a {@link JsonIgnore} annotation excludes a component from the JSON form:
 * it is not written, and not read, but gets its default value, or {@code null}, zero,
 * {@code false} or an empty optional:
 * <pre>{@code
 * record User(String name,
 *             @JsonDefault("\"user\"") String role,
 *             @JsonDefault("[]") List<String> tags,
 *             @JsonIgnore String password) {}
 *
 * MAPPER.fromJson("{ \"name\": \"Alice\" }", User.class);   // User[name=Alice, role=user, tags=[], password=null]
 * }</pre>
 *
 * <h2>JSON Names:</h2>
 * By default, a record component is mapped from the JSON object member with the same
 * name. A {@link JsonNaming} strategy derives the member names from the component names,
 * e.g. {@code first_name} for {@code firstName} with {@link JsonNaming#SNAKE_CASE}, and a
 * {@link JsonName} annotation specifies the member name of a single component, taking
 * precedence over the strategy. Two components of a record must not be mapped from the same
 * member name, otherwise an {@link IllegalArgumentException} is thrown. The keys of a
 * {@code Map} are not affected.
 * <p>
 * An enum constant is read from and written as its name, unless it has a {@link JsonName}
 * annotation, e.g. {@code enum Color { @JsonName("red") RED, ... }}; this also holds for
 * enum keys of a {@code Map}.
 *
 * <h2>Custom Types:</h2>
 * A {@link JsonDecoder} registered for a class converts the JSON values of exactly that
 * class, wherever they occur. It takes precedence over the conversion of the mapper and
 * never gets JSON null, which is handled by the mapper as described above. Its counterpart
 * for writing is a {@link JsonEncoder}.
 *
 * <h2>Writing JSON:</h2>
 * {@link #toJson(Object)} and {@link #toJsonList(List)} write records as JSON, so that the
 * result can be read back into equal records; {@link #toJsonText(Object)} writes the JSON
 * text. A value whose static type is a sealed interface needs no cast, and any other value
 * that is written as a JSON object, e.g. a map, can be written as well. The members of a
 * record are written in the
 * order of its components, with the names derived as for reading. Values are written
 * according to their runtime type, so generic records need no {@link TypeRef}:
 * <ul>
 *   <li>{@code null} as JSON null, or a {@code null} component not at all with
 *       {@link Builder#omitNulls(boolean)}; an empty {@code Optional}, {@code OptionalInt},
 *       {@code OptionalLong} or {@code OptionalDouble} component is left out, and as an
 *       element of a collection or a map value written as JSON null</li>
 *   <li>strings, characters and enum constants (their name or {@link JsonName}) as JSON
 *       strings, booleans as JSON booleans</li>
 *   <li>components with {@link JsonIgnore} not at all</li>
 *   <li>JSON values ({@code JsonValue} and its subtypes) unchanged, a {@code JsonNull} as
 *       JSON null</li>
 *   <li>numbers as JSON numbers: a {@code float} with {@link Float#toString(float)}, so
 *       {@code 0.1f} is written as {@code 0.1}; {@code BigDecimal} keeps its scale. NaN and
 *       infinite values cannot be written ({@link IllegalArgumentException}).</li>
 *   <li>{@code List} and {@code Set} as JSON arrays, {@code Map} with {@code String} or enum
 *       keys as JSON objects</li>
 *   <li>primitive arrays and multidimensional arrays of them as JSON arrays, except
 *       {@code byte[]} as a Base64 string</li>
 *   <li>{@code java.time} types, {@code UUID} and {@code URI} with their {@code toString()}
 *       as JSON strings, and values of a class with a registered {@link JsonEncoder} with
 *       that encoder</li>
 *   <li>a record of a sealed interface always with its type as the first member, see
 *       Sealed Interfaces below</li>
 * </ul>
 * Values of other types, which could not be read back, result in an
 * {@link UnsupportedOperationException}. As records are immutable, a cycle can only occur
 * through a mutable collection or map; a collection or map that contains itself results in an
 * {@link IllegalArgumentException}. Errors below the top level contain the JSON path of the
 * offending value, e.g.
 * <code>NaN cannot be written as a JSON number. Path: "&#123;items[1&#123;price".</code>;
 * exceptions of encoders and record accessors are wrapped in an
 * {@code IllegalArgumentException} with that path.
 *
 * <h2>Sealed Interfaces:</h2>
 * A value of a sealed interface whose permitted subclasses are records, possibly through
 * nested sealed interfaces, is a JSON object with a member that holds the type of the
 * record:
 * <pre>{@code
 * sealed interface Shape permits Circle, Square {}
 * record Circle(double radius) implements Shape {}
 * record Square(double side) implements Shape {}
 *
 * // {"type": "Circle", "radius": 1.0}
 * }</pre>
 * The member is named {@code "type"} unless the sealed interface is annotated with
 * {@link JsonDiscriminator}, and the type is the simple name of the record class unless
 * the record is annotated with {@link JsonTypeName}. A sealed interface can be used
 * wherever a record can, including the top level: {@code fromTyped(json, Shape.class)}.
 * A record of a sealed interface is always written with its type, even where its own
 * class is expected; when it is read as its own class, the type member is ignored.
 * <p>
 * A missing or unknown type results in a {@link java21.util.json.JsonValueException}.
 * Duplicate type names, and a type member that is also the JSON name of a component,
 * result in an {@link IllegalArgumentException}. Generic sealed interfaces, generic
 * records of a sealed interface, and permitted subclasses that are neither records nor
 * sealed interfaces (e.g. enums) are not supported ({@link UnsupportedOperationException}).
 *
 * <h2>Generic Records:</h2>
 * A generic record, e.g. {@code record Page<T>(List<T> items, int total)}, is mapped with
 * its type arguments, which may be any of the reference types above:
 * <ul>
 *   <li>as a record component, collection element, etc., e.g.
 *       {@code record UserResponse(Page<User> users, String cursor)}</li>
 *   <li>at the top level with a {@link TypeRef}, e.g.
 *       {@code MAPPER.fromTyped(json, new TypeRef<Page<User>>() {})}, or in generic code
 *       with {@code TypeRef.of(Page.class, type)}</li>
 * </ul>
 * Generic records may be recursive, e.g. {@code record Tree<T>(T value, List<Tree<T>> children)}.
 * Not supported are a generic record without type arguments ({@code Page.class} or a raw
 * {@code Page} component), wildcards ({@code Page<?>}), and a generic record type that
 * expands infinitely, e.g. {@code record Weird<T>(Weird<List<T>> next)}.
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // Define a simple record
 * record UserRecord(String name, int age) {}
 *
 * // Obtain a RecordMapper instance (it must be a constant)
 * static final RecordMapper MAPPER = RecordMapper.of(MethodHandles.lookup());
 *
 * // Map JSON text to the UserRecord
 * UserRecord user = MAPPER.fromJson("""
 *     { "name": "Alice", "age": 30 }
 *     """, UserRecord.class);
 *
 * // Map an already parsed JsonObject
 * JsonObject userJson = (JsonObject) Json.parse(text);
 * UserRecord other = MAPPER.fromTyped(userJson, UserRecord.class);
 *
 * // Write the UserRecord as JSON text: {"name":"Alice","age":30}
 * String json = MAPPER.toJsonText(user);
 * }</pre>
 */
public interface RecordMapper {

    /**
     * Factory method to obtain an instance of {@code RecordMapper}.
     * <p>
     * The provided {@link MethodHandles.Lookup} encapsulates the access
     * rights of the caller, allowing the mapper to operate on non-public records.
     * </p>
     *
     * @param lookup A {@link MethodHandles.Lookup} instance that provides the
     *               context for reflective operations, for accessing
     *               record constructors. Must not be null.
     * @return A new instance of {@code RecordMapper} with the default configuration,
     *         like {@code builder(lookup).build()}.
     * @throws NullPointerException if {@code lookup} is null.
     */
    static RecordMapper of(MethodHandles.Lookup lookup) {
        return builder(lookup).build();
    }

    /**
     * Returns a builder for a {@code RecordMapper} with a configuration that differs from
     * the default, for example:
     * <pre>{@code
     * static final RecordMapper MAPPER = RecordMapper.builder(MethodHandles.lookup())
     *         .naming(JsonNaming.SNAKE_CASE)
     *         .build();
     * }</pre>
     *
     * @param lookup A {@link MethodHandles.Lookup} instance that provides the
     *               context for reflective operations, see {@link #of(MethodHandles.Lookup)}.
     *               Must not be null.
     * @return A new builder with the default configuration.
     * @throws NullPointerException if {@code lookup} is null.
     */
    static Builder builder(MethodHandles.Lookup lookup) {
        Objects.requireNonNull(lookup, "lookup is null");
        return new Builder(lookup);
    }

    /**
     * A builder for a {@link RecordMapper}. A builder is not thread-safe; the mappers it
     * builds are not affected by later changes of the builder.
     */
    final class Builder {
        private final MethodHandles.Lookup lookup;
        private JsonNaming naming = JsonNaming.IDENTITY;
        private boolean failOnUnknownMembers;
        private boolean omitNulls;
        private final Map<Class<?>, JsonDecoder<?>> decoders = new HashMap<>();
        private final Map<Class<?>, JsonEncoder<?>> encoders = new HashMap<>();

        private Builder(MethodHandles.Lookup lookup) {
            this.lookup = lookup;
        }

        /**
         * Sets the strategy that derives the names of the JSON object members from the
         * names of the record components, {@link JsonNaming#IDENTITY} by default.
         * A {@link JsonName} annotation on a component takes precedence.
         *
         * @param naming The naming strategy. Must not be null.
         * @return This builder.
         * @throws NullPointerException if {@code naming} is null.
         */
        public Builder naming(JsonNaming naming) {
            this.naming = Objects.requireNonNull(naming, "naming is null");
            return this;
        }

        /**
         * Sets whether a JSON object member that does not correspond to a component of the
         * record is an error, {@code false} by default. If {@code true}, such a member
         * results in a {@link java21.util.json.JsonValueException}, and {@code match} returns
         * {@code null}; this detects typos in member names. The type member of a record of a
         * sealed interface is never unknown, and the keys of a {@code Map} are not checked.
         *
         * @param failOnUnknownMembers Whether unknown members are an error.
         * @return This builder.
         */
        public Builder failOnUnknownMembers(boolean failOnUnknownMembers) {
            this.failOnUnknownMembers = failOnUnknownMembers;
            return this;
        }

        /**
         * Sets whether record components that are {@code null} are left out when writing,
         * {@code false} by default. If {@code true}, a missing member of a component of a
         * reference type is read as {@code null}, so that the result can be read back; a
         * missing member of a primitive component is still an error, and a
         * {@link JsonDefault} value takes precedence. Elements of collections and values of
         * maps are not affected, and neither is a {@code JsonNull} of a {@code JsonValue}
         * component.
         *
         * @param omitNulls Whether null components are left out.
         * @return This builder.
         */
        public Builder omitNulls(boolean omitNulls) {
            this.omitNulls = omitNulls;
            return this;
        }

        /**
         * Registers a decoder for the given class, see {@link JsonDecoder}. It is used for
         * values of exactly this class, not of its subclasses, and takes precedence over
         * the conversion of the mapper, including the predefined conversions such as those
         * of {@code java.time}. A later registration for the same class replaces an
         * earlier one.
         *
         * @param <T>     The type of the values.
         * @param type    The class of the values. Must not be null or a primitive type.
         * @param decoder The decoder. Must not be null.
         * @return This builder.
         * @throws NullPointerException     if {@code type} or {@code decoder} is null.
         * @throws IllegalArgumentException if {@code type} is a primitive type.
         */
        public <T> Builder decoder(Class<T> type, JsonDecoder<? extends T> decoder) {
            Objects.requireNonNull(type, "type is null");
            Objects.requireNonNull(decoder, "decoder is null");
            if (type.isPrimitive()) {
                throw new IllegalArgumentException("A decoder cannot be registered for the primitive type "
                        + type.getName() + ", register it for the wrapper type");
            }
            decoders.put(type, decoder);
            return this;
        }

        /**
         * Registers an encoder for the given class, see {@link JsonEncoder}. It is used for
         * values whose runtime class is exactly this class and takes precedence over the
         * way the mapper writes them, including the predefined encoders such as those of
         * {@code java.time}. A later registration for the same class replaces an earlier one.
         *
         * @param <T>     The type of the values.
         * @param type    The class of the values. Must not be null or a primitive type.
         * @param encoder The encoder. Must not be null.
         * @return This builder.
         * @throws NullPointerException     if {@code type} or {@code encoder} is null.
         * @throws IllegalArgumentException if {@code type} is a primitive type.
         */
        public <T> Builder encoder(Class<T> type, JsonEncoder<? super T> encoder) {
            Objects.requireNonNull(type, "type is null");
            Objects.requireNonNull(encoder, "encoder is null");
            if (type.isPrimitive()) {
                throw new IllegalArgumentException("An encoder cannot be registered for the primitive type "
                        + type.getName() + ", register it for the wrapper type");
            }
            encoders.put(type, encoder);
            return this;
        }

        /**
         * {@return a new {@code RecordMapper} with the configuration of this builder}
         */
        public RecordMapper build() {
            return RecordMapperImpl.of(lookup, naming, failOnUnknownMembers, omitNulls, Map.copyOf(decoders),
                    Map.copyOf(encoders));
        }
    }

    /**
     * Maps the given {@link JsonObject} to an instance of the specified type, a record
     * class, a sealed interface (see Sealed Interfaces above) or a class with a registered
     * {@link JsonDecoder}.
     * <p>
     * For a record class, the mapping process involves:
     * <ol>
     *   <li>Retrieving the record components of the record class.</li>
     *   <li>For each component, extracting the corresponding value from the
     *       {@code JsonObject} using the JSON name of the component as the key
     *       (case-sensitively).</li>
     *   <li>Converting the JSON values to the types of the record components
     *       (see the supported types above), using the conversion methods of
     *       {@link java21.util.json.JsonValue}.</li>
     *   <li>Invoking the canonical constructor of the record class with the
     *       extracted and converted values.</li>
     * </ol>
     *
     * @param <T>    The type to map to.
     * @param object The {@link JsonObject} to map from. Must not be null.
     *               Its structure should align with the target record's components.
     * @param type   The record class, sealed interface or class with a registered decoder.
     *               Must not be null.
     * @return An instance of the type populated with data from the {@code JsonObject}.
     * @throws NullPointerException          if {@code object} or {@code type} is null.
     * @throws java21.util.json.JsonValueException
     *                                       if a required key is missing, a value has the
     *                                       wrong JSON type (for example a JSON number for a
     *                                       {@code String} component, or JSON null for a
     *                                       primitive component), or a
     *                                       number cannot be converted exactly. The detail
     *                                       message contains the JSON path of the value.
     * @throws UnsupportedOperationException if {@code type} is neither a record nor a sealed
     *                                       interface and has no registered decoder, or a
     *                                       record component has a type that is not supported
     *                                       by this mapper's implementation.
     */
    <T> T fromTyped(JsonObject object, Class<T> type);

    /**
     * Maps the given {@link JsonObject} to an instance of the record type referenced by
     * {@code type}, like {@link #fromTyped(JsonObject, Class)}, but also for a generic
     * record with type arguments:
     * <pre>{@code
     * Page<User> page = MAPPER.fromTyped(json, new TypeRef<Page<User>>() {});
     * }</pre>
     *
     * @param <T>    The type of the record to map to.
     * @param object The {@link JsonObject} to map from. Must not be null.
     * @param type   The record type, see {@link TypeRef}. Must not be null.
     * @return An instance of the record type populated with data from the {@code JsonObject}.
     * @throws NullPointerException          if {@code object} or {@code type} is null.
     * @throws java21.util.json.JsonValueException
     *                                       if the {@code JsonObject} cannot be mapped to the
     *                                       record type, see {@link #fromTyped(JsonObject, Class)}.
     * @throws UnsupportedOperationException if a record component has a type that is
     *                                       not supported by this mapper's implementation.
     */
    <T extends Record> T fromTyped(JsonObject object, TypeRef<T> type);

    /**
     * Maps each element of the given {@link JsonArray} to an instance of the specified
     * type, like a record component of type {@code List<T>}: every element is mapped as by
     * {@link #fromTyped(JsonObject, Class)}, and JSON null maps to a {@code null} element.
     *
     * @param <T>   The type of the elements to map to.
     * @param array The {@link JsonArray} to map from. Must not be null.
     * @param type  The record class, sealed interface or class with a registered decoder.
     *              Must not be null.
     * @return An unmodifiable list, in the order of the JSON array, that may contain
     *         {@code null} elements.
     * @throws NullPointerException          if {@code array} or {@code type} is null.
     * @throws java21.util.json.JsonValueException
     *                                       if an element cannot be mapped to the type. The
     *                                       detail message contains the JSON path of the value.
     * @throws UnsupportedOperationException if {@code type} is not supported, see
     *                                       {@link #fromTyped(JsonObject, Class)}.
     */
    <T> List<T> fromTypedList(JsonArray array, Class<T> type);

    /**
     * Maps each element of the given {@link JsonArray} to an instance of the record type
     * referenced by {@code type}, like {@link #fromTypedList(JsonArray, Class)}, but also
     * for a generic record with type arguments.
     *
     * @param <T>   The type of the records to map to.
     * @param array The {@link JsonArray} to map from. Must not be null.
     * @param type  The record type, see {@link TypeRef}. Must not be null.
     * @return An unmodifiable list, in the order of the JSON array, that may contain
     *         {@code null} elements.
     * @throws NullPointerException          if {@code array} or {@code type} is null.
     * @throws java21.util.json.JsonValueException
     *                                       if an element cannot be mapped, see
     *                                       {@link #fromTypedList(JsonArray, Class)}.
     * @throws UnsupportedOperationException if a record component has a type that is
     *                                       not supported by this mapper's implementation.
     */
    <T extends Record> List<T> fromTypedList(JsonArray array, TypeRef<T> type);

    /**
     * Maps the given {@link JsonObject} to an instance of the specified type like
     * {@link #fromTyped(JsonObject, Class)}, but returns {@code null} instead of throwing a
     * {@link java21.util.json.JsonValueException} if the {@code JsonObject} does not match
     * the type. Designed for use with record patterns, also for sealed interfaces:
     * <pre>{@code
     * if (MAPPER.match(json, UserRecord.class) instanceof UserRecord(String name, int age)) {
     *   ...
     * }
     * if (MAPPER.match(json, Shape.class) instanceof Shape shape) {
     *   switch (shape) {   // exhaustive for the sealed interface Shape
     *     case Circle(double radius) -> ...
     *     case Square(double side) -> ...
     *   }
     * }
     * }</pre>
     *
     * @param object The {@link JsonObject} to map from. Must not be null.
     * @param type   The record class, sealed interface or class with a registered decoder.
     *               Must not be null.
     * @return An instance of the type, or {@code null} if the {@code JsonObject} does not
     *         match the type.
     * @throws NullPointerException          if {@code object} or {@code type} is null.
     * @throws UnsupportedOperationException if {@code type} is not supported, see
     *                                       {@link #fromTyped(JsonObject, Class)}.
     */
    Object match(JsonObject object, Class<?> type);

    /**
     * Maps the given {@link JsonObject} to an instance of the record type referenced by
     * {@code type} like {@link #fromTyped(JsonObject, TypeRef)}, but returns {@code null}
     * instead of throwing a {@link java21.util.json.JsonValueException} if the
     * {@code JsonObject} does not match the record type.
     *
     * @param object The {@link JsonObject} to map from. Must not be null.
     * @param type   The record type, see {@link TypeRef}. Must not be null.
     * @return An instance of the record type, or {@code null} if the {@code JsonObject}
     *         does not match the record type.
     * @throws NullPointerException          if {@code object} or {@code type} is null.
     * @throws UnsupportedOperationException if a record component has a type that is
     *                                       not supported by this mapper's implementation.
     */
    Object match(JsonObject object, TypeRef<?> type);

    /**
     * Writes the given value as a JSON object, the counterpart of
     * {@link #fromTyped(JsonObject, Class)}. The value is usually a record, possibly of a
     * sealed interface, but may be any value that is written as a JSON object, such as a map
     * or a value whose {@link JsonEncoder} returns an object. The result can be read back
     * into an equal record (except for array components, which {@code equals} of a record
     * compares by identity). The members of a record are written in the order of its
     * components, with the names derived as for reading. Values are written according to
     * their runtime type, so generic records need no {@link TypeRef}; see "Writing JSON"
     * above for the details. The JSON text is obtained with {@code toString()} or
     * {@link #toJsonText(Object)}, or {@code Json.toDisplayString(value, "  ")} for an
     * indented text.
     *
     * @param value The value to write, usually a record. Must not be null.
     * @return The JSON object.
     * @throws NullPointerException          if {@code value} is null.
     * @throws UnsupportedOperationException if a value has a type that cannot be written,
     *                                       because it could not be read back.
     * @throws IllegalArgumentException      if the value is not written as a JSON object,
     *                                       a floating point value is not finite, which
     *                                       JSON cannot represent, a collection or map
     *                                       contains itself, or an encoder or a record
     *                                       accessor throws an exception.
     */
    JsonObject toJson(Object value);

    /**
     * Writes the given values as a JSON array, the counterpart of
     * {@link #fromTypedList(JsonArray, Class)}; each value is written according to its
     * runtime type as described in "Writing JSON" above, and a {@code null} element as
     * JSON null.
     *
     * @param values The values to write, usually records. Must not be null.
     * @return The JSON array.
     * @throws NullPointerException          if {@code values} is null.
     * @throws UnsupportedOperationException if a value has a type that cannot be written.
     * @throws IllegalArgumentException      if a floating point value is not finite, a
     *                                       collection or map contains itself, or an encoder
     *                                       or a record accessor throws an exception.
     */
    JsonArray toJsonList(List<?> values);

    /**
     * Parses the given JSON text, which must be a JSON object, and maps it like
     * {@link #fromTyped(JsonObject, Class)}:
     * <pre>{@code
     * User user = MAPPER.fromJson("""
     *     { "name": "Alice", "age": 30 }
     *     """, User.class);
     * }</pre>
     *
     * @param <T>  The type to map to.
     * @param json The JSON text. Must not be null.
     * @param type The record class, sealed interface or class with a registered decoder.
     *             Must not be null.
     * @return An instance of the type populated with data from the JSON text.
     * @throws NullPointerException          if {@code json} or {@code type} is null.
     * @throws java21.util.json.JsonParseException
     *                                       if the JSON text is not valid JSON.
     * @throws java21.util.json.JsonValueException
     *                                       if the JSON text is not a JSON object or cannot be
     *                                       mapped, see {@link #fromTyped(JsonObject, Class)}.
     * @throws UnsupportedOperationException if {@code type} is not supported.
     */
    default <T> T fromJson(String json, Class<T> type) {
        return fromTyped(object(Json.parse(json)), type);
    }

    /**
     * Parses the given JSON text, which must be a JSON object, and maps it like
     * {@link #fromTyped(JsonObject, TypeRef)}, e.g. to a generic record.
     *
     * @param <T>  The type of the record to map to.
     * @param json The JSON text. Must not be null.
     * @param type The record type, see {@link TypeRef}. Must not be null.
     * @return An instance of the record type populated with data from the JSON text.
     * @throws NullPointerException          if {@code json} or {@code type} is null.
     * @throws java21.util.json.JsonParseException
     *                                       if the JSON text is not valid JSON.
     * @throws java21.util.json.JsonValueException
     *                                       if the JSON text is not a JSON object or cannot be
     *                                       mapped.
     * @throws UnsupportedOperationException if a record component has a type that is
     *                                       not supported by this mapper's implementation.
     */
    default <T extends Record> T fromJson(String json, TypeRef<T> type) {
        return fromTyped(object(Json.parse(json)), type);
    }

    /**
     * Parses the given JSON text, which must be a JSON array, and maps it like
     * {@link #fromTypedList(JsonArray, Class)}.
     *
     * @param <T>  The type of the elements to map to.
     * @param json The JSON text. Must not be null.
     * @param type The record class, sealed interface or class with a registered decoder.
     *             Must not be null.
     * @return An unmodifiable list, in the order of the JSON array, that may contain
     *         {@code null} elements.
     * @throws NullPointerException          if {@code json} or {@code type} is null.
     * @throws java21.util.json.JsonParseException
     *                                       if the JSON text is not valid JSON.
     * @throws java21.util.json.JsonValueException
     *                                       if the JSON text is not a JSON array or an element
     *                                       cannot be mapped.
     * @throws UnsupportedOperationException if {@code type} is not supported.
     */
    default <T> List<T> fromJsonList(String json, Class<T> type) {
        return fromTypedList(array(Json.parse(json)), type);
    }

    /**
     * Parses the given JSON text, which must be a JSON array, and maps it like
     * {@link #fromTypedList(JsonArray, TypeRef)}, e.g. to generic records.
     *
     * @param <T>  The type of the records to map to.
     * @param json The JSON text. Must not be null.
     * @param type The record type, see {@link TypeRef}. Must not be null.
     * @return An unmodifiable list, in the order of the JSON array, that may contain
     *         {@code null} elements.
     * @throws NullPointerException          if {@code json} or {@code type} is null.
     * @throws java21.util.json.JsonParseException
     *                                       if the JSON text is not valid JSON.
     * @throws java21.util.json.JsonValueException
     *                                       if the JSON text is not a JSON array or an element
     *                                       cannot be mapped.
     * @throws UnsupportedOperationException if a record component has a type that is
     *                                       not supported by this mapper's implementation.
     */
    default <T extends Record> List<T> fromJsonList(String json, TypeRef<T> type) {
        return fromTypedList(array(Json.parse(json)), type);
    }

    /**
     * Writes the given value as compact JSON text, like {@code toJson(value).toString()}.
     * An indented text is obtained with {@code Json.toDisplayString(toJson(value), "  ")}.
     *
     * @param value The value to write, usually a record. Must not be null.
     * @return The JSON text of a JSON object.
     * @throws NullPointerException          if {@code value} is null.
     * @throws UnsupportedOperationException if a value has a type that cannot be written.
     * @throws IllegalArgumentException      if the value cannot be written, see
     *                                       {@link #toJson(Object)}.
     */
    default String toJsonText(Object value) {
        return toJson(value).toString();
    }

    private static JsonObject object(JsonValue value) {
        if (value instanceof JsonObject object) {
            return object;
        }
        // throws the JsonValueException of the JSON API, e.g. "JsonArray is not a JsonObject."
        value.asMap();
        throw new AssertionError("not a JSON object: " + value);
    }

    private static JsonArray array(JsonValue value) {
        if (value instanceof JsonArray array) {
            return array;
        }
        // throws the JsonValueException of the JSON API, e.g. "JsonObject is not a JsonArray."
        value.asList();
        throw new AssertionError("not a JSON array: " + value);
    }
}
