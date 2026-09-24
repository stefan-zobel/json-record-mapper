package dev.json.records;

import java21.util.json.JsonValue;

/**
 * Converts a JSON value to an instance of a type that the {@link RecordMapper} does not
 * support itself, or converts it differently than the mapper does. A decoder is registered
 * for a class with {@link RecordMapper.Builder#decoder(Class, JsonDecoder)}, for example:
 * <pre>{@code
 * record Money(BigDecimal amount, Currency currency) {
 *     static Money parse(String text) { ... }   // e.g. "12.50 EUR"
 * }
 *
 * static final RecordMapper MAPPER = RecordMapper.builder(MethodHandles.lookup())
 *         .decoder(Money.class, value -> Money.parse(value.asString()))
 *         .build();
 * }</pre>
 * The decoder is used wherever a value of exactly that class is mapped: for record
 * components, elements of collections and arrays, values of maps, optionals, type arguments
 * of generic records, and at the top level.
 * <p>
 * A decoder is never called with JSON null: the mapper maps JSON null to {@code null}, or to
 * an empty optional, before the decoder is involved. If the value cannot be converted, the
 * decoder should throw a {@link java21.util.json.JsonValueException}, which the conversion
 * methods of {@link JsonValue} such as {@code asString()} already do for a value of the
 * wrong JSON type. Any other runtime exception is wrapped in a {@code JsonValueException}
 * whose cause is the original exception.
 *
 * @param <T> the type the JSON value is converted to
 */
@FunctionalInterface
public interface JsonDecoder<T> {

    /**
     * Converts the given JSON value.
     *
     * @param value The JSON value, never JSON null.
     * @return The converted value.
     * @throws java21.util.json.JsonValueException if the value cannot be converted.
     */
    T fromJson(JsonValue value);
}
