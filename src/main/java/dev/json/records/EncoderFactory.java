package dev.json.records;

import java.io.Serial;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.UndeclaredThrowableException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

import java21.util.json.JsonArray;
import java21.util.json.JsonBoolean;
import java21.util.json.JsonNull;
import java21.util.json.JsonNumber;
import java21.util.json.JsonObject;
import java21.util.json.JsonString;
import java21.util.json.JsonValue;

import static java.lang.invoke.MethodType.methodType;

// Writes records as JSON values. Each record class gets a RecordEncoder; all other values are
// written according to their runtime type, so that type variables need no TypeRef. Everything
// that is written can be read back by the RecordMapper.
final class EncoderFactory {

    // encoders of the primitive component types, of type (primitive)JsonValue
    private static final Map<Class<?>, MethodHandle> PRIMITIVES;
    private static final MethodHandle ACCESSOR_FAILED;

    static {
        var lookup = MethodHandles.lookup();
        try {
            ACCESSOR_FAILED = lookup.findStatic(EncoderFactory.class, "accessorFailed",
                    methodType(Object.class, String.class, Class.class, Throwable.class));
            var longNumber = lookup.findStatic(JsonNumber.class, "of", methodType(JsonNumber.class, long.class))
                    .asType(methodType(JsonValue.class, long.class));
            PRIMITIVES = Map.of(
                    boolean.class, lookup.findStatic(JsonBoolean.class, "of", methodType(JsonBoolean.class, boolean.class))
                            .asType(methodType(JsonValue.class, boolean.class)),
                    byte.class, longNumber.asType(methodType(JsonValue.class, byte.class)),
                    short.class, longNumber.asType(methodType(JsonValue.class, short.class)),
                    char.class, lookup.findStatic(EncoderFactory.class, "encodeChar",
                            methodType(JsonValue.class, char.class)),
                    int.class, longNumber.asType(methodType(JsonValue.class, int.class)),
                    long.class, longNumber,
                    float.class, lookup.findStatic(EncoderFactory.class, "encodeFloat",
                            methodType(JsonValue.class, float.class)),
                    double.class, lookup.findStatic(EncoderFactory.class, "encodeDouble",
                            methodType(JsonValue.class, double.class)));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    // Predefined encoders for the types with predefined decoders: their standard text form
    // (ISO-8601 for java.time); encoders registered by the user replace them
    private static final JsonEncoder<Object> TO_STRING = value -> JsonString.of(value.toString());
    static final Map<Class<?>, JsonEncoder<?>> DEFAULT_ENCODERS = Map.of(
            LocalDate.class, TO_STRING,
            LocalTime.class, TO_STRING,
            LocalDateTime.class, TO_STRING,
            OffsetDateTime.class, TO_STRING,
            ZonedDateTime.class, TO_STRING,
            Instant.class, TO_STRING,
            Duration.class, TO_STRING,
            Period.class, TO_STRING,
            UUID.class, TO_STRING,
            URI.class, TO_STRING);

    // components of these types are left out if they are empty
    private static final Set<Class<?>> OPTIONALS =
            Set.of(Optional.class, OptionalInt.class, OptionalLong.class, OptionalDouble.class);

    private final MethodHandles.Lookup lookup;
    private final JsonNaming naming;
    // whether record components that are null are left out
    private final boolean omitNulls;
    // the registered encoders by exact class, including the predefined ones
    private final Map<Class<?>, JsonEncoder<?>> encoders;

    private final ClassValue<RecordEncoder> recordEncoders = new ClassValue<>() {
        @Override
        protected RecordEncoder computeValue(Class<?> type) {
            return recordEncoder(type);
        }
    };

    EncoderFactory(MethodHandles.Lookup lookup, JsonNaming naming, boolean omitNulls,
            Map<Class<?>, JsonEncoder<?>> encoders) {
        this.lookup = lookup;
        this.naming = naming;
        this.omitNulls = omitNulls;
        this.encoders = encoders;
    }

    /*private*/ static JsonValue encodeChar(char value) {
        return JsonString.of(String.valueOf(value));
    }

    // Float.toString, so that 0.1f is written as 0.1 and not as 0.10000000149011612
    /*private*/ static JsonValue encodeFloat(float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(value + " cannot be written as a JSON number");
        }
        return JsonNumber.of(Float.toString(value));
    }

    /*private*/ static JsonValue encodeDouble(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(value + " cannot be written as a JSON number");
        }
        return JsonNumber.of(value);
    }

    // An exception of a record accessor is wrapped like the one of an encoder, so that the
    // error of the component gets the JSON path; errors are passed on
    /*private*/ static Object accessorFailed(String component, Class<?> type, Throwable e) {
        if (e instanceof Error error) {
            throw error;
        }
        throw new IllegalArgumentException("Cannot read component " + component + " of record " + type.getName()
                + ": " + e.getMessage(), e);
    }

