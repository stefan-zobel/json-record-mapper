package dev.json.records;

import java21.util.json.JsonValue;

/**
 * Converts a value of a type that the {@link RecordMapper} does not write itself, or writes
 * differently, to a JSON value; the counterpart of a {@link JsonDecoder}. An encoder is
 * registered for a class with {@link RecordMapper.Builder#encoder(Class, JsonEncoder)}, for
 * example:
 * <pre>{@code
 * static final RecordMapper MAPPER = RecordMapper.builder(MethodHandles.lookup())
 *         .decoder(Currency.class, value -> Currency.getInstance(value.asString()))
 *         .encoder(Currency.class, currency -> JsonString.of(currency.getCurrencyCode()))
 *         .build();
 * }</pre>
 * The encoder is used for every value whose runtime class is exactly that class, wherever
 * it occurs: record components, elements of collections, values of maps, optionals, and at
 * the top level.
 * <p>
 * An encoder is never called with {@code null}, which the mapper writes as JSON null, and
 * must not return {@code null}. Exceptions thrown by the encoder are passed on unchanged.
 *
 * @param <T> the type of the values that are converted
 */
@FunctionalInterface
public interface JsonEncoder<T> {

    /**
     * Converts the given value to a JSON value.
     *
     * @param value The value, never {@code null}.
     * @return The JSON value, not {@code null}.
     */
    JsonValue toJson(T value);
}
