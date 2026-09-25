package dev.json.records;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MutableCallSite;
import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
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
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import java21.util.json.Json;
import java21.util.json.JsonArray;
import java21.util.json.JsonBoolean;
import java21.util.json.JsonNull;
import java21.util.json.JsonNumber;
import java21.util.json.JsonObject;
import java21.util.json.JsonParseException;
import java21.util.json.JsonString;
import java21.util.json.JsonValue;
import java21.util.json.JsonValueException;

import static java.lang.invoke.MethodType.methodType;

record RecordMapperImpl(MethodHandles.Lookup lookup, DecoderFactory factory, ClassValue<Decoder> decoders,
        ClassValue<ListDecoder> listDecoders, ConcurrentHashMap<Type, Decoder> genericDecoders,
        ConcurrentHashMap<Type, ListDecoder> genericListDecoders, EncoderFactory encoderFactory)
        implements RecordMapper {

    public interface Decoder { // hide it here
        Object decode(JsonObject object);
    }

    public interface ListDecoder { // hide it here
        List<Object> decode(JsonArray array);
    }

    // All handles are (JsonValue, ...) based: the conversion methods are default
    // methods of JsonValue that throw a JsonValueException (with the JSON path)
    // if the value has the wrong JSON type or cannot be converted exactly.
    private static final MethodHandle GET;
    private static final MethodHandle AS_STRING;
    private static final MethodHandle AS_BOOLEAN;
    private static final MethodHandle AS_INT;
    private static final MethodHandle AS_LONG;
    private static final MethodHandle AS_DOUBLE;
    private static final MethodHandle IS_JSON_NULL;
    private static final MethodHandle LATE_DECODE;
    private static final MethodHandle DECODE;
    private static final MethodHandle DECODE_SEALED;
    private static final MethodHandle CHECK_MEMBERS;
    private static final MethodHandle CONSTRUCTOR_FAILED;

    // Returns the JSON path of a value as the JSON API reports it in its own errors, e.g.
    // ' Path: "{a[1{b". Location: line 2, position 7.', or "" for a value that was not parsed.
    // The JSON API computes the path only for its own errors (the implementation package is not
    // exported), so one is provoked by a conversion to a wrong JSON type. Only called on errors.
    static String pathOf(JsonValue value) {
        try {
            if (value instanceof JsonBoolean) {
                value.asString();
            } else {
                value.asBoolean();
            }
        } catch (JsonValueException e) {
            var message = e.getMessage();
            var index = message.indexOf(" Path: ");
            return index < 0 ? "" : message.substring(index);
        }
        throw new AssertionError("no JsonValueException for " + value);
    }

    // Strict mode: every member of a JSON object must correspond to a component of the record
    /*private*/ static JsonValue checkMembers(Set<String> known, Class<?> type, JsonValue value) {
        for (var member : value.asMap().entrySet()) {
            if (!known.contains(member.getKey())) {
                throw new JsonValueException("Unknown member " + JsonString.of(member.getKey()) + " of record "
                        + type.getName() + ", expected one of " + new TreeSet<>(known) + "."
                        + pathOf(member.getValue()));
            }
        }
        return value;
    }

    // Decodes a value of a sealed interface: the discriminator member selects the record. The
    // record decoder is looked up at runtime, so recursion through the interface needs no care.
    /*private*/ static Object decodeSealed(DecoderFactory factory, SealedTypes.Hierarchy hierarchy, JsonValue value)
            throws Throwable {
        var typeValue = value.get(hierarchy.discriminator());
        var typeName = typeValue.asString();
        var type = hierarchy.types().get(typeName);
        if (type == null) {
            throw new JsonValueException(JsonString.of(typeName) + " is not a type of "
                    + hierarchy.sealedInterface().getName() + ", expected one of " + hierarchy.types().keySet() + "."
                    + pathOf(typeValue));
        }
        return (Record) factory.recordDecoder(type).invokeExact(value);
    }

    // Predefined decoders for common value types, read from their standard text form
    // (ISO-8601 for java.time); decoders registered by the user replace them
    private static final Map<Class<?>, JsonDecoder<?>> DEFAULT_DECODERS = Map.<Class<?>, JsonDecoder<?>>of(
            LocalDate.class, value -> LocalDate.parse(value.asString()),
            LocalTime.class, value -> LocalTime.parse(value.asString()),
            LocalDateTime.class, value -> LocalDateTime.parse(value.asString()),
            OffsetDateTime.class, value -> OffsetDateTime.parse(value.asString()),
            ZonedDateTime.class, value -> ZonedDateTime.parse(value.asString()),
            Instant.class, value -> Instant.parse(value.asString()),
            Duration.class, value -> Duration.parse(value.asString()),
            Period.class, value -> Period.parse(value.asString()),
            UUID.class, value -> UUID.fromString(value.asString()),
            URI.class, value -> URI.create(value.asString()));

    // Calls a registered decoder. A JsonValueException is passed on, any other runtime
    // exception is wrapped in a JsonValueException, so that match() returns null.
    /*private*/ static Object decode(JsonDecoder<?> decoder, Class<?> type, JsonValue value) {
        try {
            return decoder.fromJson(value);
        } catch (JsonValueException e) {
            throw e;
        } catch (RuntimeException e) {
            var exception = new JsonValueException("Cannot convert JSON value to " + type.getName() + ": "
                    + sentence(e.getMessage()) + pathOf(value));
            exception.initCause(e);
            throw exception;
        }
    }

    // Handler of an exception of the canonical constructor of a record, e.g. of a validation in
    // a compact constructor: it is wrapped in a JsonValueException with the path of the JSON
    // object, so that match() returns null. Errors and JsonValueExceptions are passed on.
    /*private*/ static Record constructorFailed(Class<?> type, Throwable e, JsonValue value) {
        if (e instanceof Error error) {
            throw error;
        }
        if (e instanceof JsonValueException exception) {
            throw exception;
        }
        var exception = new JsonValueException("Cannot create record " + type.getName() + ": "
                + sentence(e.getMessage()) + pathOf(value));
        exception.initCause(e);
        throw exception;
    }

    // a message as a sentence, so that the path can be appended
    private static String sentence(String message) {
        if (message == null) {
            return "";
        }
        return message.endsWith(".") ? message : message + ".";
    }

    // conversions for the primitive types, of type (JsonValue)primitive
    private static final Map<Class<?>, MethodHandle> PRIMITIVES;

    // Components of these types are optional: a missing key is treated like JSON null
    private static final Set<Class<?>> OPTIONALS =
            Set.of(Optional.class, OptionalInt.class, OptionalLong.class, OptionalDouble.class);
    private static final MethodHandle GET_OR_JSON_NULL;
    private static final MethodHandle OPTIONAL_OF;
    private static final MethodHandle OPTIONAL_INT_OF;
    private static final MethodHandle OPTIONAL_LONG_OF;
    private static final MethodHandle OPTIONAL_DOUBLE_OF;

    /*private*/ static JsonValue getOrJsonNull(JsonValue object, String name) {
        return object.tryGet(name).orElse(JsonNull.of());
    }

    // the empty values of the optional types, for ignored components
    private static final Map<Class<?>, Object> EMPTY_OPTIONALS = Map.of(Optional.class, Optional.empty(),
            OptionalInt.class, OptionalInt.empty(), OptionalLong.class, OptionalLong.empty(),
            OptionalDouble.class, OptionalDouble.empty());

    // Components with a value for a missing member (@JsonDefault, omitNulls) test for the member
    private static final MethodHandle HAS_MEMBER;
    private static final MethodHandle DEFAULT_FAILED;

    /*private*/ static boolean hasMember(JsonValue object, String name) {
        return object.tryGet(name).isPresent();
    }

    /*private*/ static Object defaultFailed(Class<?> type, String component, JsonValueException e) {
        throw new IllegalArgumentException("Invalid @JsonDefault of component " + component + " of record "
                + type.getName() + ": " + e.getMessage(), e);
    }

    // Collections: the element handle is of type (JsonValue)Object and maps JSON null itself.
    // The results are unmodifiable, keep the JSON order and may contain null elements.
    private static final MethodHandle TO_LIST;

    /*private*/ static List<Object> toList(MethodHandle element, JsonValue value) throws Throwable {
        var values = value.asList();
        var array = new Object[values.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = (Object) element.invokeExact(values.get(i));
        }
        return Collections.unmodifiableList(Arrays.asList(array));
    }

    private static final MethodHandle TO_SET;

    // duplicate elements are dropped, the first occurrence determines the order
    /*private*/ static Set<Object> toSet(MethodHandle element, JsonValue value) throws Throwable {
        var values = value.asList();
        var set = LinkedHashSet.newLinkedHashSet(values.size());
        for (var v : values) {
            set.add((Object) element.invokeExact(v));
        }
        return Collections.unmodifiableSet(set);
    }

    private static final MethodHandle TO_MAP;
    // the key handle of a Map<String, T>, of type (String)Object
    private static final MethodHandle STRING_KEY =
            MethodHandles.identity(String.class).asType(methodType(Object.class, String.class));

    // the key handle is of type (String)Object and converts a member name to a map key
    /*private*/ static Map<Object, Object> toMap(MethodHandle key, MethodHandle element, JsonValue value)
            throws Throwable {
        var members = value.asMap();
        var map = LinkedHashMap.<Object, Object>newLinkedHashMap(members.size());
        for (var member : members.entrySet()) {
            Object mapKey;
            try {
                mapKey = (Object) key.invokeExact(member.getKey());
            } catch (JsonValueException e) {
                // a name is not a JSON value, so the path is the one of the member value
                throw new JsonValueException(e.getMessage() + pathOf(member.getValue()));
            }
            map.put(mapKey, (Object) element.invokeExact(member.getValue()));
        }
        return Collections.unmodifiableMap(map);
    }

    // Arbitrary precision numbers are read from the text of the JSON number. To protect against
    // denial of service by huge numbers, the text and the digits of a BigInteger are limited.
    private static final int MAX_NUMBER_LENGTH = 1_000;

    /*private*/ static BigDecimal toBigDecimal(JsonValue value) {
        if (!(value instanceof JsonNumber number)) {
            // throws the JsonValueException of the JSON API, which contains the JSON path
            value.asDouble();
            throw new AssertionError("not a JSON number: " + value);
        }
        var text = number.toString();
        if (text.length() > MAX_NUMBER_LENGTH) {
            throw new JsonValueException("JSON number with " + text.length()
                    + " characters exceeds the limit of " + MAX_NUMBER_LENGTH + " characters." + pathOf(value));
        }
        return new BigDecimal(text);
    }

    /*private*/ static BigInteger toBigInteger(JsonValue value) {
        var decimal = toBigDecimal(value);
        if (decimal.signum() == 0) {
            return BigInteger.ZERO;
        }
        // checked before the digits are computed, e.g. for 1e999999999
        if ((long) decimal.precision() - decimal.scale() > MAX_NUMBER_LENGTH) {
            throw new JsonValueException(value + " has more than " + MAX_NUMBER_LENGTH + " digits." + pathOf(value));
        }
        try {
            return decimal.toBigIntegerExact();
        } catch (ArithmeticException e) {
            throw new JsonValueException(value + " cannot be represented as a BigInteger." + pathOf(value));
        }
    }

    // conversions for reference types that are neither records, enums, wrappers nor collections
    private static final Map<Class<?>, MethodHandle> REFERENCE_TYPES;

    // The JSON types themselves are passed on unchanged, for parts of a document whose structure
    // is open. A JsonValue keeps JSON null (as JsonNull), the other JSON types map it to null.
    private static final Map<Class<?>, MethodHandle> JSON_TYPES;

    // returns the value if it has the JSON type, otherwise the conversion of the JSON API to
    // that type throws its JsonValueException, which contains the JSON path
    /*private*/ static JsonValue toJsonType(Class<?> type, JsonValue value) {
        if (type.isInstance(value)) {
            return value;
        }
        if (type == JsonObject.class) {
            value.asMap();
        } else if (type == JsonArray.class) {
            value.asList();
        } else if (type == JsonString.class) {
            value.asString();
        } else if (type == JsonNumber.class) {
            value.asDouble();
        } else if (type == JsonBoolean.class) {
            value.asBoolean();
        }
        throw new AssertionError(value + " is not a " + type.getSimpleName());
    }

    // Primitive arrays are converted without boxing; the elements are converted like the
    // corresponding primitive components, so JSON null is rejected
    private static final Map<Class<?>, MethodHandle> PRIMITIVE_ARRAYS;

    /*private*/ static boolean[] toBooleanArray(JsonValue value) {
        var values = value.asList();
        var array = new boolean[values.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = values.get(i).asBoolean();
        }
        return array;
    }

    // Multidimensional arrays: the element handle is of type (JsonValue)Object and converts
    // the rows, which are arrays themselves, so JSON null maps to a null row
    private static final MethodHandle TO_ARRAY;

    /*private*/ static Object[] toArray(MethodHandle element, Class<?> componentType, JsonValue value)
            throws Throwable {
        var values = value.asList();
        var array = (Object[]) Array.newInstance(componentType, values.size());
        for (int i = 0; i < array.length; i++) {
            array[i] = (Object) element.invokeExact(values.get(i));
        }
        return array;
    }

    // byte[] is not read from an array of numbers, but from a Base64 JSON string
    /*private*/ static byte[] toByteArray(JsonValue value) {
        var text = value.asString();
        try {
            return Base64.getDecoder().decode(text);
        } catch (IllegalArgumentException e) {
            // the text may be long, so it is not part of the message
            throw new JsonValueException("JSON string of length " + text.length() + " is not valid Base64."
                    + pathOf(value));
        }
    }

    /*private*/ static short[] toShortArray(JsonValue value) {
        var values = value.asList();
        var array = new short[values.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = toShort(values.get(i));
        }
        return array;
    }

    // from an array of JSON strings consisting of a single character each
    /*private*/ static char[] toCharArray(JsonValue value) {
        var values = value.asList();
        var array = new char[values.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = toChar(values.get(i));
        }
        return array;
    }

    /*private*/ static int[] toIntArray(JsonValue value) {
        var values = value.asList();
        var array = new int[values.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = values.get(i).asInt();
        }
        return array;
    }

    /*private*/ static long[] toLongArray(JsonValue value) {
        var values = value.asList();
        var array = new long[values.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = values.get(i).asLong();
        }
        return array;
    }

    /*private*/ static float[] toFloatArray(JsonValue value) {
        var values = value.asList();
        var array = new float[values.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = toFloat(values.get(i));
        }
        return array;
    }

    /*private*/ static double[] toDoubleArray(JsonValue value) {
        var values = value.asList();
        var array = new double[values.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = values.get(i).asDouble();
        }
        return array;
    }

    // Enums are read from the exact JSON name of their constants (@JsonName or the name, see EnumNames)
    private static final MethodHandle ENUM_CONSTANT;
    private static final MethodHandle TO_ENUM;

    // a map key: the caller (toMap) adds the path of the member
    /*private*/ static Object enumConstant(Map<String, Object> constants, Class<?> type, String name) {
        var constant = constants.get(name);
        if (constant == null) {
            throw new JsonValueException(notAConstant(type, name));
        }
        return constant;
    }

    /*private*/ static Object toEnum(Map<String, Object> constants, Class<?> type, JsonValue value) {
        var name = value.asString();
        var constant = constants.get(name);
        if (constant == null) {
            throw new JsonValueException(notAConstant(type, name) + pathOf(value));
        }
        return constant;
    }

    private static String notAConstant(Class<?> type, String name) {
        return JsonString.of(name) + " is not a constant of " + type.getName() + ".";
    }

    // returns the type argument at the given index if it is a class or a parameterized type
    private static Type typeArgument(ParameterizedType type, int index) {
        var argument = type.getActualTypeArguments()[index];
        if (!(argument instanceof Class<?>) && !(argument instanceof ParameterizedType)) {
            // wildcards and type variables
            throw new UnsupportedOperationException("Unsupported type: " + type.getTypeName());
        }
        return argument;
    }

    // the classes of the JSON values of java21.util.json (the parser creates the same ones)
    private static final Class<?> JSON_NULL_CLASS = JsonNull.of().getClass();
    private static final Class<?> JSON_STRING_CLASS = JsonString.of("").getClass();
    private static final Class<?> JSON_NUMBER_CLASS = JsonNumber.of(0).getClass();
    private static final Class<?> JSON_OBJECT_CLASS = JsonObject.of(Map.of()).getClass();
    private static final Class<?> JSON_ARRAY_CLASS = JsonArray.of(List.of()).getClass();
    private static final Class<?> JSON_BOOLEAN_CLASS = JsonBoolean.of(true).getClass();

    // The values of the library are recognized by their class: on JDK 21, instanceof JsonNull
    // is slow if it fails, as JsonNull is an interface, and it would double the time of reading.
    // Other implementations of the (non-sealed) JSON interfaces are checked with instanceof.
    /*private*/ static boolean isJsonNull(JsonValue value) {
        var type = value.getClass();
        if (type == JSON_NULL_CLASS) {
            return true;
        }
        if (type == JSON_STRING_CLASS || type == JSON_NUMBER_CLASS || type == JSON_OBJECT_CLASS
                || type == JSON_ARRAY_CLASS || type == JSON_BOOLEAN_CLASS) {
            return false;
        }
        return value instanceof JsonNull;
    }

    // Conversions the JSON API does not provide; their errors get the JSON path from pathOf
    /*private*/ static byte toByte(JsonValue value) {
        int i = value.asInt();
        if (i < Byte.MIN_VALUE || i > Byte.MAX_VALUE) {
            throw new JsonValueException(value + " cannot be represented as a byte." + pathOf(value));
        }
        return (byte) i;
    }

    /*private*/ static short toShort(JsonValue value) {
        int i = value.asInt();
        if (i < Short.MIN_VALUE || i > Short.MAX_VALUE) {
            throw new JsonValueException(value + " cannot be represented as a short." + pathOf(value));
        }
        return (short) i;
    }

    /*private*/ static char toChar(JsonValue value) {
        var s = value.asString();
        if (s.length() != 1) {
            throw new JsonValueException(value + " cannot be represented as a char." + pathOf(value));
        }
        return s.charAt(0);
    }

    /*private*/ static float toFloat(JsonValue value) {
        var f = (float) value.asDouble();
        if (Float.isInfinite(f)) {
            throw new JsonValueException(value + " cannot be represented as a float." + pathOf(value));
        }
        return f;
    }

    // Target of a recursion call site until its record type is built (and forever if it
    // cannot be built, so that the original error is reported again on use)
    /*private*/ static Record lateDecode(DecoderFactory factory, Type type, JsonValue value) throws Throwable {
        return (Record) factory.recordDecoder(type).invokeExact(value);
    }

    // JSON null maps to null for reference types
    private static MethodHandle nullable(MethodHandle filter) {
        return MethodHandles.guardWithTest(IS_JSON_NULL, MethodHandles.empty(filter.type()), filter);
    }

    // JSON null maps to an empty optional
    private static MethodHandle emptyIfJsonNull(MethodHandle present, Object empty) {
        var returnType = present.type().returnType();
        var emptyHandle = MethodHandles.dropArguments(MethodHandles.constant(returnType, empty), 0, JsonValue.class);
        return MethodHandles.guardWithTest(IS_JSON_NULL, emptyHandle, present);
    }

    static {
        var lookup = MethodHandles.lookup();
        try {
            IS_JSON_NULL = lookup.findStatic(lookup.lookupClass(), "isJsonNull",
                    methodType(boolean.class, JsonValue.class));
            CHECK_MEMBERS = lookup.findStatic(lookup.lookupClass(), "checkMembers",
                    methodType(JsonValue.class, Set.class, Class.class, JsonValue.class));
            CONSTRUCTOR_FAILED = lookup.findStatic(lookup.lookupClass(), "constructorFailed",
                    methodType(Record.class, Class.class, Throwable.class, JsonValue.class));
            DECODE_SEALED = lookup.findStatic(lookup.lookupClass(), "decodeSealed",
                    methodType(Object.class, DecoderFactory.class, SealedTypes.Hierarchy.class, JsonValue.class));
            DECODE = lookup.findStatic(lookup.lookupClass(), "decode",
                    methodType(Object.class, JsonDecoder.class, Class.class, JsonValue.class));
            LATE_DECODE = lookup.findStatic(lookup.lookupClass(), "lateDecode",
                    methodType(Record.class, DecoderFactory.class, Type.class, JsonValue.class));
            GET = lookup.findVirtual(JsonValue.class, "get", methodType(JsonValue.class, String.class));
            HAS_MEMBER = lookup.findStatic(lookup.lookupClass(), "hasMember",
                    methodType(boolean.class, JsonValue.class, String.class));
            DEFAULT_FAILED = lookup.findStatic(lookup.lookupClass(), "defaultFailed",
                    methodType(Object.class, Class.class, String.class, JsonValueException.class));
            GET_OR_JSON_NULL = lookup.findStatic(lookup.lookupClass(), "getOrJsonNull",
                    methodType(JsonValue.class, JsonValue.class, String.class));
            OPTIONAL_OF = lookup.findStatic(Optional.class, "of", methodType(Optional.class, Object.class));
            OPTIONAL_INT_OF = lookup.findStatic(OptionalInt.class, "of", methodType(OptionalInt.class, int.class));
            OPTIONAL_LONG_OF = lookup.findStatic(OptionalLong.class, "of", methodType(OptionalLong.class, long.class));
            OPTIONAL_DOUBLE_OF = lookup.findStatic(OptionalDouble.class, "of",
                    methodType(OptionalDouble.class, double.class));
            TO_LIST = lookup.findStatic(lookup.lookupClass(), "toList",
                    methodType(List.class, MethodHandle.class, JsonValue.class));
            TO_SET = lookup.findStatic(lookup.lookupClass(), "toSet",
                    methodType(Set.class, MethodHandle.class, JsonValue.class));
            ENUM_CONSTANT = lookup.findStatic(lookup.lookupClass(), "enumConstant",
                    methodType(Object.class, Map.class, Class.class, String.class));
            TO_ENUM = lookup.findStatic(lookup.lookupClass(), "toEnum",
                    methodType(Object.class, Map.class, Class.class, JsonValue.class));
            TO_MAP = lookup.findStatic(lookup.lookupClass(), "toMap",
                    methodType(Map.class, MethodHandle.class, MethodHandle.class, JsonValue.class));
            AS_STRING = lookup.findVirtual(JsonValue.class, "asString", methodType(String.class));
            AS_BOOLEAN = lookup.findVirtual(JsonValue.class, "asBoolean", methodType(boolean.class));
            AS_INT = lookup.findVirtual(JsonValue.class, "asInt", methodType(int.class));
            AS_LONG = lookup.findVirtual(JsonValue.class, "asLong", methodType(long.class));
            AS_DOUBLE = lookup.findVirtual(JsonValue.class, "asDouble", methodType(double.class));
            PRIMITIVES = Map.of(
                    boolean.class, AS_BOOLEAN,
                    byte.class, lookup.findStatic(lookup.lookupClass(), "toByte", methodType(byte.class, JsonValue.class)),
                    short.class, lookup.findStatic(lookup.lookupClass(), "toShort", methodType(short.class, JsonValue.class)),
                    char.class, lookup.findStatic(lookup.lookupClass(), "toChar", methodType(char.class, JsonValue.class)),
                    int.class, AS_INT,
                    long.class, AS_LONG,
                    float.class, lookup.findStatic(lookup.lookupClass(), "toFloat", methodType(float.class, JsonValue.class)),
                    double.class, AS_DOUBLE);
            REFERENCE_TYPES = Map.of(
                    String.class, AS_STRING,
                    BigDecimal.class, lookup.findStatic(lookup.lookupClass(), "toBigDecimal",
                            methodType(BigDecimal.class, JsonValue.class)),
                    BigInteger.class, lookup.findStatic(lookup.lookupClass(), "toBigInteger",
                            methodType(BigInteger.class, JsonValue.class)));
            var toJsonType = lookup.findStatic(lookup.lookupClass(), "toJsonType",
                    methodType(JsonValue.class, Class.class, JsonValue.class));
            var jsonTypes = new HashMap<Class<?>, MethodHandle>();
            jsonTypes.put(JsonValue.class, MethodHandles.identity(JsonValue.class));
            for (var type : List.of(JsonObject.class, JsonArray.class, JsonString.class, JsonNumber.class,
                    JsonBoolean.class)) {
                jsonTypes.put(type, MethodHandles.insertArguments(toJsonType, 0, type)
                        .asType(methodType(type, JsonValue.class)));
            }
            JSON_TYPES = Map.copyOf(jsonTypes);
            TO_ARRAY = lookup.findStatic(lookup.lookupClass(), "toArray",
                    methodType(Object[].class, MethodHandle.class, Class.class, JsonValue.class));
            PRIMITIVE_ARRAYS = Map.of(
                    boolean[].class, lookup.findStatic(lookup.lookupClass(), "toBooleanArray",
                            methodType(boolean[].class, JsonValue.class)),
                    byte[].class, lookup.findStatic(lookup.lookupClass(), "toByteArray",
                            methodType(byte[].class, JsonValue.class)),
                    short[].class, lookup.findStatic(lookup.lookupClass(), "toShortArray",
                            methodType(short[].class, JsonValue.class)),
                    char[].class, lookup.findStatic(lookup.lookupClass(), "toCharArray",
                            methodType(char[].class, JsonValue.class)),
                    int[].class, lookup.findStatic(lookup.lookupClass(), "toIntArray",
                            methodType(int[].class, JsonValue.class)),
                    long[].class, lookup.findStatic(lookup.lookupClass(), "toLongArray",
                            methodType(long[].class, JsonValue.class)),
                    float[].class, lookup.findStatic(lookup.lookupClass(), "toFloatArray",
                            methodType(float[].class, JsonValue.class)),
                    double[].class, lookup.findStatic(lookup.lookupClass(), "toDoubleArray",
                            methodType(double[].class, JsonValue.class)));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T fromTyped(JsonObject object, Class<T> type) {
        Objects.requireNonNull(object);
        Objects.requireNonNull(type);
        return (T) decoders.get(type).decode(object);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> fromTypedList(JsonArray array, Class<T> type) {
        Objects.requireNonNull(array);
        Objects.requireNonNull(type);
        return (List<T>) listDecoders.get(type).decode(array);
    }

    @Override
    public Object match(JsonObject object, Class<?> type) {
        Objects.requireNonNull(object);
        Objects.requireNonNull(type);
        try {
            return decoders.get(type).decode(object);
        } catch (JsonValueException e) {
            return null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Record> T fromTyped(JsonObject object, TypeRef<T> type) {
        Objects.requireNonNull(object);
        Objects.requireNonNull(type);
        return (T) decoder(type.type()).decode(object);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Record> List<T> fromTypedList(JsonArray array, TypeRef<T> type) {
        Objects.requireNonNull(array);
        Objects.requireNonNull(type);
        return (List<T>) (List<?>) listDecoder(type.type()).decode(array);
    }

    @Override
    public JsonObject toJson(Object value) {
        Objects.requireNonNull(value);
        return encoderFactory.toJson(value);
    }

    @Override
    public JsonArray toJsonList(List<?> values) {
        Objects.requireNonNull(values);
        return encoderFactory.toJsonList(values);
    }

    @Override
    public Object match(JsonObject object, TypeRef<?> type) {
        Objects.requireNonNull(object);
        Objects.requireNonNull(type);
        try {
            return decoder(type.type()).decode(object);
        } catch (JsonValueException e) {
            return null;
        }
    }

    // the type of a TypeRef is a record class or a canonical parameterized record type
    private Decoder decoder(Type type) {
        if (type instanceof Class<?> clazz) {
            return decoders.get(clazz);
        }
        var decoder = genericDecoders.get(type);
        if (decoder == null) {
            decoder = MethodHandleProxies.asInterfaceInstance(Decoder.class, factory.recordDecoder(type));
            var existing = genericDecoders.putIfAbsent(type, decoder);
            if (existing != null) {
                decoder = existing;
            }
        }
        return decoder;
    }

    private ListDecoder listDecoder(Type type) {
        if (type instanceof Class<?> clazz) {
            return listDecoders.get(clazz);
        }
        var listDecoder = genericListDecoders.get(type);
        if (listDecoder == null) {
            listDecoder = listDecoder(factory.recordDecoder(type));
            var existing = genericListDecoders.putIfAbsent(type, listDecoder);
            if (existing != null) {
                listDecoder = existing;
            }
        }
        return listDecoder;
    }

    // a top-level array of records is decoded like a List<T> record component
    private static ListDecoder listDecoder(MethodHandle decoder) {
        var element = nullable(decoder.asType(methodType(Object.class, JsonValue.class)));
        var mh = MethodHandles.insertArguments(TO_LIST, 0, element);
        return MethodHandleProxies.asInterfaceInstance(ListDecoder.class, mh);
    }

    // the names of the JSON object members of the components of a record, for reading and
    // writing: the @JsonName annotation or the name derived by the naming strategy, or null
    // for a component with @JsonIgnore, which has no member
    static String[] jsonNames(Class<?> type, RecordComponent[] components, JsonNaming naming) {
        var names = new String[components.length];
        var componentsByName = new HashMap<String, String>();
        for (int i = 0; i < components.length; i++) {
            var component = components[i];
            if (component.isAnnotationPresent(JsonIgnore.class)) {
                continue;
            }
            var annotation = component.getAnnotation(JsonName.class);
            String name;
            if (annotation != null) {
                name = annotation.value();
                if (name.isEmpty()) {
                    throw new IllegalArgumentException("Empty @JsonName on component " + component.getName()
                            + " of record " + type.getName());
                }
            } else {
                name = naming.jsonName(component.getName());
            }
            var previous = componentsByName.put(name, component.getName());
            if (previous != null) {
                throw new IllegalArgumentException("Components " + previous + " and " + component.getName()
                        + " of record " + type.getName() + " have the same JSON name \"" + name + "\"");
            }
            names[i] = name;
        }
        return names;
    }

    static RecordMapper of(MethodHandles.Lookup lookup, JsonNaming naming, boolean failOnUnknownMembers,
            boolean omitNulls, Map<Class<?>, JsonDecoder<?>> registeredDecoders,
            Map<Class<?>, JsonEncoder<?>> registeredEncoders) {
        var allDecoders = new HashMap<Class<?>, JsonDecoder<?>>(DEFAULT_DECODERS);
        allDecoders.putAll(registeredDecoders);
        var factory = new DecoderFactory(lookup, naming, failOnUnknownMembers, omitNulls, Map.copyOf(allDecoders));

        var decoders = new ClassValue<Decoder>() {
            @Override
            protected Decoder computeValue(Class<?> type) {
                var mh = factory.topLevelDecoder(type);
                return MethodHandleProxies.asInterfaceInstance(Decoder.class, mh);
            }
        };

        var listDecoders = new ClassValue<ListDecoder>() {
            @Override
            protected ListDecoder computeValue(Class<?> type) {
                return listDecoder(factory.topLevelDecoder(type));
            }
        };

        var allEncoders = new HashMap<Class<?>, JsonEncoder<?>>(EncoderFactory.DEFAULT_ENCODERS);
        allEncoders.putAll(registeredEncoders);
        var encoderFactory = new EncoderFactory(lookup, naming, omitNulls, Map.copyOf(allEncoders));

        return new RecordMapperImpl(lookup, factory, decoders, listDecoders, new ConcurrentHashMap<>(),
                new ConcurrentHashMap<>(), encoderFactory);
    }

    // Builds the decoders of record types, method handles of type (JsonValue)Record
    private static final class DecoderFactory {
        private final MethodHandles.Lookup lookup;
        private final JsonNaming naming;
        // the registered decoders by class
        private final Map<Class<?>, JsonDecoder<?>> decoders;
        // whether JSON object members without a corresponding record component are an error
        private final boolean failOnUnknownMembers;
        // whether a missing member of a component of a reference type is null (as null is not written)
        private final boolean omitNulls;

        // decoders of non-generic record types
        private final ClassValue<MethodHandle> classDecoders = new ClassValue<>() {
            @Override
            protected MethodHandle computeValue(Class<?> type) {
                return build(type);
            }
        };

        // decoders of parameterized record types, e.g. Page<User>; the keys are canonical
        // (Types.ParameterizedTypeImpl) and keep their classes reachable
        private final ConcurrentHashMap<Type, MethodHandle> genericDecoders = new ConcurrentHashMap<>();

        // a generic record type that contains itself with other type arguments, e.g.
        // record Weird<T>(Weird<List<T>> next), would expand infinitely
        private static final int MAX_GENERIC_NESTING = 32;

        // Record types currently being built by this thread. A record type that refers
        // back to one of them (directly or indirectly recursive) is linked through a
        // call site; the value is null until such a back reference is encountered.
        // The call site initially looks the decoder up at runtime (lateDecode), because
        // the referring record type is published to the cache, and may be used by
        // other threads, before the type it refers back to is built. Once that type is
        // built, the call site is relinked to its decoder directly.
        private final ThreadLocal<Map<Type, MutableCallSite>> pending = ThreadLocal.withInitial(HashMap::new);

        DecoderFactory(MethodHandles.Lookup lookup, JsonNaming naming, boolean failOnUnknownMembers,
                boolean omitNulls, Map<Class<?>, JsonDecoder<?>> decoders) {
            this.lookup = lookup;
            this.naming = naming;
            this.failOnUnknownMembers = failOnUnknownMembers;
            this.omitNulls = omitNulls;
            this.decoders = decoders;
        }

        // returns the decoder of a type at the top level, of type (JsonValue)Object: a record,
        // a sealed interface or a class with a registered decoder
        MethodHandle topLevelDecoder(Class<?> type) {
            if (type.isRecord()) {
                return recordDecoder(type).asType(methodType(Object.class, JsonValue.class));
            }
            // JsonValue is a sealed interface, but not one of records
            if (decoders.containsKey(type) || SealedTypes.isSealedInterface(type) && !JSON_TYPES.containsKey(type)) {
                return convert(type).asType(methodType(Object.class, JsonValue.class));
            }
            throw new UnsupportedOperationException(type.getTypeName()
                    + " is neither a record nor a sealed interface, and no decoder is registered for it");
        }

        // returns the decoder of a record class or of a canonical parameterized record type
        MethodHandle recordDecoder(Type type) {
            if (type instanceof Class<?> clazz) {
                // a registered decoder also takes precedence at the top level
                var decoder = decoders.get(clazz);
                if (decoder != null) {
                    return registered(decoder, clazz).asType(methodType(Record.class, JsonValue.class));
                }
                return classDecoders.get(clazz);
            }
            var decoder = genericDecoders.get(type);
            if (decoder == null) {
                // not computeIfAbsent: building a decoder recursively adds other decoders
                decoder = build(type);
                var existing = genericDecoders.putIfAbsent(type, decoder);
                if (existing != null) {
                    decoder = existing;
                }
            }
            return decoder;
        }

        // returns a method handle of type (JsonValue)S for the sealed interface S; the hierarchy is
        // validated now, the decoders of its records are looked up when a value is decoded
        private MethodHandle sealed(Class<?> sealedInterface) {
            var hierarchy = SealedTypes.hierarchy(sealedInterface);
            for (var type : hierarchy.types().values()) {
                var names = jsonNames(type, type.getRecordComponents(), naming);
                if (Arrays.asList(names).contains(hierarchy.discriminator())) {
                    throw new IllegalArgumentException("The discriminator \"" + hierarchy.discriminator()
                            + "\" of sealed interface " + sealedInterface.getName()
                            + " is also the JSON name of a component of record " + type.getName());
                }
            }
            return MethodHandles.insertArguments(DECODE_SEALED, 0, this, hierarchy)
                    .asType(methodType(sealedInterface, JsonValue.class));
        }

        // returns a method handle of type (JsonValue)T that calls the registered decoder for T
        private static MethodHandle registered(JsonDecoder<?> decoder, Class<?> type) {
            return MethodHandles.insertArguments(DECODE, 0, decoder, type).asType(methodType(type, JsonValue.class));
        }

        // returns a method handle of type (JsonValue)T for a record component of type T
        private MethodHandle filter(Type type) {
            var optional = optional(type);
            if (optional != null) {
                return optional;
            }
            var convert = convert(type);
            // a JsonValue component takes JSON null as it is, as JsonNull
            if (type == JsonValue.class || type instanceof Class<?> clazz && clazz.isPrimitive()) {
                return convert;
            }
            return nullable(convert);
        }

        // returns a method handle for Optional<X>, OptionalInt, OptionalLong and OptionalDouble,
        // or null if the type is none of them
        private MethodHandle optional(Type type) {
            if (type == OptionalInt.class) {
                return emptyIfJsonNull(MethodHandles.filterReturnValue(AS_INT, OPTIONAL_INT_OF), OptionalInt.empty());
            }
            if (type == OptionalLong.class) {
                return emptyIfJsonNull(MethodHandles.filterReturnValue(AS_LONG, OPTIONAL_LONG_OF), OptionalLong.empty());
            }
            if (type == OptionalDouble.class) {
                return emptyIfJsonNull(MethodHandles.filterReturnValue(AS_DOUBLE, OPTIONAL_DOUBLE_OF),
                        OptionalDouble.empty());
            }
            if (type instanceof ParameterizedType parameterized && parameterized.getRawType() == Optional.class) {
                // Optional<Optional<...>> is rejected by convert
                var present = convert(typeArgument(parameterized, 0)).asType(methodType(Object.class, JsonValue.class));
                return emptyIfJsonNull(MethodHandles.filterReturnValue(present, OPTIONAL_OF), Optional.empty());
            }
            return null;
        }

        // returns a method handle of type (JsonValue)T that converts a value that is not JSON null
        private MethodHandle convert(Type type) {
            if (type instanceof Class<?> clazz) {
                // registered decoders take precedence over the conversions of the mapper
                var decoder = decoders.get(clazz);
                if (decoder != null) {
                    return registered(decoder, clazz);
                }
                var reference = REFERENCE_TYPES.get(clazz);
                if (reference != null) {
                    return reference;
                }
                // before the sealed interfaces, as JsonValue is one
                var jsonType = JSON_TYPES.get(clazz);
                if (jsonType != null) {
                    return jsonType;
                }
                if (SealedTypes.isSealedInterface(clazz)) {
                    return sealed(clazz);
                }
                if (clazz.isArray()) {
                    var array = PRIMITIVE_ARRAYS.get(clazz);
                    if (array != null) {
                        return array;
                    }
                    // multidimensional arrays of a primitive type, e.g. double[][];
                    // arrays of reference types are not supported
                    var elementType = clazz;
                    while (elementType.isArray()) {
                        elementType = elementType.getComponentType();
                    }
                    if (!elementType.isPrimitive()) {
                        throw new UnsupportedOperationException("Unsupported type: " + clazz.getTypeName());
                    }
                    var componentType = clazz.getComponentType();
                    var element = filter(componentType).asType(methodType(Object.class, JsonValue.class));
                    return MethodHandles.insertArguments(TO_ARRAY, 0, element, componentType)
                            .asType(methodType(clazz, JsonValue.class));
                }
                if (clazz.isRecord()) {
                    checkNotGeneric(clazz);
                    return record(clazz);
                }
                if (clazz.isEnum()) {
                    return MethodHandles.insertArguments(TO_ENUM, 0, EnumNames.constants(clazz), clazz)
                            .asType(methodType(clazz, JsonValue.class));
                }
                var primitive = PRIMITIVES.get(clazz);
                if (primitive != null) {
                    return primitive;
                }
                // wrapper types: convert like the primitive type, then box
                var wrapped = PRIMITIVES.get(methodType(clazz).unwrap().returnType());
                if (wrapped != null) {
                    return wrapped.asType(methodType(clazz, JsonValue.class));
                }
            }
            if (type instanceof ParameterizedType parameterized) {
                if (parameterized.getRawType() instanceof Class<?> raw && raw.isRecord()) {
                    return record(parameterized);
                }
                if (parameterized.getRawType() == List.class) {
                    return collection(TO_LIST, List.class, typeArgument(parameterized, 0));
                }
                if (parameterized.getRawType() == Set.class) {
                    return collection(TO_SET, Set.class, typeArgument(parameterized, 0));
                }
                if (parameterized.getRawType() == Map.class) {
                    var key = mapKey(typeArgument(parameterized, 0));
                    if (key != null) {
                        var element = filter(typeArgument(parameterized, 1))
                                .asType(methodType(Object.class, JsonValue.class));
                        return MethodHandles.insertArguments(TO_MAP, 0, key, element)
                                .asType(methodType(Map.class, JsonValue.class));
                    }
                }
            }
            throw new UnsupportedOperationException("Unsupported type: " + type.getTypeName());
        }

        // the keys of a JSON object are strings, which may be the names of enum constants;
        // returns null for any other key type
        private MethodHandle mapKey(Type keyType) {
            if (keyType == String.class) {
                return STRING_KEY;
            }
            if (keyType instanceof Class<?> clazz && clazz.isEnum()) {
                return MethodHandles.insertArguments(ENUM_CONSTANT, 0, EnumNames.constants(clazz), clazz);
            }
            return null;
        }

        // the elements (or map values) of a collection are filtered like record components,
        // so JSON null maps to null or to an empty optional
        private MethodHandle collection(MethodHandle factory, Class<?> collectionType, Type elementType) {
            var element = filter(elementType).asType(methodType(Object.class, JsonValue.class));
            return MethodHandles.insertArguments(factory, 0, element)
                    .asType(methodType(collectionType, JsonValue.class));
        }

        // a generic record class cannot be decoded without type arguments
        private static void checkNotGeneric(Class<?> type) {
            if (type.getTypeParameters().length > 0) {
                throw new UnsupportedOperationException("Generic record " + type.getName()
                        + " requires type arguments, use TypeRef");
            }
        }

        // returns a method handle of type (JsonValue)R for the record class or the canonical
        // parameterized record type R
        private MethodHandle record(Type type) {
            var inProgress = pending.get();
            MethodHandle decoder;
            if (inProgress.containsKey(type)) {
                var callSite = inProgress.computeIfAbsent(type,
                        t -> new MutableCallSite(MethodHandles.insertArguments(LATE_DECODE, 0, this, t)));
                decoder = callSite.dynamicInvoker();
            } else {
                decoder = recordDecoder(type);
            }
            return decoder.asType(methodType(Types.rawClass(type), JsonValue.class));
        }

        // builds the decoder of a record class or a canonical parameterized record type,
        // of type (JsonValue)Record
        private MethodHandle build(Type type) {
            var raw = Types.rawClass(type);
            var bindings = new HashMap<TypeVariable<?>, Type>();
            if (type instanceof ParameterizedType parameterized) {
                var parameters = raw.getTypeParameters();
                var arguments = parameterized.getActualTypeArguments();
                for (int i = 0; i < parameters.length; i++) {
                    bindings.put(parameters[i], arguments[i]);
                }
            } else {
                checkNotGeneric(raw);
            }

            var inProgress = pending.get();
            if (inProgress.keySet().stream().filter(t -> Types.rawClass(t) == raw).count() >= MAX_GENERIC_NESTING) {
                throw new UnsupportedOperationException("Generic record type expands infinitely: " + type.getTypeName());
            }
            inProgress.put(type, null);
            MethodHandle mh = null;
            try {
                mh = decoder(raw, bindings);
                return mh;
            } finally {
                var callSite = inProgress.remove(type);
                // if the type could not be built, the call site keeps looking it up at
                // runtime, which reports the original error again
                if (callSite != null && mh != null) {
                    callSite.setTarget(mh);
                    MutableCallSite.syncAll(new MutableCallSite[] { callSite });
                }
                if (inProgress.isEmpty()) {
                    pending.remove();
                }
            }
        }

        // the type variables of a generic record are replaced by the bindings in the types
        // of its components; the constructor is looked up by the erased types
        private MethodHandle decoder(Class<?> type, Map<TypeVariable<?>, Type> bindings) {
            var components = type.getRecordComponents();
            var types = Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
            var genericTypes = Arrays.stream(components)
                    .map(component -> Types.resolve(component.getGenericType(), bindings)).toArray(Type[]::new);
            var names = jsonNames(type, components, naming);

            MethodHandle constructor;
            try {
                constructor = lookup.findConstructor(type, methodType(void.class, types));
            } catch (NoSuchMethodException e) {
                throw (NoSuchMethodError) new NoSuchMethodError().initCause(e);
            } catch (IllegalAccessException e) {
                throw (IllegalAccessError) new IllegalAccessError().initCause(e);
            }
            constructor = constructor.asType(methodType(Record.class, types));
            // the constructor also takes the JSON object, for the path of its exceptions: only the
            // constructor call is guarded, the errors of the components are reported by the filters
            constructor = MethodHandles.dropArguments(constructor, 0, JsonValue.class);
            var failed = MethodHandles.dropArguments(MethodHandles.insertArguments(CONSTRUCTOR_FAILED, 0, type),
                    2, types);
            constructor = MethodHandles.catchException(constructor, Throwable.class, failed);

            var filters = IntStream.range(0, types.length).mapToObj(i -> {
                // a component of type T is converted to its binding, the constructor takes the erasure
                var filter = filter(genericTypes[i]).asType(methodType(types[i], JsonValue.class));
                // the value if the member is missing, of type (JsonValue)T, or null if it is an error
                var missing = defaultValue(type, components[i], filter);
                if (names[i] == null) {
                    // @JsonIgnore: the member is not read
                    return missing != null ? missing : absent(types[i]);
                }
                if (missing == null && omitNulls && !types[i].isPrimitive() && !OPTIONALS.contains(types[i])) {
                    // members of null components are not written, so a missing member is null
                    missing = absent(types[i]);
                }
                if (missing == null) {
                    var getter = OPTIONALS.contains(types[i]) ? GET_OR_JSON_NULL : GET;
                    return MethodHandles.filterReturnValue(MethodHandles.insertArguments(getter, 1, names[i]), filter);
                }
                var get = MethodHandles.filterReturnValue(MethodHandles.insertArguments(GET, 1, names[i]), filter);
                return MethodHandles.guardWithTest(MethodHandles.insertArguments(HAS_MEMBER, 1, names[i]), get,
                        missing);
            }).toArray(MethodHandle[]::new);

            var mh = MethodHandles.filterArguments(constructor, 1, filters);
            mh = MethodHandles.permuteArguments(mh, methodType(Record.class, JsonValue.class),
                    new int[types.length + 1]);
            if (failOnUnknownMembers) {
                // the type member of a record of a sealed interface is not unknown
                // the members of ignored components are unknown
                var known = new HashSet<String>();
                Arrays.stream(names).filter(Objects::nonNull).forEach(known::add);
                SealedTypes.discriminator(type).ifPresent(discriminator -> known.add(discriminator.name()));
                var check = MethodHandles.insertArguments(CHECK_MEMBERS, 0, Set.copyOf(known), type);
                mh = MethodHandles.filterArguments(mh, 0, check);
            }
            return mh;
        }

        // Returns the @JsonDefault value of a component as a method handle of type (JsonValue)T that
        // ignores its argument, or null if the component has none. The JSON text is parsed now, and
        // converted with the filter of the component on every use, so that arrays are not shared;
        // a value that cannot be converted is a programming error (IllegalArgumentException).
        private static MethodHandle defaultValue(Class<?> type, RecordComponent component, MethodHandle filter) {
            var annotation = component.getAnnotation(JsonDefault.class);
            if (annotation == null) {
                return null;
            }
            if (OPTIONALS.contains(component.getType())) {
                throw new IllegalArgumentException("@JsonDefault on component " + component.getName() + " of record "
                        + type.getName() + ", which is optional and therefore empty if its member is missing");
            }
            JsonValue json;
            try {
                json = Json.parse(annotation.value());
            } catch (JsonParseException e) {
                throw new IllegalArgumentException("Invalid @JsonDefault of component " + component.getName()
                        + " of record " + type.getName() + ": " + e.getMessage(), e);
            }
            var value = MethodHandles.insertArguments(filter, 0, json);
            var failed = MethodHandles.insertArguments(DEFAULT_FAILED, 0, type, component.getName())
                    .asType(methodType(value.type().returnType(), JsonValueException.class));
            value = MethodHandles.catchException(value, JsonValueException.class, failed);
            return MethodHandles.dropArguments(value, 0, JsonValue.class);
        }

        // the value of a component without a member and without a default value, of type (JsonValue)T
        // that ignores its argument: an empty optional, or null, zero or false
        private static MethodHandle absent(Class<?> type) {
            var empty = EMPTY_OPTIONALS.get(type);
            var value = empty != null ? MethodHandles.constant(type, empty) : MethodHandles.zero(type);
            return MethodHandles.dropArguments(value, 0, JsonValue.class);
        }
    }
}
