package dev.json.records;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

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

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Currency;
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
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class RecordMapperTest {

    private static JsonObject parse(String text) {
        return (JsonObject) Json.parse(text);
    }

    @Test
    @DisplayName("Should map a JsonObject to a simple record")
    public void testSimpleRecordMapping() {
        record SimpleRecord(String name, int age) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        JsonObject json = parse("""
            { "name": "Alice", "age": 30 }
            """);

        SimpleRecord result = recordMapper.fromTyped(json, SimpleRecord.class);

        assertNotNull(result);
        assertEquals("Alice", result.name());
        assertEquals(30, result.age());
    }

    @Test
    @DisplayName("Should match a JsonObject to a simple record")
    public void testSimpleRecordMatching() {
        record SimpleRecord(String name, int age) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        JsonObject json = parse("""
            { "name": "Alice", "age": 30 }
            """);

        if (recordMapper.match(json, SimpleRecord.class) instanceof SimpleRecord(String name, int age)) {
            assertEquals("Alice", name);
            assertEquals(30, age);
        } else {
            fail("Should match the record");
        }
    }

    @Test
    @DisplayName("Should map a JsonObject to a record with a long field")
    public void testLongIdRecordMapping() {
        record LongIdRecord(long id, String description) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        JsonObject json = parse("""
            { "id": 1234567890123, "description": "A test item" }
            """);

        LongIdRecord result = recordMapper.fromTyped(json, LongIdRecord.class);

        assertNotNull(result);
        assertEquals(1234567890123L, result.id());
        assertEquals("A test item", result.description());
    }


    @Test
    @DisplayName("Should map a JsonObject with nested JsonObject to a nested record")
    public void testNestedRecordMapping() {
        record AddressRecord(String street, String city) {}
        record PersonRecord(String name, int age, AddressRecord address) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        JsonObject personJson = parse("""
            {
              "name": "Bob",
              "age": 25,
              "address": { "street": "123 Main St", "city": "Anytown" }
            }
            """);

        PersonRecord result = recordMapper.fromTyped(personJson, PersonRecord.class);

        assertNotNull(result);
        assertEquals("Bob", result.name());
        assertEquals(25, result.age());
        assertNotNull(result.address());
        assertEquals("123 Main St", result.address().street());
        assertEquals("Anytown", result.address().city());
    }

    @Test
    @DisplayName("Should map a double component")
    public void testDoubleComponent() {
        record DoubleRecord(String name, double salary) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        JsonObject json = parse("""
            { "name": "Test", "salary": 50000.0 }
            """);

        if (recordMapper.match(json, DoubleRecord.class) instanceof DoubleRecord(String name, double salary)) {
            assertEquals("Test", name);
            assertEquals(50000.0, salary);
        } else {
            fail("Should match the record");
        }
    }

    @Test
    @DisplayName("Should map a boolean component")
    public void testBooleanComponent() {
        record BooleanRecord(String name, boolean flag) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        JsonObject json = parse("""
            { "name": "Test", "flag": true }
            """);

        if (recordMapper.match(json, BooleanRecord.class) instanceof BooleanRecord(String name, boolean flag)) {
            assertEquals("Test", name);
            assertEquals(true, flag);
        } else {
            fail("Should match the record");
        }
    }

    @Test
    @DisplayName("Should throw exception if a required key (int) is missing")
    public void testMissingKeyForInt() {
        record SimpleRecord(String name, int age) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        JsonObject json = parse("""
            { "name": "Charlie" }
            """);

        var exception = assertThrows(
                JsonValueException.class,
                () -> recordMapper.fromTyped(json, SimpleRecord.class),
                "Expected an exception when a key is missing from JSON"
        );
        assertTrue(exception.getMessage().contains("\"age\" does not exist"), exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception if a required key (String) is missing")
    public void testMissingKeyForString() {
        record SimpleRecord(String name, int age) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());
        JsonObject json = parse("""
            { "age": 40 }
            """);

        assertThrows(
                JsonValueException.class,
                () -> recordMapper.fromTyped(json, SimpleRecord.class),
                "Expected an exception when a key is missing from JSON"
        );
    }

    @Test
    @DisplayName("Should map correctly when component names match JSON keys (standard case)")
    public void testProductRecordMapping() {
        record ProductRecord(String productId, String productName, int stockQuantity) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        JsonObject json = parse("""
            { "productId": "P123", "productName": "Test Product", "stockQuantity": 100 }
            """);

        ProductRecord result = recordMapper.fromTyped(json, ProductRecord.class);

        assertNotNull(result);
        assertEquals("P123", result.productId());
        assertEquals("Test Product", result.productName());
        assertEquals(100, result.stockQuantity());
    }

    @Test
    @DisplayName("Mapping should build decoder for each distinct local record class")
    public void testDecoderCachingWithLocalRecords() {
        record DataRecord(String data) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        JsonObject json1 = parse("""
            { "data": "Data1" }
            """);
        DataRecord dataRecord1 = recordMapper.fromTyped(json1, DataRecord.class);
        assertEquals("Data1", dataRecord1.data());

        JsonObject json2 = parse("""
            { "data": "Data2" }
            """);
        DataRecord dataRecord2 = recordMapper.fromTyped(json2, DataRecord.class);
        assertEquals("Data2", dataRecord2.data());
    }

    @Test
    @DisplayName("Mapping a record with no components (empty record)")
    public void testEmptyRecord() {
        record EmptyRecord() {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        JsonObject json = parse("""
            { "someKey": "someValue" }
            """);
        EmptyRecord result = recordMapper.fromTyped(json, EmptyRecord.class);
        assertNotNull(result); // Should successfully create an instance
    }

    @Test
    @DisplayName("int components are converted exactly")
    public void testIntExactConversion() {
        record IntRecord(int value) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        assertEquals(30, recordMapper.fromTyped(parse("""
            { "value": 30.0 }
            """), IntRecord.class).value());
        assertThrows(JsonValueException.class, () -> recordMapper.fromTyped(parse("""
            { "value": 30.5 }
            """), IntRecord.class));
        // was silently truncated to -1294967296 with the old util.json backport
        assertThrows(JsonValueException.class, () -> recordMapper.fromTyped(parse("""
            { "value": 3000000000 }
            """), IntRecord.class));
    }

    @Test
    @DisplayName("double components accept integral JSON numbers")
    public void testDoubleFromIntegralNumber() {
        record DoubleRecord(double value) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        assertEquals(5.0, recordMapper.fromTyped(parse("""
            { "value": 5 }
            """), DoubleRecord.class).value());
        assertThrows(JsonValueException.class, () -> recordMapper.fromTyped(parse("""
            { "value": 1e400 }
            """), DoubleRecord.class));
    }

    @Test
    @DisplayName("A JSON value of the wrong type is reported with its path")
    public void testWrongTypeReportsPath() {
        record AddressRecord(String street, String city) {}
        record PersonRecord(String name, AddressRecord address) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        var exception = assertThrows(JsonValueException.class, () -> recordMapper.fromTyped(parse("""
            { "name": "Bob", "address": { "street": "123 Main St", "city": 42 } }
            """), PersonRecord.class));
        assertTrue(exception.getMessage().contains("JsonNumber is not a JsonString"), exception.getMessage());
        assertTrue(exception.getMessage().contains("Path: \"{address{city\""), exception.getMessage());
    }

    @Test
    @DisplayName("A nested record requires a JSON object")
    public void testNestedRecordFromNonObject() {
        record AddressRecord(String street, String city) {}
        record PersonRecord(String name, AddressRecord address) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        assertThrows(JsonValueException.class, () -> recordMapper.fromTyped(parse("""
            { "name": "Bob", "address": "123 Main St, Anytown" }
            """), PersonRecord.class));
    }

    @Test
    @DisplayName("JSON null maps to null for String components")
    public void testNullForString() {
        record SimpleRecord(String name, int age) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        assertEquals(new SimpleRecord(null, 1), recordMapper.fromTyped(parse("""
            { "name": null, "age": 1 }
            """), SimpleRecord.class));
    }

    @Test
    @DisplayName("JSON null maps to null for record components")
    public void testNullForNestedRecord() {
        record AddressRecord(String street, String city) {}
        record PersonRecord(String name, AddressRecord address) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        assertEquals(new PersonRecord("Bob", null), recordMapper.fromTyped(parse("""
            { "name": "Bob", "address": null }
            """), PersonRecord.class));
    }

    @Test
    @DisplayName("JSON null is rejected for primitive components")
    public void testNullForPrimitives() {
        record IntRecord(int value) {}
        record LongRecord(long value) {}
        record DoubleRecord(double value) {}
        record BooleanRecord(boolean value) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());
        var json = parse("""
            { "value": null }
            """);

        for (var recordClass : List.of(IntRecord.class, LongRecord.class, DoubleRecord.class)) {
            var exception = assertThrows(JsonValueException.class, () -> recordMapper.fromTyped(json, recordClass));
            assertTrue(exception.getMessage().contains("JsonNull is not a JsonNumber"), exception.getMessage());
            assertTrue(exception.getMessage().contains("Path: \"{value\""), exception.getMessage());
        }
        var exception = assertThrows(JsonValueException.class, () -> recordMapper.fromTyped(json, BooleanRecord.class));
        assertTrue(exception.getMessage().contains("JsonNull is not a JsonBoolean"), exception.getMessage());
    }

    @Test
    @DisplayName("A missing key is still an error for reference components")
    public void testMissingKeyForReferenceComponent() {
        record AddressRecord(String street, String city) {}
        record PersonRecord(String name, AddressRecord address) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        assertThrows(JsonValueException.class, () -> recordMapper.fromTyped(parse("""
            { "name": "Bob" }
            """), PersonRecord.class));
    }

    @Test
    @DisplayName("match accepts JSON null for reference components")
    public void testMatchWithNullComponent() {
        record SimpleRecord(String name, int age) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        if (recordMapper.match(parse("""
            { "name": null, "age": 30 }
            """), SimpleRecord.class) instanceof SimpleRecord(String name, int age)) {
            assertNull(name);
            assertEquals(30, age);
        } else {
            fail("Should match the record");
        }
    }

    // Recursive records are member records: local records cannot refer to each other mutually

    record Node(String value, Node next) {}

    record Tree(String value, Tree left, Tree right) {}

    record Even(String name, Odd next) {}
    record Odd(String name, Even next) {}

    record Broken(Referrer referrer, Object unsupported) {}
    record Referrer(Broken broken) {}

    @Test
    @DisplayName("Should map a self-recursive record")
    public void testSelfRecursiveRecord() {
        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        var result = recordMapper.fromTyped(parse("""
            { "value": "a", "next": { "value": "b", "next": { "value": "c", "next": null } } }
            """), Node.class);

        assertEquals(new Node("a", new Node("b", new Node("c", null))), result);
    }

    @Test
    @DisplayName("Should map a record with several recursive components")
    public void testTreeRecord() {
        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        var result = recordMapper.fromTyped(parse("""
            {
              "value": "root",
              "left": { "value": "l", "left": null, "right": null },
              "right": { "value": "r", "left": { "value": "rl", "left": null, "right": null }, "right": null }
            }
            """), Tree.class);

        assertEquals(new Tree("root", new Tree("l", null, null),
                new Tree("r", new Tree("rl", null, null), null)), result);
    }

    @Test
    @DisplayName("Should map mutually recursive records, starting from either type")
    public void testMutuallyRecursiveRecords() {
        var recordMapper = RecordMapper.of(MethodHandles.lookup());
        var json = parse("""
            { "name": "0", "next": { "name": "1", "next": { "name": "2", "next": null } } }
            """);

        assertEquals(new Even("0", new Odd("1", new Even("2", null))), recordMapper.fromTyped(json, Even.class));
        assertEquals(new Odd("0", new Even("1", new Odd("2", null))), recordMapper.fromTyped(json, Odd.class));

        var otherMapper = RecordMapper.of(MethodHandles.lookup());
        assertEquals(new Odd("0", new Even("1", new Odd("2", null))), otherMapper.fromTyped(json, Odd.class));
        assertEquals(new Even("0", new Odd("1", new Even("2", null))), otherMapper.fromTyped(json, Even.class));
    }

    @Test
    @DisplayName("Errors deep inside a recursive record are reported with their path")
    public void testRecursiveRecordErrors() {
        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        var exception = assertThrows(JsonValueException.class, () -> recordMapper.fromTyped(parse("""
            { "value": "a", "next": { "value": "b", "next": { "value": 3, "next": null } } }
            """), Node.class));
        assertTrue(exception.getMessage().contains("Path: \"{next{next{value\""), exception.getMessage());

        assertThrows(JsonValueException.class, () -> recordMapper.fromTyped(parse("""
            { "value": "a", "next": { "value": "b" } }
            """), Node.class));
    }

    @Test
    @DisplayName("A recursive record that cannot be built keeps failing with the original error")
    public void testRecursiveRecordWithUnsupportedComponent() {
        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        // building Broken builds (and caches) Referrer, which refers back to Broken
        assertThrows(UnsupportedOperationException.class, () -> recordMapper.fromTyped(parse("""
            { "referrer": null, "unsupported": 1 }
            """), Broken.class));

        assertEquals(new Referrer(null), recordMapper.fromTyped(parse("""
            { "broken": null }
            """), Referrer.class));
        assertThrows(UnsupportedOperationException.class, () -> recordMapper.fromTyped(parse("""
            { "broken": { "referrer": null, "unsupported": 1 } }
            """), Referrer.class));
    }

    @Test
    @DisplayName("Should map a deeply nested recursive record")
    public void testDeepRecursiveRecord() {
        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        int depth = 1_000;
        var text = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            text.append("{ \"value\": \"").append(i).append("\", \"next\": ");
        }
        text.append("null").append(" }".repeat(depth));

        var node = recordMapper.fromTyped(parse(text.toString()), Node.class);
        for (int i = 0; i < depth; i++) {
            assertEquals(Integer.toString(i), node.value());
            node = node.next();
        }
        assertNull(node);
    }

    @Test
    @DisplayName("Concurrent first use of a recursive record")
    public void testConcurrentRecursiveDecoderCreation() throws Exception {
        var json = parse("""
            { "name": "0", "next": { "name": "1", "next": { "name": "2", "next": null } } }
            """);
        var expectedEven = new Even("0", new Odd("1", new Even("2", null)));
        var expectedOdd = new Odd("0", new Even("1", new Odd("2", null)));
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int round = 0; round < 200; round++) {
                var recordMapper = RecordMapper.of(MethodHandles.lookup());
                var barrier = new CyclicBarrier(threads);
                var results = new ArrayList<Future<Record>>();
                for (int t = 0; t < threads; t++) {
                    // both entry points: building one type publishes the other one before the cycle is closed
                    var recordClass = t % 2 == 0 ? Even.class : Odd.class;
                    Callable<Record> task = () -> {
                        barrier.await();
                        return recordMapper.fromTyped(json, recordClass);
                    };
                    results.add(pool.submit(task));
                }
                for (int t = 0; t < threads; t++) {
                    assertEquals(t % 2 == 0 ? expectedEven : expectedOdd, results.get(t).get());
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("match returns null if the JsonObject does not match the record")
    public void testMatchReturnsNull() {
        record SimpleRecord(String name, int age) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        assertNull(recordMapper.match(parse("""
            { "name": 1, "age": 1 }
            """), SimpleRecord.class));
        assertNull(recordMapper.match(parse("""
            { "name": "Alice" }
            """), SimpleRecord.class));
        assertNull(recordMapper.match(parse("""
            { "name": "Alice", "age": 30.5 }
            """), SimpleRecord.class));
    }

    @Test
    @DisplayName("Should throw UnsupportedOperationException for unhandled types in record components")
    public void testUnsupportedTypeInRecord() {
        record ObjectRecord(Object value) {}
        record CollectionRecord(Collection<String> values) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        var objectJson = parse("""
            { "value": 1 }
            """);
        var exception = assertThrows(UnsupportedOperationException.class,
                () -> recordMapper.fromTyped(objectJson, ObjectRecord.class));
        assertTrue(exception.getMessage().contains("Unsupported type: java.lang.Object"), exception.getMessage());
        assertThrows(UnsupportedOperationException.class, () -> recordMapper.match(objectJson, ObjectRecord.class));

        var collectionJson = parse("""
            { "values": ["a", "b"] }
            """);
        assertThrows(UnsupportedOperationException.class, () -> recordMapper.fromTyped(collectionJson, CollectionRecord.class));
        assertThrows(UnsupportedOperationException.class, () -> recordMapper.match(collectionJson, CollectionRecord.class));
    }

    @Test
    @DisplayName("Should map a JsonObject created with the factory methods")
    public void testFactoryCreatedJsonObject() {
        record SimpleRecord(String name, int age) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        JsonObject json = JsonObject.of(Map.of(
                "name", JsonString.of("Alice"),
                "age", JsonNumber.of(30)
        ));

        assertEquals(new SimpleRecord("Alice", 30), recordMapper.fromTyped(json, SimpleRecord.class));
    }

    private static <T extends Record> void assertMappingFails(RecordMapper recordMapper, Class<T> recordClass,
                                                                                                                        String... jsonValues) {
        for (var jsonValue : jsonValues) {
            var json = parse("{ \"value\": " + jsonValue + " }");
            assertThrows(JsonValueException.class, () -> recordMapper.fromTyped(json, recordClass), jsonValue);
            assertNull(recordMapper.match(json, recordClass), jsonValue);
        }
    }

    private static <T extends Record> T map(RecordMapper recordMapper, Class<T> recordClass, String jsonValue) {
        return recordMapper.fromTyped(parse("{ \"value\": " + jsonValue + " }"), recordClass);
    }

    @Test
    @DisplayName("byte and short components are range checked")
    public void testByteAndShort() {
        record ByteRecord(byte value) {}
        record ShortRecord(short value) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        assertEquals(127, map(recordMapper, ByteRecord.class, "127").value());
        assertEquals(-128, map(recordMapper, ByteRecord.class, "-128").value());
        assertEquals(1, map(recordMapper, ByteRecord.class, "1.0").value());
        assertMappingFails(recordMapper, ByteRecord.class, "128", "-129", "1.5", "\"1\"", "null");

        var exception = assertThrows(JsonValueException.class, () -> map(recordMapper, ByteRecord.class, "128"));
        assertTrue(exception.getMessage().contains("128 cannot be represented as a byte"), exception.getMessage());

        assertEquals(32767, map(recordMapper, ShortRecord.class, "32767").value());
        assertEquals(-32768, map(recordMapper, ShortRecord.class, "-32768").value());
        assertMappingFails(recordMapper, ShortRecord.class, "32768", "-32769", "0.5");
    }

    @Test
    @DisplayName("char components are mapped from single character strings")
    public void testChar() {
        record CharRecord(char value) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        assertEquals('x', map(recordMapper, CharRecord.class, "\"x\"").value());
        assertEquals('\n', map(recordMapper, CharRecord.class, "\"\\n\"").value());
        // a supplementary character (surrogate pair) does not fit into a char
        assertMappingFails(recordMapper, CharRecord.class, "\"\"", "\"xy\"", "\"\\ud83d\\ude00\"", "120", "null");
    }

    @Test
    @DisplayName("float components are rounded but range checked")
    public void testFloat() {
        record FloatRecord(float value) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        assertEquals(1.5f, map(recordMapper, FloatRecord.class, "1.5").value());
        assertEquals(5.0f, map(recordMapper, FloatRecord.class, "5").value());
        assertEquals(0.1f, map(recordMapper, FloatRecord.class, "0.1").value());
        assertEquals(3.4e38f, map(recordMapper, FloatRecord.class, "3.4e38").value());
        assertMappingFails(recordMapper, FloatRecord.class, "1e39", "-1e39", "1e400", "\"1.5\"", "null");
    }

    @Test
    @DisplayName("Wrapper components are mapped like their primitive types, JSON null maps to null")
    public void testWrappers() {
        record Wrappers(Boolean z, Byte b, Short s, Character c, Integer i, Long l, Float f, Double d) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        assertEquals(new Wrappers(true, (byte) 1, (short) 2, 'c', 4, 5L, 6.5f, 7.5),
                recordMapper.fromTyped(parse("""
                    { "z": true, "b": 1, "s": 2, "c": "c", "i": 4, "l": 5, "f": 6.5, "d": 7.5 }
                    """), Wrappers.class));
        assertEquals(new Wrappers(null, null, null, null, null, null, null, null),
                recordMapper.fromTyped(parse("""
                    { "z": null, "b": null, "s": null, "c": null, "i": null, "l": null, "f": null, "d": null }
                    """), Wrappers.class));

        // a missing key is still an error
        assertThrows(JsonValueException.class, () -> recordMapper.fromTyped(parse("""
            { "z": true, "b": 1, "s": 2, "c": "c", "i": 4, "l": 5, "f": 6.5 }
            """), Wrappers.class));

        record IntegerRecord(Integer value) {}
        assertMappingFails(recordMapper, IntegerRecord.class, "3000000000", "1.5", "\"1\"");
    }

    @Test
    @DisplayName("Optional components are empty for JSON null and for a missing key")
    public void testOptional() {
        record Person(String name, Optional<String> nickname, Optional<Integer> age) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        assertEquals(new Person("Alice", Optional.of("Al"), Optional.of(30)), recordMapper.fromTyped(parse("""
            { "name": "Alice", "nickname": "Al", "age": 30 }
            """), Person.class));
        assertEquals(new Person("Alice", Optional.empty(), Optional.empty()), recordMapper.fromTyped(parse("""
            { "name": "Alice", "nickname": null, "age": null }
            """), Person.class));
        assertEquals(new Person("Alice", Optional.empty(), Optional.empty()), recordMapper.fromTyped(parse("""
            { "name": "Alice" }
            """), Person.class));

        // a value of the wrong type is an error, not an empty optional
        var exception = assertThrows(JsonValueException.class, () -> recordMapper.fromTyped(parse("""
            { "name": "Alice", "nickname": 42 }
            """), Person.class));
        assertTrue(exception.getMessage().contains("Path: \"{nickname\""), exception.getMessage());
        assertThrows(JsonValueException.class, () -> recordMapper.fromTyped(parse("""
            { "name": "Alice", "age": 30.5 }
            """), Person.class));
    }

    @Test
    @DisplayName("Optional record components, also recursive ones")
    public void testOptionalRecord() {
        record Address(String city) {}
        record Person(String name, Optional<Address> address) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        assertEquals(new Person("Bob", Optional.of(new Address("Anytown"))), recordMapper.fromTyped(parse("""
            { "name": "Bob", "address": { "city": "Anytown" } }
            """), Person.class));
        assertEquals(new Person("Bob", Optional.empty()), recordMapper.fromTyped(parse("""
            { "name": "Bob" }
            """), Person.class));

        // the leaf has no "next" key at all
        assertEquals(new OptNode("a", Optional.of(new OptNode("b", Optional.empty()))),
                recordMapper.fromTyped(parse("""
                    { "value": "a", "next": { "value": "b" } }
                    """), OptNode.class));
    }

    record OptNode(String value, Optional<OptNode> next) {}

    @Test
    @DisplayName("OptionalInt, OptionalLong and OptionalDouble components")
    public void testPrimitiveOptionals() {
        record Numbers(OptionalInt i, OptionalLong l, OptionalDouble d) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        assertEquals(new Numbers(OptionalInt.of(1), OptionalLong.of(2L), OptionalDouble.of(3.5)),
                recordMapper.fromTyped(parse("""
                    { "i": 1, "l": 2, "d": 3.5 }
                    """), Numbers.class));
        assertEquals(new Numbers(OptionalInt.empty(), OptionalLong.empty(), OptionalDouble.empty()),
                recordMapper.fromTyped(parse("""
                    { "i": null, "l": null, "d": null }
                    """), Numbers.class));
        assertEquals(new Numbers(OptionalInt.empty(), OptionalLong.empty(), OptionalDouble.empty()),
                recordMapper.fromTyped(parse("{}"), Numbers.class));
        assertThrows(JsonValueException.class, () -> recordMapper.fromTyped(parse("""
            { "i": 1.5 }
            """), Numbers.class));
    }

    @Test
    @DisplayName("match succeeds if the key of an optional component is missing")
    public void testMatchWithMissingOptional() {
        record Person(String name, Optional<String> nickname) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        if (recordMapper.match(parse("""
            { "name": "Alice" }
            """), Person.class) instanceof Person(String name, Optional<String> nickname)) {
            assertEquals("Alice", name);
            assertEquals(Optional.empty(), nickname);
        } else {
            fail("Should match the record");
        }
    }

    @Test
    @DisplayName("Unsupported optional and generic component types")
    @SuppressWarnings("rawtypes")
    public void testUnsupportedOptionals() {
        record RawOptional(Optional value) {}
        record WildcardOptional(Optional<?> value) {}
        record NestedOptional(Optional<Optional<String>> value) {}
        record OptionalOfOptionalInt(Optional<OptionalInt> value) {}
        record Box<T>(T value) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());
        var json = parse("""
            { "value": "x" }
            """);

        for (var recordClass : List.of(RawOptional.class, WildcardOptional.class, NestedOptional.class,
                OptionalOfOptionalInt.class, Box.class)) {
            assertThrows(UnsupportedOperationException.class, () -> recordMapper.fromTyped(json, recordClass),
                    recordClass.getSimpleName());
        }
    }

    @Test
    @DisplayName("List components, with null elements")
    public void testList() {
        record Lists(List<String> strings, List<Integer> numbers, List<String> empty) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        var result = recordMapper.fromTyped(parse("""
            { "strings": ["a", "b"], "numbers": [1, null, 3], "empty": [] }
            """), Lists.class);

        assertEquals(List.of("a", "b"), result.strings());
        assertEquals(Arrays.asList(1, null, 3), result.numbers());
        assertEquals(List.of(), result.empty());
        assertThrows(UnsupportedOperationException.class, () -> result.strings().add("c"));
    }

    @Test
    @DisplayName("Lists of records, lists and optionals")
    public void testNestedLists() {
        record Point(int x, int y) {}
        record Shapes(List<Point> points, List<List<Integer>> matrix, List<Optional<Integer>> gaps) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        var result = recordMapper.fromTyped(parse("""
            {
              "points": [ { "x": 1, "y": 2 }, null ],
              "matrix": [ [1, 2], [], null ],
              "gaps": [ 1, null ]
            }
            """), Shapes.class);

        assertEquals(Arrays.asList(new Point(1, 2), null), result.points());
        assertEquals(Arrays.asList(List.of(1, 2), List.of(), null), result.matrix());
        assertEquals(List.of(Optional.of(1), Optional.empty()), result.gaps());
    }

    @Test
    @DisplayName("JSON null, missing keys and Optional<List<T>>")
    public void testListNullAndMissing() {
        record Names(List<String> names) {}
        record OptionalNames(Optional<List<String>> names) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        assertEquals(new Names(null), recordMapper.fromTyped(parse("""
            { "names": null }
            """), Names.class));
        assertThrows(JsonValueException.class, () -> recordMapper.fromTyped(parse("{}"), Names.class));

        assertEquals(new OptionalNames(Optional.of(List.of("a"))), recordMapper.fromTyped(parse("""
            { "names": ["a"] }
            """), OptionalNames.class));
        assertEquals(new OptionalNames(Optional.empty()), recordMapper.fromTyped(parse("""
            { "names": null }
            """), OptionalNames.class));
        assertEquals(new OptionalNames(Optional.empty()), recordMapper.fromTyped(parse("{}"), OptionalNames.class));
    }

    @Test
    @DisplayName("List errors are reported with their path")
    public void testListErrors() {
        record Numbers(List<Integer> values) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        var exception = assertThrows(JsonValueException.class, () -> recordMapper.fromTyped(parse("""
            { "values": [1, "two", 3] }
            """), Numbers.class));
        assertTrue(exception.getMessage().contains("Path: \"{values[1\""), exception.getMessage());
        assertThrows(JsonValueException.class, () -> recordMapper.fromTyped(parse("""
            { "values": { "a": 1 } }
            """), Numbers.class));
        assertNull(recordMapper.match(parse("""
            { "values": [1, 2.5] }
            """), Numbers.class));
    }

    record TreeNode(String value, List<TreeNode> children) {}

    @Test
    @DisplayName("Recursive records through lists")
    public void testRecursiveList() {
        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        var result = recordMapper.fromTyped(parse("""
            {
              "value": "root",
              "children": [
                { "value": "a", "children": [] },
                { "value": "b", "children": [ { "value": "b1", "children": [] } ] }
              ]
            }
            """), TreeNode.class);

        assertEquals(new TreeNode("root", List.of(
                new TreeNode("a", List.of()),
                new TreeNode("b", List.of(new TreeNode("b1", List.of()))))), result);
    }

    @Test
    @DisplayName("Unsupported list types")
    @SuppressWarnings("rawtypes")
    public void testUnsupportedLists() {
        record RawList(List values) {}
        record WildcardList(List<?> values) {}
        record BoundedList(List<? extends CharSequence> values) {}
        record ConcreteList(ArrayList<String> values) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());
        var json = parse("""
            { "values": ["x"] }
            """);

        for (var recordClass : List.of(RawList.class, WildcardList.class, BoundedList.class, ConcreteList.class)) {
            assertThrows(UnsupportedOperationException.class, () -> recordMapper.fromTyped(json, recordClass),
                    recordClass.getSimpleName());
        }
    }

    @Test
    @DisplayName("Set components drop duplicates and keep the order of the first occurrence")
    public void testSet() {
        record Point(int x, int y) {}
        record Sets(Set<String> tags, Set<Point> points) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        var result = recordMapper.fromTyped(parse("""
            {
              "tags": ["b", "a", "b", null, "c", null],
              "points": [ { "x": 1, "y": 2 }, { "x": 1, "y": 2 }, { "x": 0, "y": 0 } ]
            }
            """), Sets.class);

        assertEquals(Arrays.asList("b", "a", null, "c"), new ArrayList<>(result.tags()));
        assertEquals(List.of(new Point(1, 2), new Point(0, 0)), new ArrayList<>(result.points()));
        assertThrows(UnsupportedOperationException.class, () -> result.tags().add("d"));
        assertThrows(JsonValueException.class, () -> recordMapper.fromTyped(parse("""
            { "tags": { "a": "b" }, "points": [] }
            """), Sets.class));
    }

    @Test
    @DisplayName("Map components keep the member order and allow null values")
    public void testMap() {
        record Point(int x, int y) {}
        record Maps(Map<String, Integer> counts, Map<String, Point> points, Map<String, List<String>> groups) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        var result = recordMapper.fromTyped(parse("""
            {
              "counts": { "z": 1, "a": null, "m": 3 },
              "points": { "origin": { "x": 0, "y": 0 } },
              "groups": { "vowels": ["a", "e"], "none": [] }
            }
            """), Maps.class);

        assertEquals(Arrays.asList("z", "a", "m"), new ArrayList<>(result.counts().keySet()));
        assertEquals(Arrays.asList(1, null, 3), new ArrayList<>(result.counts().values()));
        assertEquals(Map.of("origin", new Point(0, 0)), result.points());
        assertEquals(Map.of("vowels", List.of("a", "e"), "none", List.of()), result.groups());
        assertThrows(UnsupportedOperationException.class, () -> result.counts().put("x", 0));

        var exception = assertThrows(JsonValueException.class, () -> recordMapper.fromTyped(parse("""
            { "counts": [1, 2], "points": {}, "groups": {} }
            """), Maps.class));
        assertTrue(exception.getMessage().contains("JsonArray is not a JsonObject"), exception.getMessage());
    }

    @Test
    @DisplayName("Unsupported set and map types")
    @SuppressWarnings("rawtypes")
    public void testUnsupportedSetsAndMaps() {
        record IntegerKeys(Map<Integer, String> values) {}
        record RawMap(Map values) {}
        record WildcardSet(Set<?> values) {}
        record WildcardMapValue(Map<String, ?> values) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());
        var json = parse("""
            { "values": {} }
            """);

        for (var recordClass : List.of(IntegerKeys.class, RawMap.class, WildcardSet.class, WildcardMapValue.class)) {
            assertThrows(UnsupportedOperationException.class, () -> recordMapper.fromTyped(json, recordClass),
                    recordClass.getSimpleName());
        }
    }

    private static JsonArray parseArray(String text) {
        return (JsonArray) Json.parse(text);
    }

    @Test
    @DisplayName("fromTypedList maps a top-level JSON array")
    public void testFromTypedList() {
        record User(String name, int age) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        var users = recordMapper.fromTypedList(parseArray("""
            [ { "name": "Alice", "age": 30 }, null, { "name": "Bob", "age": 25 } ]
            """), User.class);

        assertEquals(Arrays.asList(new User("Alice", 30), null, new User("Bob", 25)), users);
        assertThrows(UnsupportedOperationException.class, () -> users.add(new User("Carol", 1)));
        assertEquals(List.of(), recordMapper.fromTypedList(parseArray("[]"), User.class));
    }

    @Test
    @DisplayName("fromTypedList reports errors with their path")
    public void testFromTypedListErrors() {
        record User(String name, int age) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        var exception = assertThrows(JsonValueException.class, () -> recordMapper.fromTypedList(parseArray("""
            [ { "name": "Alice", "age": 30 }, "Bob" ]
            """), User.class));
        assertTrue(exception.getMessage().contains("JsonString is not a JsonObject"), exception.getMessage());
        assertTrue(exception.getMessage().contains("Path: \"[1\""), exception.getMessage());

        exception = assertThrows(JsonValueException.class, () -> recordMapper.fromTypedList(parseArray("""
            [ { "name": "Alice", "age": 30 }, { "name": "Bob", "age": 25.5 } ]
            """), User.class));
        assertTrue(exception.getMessage().contains("Path: \"[1{age\""), exception.getMessage());

        assertThrows(NullPointerException.class, () -> recordMapper.fromTypedList(null, User.class));
        assertThrows(NullPointerException.class, () -> recordMapper.fromTypedList(parseArray("[]"), (Class<User>) null));
        assertThrows(NullPointerException.class, () -> recordMapper.fromTypedList(parseArray("[]"), (TypeRef<User>) null));
    }

    @Test
    @DisplayName("fromTypedList with unsupported and recursive record types")
    public void testFromTypedListRecordTypes() {
        record ObjectRecord(Object value) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        assertThrows(UnsupportedOperationException.class, () -> recordMapper.fromTypedList(parseArray("""
            [ { "value": 1 } ]
            """), ObjectRecord.class));

        assertEquals(List.of(new Node("a", new Node("b", null)), new Node("c", null)),
                recordMapper.fromTypedList(parseArray("""
                    [ { "value": "a", "next": { "value": "b", "next": null } }, { "value": "c", "next": null } ]
                    """), Node.class));
    }

    enum Color { RED, GREEN, BLUE }

    private enum Secret { HIDDEN }

    enum Op {
        PLUS { int apply(int a, int b) { return a + b; } },
        MINUS { int apply(int a, int b) { return a - b; } };

        abstract int apply(int a, int b);
    }

    @Test
    @DisplayName("Enum components are mapped from the exact name of the constant")
    public void testEnum() {
        record Paint(Color value) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        assertEquals(new Paint(Color.RED), map(recordMapper, Paint.class, "\"RED\""));
        assertEquals(new Paint(null), map(recordMapper, Paint.class, "null"));
        assertMappingFails(recordMapper, Paint.class, "\"red\"", "\"PURPLE\"", "\"\"", "0");

        var exception = assertThrows(JsonValueException.class, () -> map(recordMapper, Paint.class, "\"PURPLE\""));
        assertTrue(exception.getMessage().contains("\"PURPLE\" is not a constant of " + Color.class.getName()),
                exception.getMessage());
        exception = assertThrows(JsonValueException.class, () -> map(recordMapper, Paint.class, "0"));
        assertTrue(exception.getMessage().contains("JsonNumber is not a JsonString"), exception.getMessage());
        assertTrue(exception.getMessage().contains("Path: \"{value\""), exception.getMessage());
    }

    @Test
    @DisplayName("Enums as collection elements and optionals")
    public void testEnumElements() {
        record Palette(List<Color> list, Set<Color> set, Optional<Color> favorite, Optional<Color> hated) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        var result = recordMapper.fromTyped(parse("""
            { "list": ["RED", null, "RED"], "set": ["BLUE", "RED", "BLUE"], "favorite": "GREEN" }
            """), Palette.class);

        assertEquals(Arrays.asList(Color.RED, null, Color.RED), result.list());
        assertEquals(List.of(Color.BLUE, Color.RED), new ArrayList<>(result.set()));
        assertEquals(Optional.of(Color.GREEN), result.favorite());
        assertEquals(Optional.empty(), result.hated());
    }

    @Test
    @DisplayName("Private enums and enums with constant specific class bodies")
    public void testSpecialEnums() {
        record Hidden(Secret secret) {}
        record Operation(Op op, int a, int b) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        assertEquals(new Hidden(Secret.HIDDEN), recordMapper.fromTyped(parse("""
            { "secret": "HIDDEN" }
            """), Hidden.class));

        var operation = recordMapper.fromTyped(parse("""
            { "op": "MINUS", "a": 5, "b": 3 }
            """), Operation.class);
        assertEquals(Op.MINUS, operation.op());
        assertEquals(2, operation.op().apply(operation.a(), operation.b()));
    }

    @Test
    @DisplayName("Enums as map keys")
    public void testEnumMapKeys() {
        record Stock(Map<Color, Integer> counts, Map<Color, List<String>> names) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        var result = recordMapper.fromTyped(parse("""
            {
              "counts": { "GREEN": 2, "RED": null, "BLUE": 1 },
              "names": { "RED": ["cherry", "rose"] }
            }
            """), Stock.class);

        assertEquals(List.of(Color.GREEN, Color.RED, Color.BLUE), new ArrayList<>(result.counts().keySet()));
        assertEquals(Arrays.asList(2, null, 1), new ArrayList<>(result.counts().values()));
        assertEquals(Map.of(Color.RED, List.of("cherry", "rose")), result.names());
        assertThrows(UnsupportedOperationException.class, () -> result.counts().put(Color.RED, 3));

        var exception = assertThrows(JsonValueException.class, () -> recordMapper.fromTyped(parse("""
            { "counts": { "red": 1 }, "names": {} }
            """), Stock.class));
        assertTrue(exception.getMessage().contains("\"red\" is not a constant of " + Color.class.getName()),
                exception.getMessage());
    }

    @Test
    @DisplayName("BigDecimal components are read exactly from the JSON number")
    public void testBigDecimal() {
        record Amount(BigDecimal value) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        var amount = map(recordMapper, Amount.class, "1.50").value();
        assertEquals(new BigDecimal("1.50"), amount);
        assertEquals(2, amount.scale());
        assertEquals(new BigDecimal("3.141592653589793238462643383279"),
                map(recordMapper, Amount.class, "3.141592653589793238462643383279").value());
        // a huge exponent is stored compactly
        assertEquals(new BigDecimal("1e999999999"), map(recordMapper, Amount.class, "1e999999999").value());
        assertNull(map(recordMapper, Amount.class, "null").value());

        var exception = assertThrows(JsonValueException.class, () -> map(recordMapper, Amount.class, "\"1.5\""));
        assertTrue(exception.getMessage().contains("JsonString is not a JsonNumber"), exception.getMessage());
        assertTrue(exception.getMessage().contains("Path: \"{value\""), exception.getMessage());

        // the text of the number is limited to 1000 characters
        assertEquals(new BigDecimal("9".repeat(1000)), map(recordMapper, Amount.class, "9".repeat(1000)).value());
        assertMappingFails(recordMapper, Amount.class, "9".repeat(1001), "\"1.5\"", "true");
    }

    @Test
    @DisplayName("BigInteger components are converted exactly and limited to 1000 digits")
    public void testBigInteger() {
        record Id(BigInteger value) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        assertEquals(new BigInteger("123456789012345678901234567890"),
                map(recordMapper, Id.class, "123456789012345678901234567890").value());
        assertEquals(BigInteger.ONE, map(recordMapper, Id.class, "1.0").value());
        assertEquals(BigInteger.valueOf(100), map(recordMapper, Id.class, "1e2").value());
        assertEquals(BigInteger.ZERO, map(recordMapper, Id.class, "0e999999999").value());
        assertEquals(BigInteger.TEN.pow(999), map(recordMapper, Id.class, "1e999").value());
        assertMappingFails(recordMapper, Id.class, "1.5", "1e1000", "1e-5", "\"1\"");

        // rejected before the value is computed
        var exception = assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> assertThrows(JsonValueException.class, () -> map(recordMapper, Id.class, "1e999999999")));
        assertTrue(exception.getMessage().contains("has more than 1000 digits"), exception.getMessage());
    }

    @Test
    @DisplayName("Arbitrary precision numbers as optionals and collection elements")
    public void testBigNumberElements() {
        record Ledger(Optional<BigDecimal> total, List<BigInteger> ids, Map<String, BigDecimal> prices) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        assertEquals(new Ledger(Optional.empty(), Arrays.asList(BigInteger.ONE, null),
                        Map.of("apple", new BigDecimal("0.99"))),
                recordMapper.fromTyped(parse("""
                    { "ids": [1, null], "prices": { "apple": 0.99 } }
                    """), Ledger.class));
    }

    @Test
    @DisplayName("Primitive array components")
    public void testPrimitiveArrays() {
        record Arrays1(int[] i, long[] l, double[] d, float[] f, short[] s, boolean[] z, char[] c, int[] empty) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        var result = recordMapper.fromTyped(parse("""
            {
              "i": [1, 2, 3.0], "l": [9007199254740993], "d": [5, 0.5], "f": [1.5],
              "s": [-32768, 32767], "z": [true, false], "c": ["a", "\\n"], "empty": []
            }
            """), Arrays1.class);

        assertArrayEquals(new int[] { 1, 2, 3 }, result.i());
        assertArrayEquals(new long[] { 9007199254740993L }, result.l());
        assertArrayEquals(new double[] { 5.0, 0.5 }, result.d());
        assertArrayEquals(new float[] { 1.5f }, result.f());
        assertArrayEquals(new short[] { -32768, 32767 }, result.s());
        assertArrayEquals(new boolean[] { true, false }, result.z());
        assertArrayEquals(new char[] { 'a', '\n' }, result.c());
        assertArrayEquals(new int[0], result.empty());
    }

    @Test
    @DisplayName("Primitive array elements are converted like primitive components")
    public void testPrimitiveArrayElementErrors() {
        record Ints(int[] value) {}
        record Floats(float[] value) {}
        record Shorts(short[] value) {}
        record Chars(char[] value) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        assertMappingFails(recordMapper, Ints.class, "[1, 1.5]", "[1, null]", "[\"1\"]", "{ \"a\": 1 }", "1");
        assertMappingFails(recordMapper, Floats.class, "[1e39]");
        assertMappingFails(recordMapper, Shorts.class, "[32768]");
        assertMappingFails(recordMapper, Chars.class, "[\"ab\"]", "[\"\"]", "\"ab\"");

        var exception = assertThrows(JsonValueException.class, () -> map(recordMapper, Ints.class, "[1, null]"));
        assertTrue(exception.getMessage().contains("JsonNull is not a JsonNumber"), exception.getMessage());
        assertTrue(exception.getMessage().contains("Path: \"{value[1\""), exception.getMessage());
    }

    @Test
    @DisplayName("JSON null and missing keys for array components, fresh arrays per call")
    public void testArrayNullAndMissing() {
        record Ints(int[] value) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        assertNull(map(recordMapper, Ints.class, "null").value());
        assertThrows(JsonValueException.class, () -> recordMapper.fromTyped(parse("{}"), Ints.class));

        var json = parse("""
            { "value": [1, 2] }
            """);
        assertNotSame(recordMapper.fromTyped(json, Ints.class).value(), recordMapper.fromTyped(json, Ints.class).value());
    }

    @Test
    @DisplayName("byte[] components are read from a Base64 string")
    public void testByteArray() {
        record Blob(byte[] value) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        assertArrayEquals(new byte[] { 1, 2, -1 }, map(recordMapper, Blob.class, "\"AQL/\"").value());
        assertArrayEquals(new byte[0], map(recordMapper, Blob.class, "\"\"").value());
        // like java.util.Base64, the padding is optional
        assertArrayEquals(new byte[] { 1, 2 }, map(recordMapper, Blob.class, "\"AQI=\"").value());
        assertArrayEquals(new byte[] { 1, 2 }, map(recordMapper, Blob.class, "\"AQI\"").value());
        assertNull(map(recordMapper, Blob.class, "null").value());
        assertMappingFails(recordMapper, Blob.class, "\"!!\"", "\"A\"", "[1, 2]");

        var exception = assertThrows(JsonValueException.class, () -> map(recordMapper, Blob.class, "\"!!\""));
        assertTrue(exception.getMessage().contains("is not valid Base64"), exception.getMessage());
        exception = assertThrows(JsonValueException.class, () -> map(recordMapper, Blob.class, "[1, 2]"));
        assertTrue(exception.getMessage().contains("JsonArray is not a JsonString"), exception.getMessage());
        assertTrue(exception.getMessage().contains("Path: \"{value\""), exception.getMessage());
    }

    @Test
    @DisplayName("Multidimensional primitive arrays, ragged and with null rows")
    public void testMultidimensionalArrays() {
        record Tensor(double[][] matrix, int[][][] cube, byte[][] blobs) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        var result = recordMapper.fromTyped(parse("""
            {
              "matrix": [ [1.0, 2.0], [3.5], null, [] ],
              "cube": [ [ [1, 2], [3] ], [] ],
              "blobs": [ "AQL/", "" ]
            }
            """), Tensor.class);

        assertArrayEquals(new double[][] { { 1.0, 2.0 }, { 3.5 }, null, {} }, result.matrix());
        assertArrayEquals(new int[][][] { { { 1, 2 }, { 3 } }, {} }, result.cube());
        assertArrayEquals(new byte[][] { { 1, 2, -1 }, {} }, result.blobs());

        var exception = assertThrows(JsonValueException.class, () -> recordMapper.fromTyped(parse("""
            { "matrix": [ [1.0], ["x"] ], "cube": [], "blobs": [] }
            """), Tensor.class));
        assertTrue(exception.getMessage().contains("Path: \"{matrix[1[0\""), exception.getMessage());
    }

    @Test
    @DisplayName("Primitive arrays in optionals and collections")
    public void testArraysInOptionalsAndCollections() {
        record Series(Optional<int[]> ids, List<double[]> rows, Map<String, double[]> columns) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        var result = recordMapper.fromTyped(parse("""
            { "rows": [ [1, 2], null ], "columns": { "x": [0.5] } }
            """), Series.class);

        assertEquals(Optional.empty(), result.ids());
        assertArrayEquals(new double[] { 1, 2 }, result.rows().get(0));
        assertNull(result.rows().get(1));
        assertArrayEquals(new double[] { 0.5 }, result.columns().get("x"));

        var withIds = recordMapper.fromTyped(parse("""
            { "ids": [7, 8], "rows": [], "columns": {} }
            """), Series.class);
        assertArrayEquals(new int[] { 7, 8 }, withIds.ids().orElseThrow());
    }

    @Test
    @DisplayName("Arrays of reference types are not supported")
    public void testUnsupportedArrays() {
        record Point(int x, int y) {}
        record Strings(String[] values) {}
        record Integers(Integer[] values) {}
        record Points(Point[] values) {}
        record StringMatrix(String[][] values) {}
        record GenericArray(List<String>[] values) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());
        var json = parse("""
            { "values": [] }
            """);

        for (var recordClass : List.of(Strings.class, Integers.class, Points.class, StringMatrix.class,
                GenericArray.class)) {
            assertThrows(UnsupportedOperationException.class, () -> recordMapper.fromTyped(json, recordClass),
                    recordClass.getSimpleName());
        }
        var exception = assertThrows(UnsupportedOperationException.class,
                () -> recordMapper.fromTyped(json, StringMatrix.class));
        assertTrue(exception.getMessage().contains("java.lang.String[][]"), exception.getMessage());
    }

    // Generic records are member records, so that they can refer to each other

    record Item(String name, int qty) {}

    record Order(String id) {}

    record Page<T>(List<T> items, int total) {}

    record Envelope<T>(Page<T> page, Optional<T> first) {}

    record Pair<A, B>(A first, B second) {}

    record Box<T extends Number>(T value) {}

    record GTree<T>(T value, List<GTree<T>> children) {}

    record GA<T>(T value, GB<T> b) {}
    record GB<T>(GA<T> a) {}

    record Weird<T>(T value, Weird<List<T>> next) {}

    private static final String ITEM_PAGE = """
        { "items": [ { "name": "a", "qty": 1 }, { "name": "b", "qty": 2 } ], "total": 2 }
        """;

    @Test
    @DisplayName("Generic record components with type arguments")
    public void testGenericRecordComponent() {
        record ItemResponse(Page<Item> items, String cursor) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        var result = recordMapper.fromTyped(parse("""
            { "items": %s, "cursor": "next" }
            """.formatted(ITEM_PAGE)), ItemResponse.class);

        assertEquals(new ItemResponse(new Page<>(List.of(new Item("a", 1), new Item("b", 2)), 2), "next"), result);
    }

    @Test
    @DisplayName("Nested generic records, several and bounded type parameters")
    public void testNestedGenericRecords() {
        record Holder(Envelope<Item> envelope, Pair<String, List<Integer>> pair, Box<BigDecimal> box) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        var result = recordMapper.fromTyped(parse("""
            {
              "envelope": { "page": %s, "first": { "name": "a", "qty": 1 } },
              "pair": { "first": "x", "second": [1, 2] },
              "box": { "value": 1.50 }
            }
            """.formatted(ITEM_PAGE)), Holder.class);

        assertEquals(new Envelope<>(new Page<>(List.of(new Item("a", 1), new Item("b", 2)), 2),
                Optional.of(new Item("a", 1))), result.envelope());
        assertEquals(new Pair<>("x", List.of(1, 2)), result.pair());
        assertEquals(new Box<>(new BigDecimal("1.50")), result.box());
    }

    @Test
    @DisplayName("Generic records in collections and with other type arguments")
    public void testGenericRecordsInCollections() {
        record Catalog(List<Page<Item>> pages, Map<String, Box<Integer>> boxes, Page<int[]> arrays, Page<Color> colors) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        var result = recordMapper.fromTyped(parse("""
            {
              "pages": [ %s, null ],
              "boxes": { "one": { "value": 1 } },
              "arrays": { "items": [ [1, 2], [] ], "total": 2 },
              "colors": { "items": [ "RED", null ], "total": 2 }
            }
            """.formatted(ITEM_PAGE)), Catalog.class);

        assertEquals(Arrays.asList(new Page<>(List.of(new Item("a", 1), new Item("b", 2)), 2), null), result.pages());
        assertEquals(Map.of("one", new Box<>(1)), result.boxes());
        assertArrayEquals(new int[] { 1, 2 }, result.arrays().items().get(0));
        assertEquals(Arrays.asList(Color.RED, null), result.colors().items());
    }

    @Test
    @DisplayName("One generic record class with two different type arguments")
    public void testTwoInstantiations() {
        record Both(Page<Item> items, Page<Order> orders) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        var result = recordMapper.fromTyped(parse("""
            { "items": %s, "orders": { "items": [ { "id": "o1" } ], "total": 1 } }
            """.formatted(ITEM_PAGE)), Both.class);

        assertEquals(new Item("a", 1), result.items().items().get(0));
        assertEquals(new Order("o1"), result.orders().items().get(0));

        // an Order is not an Item
        var exception = assertThrows(JsonValueException.class, () -> recordMapper.fromTyped(parse("""
            { "items": { "items": [ { "id": "o1" } ], "total": 1 }, "orders": { "items": [], "total": 0 } }
            """), Both.class));
        assertTrue(exception.getMessage().contains("\"name\" does not exist"), exception.getMessage());
    }

    @Test
    @DisplayName("Recursive and mutually recursive generic records")
    public void testRecursiveGenericRecords() {
        record TreeHolder(GTree<String> tree) {}
        record ABHolder(GA<String> a) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        assertEquals(new TreeHolder(new GTree<>("root", List.of(new GTree<>("leaf", List.of())))),
                recordMapper.fromTyped(parse("""
                    { "tree": { "value": "root", "children": [ { "value": "leaf", "children": [] } ] } }
                    """), TreeHolder.class));

        assertEquals(new ABHolder(new GA<>("x", new GB<>(new GA<>("y", null)))),
                recordMapper.fromTyped(parse("""
                    { "a": { "value": "x", "b": { "a": { "value": "y", "b": null } } } }
                    """), ABHolder.class));
    }

    @Test
    @DisplayName("A generic record type that expands infinitely is rejected")
    public void testInfinitelyExpandingGenericRecord() {
        record WeirdHolder(Weird<String> weird) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        var exception = assertThrows(UnsupportedOperationException.class, () -> recordMapper.fromTyped(parse("""
            { "weird": null }
            """), WeirdHolder.class));
        assertTrue(exception.getMessage().contains("expands infinitely"), exception.getMessage());
    }

    @Test
    @DisplayName("Generic records without type arguments or with wildcards are not supported")
    @SuppressWarnings("rawtypes")
    public void testRawGenericRecords() {
        record RawHolder(Page page) {}
        record WildcardHolder(Page<?> page) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());
        var json = parse(ITEM_PAGE);

        var exception = assertThrows(UnsupportedOperationException.class, () -> recordMapper.fromTyped(json, Page.class));
        assertTrue(exception.getMessage().contains("requires type arguments, use TypeRef"), exception.getMessage());
        exception = assertThrows(UnsupportedOperationException.class, () -> recordMapper.fromTyped(parse("""
            { "page": null }
            """), RawHolder.class));
        assertTrue(exception.getMessage().contains("use TypeRef"), exception.getMessage());
        assertThrows(UnsupportedOperationException.class, () -> recordMapper.fromTyped(parse("""
            { "page": null }
            """), WildcardHolder.class));
    }

    @Test
    @DisplayName("Errors inside generic records are reported with their path")
    public void testGenericRecordErrorPath() {
        record ItemResponse(Page<Item> items, String cursor) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        var exception = assertThrows(JsonValueException.class, () -> recordMapper.fromTyped(parse("""
            { "items": { "items": [ { "name": "a", "qty": 1 }, { "name": "b", "qty": "two" } ], "total": 2 },
              "cursor": null }
            """), ItemResponse.class));
        assertTrue(exception.getMessage().contains("Path: \"{items{items[1{qty\""), exception.getMessage());
    }

    @Test
    @DisplayName("Top-level generic records with TypeRef")
    public void testTypeRef() {
        var recordMapper = RecordMapper.of(MethodHandles.lookup());
        var expected = new Page<>(List.of(new Item("a", 1), new Item("b", 2)), 2);

        assertEquals(expected, recordMapper.fromTyped(parse(ITEM_PAGE), new TypeRef<Page<Item>>() {}));
        assertEquals(Arrays.asList(expected, null),
                recordMapper.fromTypedList(parseArray("[ %s, null ]".formatted(ITEM_PAGE)), new TypeRef<Page<Item>>() {}));

        if (recordMapper.match(parse(ITEM_PAGE), new TypeRef<Page<Item>>() {}) instanceof Page<?>(List<?> items, int total)) {
            assertEquals(expected.items(), items);
            assertEquals(2, total);
        } else {
            fail("Should match the record");
        }
        assertNull(recordMapper.match(parse("""
            { "items": [ { "id": "o1" } ], "total": 1 }
            """), new TypeRef<Page<Item>>() {}));

        // a non-generic record
        assertEquals(new Item("a", 1), recordMapper.fromTyped(parse("""
            { "name": "a", "qty": 1 }
            """), new TypeRef<Item>() {}));
    }

    private static <T extends Record> Page<T> loadPage(RecordMapper recordMapper, JsonObject json, Class<T> type) {
        return recordMapper.fromTyped(json, TypeRef.of(Page.class, type));
    }

    @Test
    @DisplayName("TypeRef.of composes a type at runtime, e.g. in generic code")
    public void testTypeRefOf() {
        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        Page<Item> page = loadPage(recordMapper, parse(ITEM_PAGE), Item.class);
        assertEquals(new Item("b", 2), page.items().get(1));

        TypeRef<Page<Box<Integer>>> boxes = TypeRef.of(Page.class, TypeRef.of(Box.class, Integer.class).type());
        assertEquals(new Page<>(List.of(new Box<>(7)), 1), recordMapper.fromTyped(parse("""
            { "items": [ { "value": 7 } ], "total": 1 }
            """), boxes));

        // equal types are equal, however they were created
        assertEquals(new TypeRef<Page<Item>>() {}, TypeRef.of(Page.class, Item.class));
        assertEquals(new TypeRef<Page<Item>>() {}.hashCode(), TypeRef.of(Page.class, Item.class).hashCode());
        assertEquals(new TypeRef<Item>() {}, TypeRef.of(Item.class));
        assertTrue(TypeRef.of(Page.class, Item.class).toString().contains("Page<" + Item.class.getTypeName() + ">"));
    }

    private static <T extends Record> TypeRef<Page<T>> pageOfTypeVariable() {
        return new TypeRef<Page<T>>() {};
    }

    @SuppressWarnings("rawtypes")
    static final class RawTypeRef extends TypeRef {}

    @Test
    @DisplayName("Invalid TypeRefs are rejected")
    public void testInvalidTypeRefs() {
        var exception = assertThrows(IllegalArgumentException.class, RecordMapperTest::pageOfTypeVariable);
        assertTrue(exception.getMessage().contains("use TypeRef.of"), exception.getMessage());
        assertThrows(IllegalArgumentException.class, () -> new TypeRef<Page<?>>() {});
        assertThrows(IllegalStateException.class, RawTypeRef::new);

        assertThrows(IllegalArgumentException.class, () -> TypeRef.of(Page.class));
        assertThrows(IllegalArgumentException.class, () -> TypeRef.of(Pair.class, String.class));
        assertThrows(IllegalArgumentException.class, () -> TypeRef.of(String.class));
        assertThrows(IllegalArgumentException.class, () -> TypeRef.of(Page.class, int.class));
        assertThrows(NullPointerException.class, () -> TypeRef.of(Page.class, (Type) null));
    }

    @Test
    @DisplayName("Concurrent first use of recursive generic records through TypeRef")
    public void testConcurrentGenericDecoderCreation() throws Exception {
        var gaJson = parse("""
            { "value": "x", "b": { "a": { "value": "y", "b": null } } }
            """);
        var gbJson = parse("""
            { "a": { "value": "x", "b": { "a": null } } }
            """);
        var expectedA = new GA<>("x", new GB<>(new GA<>("y", null)));
        var expectedB = new GB<>(new GA<>("x", new GB<String>(null)));
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int round = 0; round < 200; round++) {
                var recordMapper = RecordMapper.of(MethodHandles.lookup());
                var barrier = new CyclicBarrier(threads);
                var results = new ArrayList<Future<Record>>();
                for (int t = 0; t < threads; t++) {
                    // both entry points: building one type publishes the other one before the cycle is closed
                    boolean even = t % 2 == 0;
                    Callable<Record> task = () -> {
                        barrier.await();
                        return even
                                ? recordMapper.fromTyped(gaJson, new TypeRef<GA<String>>() {})
                                : recordMapper.fromTyped(gbJson, new TypeRef<GB<String>>() {});
                    };
                    results.add(pool.submit(task));
                }
                for (int t = 0; t < threads; t++) {
                    assertEquals(t % 2 == 0 ? expectedA : expectedB, results.get(t).get());
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // JSON names

    @Test
    @DisplayName("@JsonName maps a component from a member with another name")
    public void testJsonName() {
        record User(@JsonName("first_name") String firstName, @JsonName("user-id") long userId, int age) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        assertEquals(new User("Alice", 42, 30), recordMapper.fromTyped(parse("""
                { "first_name": "Alice", "user-id": 42, "age": 30 }
                """), User.class));

        // the component name is not accepted instead of the JSON name
        var exception = assertThrows(JsonValueException.class, () -> recordMapper.fromTyped(parse("""
                { "firstName": "Alice", "user-id": 42, "age": 30 }
                """), User.class));
        assertTrue(exception.getMessage().contains("\"first_name\" does not exist"), exception.getMessage());
    }

    @Test
    @DisplayName("Naming strategies")
    public void testJsonNaming() {
        String[][] cases = {
                // component, SNAKE_CASE, KEBAB_CASE
                { "firstName", "first_name", "first-name" },
                { "userID", "user_id", "user-id" },
                { "URLValue", "url_value", "url-value" },
                { "HTTPServerPort", "http_server_port", "http-server-port" },
                { "address2Line", "address2_line", "address2-line" },
                { "value2", "value2", "value2" },
                { "x", "x", "x" },
                { "already_snake", "already_snake", "already_snake" },
        };
        for (var c : cases) {
            assertEquals(c[0], JsonNaming.IDENTITY.jsonName(c[0]));
            assertEquals(c[1], JsonNaming.SNAKE_CASE.jsonName(c[0]), c[0]);
            assertEquals(c[2], JsonNaming.KEBAB_CASE.jsonName(c[0]), c[0]);
        }
    }

    record StreetAddress(String streetName, String zipCode) {}

    record PhoneNumber(String countryCode, String phoneNumber) {}

    record Customer(String firstName, Optional<String> middleName, StreetAddress homeAddress,
                    List<PhoneNumber> phoneNumbers) {}

    record Labeled<T>(String labelText, T labeledValue) {}

    @Test
    @DisplayName("SNAKE_CASE applies to nested records, lists, optionals and generic records")
    public void testSnakeCase() {
        var recordMapper = RecordMapper.builder(MethodHandles.lookup()).naming(JsonNaming.SNAKE_CASE).build();

        var customer = recordMapper.fromTyped(parse("""
                {
                  "first_name": "Alice",
                  "home_address": { "street_name": "Main St", "zip_code": "12345" },
                  "phone_numbers": [ { "country_code": "+49", "phone_number": "123" } ]
                }
                """), Customer.class);
        assertEquals(new Customer("Alice", Optional.empty(), new StreetAddress("Main St", "12345"),
                List.of(new PhoneNumber("+49", "123"))), customer);

        assertEquals(new Labeled<>("zip", new StreetAddress("Main St", "12345")), recordMapper.fromTyped(parse("""
                { "label_text": "zip", "labeled_value": { "street_name": "Main St", "zip_code": "12345" } }
                """), new TypeRef<Labeled<StreetAddress>>() {}));
    }

    @Test
    @DisplayName("KEBAB_CASE and @JsonName taking precedence over the strategy")
    public void testKebabCaseAndPrecedence() {
        record Account(@JsonName("ID") long accountId, String accountName) {}

        var kebab = RecordMapper.builder(MethodHandles.lookup()).naming(JsonNaming.KEBAB_CASE).build();
        assertEquals(new StreetAddress("Main St", "12345"), kebab.fromTyped(parse("""
                { "street-name": "Main St", "zip-code": "12345" }
                """), StreetAddress.class));

        var snake = RecordMapper.builder(MethodHandles.lookup()).naming(JsonNaming.SNAKE_CASE).build();
        assertEquals(new Account(7, "main"), snake.fromTyped(parse("""
                { "ID": 7, "account_name": "main" }
                """), Account.class));
    }

    @Test
    @DisplayName("Mappers with different naming strategies are independent")
    public void testIndependentMappers() {
        var identity = RecordMapper.of(MethodHandles.lookup());
        var snake = RecordMapper.builder(MethodHandles.lookup()).naming(JsonNaming.SNAKE_CASE).build();
        var expected = new StreetAddress("Main St", "12345");

        assertEquals(expected, identity.fromTyped(parse("""
                { "streetName": "Main St", "zipCode": "12345" }
                """), StreetAddress.class));
        assertEquals(expected, snake.fromTyped(parse("""
                { "street_name": "Main St", "zip_code": "12345" }
                """), StreetAddress.class));
        assertNull(identity.match(parse("""
                { "street_name": "Main St", "zip_code": "12345" }
                """), StreetAddress.class));
    }

    @Test
    @DisplayName("Invalid JSON names and builder arguments are rejected")
    public void testInvalidJsonNames() {
        record Duplicate(@JsonName("a") String x, String a) {}
        record SnakeDuplicate(String fooBar, String foo_bar) {}
        record Empty(@JsonName("") String x) {}

        var identity = RecordMapper.of(MethodHandles.lookup());
        var snake = RecordMapper.builder(MethodHandles.lookup()).naming(JsonNaming.SNAKE_CASE).build();
        var json = parse("{}");

        var exception = assertThrows(IllegalArgumentException.class, () -> identity.fromTyped(json, Duplicate.class));
        assertTrue(exception.getMessage().contains("same JSON name \"a\""), exception.getMessage());
        // a different strategy only collides with the other mapper
        assertThrows(JsonValueException.class, () -> identity.fromTyped(json, SnakeDuplicate.class));
        assertThrows(IllegalArgumentException.class, () -> snake.fromTyped(json, SnakeDuplicate.class));
        assertThrows(IllegalArgumentException.class, () -> identity.fromTyped(json, Empty.class));

        assertThrows(NullPointerException.class, () -> RecordMapper.builder(null));
        assertThrows(NullPointerException.class, () -> RecordMapper.builder(MethodHandles.lookup()).naming(null));
    }

    // Decoders

    @Test
    @DisplayName("A decoder for a type the mapper does not support, everywhere, never with JSON null")
    public void testDecoder() {
        record Price(BigDecimal amount, Currency currency, List<Currency> accepted, Optional<Currency> fallback,
                     Map<String, Currency> byCountry) {}

        var calls = new AtomicInteger();
        var recordMapper = RecordMapper.builder(MethodHandles.lookup())
                .decoder(Currency.class, value -> {
                    calls.incrementAndGet();
                    return Currency.getInstance(value.asString());
                })
                .build();

        var price = recordMapper.fromTyped(parse("""
                {
                  "amount": 12.50, "currency": "EUR", "accepted": [ "EUR", null, "USD" ],
                  "fallback": null, "byCountry": { "CH": "CHF", "XX": null }
                }
                """), Price.class);

        var eur = Currency.getInstance("EUR");
        assertEquals(new Price(new BigDecimal("12.50"), eur, Arrays.asList(eur, null, Currency.getInstance("USD")),
                Optional.empty(), price.byCountry()), price);
        assertEquals(Currency.getInstance("CHF"), price.byCountry().get("CH"));
        assertNull(price.byCountry().get("XX"));
        // JSON null is mapped without calling the decoder
        assertEquals(4, calls.get());

        assertEquals(new Page<>(List.of(eur), 1), recordMapper.fromTyped(parse("""
                { "items": [ "EUR" ], "total": 1 }
                """), new TypeRef<Page<Currency>>() {}));
    }

    record Point(int x, int y) {}

    @Test
    @DisplayName("Decoders take precedence over the conversions of the mapper, also at the top level")
    public void testDecoderPrecedence() {
        record Shape(String name, List<String> tags, Point origin) {}

        // a point is written as [x, y] instead of { "x": .., "y": .. }
        var recordMapper = RecordMapper.builder(MethodHandles.lookup())
                .decoder(String.class, value -> value.asString().strip())
                .decoder(Point.class, value -> new Point(value.get(0).asInt(), value.get(1).asInt()))
                .build();

        assertEquals(new Shape("square", List.of("a", "b"), new Point(1, 2)), recordMapper.fromTyped(parse("""
                { "name": "  square ", "tags": [ " a", "b " ], "origin": [1, 2] }
                """), Shape.class));

        // top level: the decoder gets the JSON object
        var objectDecoder = RecordMapper.builder(MethodHandles.lookup())
                .decoder(Point.class, value -> new Point(value.get("left").asInt(), value.get("top").asInt()))
                .build();
        assertEquals(new Point(3, 4), objectDecoder.fromTyped(parse("""
                { "left": 3, "top": 4 }
                """), Point.class));
        assertEquals(List.of(new Point(3, 4)), objectDecoder.fromTypedList(parseArray("""
                [ { "left": 3, "top": 4 } ]
                """), Point.class));
        assertNull(objectDecoder.match(parse("""
                { "x": 3, "y": 4 }
                """), Point.class));
    }

    @Test
    @DisplayName("Exceptions of decoders are reported as JsonValueException")
    public void testDecoderErrors() {
        record Price(Currency currency) {}

        var recordMapper = RecordMapper.builder(MethodHandles.lookup())
                .decoder(Currency.class, value -> Currency.getInstance(value.asString()))
                .build();

        // Currency.getInstance throws an IllegalArgumentException for an unknown code
        var exception = assertThrows(JsonValueException.class, () -> recordMapper.fromTyped(parse("""
                { "currency": "XXXX" }
                """), Price.class));
        assertTrue(exception.getMessage().startsWith("Cannot convert JSON value to java.util.Currency"),
                exception.getMessage());
        assertTrue(exception.getCause() instanceof IllegalArgumentException, String.valueOf(exception.getCause()));

        // a JsonValueException of the decoder is passed on with its path
        exception = assertThrows(JsonValueException.class, () -> recordMapper.fromTyped(parse("""
                { "currency": 978 }
                """), Price.class));
        assertTrue(exception.getMessage().contains("Path: \"{currency\""), exception.getMessage());

        assertNull(recordMapper.match(parse("""
                { "currency": "XXXX" }
                """), Price.class));
    }

    @Test
    @DisplayName("Registration of decoders")
    public void testDecoderRegistration() {
        record Price(Currency currency) {}

        var builder = RecordMapper.builder(MethodHandles.lookup())
                .decoder(Currency.class, value -> Currency.getInstance("USD"))
                .decoder(Currency.class, value -> Currency.getInstance(value.asString()));
        var recordMapper = builder.build();
        // later changes of the builder do not affect a built mapper
        builder.decoder(Currency.class, value -> Currency.getInstance("JPY"));

        // the later registration replaces the earlier one
        assertEquals(new Price(Currency.getInstance("EUR")), recordMapper.fromTyped(parse("""
                { "currency": "EUR" }
                """), Price.class));

        assertThrows(IllegalArgumentException.class, () -> builder.decoder(int.class, value -> 1));
        assertThrows(NullPointerException.class, () -> builder.decoder(null, value -> 1));
        assertThrows(NullPointerException.class, () -> builder.decoder(Currency.class, null));
    }

    // Predefined decoders

    @Test
    @DisplayName("java.time types, UUID and URI are read from their standard text form")
    public void testPredefinedDecoders() {
        record Values(LocalDate date, LocalTime time, LocalDateTime dateTime, OffsetDateTime offsetDateTime,
                      ZonedDateTime zonedDateTime, Instant instant, Duration duration, Period period, UUID uuid,
                      URI uri) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        var values = recordMapper.fromTyped(parse("""
                {
                  "date": "2026-09-24", "time": "13:45:30", "dateTime": "2026-09-24T13:45:30",
                  "offsetDateTime": "2026-09-24T13:45:30+02:00",
                  "zonedDateTime": "2026-09-24T13:45:30+02:00[Europe/Berlin]",
                  "instant": "2026-09-24T11:45:30Z", "duration": "PT1H30M", "period": "P1Y2M3D",
                  "uuid": "123e4567-e89b-12d3-a456-426614174000", "uri": "https://example.com/a?b=c"
                }
                """), Values.class);

        assertEquals(new Values(
                LocalDate.of(2026, 9, 24),
                LocalTime.of(13, 45, 30),
                LocalDateTime.of(2026, 9, 24, 13, 45, 30),
                OffsetDateTime.of(2026, 9, 24, 13, 45, 30, 0, ZoneOffset.ofHours(2)),
                ZonedDateTime.of(2026, 9, 24, 13, 45, 30, 0, ZoneId.of("Europe/Berlin")),
                Instant.parse("2026-09-24T11:45:30Z"),
                Duration.ofMinutes(90),
                Period.of(1, 2, 3),
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                URI.create("https://example.com/a?b=c")), values);
    }

    @Test
    @DisplayName("Predefined decoders in optionals and collections, errors, and replacing them")
    public void testPredefinedDecodersUsage() {
        record Event(Optional<LocalDate> date, List<Instant> timestamps) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        assertEquals(new Event(Optional.empty(), List.of(Instant.parse("2026-09-24T11:45:30Z"))),
                recordMapper.fromTyped(parse("""
                        { "timestamps": [ "2026-09-24T11:45:30Z" ] }
                        """), Event.class));

        // an invalid date
        var exception = assertThrows(JsonValueException.class, () -> recordMapper.fromTyped(parse("""
                { "date": "2026-13-01", "timestamps": [] }
                """), Event.class));
        assertTrue(exception.getCause() instanceof DateTimeParseException, String.valueOf(exception.getCause()));
        // a number instead of a string
        exception = assertThrows(JsonValueException.class, () -> recordMapper.fromTyped(parse("""
                { "timestamps": [ 1790250330000 ] }
                """), Event.class));
        assertTrue(exception.getMessage().contains("Path: \"{timestamps[0\""), exception.getMessage());

        // replaced: Instant from epoch milliseconds
        var epochMillis = RecordMapper.builder(MethodHandles.lookup())
                .decoder(Instant.class, value -> Instant.ofEpochMilli(value.asLong()))
                .build();
        assertEquals(new Event(Optional.empty(), List.of(Instant.ofEpochMilli(1790250330000L))),
                epochMillis.fromTyped(parse("""
                        { "timestamps": [ 1790250330000 ] }
                        """), Event.class));
    }

    // Writing JSON

    @Test
    @DisplayName("toJson writes scalar values")
    public void testToJsonScalars() {
        record Scalars(String s, char c, boolean z, byte b, short sh, int i, long l, float f, double d,
                       Integer boxed, BigDecimal decimal, BigInteger big, Color color, Op op) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        var json = recordMapper.toJson(new Scalars("a\"b", 'x', true, (byte) -1, (short) 2, 3, Long.MAX_VALUE,
                0.1f, 2.5, 7, new BigDecimal("1.50"), new BigInteger("123456789012345678901234567890"),
                Color.RED, Op.MINUS));

        assertEquals("{\"s\":\"a\\\"b\",\"c\":\"x\",\"z\":true,\"b\":-1,\"sh\":2,\"i\":3,"
                + "\"l\":9223372036854775807,\"f\":0.1,\"d\":2.5,\"boxed\":7,\"decimal\":1.50,"
                + "\"big\":123456789012345678901234567890,\"color\":\"RED\",\"op\":\"MINUS\"}", json.toString());
    }

    @Test
    @DisplayName("toJson writes nested records, null as JSON null and leaves out empty optionals")
    public void testToJsonNullAndOptional() {
        record City(String name) {}
        record Person(String name, City city, City previousCity, Optional<String> nickname, Optional<String> title,
                      OptionalInt age, OptionalInt height) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        var json = recordMapper.toJson(new Person("Bob", new City("X"), null, Optional.of("B"), Optional.empty(),
                OptionalInt.of(3), OptionalInt.empty()));

        assertEquals("{\"name\":\"Bob\",\"city\":{\"name\":\"X\"},\"previousCity\":null,\"nickname\":\"B\",\"age\":3}",
                json.toString());
    }

    @Test
    @DisplayName("toJson uses the JSON names of the components")
    public void testToJsonNames() {
        record Account(@JsonName("ID") long accountId, String accountName, StreetAddress homeAddress) {}

        var recordMapper = RecordMapper.builder(MethodHandles.lookup()).naming(JsonNaming.SNAKE_CASE).build();

        assertEquals("{\"ID\":1,\"account_name\":\"main\",\"home_address\":{\"street_name\":\"Main St\",\"zip_code\":\"1\"}}",
                recordMapper.toJson(new Account(1, "main", new StreetAddress("Main St", "1"))).toString());
    }

    @Test
    @DisplayName("Numbers that JSON cannot represent are rejected")
    public void testToJsonNotFinite() {
        record Measurement(double value) {}
        record FloatMeasurement(float value) {}
        record Measurements(List<Double> values) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        assertThrows(IllegalArgumentException.class, () -> recordMapper.toJson(new Measurement(Double.NaN)));
        assertThrows(IllegalArgumentException.class,
                () -> recordMapper.toJson(new FloatMeasurement(Float.POSITIVE_INFINITY)));
        assertThrows(IllegalArgumentException.class,
                () -> recordMapper.toJson(new Measurements(List.of(1.0, Double.NEGATIVE_INFINITY))));
    }

    @Test
    @DisplayName("toJsonList writes a list of records, null elements as JSON null")
    public void testToJsonList() {
        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        assertEquals("[{\"name\":\"a\",\"qty\":1},null]",
                recordMapper.toJsonList(Arrays.asList(new Item("a", 1), null)).toString());
        assertEquals("[]", recordMapper.toJsonList(List.of()).toString());
        assertThrows(NullPointerException.class, () -> recordMapper.toJson(null));
        assertThrows(NullPointerException.class, () -> recordMapper.toJsonList(null));
    }

    @Test
    @DisplayName("toJson writes collections, maps and optional elements")
    public void testToJsonCollections() {
        record Collections1(List<String> list, Set<Integer> set, Map<String, Double> map, Map<Color, Boolean> colors,
                            List<Optional<String>> optionals) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        var map = new LinkedHashMap<String, Double>();
        map.put("b", 1.5);
        map.put("a", null);
        var json = recordMapper.toJson(new Collections1(Arrays.asList("x", null), new LinkedHashSet<>(List.of(3, 1)),
                map, Map.of(Color.RED, true), List.of(Optional.of("y"), Optional.empty())));

        assertEquals("{\"list\":[\"x\",null],\"set\":[3,1],\"map\":{\"b\":1.5,\"a\":null},\"colors\":{\"RED\":true},"
                + "\"optionals\":[\"y\",null]}", json.toString());
    }

    @Test
    @DisplayName("toJson writes primitive and multidimensional arrays")
    public void testToJsonArrays() {
        record Arrays2(int[] ints, double[][] matrix, byte[] blob, char[] chars, boolean[] flags, float[] floats) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        var json = recordMapper.toJson(new Arrays2(new int[] { 1, 2 }, new double[][] { { 1.5 }, null, {} },
                new byte[] { 1, 2, -1 }, new char[] { 'a', 'b' }, new boolean[] { true }, new float[] { 0.1f }));

        assertEquals("{\"ints\":[1,2],\"matrix\":[[1.5],null,[]],\"blob\":\"AQL/\",\"chars\":[\"a\",\"b\"],"
                + "\"flags\":[true],\"floats\":[0.1]}", json.toString());
    }

    @Test
    @DisplayName("toJson writes generic and recursive records according to the runtime types")
    public void testToJsonGenericAndRecursive() {
        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        assertEquals("{\"items\":[{\"name\":\"a\",\"qty\":1}],\"total\":1}",
                recordMapper.toJson(new Page<>(List.of(new Item("a", 1)), 1)).toString());
        assertEquals("{\"first\":\"x\",\"second\":[1,2]}",
                recordMapper.toJson(new Pair<>("x", List.of(1, 2))).toString());
        assertEquals("{\"value\":\"a\",\"next\":{\"value\":\"b\",\"next\":null}}",
                recordMapper.toJson(new Node("a", new Node("b", null))).toString());
        assertEquals("{\"value\":\"root\",\"children\":[{\"value\":\"leaf\",\"children\":[]}]}",
                recordMapper.toJson(new GTree<>("root", List.of(new GTree<>("leaf", List.of())))).toString());
    }

    @Test
    @DisplayName("Values that could not be read back are not written")
    public void testToJsonUnsupported() {
        record Strings(String[] values) {}
        record IntegerKeys(Map<Integer, String> values) {}
        record Anything(Object value) {}
        record Queue(Collection<String> values) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        assertThrows(UnsupportedOperationException.class, () -> recordMapper.toJson(new Strings(new String[] { "a" })));
        assertThrows(UnsupportedOperationException.class,
                () -> recordMapper.toJson(new IntegerKeys(Map.of(1, "a"))));
        var exception = assertThrows(UnsupportedOperationException.class,
                () -> recordMapper.toJson(new Anything(Thread.currentThread())));
        assertTrue(exception.getMessage().contains("Unsupported type: java.lang.Thread"), exception.getMessage());
        assertThrows(UnsupportedOperationException.class,
                () -> recordMapper.toJson(new Queue(new ArrayDeque<>(List.of("a")))));
        // a supported runtime type is written, even if the declared type is not supported for reading
        assertEquals("{\"value\":\"a\"}", recordMapper.toJson(new Anything("a")).toString());
    }

    @Test
    @DisplayName("An encoder writes a type the mapper does not support, everywhere")
    public void testEncoder() {
        record Price(Currency currency, List<Currency> accepted, Map<String, Currency> byCountry) {}

        var recordMapper = RecordMapper.builder(MethodHandles.lookup())
                .encoder(Currency.class, currency -> JsonString.of(currency.getCurrencyCode()))
                .build();

        var eur = Currency.getInstance("EUR");
        assertEquals("{\"currency\":\"EUR\",\"accepted\":[\"EUR\",null],\"byCountry\":{\"CH\":\"CHF\"}}",
                recordMapper.toJson(new Price(eur, Arrays.asList(eur, null),
                        Map.of("CH", Currency.getInstance("CHF")))).toString());
    }

    @Test
    @DisplayName("java.time types, UUID and URI are written in their standard text form")
    public void testPredefinedEncoders() {
        record Values(LocalDate date, LocalTime time, LocalDateTime dateTime, OffsetDateTime offsetDateTime,
                      ZonedDateTime zonedDateTime, Instant instant, Duration duration, Period period, UUID uuid,
                      URI uri) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        var json = recordMapper.toJson(new Values(
                LocalDate.of(2026, 9, 24),
                LocalTime.of(13, 45, 30),
                LocalDateTime.of(2026, 9, 24, 13, 45, 30),
                OffsetDateTime.of(2026, 9, 24, 13, 45, 30, 0, ZoneOffset.ofHours(2)),
                ZonedDateTime.of(2026, 9, 24, 13, 45, 30, 0, ZoneId.of("Europe/Berlin")),
                Instant.parse("2026-09-24T11:45:30Z"),
                Duration.ofMinutes(90),
                Period.of(1, 2, 3),
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                URI.create("https://example.com/a?b=c")));

        assertEquals("{\"date\":\"2026-09-24\",\"time\":\"13:45:30\",\"dateTime\":\"2026-09-24T13:45:30\","
                + "\"offsetDateTime\":\"2026-09-24T13:45:30+02:00\","
                + "\"zonedDateTime\":\"2026-09-24T13:45:30+02:00[Europe/Berlin]\",\"instant\":\"2026-09-24T11:45:30Z\","
                + "\"duration\":\"PT1H30M\",\"period\":\"P1Y2M3D\",\"uuid\":\"123e4567-e89b-12d3-a456-426614174000\","
                + "\"uri\":\"https://example.com/a?b=c\"}", json.toString());
    }

    @Test
    @DisplayName("Encoders replace the way the mapper writes a value, also for records")
    public void testEncoderPrecedence() {
        record Event(Instant at, Point where) {}

        var recordMapper = RecordMapper.builder(MethodHandles.lookup())
                .encoder(Instant.class, instant -> JsonNumber.of(instant.toEpochMilli()))
                .encoder(Point.class, point -> JsonArray.of(List.of(JsonNumber.of(point.x()), JsonNumber.of(point.y()))))
                .build();

        assertEquals("{\"at\":1790250330000,\"where\":[1,2]}",
                recordMapper.toJson(new Event(Instant.ofEpochMilli(1790250330000L), new Point(1, 2))).toString());
        // at the top level, the result of the encoder of a record must be a JSON object
        assertThrows(IllegalArgumentException.class, () -> recordMapper.toJson(new Point(1, 2)));
    }

    @Test
    @DisplayName("Registration of encoders and encoders returning null")
    public void testEncoderRegistration() {
        record Price(Currency currency) {}

        var builder = RecordMapper.builder(MethodHandles.lookup());
        var recordMapper = builder.encoder(Currency.class, currency -> null).build();

        var exception = assertThrows(NullPointerException.class,
                () -> recordMapper.toJson(new Price(Currency.getInstance("EUR"))));
        assertTrue(exception.getMessage().contains("java.util.Currency"), exception.getMessage());

        assertThrows(IllegalArgumentException.class, () -> builder.encoder(int.class, value -> JsonNumber.of(value)));
        assertThrows(NullPointerException.class, () -> builder.encoder(null, value -> JsonNull.of()));
        assertThrows(NullPointerException.class, () -> builder.encoder(Currency.class, null));
    }

    // Round trips: what is written can be read back

    record Everything(String text, char letter, boolean flag, byte smallNumber, short shortNumber, int number,
                      long longNumber, float floatNumber, double doubleNumber, Integer boxedNumber,
                      BigDecimal decimal, BigInteger bigNumber, Color color, Op operation, Item item,
                      Item missingItem, Optional<String> nickname, Optional<String> noNickname, OptionalInt count,
                      OptionalDouble noRatio, List<Item> items, Set<Color> colors,
                      Map<String, List<Integer>> groups, Map<Color, Integer> counts, List<Optional<Integer>> gaps,
                      LocalDate date, Instant instant, UUID uuid, Page<Item> page, Node node) {}

    private static Everything everything() {
        var groups = new LinkedHashMap<String, List<Integer>>();
        groups.put("odd", List.of(1, 3));
        groups.put("none", null);
        return new Everything("a \"quoted\"\ttext", 'x', true, (byte) -128, (short) 32767, -1, Long.MIN_VALUE,
                0.1f, 1e-300, null, new BigDecimal("1.50"), new BigInteger("-123456789012345678901234567890"),
                Color.GREEN, Op.PLUS, new Item("a", 1), null, Optional.of("nick"), Optional.empty(),
                OptionalInt.of(3), OptionalDouble.empty(), List.of(new Item("b", 2)), Set.of(Color.RED),
                groups, Map.of(Color.BLUE, 7), List.of(Optional.of(1), Optional.empty()),
                LocalDate.of(2026, 9, 24), Instant.parse("2026-09-24T11:45:30.123Z"),
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                new Page<>(List.of(new Item("c", 3)), 1), new Node("n1", new Node("n2", null)));
    }

    @Test
    @DisplayName("Round trip of a record with all kinds of values")
    public void testRoundTrip() {
        var everything = everything();

        for (var naming : JsonNaming.values()) {
            var recordMapper = RecordMapper.builder(MethodHandles.lookup()).naming(naming).build();
            var json = recordMapper.toJson(everything);
            assertEquals(everything, recordMapper.fromTyped(json, Everything.class), naming.name());
            // also through the JSON text
            assertEquals(everything, recordMapper.fromTyped((JsonObject) Json.parse(json.toString()), Everything.class),
                    naming.name());
        }
    }

    @Test
    @DisplayName("Round trip of arrays, generic records at the top level and custom types")
    public void testRoundTripSpecialCases() {
        record WithArrays(int[] ints, double[][] matrix, byte[] blob, char[] chars) {}
        record Price(Currency currency, List<Currency> accepted) {}

        var recordMapper = RecordMapper.builder(MethodHandles.lookup())
                .decoder(Currency.class, value -> Currency.getInstance(value.asString()))
                .encoder(Currency.class, currency -> JsonString.of(currency.getCurrencyCode()))
                .build();

        // arrays are compared by identity in equals of a record
        var arrays = new WithArrays(new int[] { 1, 2 }, new double[][] { { 0.5 }, null }, new byte[] { 0, -1 },
                new char[] { 'a' });
        var readArrays = recordMapper.fromTyped(recordMapper.toJson(arrays), WithArrays.class);
        assertArrayEquals(arrays.ints(), readArrays.ints());
        assertArrayEquals(arrays.matrix(), readArrays.matrix());
        assertArrayEquals(arrays.blob(), readArrays.blob());
        assertArrayEquals(arrays.chars(), readArrays.chars());

        var page = new Page<>(List.of(new Item("a", 1), new Item("b", 2)), 2);
        assertEquals(page, recordMapper.fromTyped(recordMapper.toJson(page), new TypeRef<Page<Item>>() {}));
        assertEquals(List.of(page), recordMapper.fromTypedList(recordMapper.toJsonList(List.of(page)),
                new TypeRef<Page<Item>>() {}));

        var price = new Price(Currency.getInstance("EUR"), List.of(Currency.getInstance("USD")));
        assertEquals(price, recordMapper.fromTyped(recordMapper.toJson(price), Price.class));
    }

    // Sealed interfaces

    sealed interface Shape permits Circle, Square {}
    record Circle(double radius) implements Shape {}
    record Square(double side) implements Shape {}

    @JsonDiscriminator("kind")
    sealed interface Animal permits Cat, Dog {}
    @JsonTypeName("cat")
    record Cat(String name) implements Animal {}
    @JsonTypeName("dog")
    record Dog(String name, boolean good) implements Animal {}

    sealed interface Figure permits Dot, Polygon {}
    record Dot() implements Figure {}
    sealed interface Polygon extends Figure permits Triangle, Rectangle {}
    record Triangle(double base, double height) implements Polygon {}
    record Rectangle(double width, double height) implements Polygon {}

    sealed interface Expr permits Num, Add {}
    record Num(int value) implements Expr {}
    record Add(Expr left, Expr right) implements Expr {}

    sealed interface Clash permits ClashRecord {}
    record ClashRecord(String type) implements Clash {}

    sealed interface Twins permits TwinA, TwinB {}
    @JsonTypeName("twin")
    record TwinA() implements Twins {}
    @JsonTypeName("twin")
    record TwinB() implements Twins {}

    sealed interface WithClass permits Plain {}
    static final class Plain implements WithClass {}

    sealed interface WithEnum permits Level {}
    enum Level implements WithEnum { LOW }

    sealed interface Result<T> permits Ok {}
    record Ok<T>(T value) implements Result<T> {}

    @Test
    @DisplayName("Sealed interfaces as components, elements, optionals and map values")
    public void testSealedComponents() {
        record Drawing(Shape main, List<Shape> shapes, Optional<Shape> highlight, Map<String, Shape> named,
                       Shape none) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        var drawing = recordMapper.fromTyped(parse("""
                {
                  "main": { "type": "Circle", "radius": 1.0 },
                  "shapes": [ { "type": "Square", "side": 2.0 }, null, { "radius": 3.0, "type": "Circle" } ],
                  "named": { "sq": { "type": "Square", "side": 4.0 } },
                  "none": null
                }
                """), Drawing.class);

        assertEquals(new Drawing(new Circle(1.0), Arrays.asList(new Square(2.0), null, new Circle(3.0)),
                Optional.empty(), Map.of("sq", new Square(4.0)), null), drawing);
    }

    @Test
    @DisplayName("Sealed interfaces at the top level, also with match and record patterns")
    public void testSealedTopLevel() {
        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        Shape shape = recordMapper.fromTyped(parse("""
                { "type": "Circle", "radius": 1.5 }
                """), Shape.class);
        assertEquals(new Circle(1.5), shape);

        List<Shape> shapes = recordMapper.fromTypedList(parseArray("""
                [ { "type": "Square", "side": 1 }, null ]
                """), Shape.class);
        assertEquals(Arrays.asList(new Square(1), null), shapes);

        if (recordMapper.match(parse("""
                { "type": "Square", "side": 2 }
                """), Shape.class) instanceof Shape matched) {
            var area = switch (matched) {
                case Circle(double radius) -> Math.PI * radius * radius;
                case Square(double side) -> side * side;
            };
            assertEquals(4.0, area);
        } else {
            fail("Should match the sealed interface");
        }
        assertNull(recordMapper.match(parse("""
                { "type": "Hexagon" }
                """), Shape.class));
    }

    @Test
    @DisplayName("@JsonDiscriminator and @JsonTypeName")
    public void testSealedCustomNames() {
        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        assertEquals(List.of(new Cat("Tom"), new Dog("Rex", true)), recordMapper.fromTypedList(parseArray("""
                [ { "kind": "cat", "name": "Tom" }, { "kind": "dog", "name": "Rex", "good": true } ]
                """), Animal.class));
        assertThrows(JsonValueException.class, () -> recordMapper.fromTyped(parse("""
                { "type": "cat", "name": "Tom" }
                """), Animal.class));
        assertThrows(JsonValueException.class, () -> recordMapper.fromTyped(parse("""
                { "kind": "Cat", "name": "Tom" }
                """), Animal.class));
    }

    @Test
    @DisplayName("Nested sealed interfaces and recursion through a sealed interface")
    public void testNestedAndRecursiveSealed() {
        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        assertEquals(List.of(new Dot(), new Triangle(1, 2), new Rectangle(3, 4)), recordMapper.fromTypedList(parseArray("""
                [ { "type": "Dot" }, { "type": "Triangle", "base": 1, "height": 2 },
                  { "type": "Rectangle", "width": 3, "height": 4 } ]
                """), Figure.class));
        assertEquals(new Triangle(1, 2), recordMapper.fromTyped(parse("""
                { "type": "Triangle", "base": 1, "height": 2 }
                """), Polygon.class));
        // a Dot is a Figure, but not a Polygon
        assertThrows(JsonValueException.class, () -> recordMapper.fromTyped(parse("""
                { "type": "Dot" }
                """), Polygon.class));

        assertEquals(new Add(new Num(1), new Add(new Num(2), new Num(3))), recordMapper.fromTyped(parse("""
                { "type": "Add", "left": { "type": "Num", "value": 1 },
                  "right": { "type": "Add", "left": { "type": "Num", "value": 2 }, "right": { "type": "Num", "value": 3 } } }
                """), Expr.class));
    }

    @Test
    @DisplayName("Errors of values of sealed interfaces")
    public void testSealedValueErrors() {
        record Drawing(List<Shape> shapes) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        var exception = assertThrows(JsonValueException.class, () -> recordMapper.fromTyped(parse("""
                { "shapes": [ { "radius": 1 } ] }
                """), Drawing.class));
        assertTrue(exception.getMessage().contains("\"type\" does not exist"), exception.getMessage());
        assertTrue(exception.getMessage().contains("Path: \"{shapes[0\""), exception.getMessage());

        exception = assertThrows(JsonValueException.class, () -> recordMapper.fromTyped(parse("""
                { "shapes": [ { "type": 1 } ] }
                """), Drawing.class));
        assertTrue(exception.getMessage().contains("Path: \"{shapes[0{type\""), exception.getMessage());

        exception = assertThrows(JsonValueException.class, () -> recordMapper.fromTyped(parse("""
                { "shapes": [ { "type": "Hexagon" } ] }
                """), Drawing.class));
        assertTrue(exception.getMessage().contains("\"Hexagon\" is not a type of " + Shape.class.getName()),
                exception.getMessage());
        assertNull(recordMapper.match(parse("""
                { "shapes": [ { "type": "Hexagon" } ] }
                """), Drawing.class));
    }

    @Test
    @DisplayName("Invalid and unsupported sealed interfaces and top-level types")
    public void testInvalidSealedInterfaces() {
        var recordMapper = RecordMapper.of(MethodHandles.lookup());
        var json = parse("""
                { "type": "x" }
                """);

        assertThrows(IllegalArgumentException.class, () -> recordMapper.fromTyped(json, Clash.class));
        var exception = assertThrows(IllegalArgumentException.class, () -> recordMapper.fromTyped(json, Twins.class));
        assertTrue(exception.getMessage().contains("same type name \"twin\""), exception.getMessage());
        assertThrows(UnsupportedOperationException.class, () -> recordMapper.fromTyped(json, WithClass.class));
        assertThrows(UnsupportedOperationException.class, () -> recordMapper.fromTyped(json, WithEnum.class));
        assertThrows(UnsupportedOperationException.class, () -> recordMapper.fromTyped(json, Result.class));
        var unsupported = assertThrows(UnsupportedOperationException.class,
                () -> recordMapper.fromTyped(json, String.class));
        assertTrue(unsupported.getMessage().contains("neither a record nor a sealed interface"),
                unsupported.getMessage());
    }

    @Test
    @DisplayName("Registered decoders take precedence for sealed interfaces, and work at the top level")
    public void testSealedAndTopLevelDecoders() {
        var recordMapper = RecordMapper.builder(MethodHandles.lookup())
                // a shape written as { "circle": radius }
                .decoder(Shape.class, value -> new Circle(value.get("circle").asDouble()))
                .decoder(Currency.class, value -> Currency.getInstance(value.get("code").asString()))
                .build();

        assertEquals(new Circle(2), recordMapper.fromTyped(parse("""
                { "circle": 2 }
                """), Shape.class));
        assertEquals(Currency.getInstance("EUR"), recordMapper.fromTyped(parse("""
                { "code": "EUR" }
                """), Currency.class));
    }

    @JsonDiscriminator("a")
    sealed interface SideA permits Both {}
    @JsonDiscriminator("b")
    sealed interface SideB permits Both {}
    record Both() implements SideA, SideB {}

    @Test
    @DisplayName("Records of sealed interfaces are always written with their type, as the first member")
    public void testSealedToJson() {
        record Drawing(Shape main, List<Shape> shapes, Circle circle) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        assertEquals("{\"type\":\"Circle\",\"radius\":1.0}", recordMapper.toJson(new Circle(1)).toString());
        assertEquals("{\"main\":{\"type\":\"Square\",\"side\":2.0},\"shapes\":[{\"type\":\"Circle\",\"radius\":1.0},null],"
                        + "\"circle\":{\"type\":\"Circle\",\"radius\":3.0}}",
                recordMapper.toJson(new Drawing(new Square(2), Arrays.asList(new Circle(1), null), new Circle(3))).toString());
        assertEquals("[{\"kind\":\"cat\",\"name\":\"Tom\"},{\"kind\":\"dog\",\"name\":\"Rex\",\"good\":true}]",
                recordMapper.toJsonList(List.of(new Cat("Tom"), new Dog("Rex", true))).toString());
        assertEquals("{\"type\":\"Triangle\",\"base\":1.0,\"height\":2.0}",
                recordMapper.toJson(new Triangle(1, 2)).toString());
        assertEquals("{\"type\":\"Dot\"}", recordMapper.toJson(new Dot()).toString());
    }

    @Test
    @DisplayName("Invalid discriminators when writing, and encoders of records of sealed interfaces")
    public void testSealedToJsonSpecialCases() {
        record Drawing(List<Shape> shapes) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        var exception = assertThrows(IllegalArgumentException.class, () -> recordMapper.toJson(new Both()));
        assertTrue(exception.getMessage().contains("different discriminators"), exception.getMessage());
        assertThrows(IllegalArgumentException.class, () -> recordMapper.toJson(new ClashRecord("x")));

        var circles = RecordMapper.builder(MethodHandles.lookup())
                .encoder(Circle.class, circle -> JsonString.of("circle " + circle.radius()))
                .build();
        assertEquals("{\"shapes\":[\"circle 1.0\",{\"type\":\"Square\",\"side\":2.0}]}",
                circles.toJson(new Drawing(List.of(new Circle(1), new Square(2)))).toString());
    }

    @Test
    @DisplayName("Round trip of values of sealed interfaces")
    public void testSealedRoundTrip() {
        record Canvas(List<Shape> allShapes, Optional<Shape> selectedShape, Map<String, Figure> namedFigures,
                      Expr formula) {}

        var canvas = new Canvas(List.of(new Circle(1), new Square(2)), Optional.of(new Circle(3)),
                Map.of("t", new Triangle(1, 2), "d", new Dot()),
                new Add(new Num(1), new Add(new Num(2), new Num(3))));

        for (var naming : JsonNaming.values()) {
            var recordMapper = RecordMapper.builder(MethodHandles.lookup()).naming(naming).build();
            assertEquals(canvas, recordMapper.fromTyped(recordMapper.toJson(canvas), Canvas.class), naming.name());
            assertEquals(canvas.formula(), recordMapper.fromTyped(recordMapper.toJson(canvas.formula()), Expr.class));
            List<Figure> figures = List.of(new Dot(), new Rectangle(3, 4));
            assertEquals(figures, recordMapper.fromTypedList(recordMapper.toJsonList(List.of(new Dot(),
                    new Rectangle(3, 4))), Figure.class));
            List<Animal> animals = List.of(new Cat("Tom"), new Dog("Rex", false));
            assertEquals(animals, recordMapper.fromTypedList(recordMapper.toJsonList(List.of(new Cat("Tom"),
                    new Dog("Rex", false))), Animal.class));
        }
    }

    // toJson(Object), strict mode, JSON text

    @Test
    @DisplayName("toJson takes any value that is written as a JSON object")
    public void testToJsonObject() {
        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        Shape shape = new Square(2);
        assertEquals("{\"type\":\"Square\",\"side\":2.0}", recordMapper.toJson(shape).toString());
        List<Shape> shapes = List.of(new Circle(1), new Square(2));
        assertEquals("[{\"type\":\"Circle\",\"radius\":1.0},{\"type\":\"Square\",\"side\":2.0}]",
                recordMapper.toJsonList(shapes).toString());
        assertEquals("{\"a\":1}", recordMapper.toJson(Map.of("a", 1)).toString());

        var exception = assertThrows(IllegalArgumentException.class, () -> recordMapper.toJson("abc"));
        assertTrue(exception.getMessage().contains("not written as a JSON object but as a JSON string"),
                exception.getMessage());
        assertThrows(IllegalArgumentException.class, () -> recordMapper.toJson(List.of()));
    }

    @Test
    @DisplayName("toJsonList and fromTypedList with values of a class with an encoder and a decoder")
    public void testToJsonListWithCodec() {
        var recordMapper = RecordMapper.builder(MethodHandles.lookup())
                .decoder(Currency.class, value -> Currency.getInstance(value.asString()))
                .encoder(Currency.class, currency -> JsonString.of(currency.getCurrencyCode()))
                .build();

        var currencies = Arrays.asList(Currency.getInstance("EUR"), null, Currency.getInstance("USD"));
        var json = recordMapper.toJsonList(currencies);
        assertEquals("[\"EUR\",null,\"USD\"]", json.toString());
        assertEquals(currencies, recordMapper.fromTypedList(json, Currency.class));
    }

    @Test
    @DisplayName("Unknown members are ignored by default and rejected in the strict mode")
    public void testFailOnUnknownMembers() {
        record Person(String name, StreetAddress address) {}

        var lenient = RecordMapper.of(MethodHandles.lookup());
        var strict = RecordMapper.builder(MethodHandles.lookup()).failOnUnknownMembers(true).build();
        var json = parse("""
                { "name": "Bob", "address": { "streetName": "Main St", "zipCode": "1", "city": "X" } }
                """);

        assertEquals(new Person("Bob", new StreetAddress("Main St", "1")), lenient.fromTyped(json, Person.class));
        var exception = assertThrows(JsonValueException.class, () -> strict.fromTyped(json, Person.class));
        assertTrue(exception.getMessage().contains("Unknown member \"city\" of record " + StreetAddress.class.getName()),
                exception.getMessage());
        assertNull(strict.match(json, Person.class));
        assertThrows(JsonValueException.class, () -> strict.fromTyped(parse("""
                { "name": "Bob", "address": null, "age": 3 }
                """), Person.class));
        assertEquals(new Person("Bob", null), strict.fromTyped(parse("""
                { "name": "Bob", "address": null }
                """), Person.class));
    }

    @Test
    @DisplayName("The strict mode accepts type members, JSON names, missing optionals and any map keys")
    public void testFailOnUnknownMembersSpecialCases() {
        record Settings(Optional<String> theme, Map<String, Integer> limits) {}

        var strict = RecordMapper.builder(MethodHandles.lookup()).failOnUnknownMembers(true).build();

        // the type member of a record of a sealed interface
        assertEquals(new Circle(1), strict.fromTyped(parse("""
                { "type": "Circle", "radius": 1 }
                """), Shape.class));
        assertEquals(new Circle(1), strict.fromTyped(parse("""
                { "type": "Circle", "radius": 1 }
                """), Circle.class));
        assertThrows(JsonValueException.class, () -> strict.fromTyped(parse("""
                { "type": "Circle", "radius": 1, "color": "red" }
                """), Shape.class));

        // missing optionals and any keys of maps
        assertEquals(new Settings(Optional.empty(), Map.of("a", 1, "b", 2)), strict.fromTyped(parse("""
                { "limits": { "a": 1, "b": 2 } }
                """), Settings.class));

        // the JSON names, not the component names
        var strictSnake = RecordMapper.builder(MethodHandles.lookup())
                .naming(JsonNaming.SNAKE_CASE)
                .failOnUnknownMembers(true)
                .build();
        assertEquals(new StreetAddress("Main St", "1"), strictSnake.fromTyped(parse("""
                { "street_name": "Main St", "zip_code": "1" }
                """), StreetAddress.class));
        assertThrows(JsonValueException.class, () -> strictSnake.fromTyped(parse("""
                { "street_name": "Main St", "zip_code": "1", "zipCode": "2" }
                """), StreetAddress.class));

        // generic records
        assertThrows(JsonValueException.class, () -> strict.fromTyped(parse("""
                { "items": [ { "name": "a", "qty": 1, "price": 2 } ], "total": 1 }
                """), new TypeRef<Page<Item>>() {}));
    }

    @Test
    @DisplayName("fromJson, fromJsonList and toJsonText work with JSON text")
    public void testJsonText() {
        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        assertEquals(new Item("a", 1), recordMapper.fromJson("""
                { "name": "a", "qty": 1 }
                """, Item.class));
        assertEquals(new Circle(2), recordMapper.fromJson("""
                { "type": "Circle", "radius": 2 }
                """, Shape.class));
        assertEquals(new Page<>(List.of(new Item("a", 1)), 1), recordMapper.fromJson("""
                { "items": [ { "name": "a", "qty": 1 } ], "total": 1 }
                """, new TypeRef<Page<Item>>() {}));
        assertEquals(Arrays.asList(new Item("a", 1), null), recordMapper.fromJsonList("""
                [ { "name": "a", "qty": 1 }, null ]
                """, Item.class));
        assertEquals(List.of(new Page<>(List.of(), 0)), recordMapper.fromJsonList("""
                [ { "items": [], "total": 0 } ]
                """, new TypeRef<Page<Item>>() {}));

        assertEquals("{\"name\":\"a\",\"qty\":1}", recordMapper.toJsonText(new Item("a", 1)));

        // round trip through the text
        var everything = everything();
        assertEquals(everything, recordMapper.fromJson(recordMapper.toJsonText(everything), Everything.class));
    }

    @Test
    @DisplayName("Errors of fromJson and fromJsonList")
    public void testJsonTextErrors() {
        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        assertThrows(JsonParseException.class, () -> recordMapper.fromJson("{ \"name\": ", Item.class));
        var exception = assertThrows(JsonValueException.class, () -> recordMapper.fromJson("[]", Item.class));
        assertTrue(exception.getMessage().contains("JsonArray is not a JsonObject"), exception.getMessage());
        exception = assertThrows(JsonValueException.class, () -> recordMapper.fromJsonList("{}", Item.class));
        assertTrue(exception.getMessage().contains("JsonObject is not a JsonArray"), exception.getMessage());
        assertThrows(NullPointerException.class, () -> recordMapper.fromJson(null, Item.class));
        assertThrows(NullPointerException.class, () -> recordMapper.toJsonText(null));
    }

    // JSON paths in error messages

    // asserts a JsonValueException with the message and the JSON path, as the JSON API reports it
    private static void assertPathError(Executable call, String message, String path) {
        var exception = assertThrows(JsonValueException.class, call);
        assertTrue(exception.getMessage().contains(message), exception.getMessage());
        assertTrue(exception.getMessage().contains(" Path: \"" + path + "\". Location: line "),
                exception.getMessage());
    }

    @Test
    @DisplayName("Conversion errors of the mapper contain the JSON path")
    public void testReadErrorPaths() {
        record Bytes(byte value) {}
        record Outer(Bytes inner) {}
        record Chars(char value) {}
        record Floats(float value) {}
        record Colors(List<Color> colors) {}
        record Counts(Map<Color, Integer> counts) {}
        record Data(byte[] value) {}
        record Big(BigInteger value) {}
        record Numbers(List<Bytes> values) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        assertPathError(() -> recordMapper.fromTyped(parse("""
                {
                  "inner": { "value": 128 }
                }
                """), Outer.class), "128 cannot be represented as a byte.", "{inner{value");
        assertPathError(() -> recordMapper.fromTyped(parse("""
                { "values": [ { "value": 1 }, { "value": -129 } ] }
                """), Numbers.class), "-129 cannot be represented as a byte.", "{values[1{value");
        assertPathError(() -> recordMapper.fromTyped(parse("""
                { "value": "ab" }
                """), Chars.class), "cannot be represented as a char.", "{value");
        assertPathError(() -> recordMapper.fromTyped(parse("""
                { "value": 1e39 }
                """), Floats.class), "cannot be represented as a float.", "{value");
        assertPathError(() -> recordMapper.fromTyped(parse("""
                { "colors": [ "RED", "PURPLE" ] }
                """), Colors.class), "\"PURPLE\" is not a constant of " + Color.class.getName(), "{colors[1");
        assertPathError(() -> recordMapper.fromTyped(parse("""
                { "counts": { "RED": 1, "PURPLE": 2 } }
                """), Counts.class), "\"PURPLE\" is not a constant of " + Color.class.getName(), "{counts{PURPLE");
        assertPathError(() -> recordMapper.fromTyped(parse("""
                { "value": "!!!" }
                """), Data.class), "is not valid Base64.", "{value");
        assertPathError(() -> recordMapper.fromTyped(parse("""
                { "value": 1.5 }
                """), Big.class), "cannot be represented as a BigInteger.", "{value");
        assertPathError(() -> recordMapper.fromTyped(parse("""
                { "value": 1e1001 }
                """), Big.class), "has more than 1000 digits.", "{value");

        // the location is the one of the value
        var exception = assertThrows(JsonValueException.class, () -> recordMapper.fromTyped(parse("""
                {
                  "inner": { "value": 128 }
                }
                """), Outer.class));
        assertTrue(exception.getMessage().endsWith("Location: line 1, position 22."), exception.getMessage());
    }

    @Test
    @DisplayName("Errors of decoders, sealed interfaces and the strict mode contain the JSON path")
    public void testReadErrorPathsOfOtherErrors() {
        record Price(Currency currency) {}
        record Cart(List<Price> prices) {}
        record Drawing(List<Shape> shapes) {}
        record Person(String name, StreetAddress address) {}

        var recordMapper = RecordMapper.builder(MethodHandles.lookup())
                .decoder(Currency.class, value -> Currency.getInstance(value.asString()))
                .build();
        assertPathError(() -> recordMapper.fromTyped(parse("""
                { "prices": [ { "currency": "XXXX" } ] }
                """), Cart.class), "Cannot convert JSON value to java.util.Currency", "{prices[0{currency");
        assertPathError(() -> recordMapper.fromTyped(parse("""
                { "shapes": [ { "type": "Circle", "radius": 1 }, { "type": "Hexagon" } ] }
                """), Drawing.class), "\"Hexagon\" is not a type of " + Shape.class.getName(), "{shapes[1{type");

        var strict = RecordMapper.builder(MethodHandles.lookup()).failOnUnknownMembers(true).build();
        assertPathError(() -> strict.fromTyped(parse("""
                { "name": "Bob", "address": { "streetName": "Main St", "zipCode": "1", "city": "X" } }
                """), Person.class), "Unknown member \"city\"", "{address{city");
    }

    @Test
    @DisplayName("Errors for values that were not parsed have no JSON path")
    public void testReadErrorWithoutPath() {
        record Bytes(byte value) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        var exception = assertThrows(JsonValueException.class,
                () -> recordMapper.fromTyped(JsonObject.of(Map.of("value", JsonNumber.of(128))), Bytes.class));
        assertEquals("128 cannot be represented as a byte.", exception.getMessage());
        assertNull(recordMapper.match(JsonObject.of(Map.of("value", JsonNumber.of(128))), Bytes.class));
        assertEquals(new Bytes((byte) 127),
                recordMapper.fromTyped(JsonObject.of(Map.of("value", JsonNumber.of(127))), Bytes.class));
    }

    @Test
    @DisplayName("Errors while writing keep their type and contain the JSON path")
    public void testWriteErrorPaths() {
        record Line(String product, double price) {}
        record Invoice(List<Line> items, Map<String, Object> extra, float[] weights, Optional<Double> discount) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());
        var items = List.of(new Line("a", 1), new Line("b", 2));

        var exception = assertThrows(IllegalArgumentException.class, () -> recordMapper.toJson(new Invoice(
                List.of(new Line("a", 1), new Line("b", Double.NaN)), Map.of(), new float[0], Optional.empty())));
        assertEquals("NaN cannot be written as a JSON number. Path: \"{items[1{price\".", exception.getMessage());
        exception = assertThrows(IllegalArgumentException.class, () -> recordMapper.toJson(new Invoice(
                items, Map.of(), new float[] { 1, Float.NaN }, Optional.empty())));
        assertTrue(exception.getMessage().endsWith(" Path: \"{weights[1\"."), exception.getMessage());
        exception = assertThrows(IllegalArgumentException.class, () -> recordMapper.toJson(new Invoice(
                items, Map.of(), new float[0], Optional.of(Double.POSITIVE_INFINITY))));
        assertTrue(exception.getMessage().endsWith(" Path: \"{discount\"."), exception.getMessage());

        var unsupported = assertThrows(UnsupportedOperationException.class, () -> recordMapper.toJson(new Invoice(
                items, Map.of("thread", Thread.currentThread()), new float[0], Optional.empty())));
        assertEquals("Unsupported type: java.lang.Thread. Path: \"{extra{thread\".", unsupported.getMessage());

        var nullKey = new LinkedHashMap<String, Object>();
        nullKey.put(null, 1);
        exception = assertThrows(IllegalArgumentException.class, () -> recordMapper.toJson(new Invoice(
                items, Map.of("nested", nullKey), new float[0], Optional.empty())));
        assertTrue(exception.getMessage().contains("null key"), exception.getMessage());
        assertTrue(exception.getMessage().endsWith(" Path: \"{extra{nested\"."), exception.getMessage());

        exception = assertThrows(IllegalArgumentException.class,
                () -> recordMapper.toJsonList(List.of(new Line("a", 1), new Line("b", Double.NEGATIVE_INFINITY))));
        assertTrue(exception.getMessage().endsWith(" Path: \"[1{price\"."), exception.getMessage());

        // errors at the top level have no path
        unsupported = assertThrows(UnsupportedOperationException.class,
                () -> recordMapper.toJson(Thread.currentThread()));
        assertEquals("Unsupported type: java.lang.Thread", unsupported.getMessage());
    }

    @Test
    @DisplayName("Exceptions of encoders and accessors are wrapped and contain the JSON path")
    public void testWriteErrorsOfEncodersAndAccessors() {
        record Price(Currency currency) {}
        record Cart(List<Price> prices) {}
        record Broken(int value) {
            public int value() {
                throw new IllegalStateException("broken");
            }
        }
        record Holder(Broken broken) {}

        var failing = RecordMapper.builder(MethodHandles.lookup())
                .encoder(Currency.class, currency -> {
                    throw new IllegalStateException("boom");
                })
                .build();
        var exception = assertThrows(IllegalArgumentException.class,
                () -> failing.toJson(new Cart(List.of(new Price(Currency.getInstance("EUR"))))));
        assertEquals("Cannot write value of type java.util.Currency: boom. Path: \"{prices[0{currency\".",
                exception.getMessage());
        assertTrue(exception.getCause() instanceof IllegalStateException, String.valueOf(exception.getCause()));

        var returningNull = RecordMapper.builder(MethodHandles.lookup())
                .encoder(Currency.class, currency -> null)
                .build();
        var npe = assertThrows(NullPointerException.class,
                () -> returningNull.toJson(new Price(Currency.getInstance("EUR"))));
        assertEquals("The encoder for java.util.Currency returned null. Path: \"{currency\".", npe.getMessage());

        var recordMapper = RecordMapper.of(MethodHandles.lookup());
        exception = assertThrows(IllegalArgumentException.class, () -> recordMapper.toJson(new Holder(new Broken(1))));
        assertTrue(exception.getMessage().startsWith("Cannot read component value of record "
                + Broken.class.getName() + ": broken"), exception.getMessage());
        assertTrue(exception.getMessage().endsWith(" Path: \"{broken{value\"."), exception.getMessage());
        assertTrue(exception.getCause() instanceof IllegalStateException, String.valueOf(exception.getCause()));
    }

    @Test
    @DisplayName("Cycles are detected while writing")
    public void testWriteCycles() {
        record Node(String name, List<Node> children) {}
        record Values(List<Object> values) {}
        record Settings(Map<String, Object> settings) {}
        record Twice(List<Node> first, List<Node> second) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        // a list that contains a record that contains the list
        var children = new ArrayList<Node>();
        var root = new Node("root", children);
        children.add(root);
        var exception = assertThrows(IllegalArgumentException.class, () -> recordMapper.toJson(root));
        assertEquals("Cycle: a java.util.ArrayList contains itself. Path: \"{children[0{children\".",
                exception.getMessage());
        assertThrows(IllegalArgumentException.class, () -> recordMapper.toJsonText(root));

        // a list that contains itself
        var values = new ArrayList<Object>();
        values.add(1);
        values.add(values);
        exception = assertThrows(IllegalArgumentException.class, () -> recordMapper.toJson(new Values(values)));
        assertTrue(exception.getMessage().endsWith(" Path: \"{values[1\"."), exception.getMessage());
        exception = assertThrows(IllegalArgumentException.class, () -> recordMapper.toJsonList(values));
        assertTrue(exception.getMessage().endsWith(" Path: \"[1\"."), exception.getMessage());

        // a map that contains itself, also at the top level
        var settings = new LinkedHashMap<String, Object>();
        settings.put("self", settings);
        exception = assertThrows(IllegalArgumentException.class, () -> recordMapper.toJson(new Settings(settings)));
        assertEquals("Cycle: a java.util.LinkedHashMap contains itself. Path: \"{settings{self\".",
                exception.getMessage());
        exception = assertThrows(IllegalArgumentException.class, () -> recordMapper.toJson(settings));
        assertTrue(exception.getMessage().endsWith(" Path: \"{self\"."), exception.getMessage());

        // the same instance in several places is not a cycle
        var shared = List.of(new Node("leaf", List.of()));
        assertEquals("{\"first\":[{\"name\":\"leaf\",\"children\":[]}],\"second\":[{\"name\":\"leaf\",\"children\":[]}]}",
                recordMapper.toJsonText(new Twice(shared, shared)));
        var tree = new Node("a", List.of(new Node("b", shared), new Node("c", shared)));
        assertEquals(tree, recordMapper.fromJson(recordMapper.toJsonText(tree), Node.class));
        assertEquals(2, recordMapper.toJsonList(List.of(shared, shared)).asList().size());
    }

    // Exceptions of record constructors

    record Age(int value) {
        Age {
            if (value < 0) {
                throw new IllegalArgumentException("negative age");
            }
        }
    }

    sealed interface Quantity permits Positive {}
    record Positive(int value) implements Quantity {
        Positive {
            if (value <= 0) {
                throw new IllegalArgumentException("not positive: " + value);
            }
        }
    }

    @Test
    @DisplayName("Exceptions of record constructors are reported as JsonValueException with the JSON path")
    public void testConstructorErrors() {
        record Person(String name, Age age) {}
        record People(List<Person> people) {}
        record Named(String name) {
            Named {
                Objects.requireNonNull(name, "name");
            }
        }

        var recordMapper = RecordMapper.of(MethodHandles.lookup());
        var json = parse("""
                { "people": [ { "name": "Alice", "age": { "value": 30 } }, { "name": "Bob", "age": { "value": -1 } } ] }
                """);

        assertPathError(() -> recordMapper.fromTyped(json, People.class),
                "Cannot create record " + Age.class.getName() + ": negative age.", "{people[1{age");
        var exception = assertThrows(JsonValueException.class, () -> recordMapper.fromTyped(json, People.class));
        assertTrue(exception.getCause() instanceof IllegalArgumentException, String.valueOf(exception.getCause()));
        assertNull(recordMapper.match(json, People.class));

        // a NullPointerException of a validation, also at the top level
        exception = assertThrows(JsonValueException.class, () -> recordMapper.fromTyped(parse("""
                { "name": null }
                """), Named.class));
        assertTrue(exception.getMessage().startsWith("Cannot create record " + Named.class.getName() + ": name."),
                exception.getMessage());
        assertTrue(exception.getMessage().contains(" Path: \"\"."), exception.getMessage());
        assertTrue(exception.getCause() instanceof NullPointerException, String.valueOf(exception.getCause()));

        // the other entry points
        assertPathError(() -> recordMapper.fromTypedList(parseArray("""
                [ { "value": 1 }, { "value": -2 } ]
                """), Age.class), "negative age", "[1");
        assertPathError(() -> recordMapper.fromTyped(parse("""
                { "type": "Positive", "value": 0 }
                """), Quantity.class), "not positive: 0", "");
        assertNull(recordMapper.match(parse("""
                { "type": "Positive", "value": 0 }
                """), Quantity.class));
        assertPathError(() -> recordMapper.fromTyped(parse("""
                { "items": [ { "value": -3 } ], "total": 1 }
                """), new TypeRef<Page<Age>>() {}), "negative age", "{items[0");

        // valid values are not affected
        assertEquals(new People(List.of(new Person("Alice", new Age(30)))), recordMapper.fromTyped(parse("""
                { "people": [ { "name": "Alice", "age": { "value": 30 } } ] }
                """), People.class));
    }

    @Test
    @DisplayName("Errors of record constructors are passed on")
    public void testConstructorAssertionError() {
        record Broken(int value) {
            Broken {
                if (value >= 0) {
                    throw new AssertionError("broken");
                }
            }
        }

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        assertThrows(AssertionError.class, () -> recordMapper.fromTyped(parse("""
                { "value": 1 }
                """), Broken.class));
    }

    // Components of the JSON types

    record Event(String type, JsonObject payload, JsonValue meta, JsonArray tags, JsonString label,
                 JsonNumber amount, JsonBoolean flag) {}

    // the JSON text of an Event, with the member replaced by the value (JSON text)
    private static String eventJson(String member, String value) {
        var members = new LinkedHashMap<String, String>();
        members.put("type", "\"order\"");
        members.put("payload", "{\"id\":7,\"items\":[1,2]}");
        members.put("meta", "[true,null]");
        members.put("tags", "[\"a\",\"b\"]");
        members.put("label", "\"x\"");
        members.put("amount", "1.50");
        members.put("flag", "false");
        if (value == null) {
            members.remove(member);
        } else {
            members.put(member, value);
        }
        var text = new StringBuilder("{");
        members.forEach((name, json) -> text.append(text.length() > 1 ? "," : "")
                .append('"').append(name).append("\":").append(json));
        return text.append('}').toString();
    }

    @Test
    @DisplayName("Components of the JSON types are read and written unchanged")
    public void testJsonTypeComponents() {
        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        var text = eventJson("type", "\"order\"");
        var event = recordMapper.fromJson(text, Event.class);
        assertEquals("order", event.type());
        assertEquals(7, event.payload().get("id").asInt());
        assertEquals("{\"id\":7,\"items\":[1,2]}", event.payload().toString());
        assertEquals("[true,null]", event.meta().toString());
        assertEquals(List.of("a", "b"), event.tags().asList().stream().map(JsonValue::asString).toList());
        assertEquals("x", event.label().asString());
        assertEquals("1.50", event.amount().toString());
        assertEquals(false, event.flag().asBoolean());
        // written unchanged, the number keeps its text
        assertEquals(text, recordMapper.toJsonText(event));

        // JSON null: JsonNull for a JsonValue, null for the other JSON types
        var nulls = recordMapper.fromJson("""
                { "type": "t", "payload": null, "meta": null, "tags": null, "label": null, "amount": null, "flag": null }
                """, Event.class);
        assertTrue(nulls.meta() instanceof JsonNull, String.valueOf(nulls.meta()));
        assertNull(nulls.payload());
        assertNull(nulls.tags());
        assertNull(nulls.label());
        assertNull(nulls.amount());
        assertNull(nulls.flag());
        assertEquals("{\"type\":\"t\",\"payload\":null,\"meta\":null,\"tags\":null,\"label\":null,\"amount\":null,"
                + "\"flag\":null}", recordMapper.toJsonText(nulls));

        // a missing member is an error, also for a JsonValue
        var exception = assertThrows(JsonValueException.class,
                () -> recordMapper.fromJson(eventJson("meta", null), Event.class));
        assertTrue(exception.getMessage().contains("\"meta\" does not exist"), exception.getMessage());

        // any members of a JSON object that is passed on, also in the strict mode
        var strict = RecordMapper.builder(MethodHandles.lookup()).failOnUnknownMembers(true).build();
        assertEquals("{\"anything\":{\"goes\":true}}", strict.fromJson(eventJson("payload",
                "{\"anything\":{\"goes\":true}}"), Event.class).payload().toString());

        // a JSON object at the top level is written as it is
        var object = parse("""
                { "a": [1, {}] }
                """);
        assertTrue(recordMapper.toJson(object) == object);
        assertThrows(IllegalArgumentException.class, () -> recordMapper.toJson(JsonString.of("x")));
    }

    @Test
    @DisplayName("JSON types in optionals, collections, maps and generic records")
    public void testJsonTypesInContainers() {
        record Envelope(Optional<JsonValue> extra, List<JsonValue> values, Map<String, JsonValue> extras) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        var envelope = recordMapper.fromJson("""
                { "values": [1, null, "a"], "extras": { "x": {}, "y": null } }
                """, Envelope.class);
        assertEquals(Optional.empty(), envelope.extra());
        assertTrue(envelope.values().get(1) instanceof JsonNull, String.valueOf(envelope.values().get(1)));
        assertEquals("[1,null,\"a\"]", recordMapper.toJsonList(envelope.values()).toString());
        assertEquals("{}", envelope.extras().get("x").toString());
        assertTrue(envelope.extras().get("y") instanceof JsonNull, String.valueOf(envelope.extras().get("y")));
        assertEquals("{\"values\":[1,null,\"a\"],\"extras\":{\"x\":{},\"y\":null}}", recordMapper.toJsonText(envelope));

        assertEquals(Optional.empty(), recordMapper.fromJson("""
                { "extra": null, "values": [], "extras": {} }
                """, Envelope.class).extra());
        assertEquals("{\"a\":1}", recordMapper.fromJson("""
                { "extra": { "a": 1 }, "values": [], "extras": {} }
                """, Envelope.class).extra().orElseThrow().toString());

        var page = recordMapper.fromJson("""
                { "items": [ { "a": 1 }, null ], "total": 2 }
                """, new TypeRef<Page<JsonObject>>() {});
        assertEquals("{\"a\":1}", page.items().get(0).toString());
        assertNull(page.items().get(1));
        assertEquals("{\"items\":[{\"a\":1},null],\"total\":2}", recordMapper.toJsonText(page));
    }

    @Test
    @DisplayName("JSON types with a wrong JSON type and unsupported uses")
    public void testJsonTypeErrors() {
        record WithJsonNull(JsonNull value) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        assertPathError(() -> recordMapper.fromTyped(parse(eventJson("payload", "[1]")), Event.class),
                "JsonArray is not a JsonObject", "{payload");
        assertPathError(() -> recordMapper.fromTyped(parse(eventJson("tags", "{}")), Event.class),
                "JsonObject is not a JsonArray", "{tags");
        assertPathError(() -> recordMapper.fromTyped(parse(eventJson("label", "1")), Event.class),
                "JsonNumber is not a JsonString", "{label");
        assertPathError(() -> recordMapper.fromTyped(parse(eventJson("amount", "\"1\"")), Event.class),
                "JsonString is not a JsonNumber", "{amount");
        assertPathError(() -> recordMapper.fromTyped(parse(eventJson("flag", "1")), Event.class),
                "JsonNumber is not a JsonBoolean", "{flag");
        assertNull(recordMapper.match(parse(eventJson("payload", "[1]")), Event.class));

        var json = parse("""
                { "value": null }
                """);
        assertThrows(UnsupportedOperationException.class, () -> recordMapper.fromTyped(json, WithJsonNull.class));
        // not at the top level
        assertThrows(UnsupportedOperationException.class, () -> recordMapper.fromTyped(json, JsonObject.class));
        assertThrows(UnsupportedOperationException.class, () -> recordMapper.fromTyped(json, JsonValue.class));
    }

    // JSON names of enum constants

    enum Light {
        @JsonName("red") RED,
        @JsonName("light-green") LIGHT_GREEN,
        BLUE,
        @JsonName("special") SPECIAL {
            @Override
            public String toString() {
                return "a constant with a body";
            }
        }
    }

    enum ClashingNames { @JsonName("x") A, @JsonName("x") B }

    enum EmptyName { @JsonName("") A }

    @Test
    @DisplayName("Enum constants are read and written with their @JsonName")
    public void testEnumJsonNames() {
        record Lights(Light light, List<Light> all, Map<Light, Integer> counts) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        var lights = new Lights(Light.LIGHT_GREEN, List.of(Light.RED, Light.BLUE, Light.SPECIAL),
                new LinkedHashMap<>(Map.of(Light.RED, 1)));
        var text = recordMapper.toJsonText(lights);
        assertEquals("{\"light\":\"light-green\",\"all\":[\"red\",\"BLUE\",\"special\"],\"counts\":{\"red\":1}}", text);
        assertEquals(lights, recordMapper.fromJson(text, Lights.class));

        // the name of the constant is not accepted, if it has a @JsonName
        assertPathError(() -> recordMapper.fromTyped(parse("""
                { "light": "RED", "all": [], "counts": {} }
                """), Lights.class), "\"RED\" is not a constant of " + Light.class.getName(), "{light");
        assertPathError(() -> recordMapper.fromTyped(parse("""
                { "light": "red", "all": [], "counts": { "LIGHT_GREEN": 2 } }
                """), Lights.class), "\"LIGHT_GREEN\" is not a constant of " + Light.class.getName(),
                "{counts{LIGHT_GREEN");

        // enums without annotations are not affected
        assertEquals("{\"value\":\"GREEN\"}", recordMapper.toJsonText(new EnumRecordForNames(Color.GREEN)));
    }

    record EnumRecordForNames(Color value) {}

    @Test
    @DisplayName("Duplicate and empty JSON names of enum constants are rejected")
    public void testEnumJsonNameErrors() {
        record WithClash(ClashingNames value) {}
        record WithEmpty(EmptyName value) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        var exception = assertThrows(IllegalArgumentException.class, () -> recordMapper.fromTyped(parse("""
                { "value": "x" }
                """), WithClash.class));
        assertTrue(exception.getMessage().contains("same JSON name \"x\""), exception.getMessage());
        assertThrows(IllegalArgumentException.class, () -> recordMapper.toJson(new WithClash(ClashingNames.A)));
        exception = assertThrows(IllegalArgumentException.class, () -> recordMapper.toJson(new WithEmpty(EmptyName.A)));
        assertTrue(exception.getMessage().contains("Empty @JsonName on constant A"), exception.getMessage());
    }

    // Default values

    record Settings(@JsonDefault("10") int limit,
                    @JsonDefault("\"dark\"") String theme,
                    @JsonDefault("[\"a\"]") List<String> tags,
                    @JsonDefault("\"red\"") Light light,
                    @JsonDefault("{\"name\":\"x\",\"qty\":1}") Item item,
                    @JsonDefault("{\"x\":1}") JsonValue extra,
                    @JsonDefault("[1,2]") int[] numbers) {}

    @Test
    @DisplayName("@JsonDefault values are used for missing members")
    public void testDefaultValues() {
        record Price(@JsonDefault("\"EUR\"") Currency currency) {}
        record Box<T>(@JsonDefault("[]") List<T> items) {}

        var recordMapper = RecordMapper.builder(MethodHandles.lookup())
                .decoder(Currency.class, value -> Currency.getInstance(value.asString()))
                .build();

        var settings = recordMapper.fromJson("{}", Settings.class);
        assertEquals(10, settings.limit());
        assertEquals("dark", settings.theme());
        assertEquals(List.of("a"), settings.tags());
        assertEquals(Light.RED, settings.light());
        assertEquals(new Item("x", 1), settings.item());
        assertEquals("{\"x\":1}", settings.extra().toString());
        assertArrayEquals(new int[] { 1, 2 }, settings.numbers());
        // converted on every use, so that arrays are not shared
        assertNotSame(settings.numbers(), recordMapper.fromJson("{}", Settings.class).numbers());

        // present values take precedence, and JSON null is not a missing member
        var present = recordMapper.fromJson("""
                { "limit": 3, "theme": null, "tags": [], "light": "BLUE", "numbers": null }
                """, Settings.class);
        assertEquals(3, present.limit());
        assertNull(present.theme());
        assertEquals(List.of(), present.tags());
        assertEquals(Light.BLUE, present.light());
        assertNull(present.numbers());
        assertPathError(() -> recordMapper.fromJson("""
                { "limit": null }
                """, Settings.class), "JsonNull is not a JsonNumber", "{limit");

        // a decoder, a generic component and the strict mode
        assertEquals(new Price(Currency.getInstance("EUR")), recordMapper.fromJson("{}", Price.class));
        assertEquals(new Box<Item>(List.of()), recordMapper.fromJson("{}", new TypeRef<Box<Item>>() {}));
        var strict = RecordMapper.builder(MethodHandles.lookup()).failOnUnknownMembers(true).build();
        assertEquals(10, strict.fromJson("{}", Settings.class).limit());
    }

    @Test
    @DisplayName("Invalid @JsonDefault values are programming errors")
    public void testDefaultValueErrors() {
        record BadSyntax(@JsonDefault("{") int value) {}
        record BadType(@JsonDefault("\"x\"") int value) {}
        record OptionalDefault(@JsonDefault("1") Optional<Integer> value) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        var exception = assertThrows(IllegalArgumentException.class, () -> recordMapper.fromJson("""
                { "value": 1 }
                """, BadSyntax.class));
        assertTrue(exception.getMessage().startsWith("Invalid @JsonDefault of component value of record "
                + BadSyntax.class.getName()), exception.getMessage());

        // the type is checked on use, if the member is missing
        assertEquals(new BadType(1), recordMapper.fromJson("""
                { "value": 1 }
                """, BadType.class));
        exception = assertThrows(IllegalArgumentException.class, () -> recordMapper.fromJson("{}", BadType.class));
        assertTrue(exception.getMessage().contains("JsonString is not a JsonNumber"), exception.getMessage());
        assertTrue(exception.getCause() instanceof JsonValueException, String.valueOf(exception.getCause()));
        assertThrows(IllegalArgumentException.class, () -> recordMapper.match(parse("{}"), BadType.class));

        exception = assertThrows(IllegalArgumentException.class, () -> recordMapper.fromJson("{}",
                OptionalDefault.class));
        assertTrue(exception.getMessage().contains("optional"), exception.getMessage());
    }

    // Ignored components

    sealed interface Token permits Word {}
    record Word(String text, @JsonIgnore int length) implements Token {}

    @Test
    @DisplayName("@JsonIgnore components are neither written nor read")
    public void testJsonIgnore() {
        record Account(String name, @JsonIgnore String password, @JsonIgnore int attempts,
                       @JsonIgnore Optional<String> note, @JsonIgnore @JsonDefault("\"user\"") String role) {}
        record Shadow(@JsonName("x") String a, @JsonIgnore @JsonName("x") String b) {}

        var recordMapper = RecordMapper.of(MethodHandles.lookup());

        assertEquals("{\"name\":\"a\"}",
                recordMapper.toJsonText(new Account("a", "secret", 3, Optional.of("n"), "admin")));
        var json = """
                { "name": "a", "password": "p", "attempts": 5, "note": "n", "role": "admin" }
                """;
        assertEquals(new Account("a", null, 0, Optional.empty(), "user"), recordMapper.fromJson(json, Account.class));

        // in the strict mode, the member of an ignored component is unknown
        var strict = RecordMapper.builder(MethodHandles.lookup()).failOnUnknownMembers(true).build();
        var exception = assertThrows(JsonValueException.class, () -> strict.fromJson(json, Account.class));
        assertTrue(exception.getMessage().contains("Unknown member \"password\""), exception.getMessage());
        assertEquals(new Account("a", null, 0, Optional.empty(), "user"), strict.fromJson("""
                { "name": "a" }
                """, Account.class));

        // an ignored component has no JSON name
        assertEquals("{\"x\":\"1\"}", recordMapper.toJsonText(new Shadow("1", "2")));
        assertEquals(new Shadow("1", null), recordMapper.fromJson("{\"x\":\"1\"}", Shadow.class));

        // a record of a sealed interface
        Token token = new Word("hi", 2);
        assertEquals("{\"type\":\"Word\",\"text\":\"hi\"}", recordMapper.toJsonText(token));
        assertEquals(new Word("hi", 0), recordMapper.fromJson(recordMapper.toJsonText(token), Token.class));
    }

    // Omitted nulls

    @Test
    @DisplayName("omitNulls leaves out null components and reads missing members as null")
    public void testOmitNulls() {
        record Profile(String name, String email, List<String> tags, Map<String, String> attrs, JsonValue extra,
                       int age, Optional<String> nick) {}
        record WithDefault(@JsonDefault("\"x\"") String value) {}

        var recordMapper = RecordMapper.builder(MethodHandles.lookup()).omitNulls(true).build();
        var attrs = new LinkedHashMap<String, String>();
        attrs.put("k", null);

        // null elements, null map values and JsonNull are written
        var profile = new Profile("a", null, Arrays.asList("x", null), attrs, JsonNull.of(), 3, Optional.empty());
        var text = recordMapper.toJsonText(profile);
        assertEquals("{\"name\":\"a\",\"tags\":[\"x\",null],\"attrs\":{\"k\":null},\"extra\":null,\"age\":3}", text);
        var read = recordMapper.fromJson(text, Profile.class);
        assertNull(read.email());
        assertEquals(Arrays.asList("x", null), read.tags());
        assertEquals(attrs, read.attrs());
        assertTrue(read.extra() instanceof JsonNull, String.valueOf(read.extra()));
        assertEquals(text, recordMapper.toJsonText(read));

        // round trip with equals
        var sparse = new Profile(null, null, null, null, null, 7, Optional.of("n"));
        assertEquals("{\"age\":7,\"nick\":\"n\"}", recordMapper.toJsonText(sparse));
        assertEquals(sparse, recordMapper.fromJson(recordMapper.toJsonText(sparse), Profile.class));

        // a missing primitive member is still an error
        var exception = assertThrows(JsonValueException.class, () -> recordMapper.fromJson("{}", Profile.class));
        assertTrue(exception.getMessage().contains("\"age\" does not exist"), exception.getMessage());

        // a default value takes precedence
        assertEquals(new WithDefault("x"), recordMapper.fromJson("{}", WithDefault.class));
        assertEquals("{}", recordMapper.toJsonText(new WithDefault(null)));

        // not by default
        var defaultMapper = RecordMapper.of(MethodHandles.lookup());
        assertTrue(defaultMapper.toJsonText(sparse).contains("\"email\":null"), defaultMapper.toJsonText(sparse));
        assertThrows(JsonValueException.class, () -> defaultMapper.fromJson("""
                { "age": 7 }
                """, Profile.class));
    }
}
