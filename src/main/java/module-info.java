/**
 * Maps JSON objects of {@code java21.util.json} to records and records back to JSON,
 * see {@link dev.json.records.RecordMapper}.
 * <p>
 * The JSON API is required transitively, as it is part of the API of the mapper. The records
 * of a module that uses the mapper need not be opened: they are accessed with the
 * {@link java.lang.invoke.MethodHandles.Lookup} that is passed to the mapper.
 */
module dev.json.records {
    requires transitive java21.util.json;

    exports dev.json.records;
}