    // Carries an error from where it occurred to the top level and collects the JSON path on
    // the way, so that the normal path has no costs besides the exception handlers
    private static final class PathException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        private final RuntimeException error;
        // the segments of the path, the innermost first, e.g. "{price", "[1", "{items"
        private final ArrayList<String> segments = new ArrayList<>();

        private PathException(RuntimeException error) {
            super(null, null, false, false);
            this.error = error;
        }
    }

    // adds a segment of the JSON path to an error that is propagated to the top level
    private static PathException at(RuntimeException e, String segment) {
        var pathException = e instanceof PathException existing ? existing : new PathException(e);
        pathException.segments.add(segment);
        return pathException;
    }

    // Returns the error with the JSON path appended to its message, in the format of the JSON
    // API, e.g. 'NaN cannot be written as a JSON number. Path: "{items[1{price".'; the error
    // keeps its type, cause and stack trace
    private static RuntimeException withPath(PathException e) {
        var error = e.error;
        var path = new StringBuilder();
        for (int i = e.segments.size() - 1; i >= 0; i--) {
            path.append(e.segments.get(i));
        }
        var message = Objects.requireNonNullElse(error.getMessage(), "");
        message = (message.endsWith(".") ? message : message + ".") + " Path: \"" + path + "\".";
        RuntimeException result;
        if (error.getClass() == IllegalArgumentException.class) {
            result = new IllegalArgumentException(message, error.getCause());
        } else if (error.getClass() == UnsupportedOperationException.class) {
            result = new UnsupportedOperationException(message, error.getCause());
        } else if (error.getClass() == NullPointerException.class) {
            result = new NullPointerException(message);
            if (error.getCause() != null) {
                result.initCause(error.getCause());
            }
        } else {
            return error;
        }
        result.setStackTrace(error.getStackTrace());
        return result;
    }

    // The collections and maps that are currently written, from the innermost one outwards.
    // Records are immutable, so a value can only contain itself through a mutable collection
    // or map; such a cycle is detected when the same instance is entered again.
    private record Containers(Object container, Containers parent) {}

    private static Containers enter(Object container, Containers parents) {
        for (var containers = parents; containers != null; containers = containers.parent()) {
            if (containers.container() == container) {
                throw new IllegalArgumentException("Cycle: a " + container.getClass().getName() + " contains itself.");
            }
        }
        return new Containers(container, parents);
    }

    // How a component is written
    private enum Kind {
        ENCODED,  // the getter returns the JSON value (primitive components)
        OPTIONAL, // an empty optional is left out
        VALUE     // written according to the runtime type
    }

    // the getter is of type (Record)Object
    private record Component(String name, MethodHandle getter, Kind kind) {}

    // discriminator and typeName are null, unless the record is a subtype of a sealed interface
    private record RecordEncoder(String discriminator, JsonValue typeName, Component[] components) {}

    private RecordEncoder recordEncoder(Class<?> type) {
        var recordComponents = type.getRecordComponents();
        var names = RecordMapperImpl.jsonNames(type, recordComponents, naming);
        var components = new ArrayList<Component>(recordComponents.length);
        for (int i = 0; i < recordComponents.length; i++) {
            if (names[i] == null) {
                // @JsonIgnore
                continue;
            }
            var recordComponent = recordComponents[i];
            MethodHandle accessor;
            try {
                accessor = lookup.unreflect(recordComponent.getAccessor());
            } catch (IllegalAccessException e) {
                throw (IllegalAccessError) new IllegalAccessError().initCause(e);
            }
            var componentType = recordComponent.getType();
            accessor = accessor.asType(methodType(componentType, Record.class));
            var failed = MethodHandles.insertArguments(ACCESSOR_FAILED, 0, recordComponent.getName(), type)
                    .asType(methodType(componentType, Throwable.class));
            accessor = MethodHandles.catchException(accessor, Throwable.class,
                    MethodHandles.dropArguments(failed, 1, Record.class));
            var primitive = PRIMITIVES.get(componentType);
            Kind kind;
            if (primitive != null) {
                accessor = MethodHandles.filterReturnValue(accessor, primitive);
                kind = Kind.ENCODED;
            } else {
                kind = OPTIONALS.contains(componentType) ? Kind.OPTIONAL : Kind.VALUE;
            }
            components.add(new Component(names[i], accessor.asType(methodType(Object.class, Record.class)), kind));
        }
        // a record of a sealed interface is always written with its type, so that it can be read
        // back as a value of the interface
        var discriminator = SealedTypes.discriminator(type);
        if (discriminator.isEmpty()) {
            return new RecordEncoder(null, null, components.toArray(Component[]::new));
        }
        var name = discriminator.get().name();
        if (Arrays.asList(names).contains(name)) {
            throw new IllegalArgumentException("The discriminator \"" + name + "\" of record " + type.getName()
                    + " is also the JSON name of one of its components");
        }
        return new RecordEncoder(name, JsonString.of(discriminator.get().typeName()),
                components.toArray(Component[]::new));
    }

    private JsonObject encodeRecord(Record record, Containers parents) {
        var recordEncoder = recordEncoders.get(record.getClass());
        var components = recordEncoder.components();
        var members = LinkedHashMap.<String, JsonValue>newLinkedHashMap(components.length + 1);
        if (recordEncoder.discriminator() != null) {
            members.put(recordEncoder.discriminator(), recordEncoder.typeName());
        }
        for (var component : components) {
            try {
                // exceptions of the accessor are wrapped by the getter (accessorFailed)
                var value = (Object) component.getter().invokeExact(record);
                switch (component.kind()) {
                    case ENCODED -> members.put(component.name(), (JsonValue) value);
                    case OPTIONAL -> {
                        if (!isEmptyOptional(value)) {
                            members.put(component.name(), encodeValue(value, parents));
                        }
                    }
                    case VALUE -> {
                        if (value != null || !omitNulls) {
                            members.put(component.name(), encodeValue(value, parents));
                        }
                    }
                }
            } catch (RuntimeException e) {
                throw at(e, "{" + component.name());
            } catch (Error e) {
                throw e;
            } catch (Throwable e) {
                throw new UndeclaredThrowableException(e);
            }
        }
        return JsonObject.of(members);
    }

    private static boolean isEmptyOptional(Object value) {
        return switch (value) {
            case Optional<?> optional -> optional.isEmpty();
            case OptionalInt optional -> optional.isEmpty();
            case OptionalLong optional -> optional.isEmpty();
            case OptionalDouble optional -> optional.isEmpty();
            case null, default -> false;
        };
    }

    // writes a value at the top level, which must be written as a JSON object: a record, a map,
    // or a value whose encoder returns an object
    JsonObject toJson(Object value) {
        JsonValue json;
        try {
            json = encodeValue(value, null);
        } catch (PathException e) {
            throw withPath(e);
        }
        if (json instanceof JsonObject object) {
            return object;
        }
        var kind = switch (json) {
            case JsonString ignored -> "a JSON string";
            case JsonNumber ignored -> "a JSON number";
            case JsonBoolean ignored -> "a JSON boolean";
            case JsonArray ignored -> "a JSON array";
            default -> "JSON null";
        };
        throw new IllegalArgumentException(value.getClass().getName() + " is not written as a JSON object but as "
                + kind);
    }

    // writes the values as a JSON array, the elements according to their runtime type
    JsonArray toJsonList(List<?> values) {
        // the list itself is a container, as it might contain itself
        var parents = enter(values, null);
        var elements = new ArrayList<JsonValue>(values.size());
        int index = 0;
        try {
            for (var value : values) {
                try {
                    elements.add(encodeValue(value, parents));
                } catch (RuntimeException e) {
                    throw at(e, "[" + index);
                }
                index++;
            }
        } catch (PathException e) {
            throw withPath(e);
        }
        return JsonArray.of(elements);
    }

    // Writes a value according to its runtime type. An error below the top level is thrown as
    // a PathException, which the top level (toJson, toJsonList) converts back.
    private JsonValue encodeValue(Object value, Containers parents) {
        if (value == null) {
            return JsonNull.of();
        }
        var encoder = encoders.get(value.getClass());
        if (encoder != null) {
            return encodeWith(encoder, value);
        }
        return switch (value) {
            // JSON values are written unchanged, JsonNull as JSON null
            case JsonValue json -> json;
            case String string -> JsonString.of(string);
            case Boolean bool -> JsonBoolean.of(bool);
            case Integer number -> JsonNumber.of(number);
            case Long number -> JsonNumber.of(number);
            case Short number -> JsonNumber.of(number);
            case Byte number -> JsonNumber.of(number);
            case Double number -> encodeDouble(number);
            case Float number -> encodeFloat(number);
            case Character character -> encodeChar(character);
            case BigDecimal number -> JsonNumber.of(number.toString());
            case BigInteger number -> JsonNumber.of(number.toString());
            case Enum<?> constant -> JsonString.of(EnumNames.name(constant));
            case Record record -> encodeRecord(record, parents);
            case List<?> list -> encodeCollection(list, parents);
            case Set<?> set -> encodeCollection(set, parents);
            case Map<?, ?> map -> encodeMap(map, parents);
            // an optional element or map value: an empty optional is read back from JSON null
            case Optional<?> optional -> optional.isPresent() ? encodeValue(optional.get(), parents) : JsonNull.of();
            case OptionalInt optional -> optional.isPresent() ? JsonNumber.of(optional.getAsInt()) : JsonNull.of();
            case OptionalLong optional -> optional.isPresent() ? JsonNumber.of(optional.getAsLong()) : JsonNull.of();
            case OptionalDouble optional -> optional.isPresent() ? encodeDouble(optional.getAsDouble()) : JsonNull.of();
            case byte[] array -> JsonString.of(Base64.getEncoder().encodeToString(array));
            case boolean[] array -> {
                var values = new ArrayList<JsonValue>(array.length);
                for (var element : array) {
                    values.add(JsonBoolean.of(element));
                }
                yield JsonArray.of(values);
            }
            case short[] array -> {
                var values = new ArrayList<JsonValue>(array.length);
                for (var element : array) {
                    values.add(JsonNumber.of(element));
                }
                yield JsonArray.of(values);
            }
            case char[] array -> {
                var values = new ArrayList<JsonValue>(array.length);
                for (var element : array) {
                    values.add(encodeChar(element));
                }
                yield JsonArray.of(values);
            }
            case int[] array -> {
                var values = new ArrayList<JsonValue>(array.length);
                for (var element : array) {
                    values.add(JsonNumber.of(element));
                }
                yield JsonArray.of(values);
            }
            case long[] array -> {
                var values = new ArrayList<JsonValue>(array.length);
                for (var element : array) {
                    values.add(JsonNumber.of(element));
                }
                yield JsonArray.of(values);
            }
            case float[] array -> {
                var values = new ArrayList<JsonValue>(array.length);
                for (int i = 0; i < array.length; i++) {
                    try {
                        values.add(encodeFloat(array[i]));
                    } catch (RuntimeException e) {
                        throw at(e, "[" + i);
                    }
                }
                yield JsonArray.of(values);
            }
            case double[] array -> {
                var values = new ArrayList<JsonValue>(array.length);
                for (int i = 0; i < array.length; i++) {
                    try {
                        values.add(encodeDouble(array[i]));
                    } catch (RuntimeException e) {
                        throw at(e, "[" + i);
                    }
                }
                yield JsonArray.of(values);
            }
            // multidimensional arrays of a primitive type, e.g. double[][]; they cannot contain
            // themselves, as the rows are of another type
            case Object[] array when isMultidimensionalPrimitiveArray(array.getClass()) -> {
                var values = new ArrayList<JsonValue>(array.length);
                for (int i = 0; i < array.length; i++) {
                    try {
                        values.add(encodeValue(array[i], parents));
                    } catch (RuntimeException e) {
                        throw at(e, "[" + i);
                    }
                }
                yield JsonArray.of(values);
            }
            default -> throw new UnsupportedOperationException("Unsupported type: " + value.getClass().getTypeName());
        };
    }

    // Calls a registered encoder; any runtime exception is wrapped in an IllegalArgumentException,
    // so that it gets the JSON path
    @SuppressWarnings("unchecked")
    private static JsonValue encodeWith(JsonEncoder<?> encoder, Object value) {
        JsonValue result;
        try {
            result = ((JsonEncoder<Object>) encoder).toJson(value);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Cannot write value of type " + value.getClass().getName() + ": "
                    + e.getMessage(), e);
        }
        if (result == null) {
            throw new NullPointerException("The encoder for " + value.getClass().getName() + " returned null.");
        }
        return result;
    }

    private static boolean isMultidimensionalPrimitiveArray(Class<?> type) {
        var elementType = type.getComponentType();
        if (!elementType.isArray()) {
            return false;
        }
        while (elementType.isArray()) {
            elementType = elementType.getComponentType();
        }
        return elementType.isPrimitive();
    }

    private JsonArray encodeCollection(Collection<?> collection, Containers parents) {
        var containers = enter(collection, parents);
        var values = new ArrayList<JsonValue>(collection.size());
        int index = 0;
        for (var element : collection) {
            try {
                values.add(encodeValue(element, containers));
            } catch (RuntimeException e) {
                throw at(e, "[" + index);
            }
            index++;
        }
        return JsonArray.of(values);
    }

    // the keys of a JSON object are strings, so only String and enum keys can be read back
    private JsonObject encodeMap(Map<?, ?> map, Containers parents) {
        var containers = enter(map, parents);
        var members = LinkedHashMap.<String, JsonValue>newLinkedHashMap(map.size());
        for (var entry : map.entrySet()) {
            var name = switch (entry.getKey()) {
                case String string -> string;
                case Enum<?> constant -> EnumNames.name(constant);
                case null -> throw new IllegalArgumentException("A map with a null key cannot be written as JSON.");
                default -> throw new UnsupportedOperationException(
                        "Unsupported map key type: " + entry.getKey().getClass().getTypeName());
            };
            try {
                members.put(name, encodeValue(entry.getValue(), containers));
            } catch (RuntimeException e) {
                throw at(e, "{" + name);
            }
        }
        return JsonObject.of(members);
    }
}
