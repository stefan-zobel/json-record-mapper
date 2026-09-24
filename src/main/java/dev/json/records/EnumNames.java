package dev.json.records;

import java.util.HashMap;
import java.util.Map;

// The JSON names of the constants of an enum, for reading and writing: the @JsonName annotation of
// a constant or the name of the constant
final class EnumNames {

    private EnumNames() {}

    // constants: JSON name -> constant; names: constant -> JSON name
    private record Names(Map<String, Object> constants, Map<Object, String> names) {}

    private static final ClassValue<Names> NAMES = new ClassValue<>() {
        @Override
        protected Names computeValue(Class<?> type) {
            var constants = new HashMap<String, Object>();
            var names = new HashMap<Object, String>();
            for (var constant : type.getEnumConstants()) {
                var javaName = ((Enum<?>) constant).name();
                String name;
                try {
                    var annotation = type.getField(javaName).getAnnotation(JsonName.class);
                    name = annotation != null ? annotation.value() : javaName;
                } catch (NoSuchFieldException e) {
                    throw new AssertionError(e);
                }
                if (name.isEmpty()) {
                    throw new IllegalArgumentException("Empty @JsonName on constant " + javaName + " of enum "
                            + type.getName());
                }
                var previous = constants.put(name, constant);
                if (previous != null) {
                    throw new IllegalArgumentException("Constants " + ((Enum<?>) previous).name() + " and "
                            + javaName + " of enum " + type.getName() + " have the same JSON name \"" + name + "\"");
                }
                names.put(constant, name);
            }
            return new Names(Map.copyOf(constants), Map.copyOf(names));
        }
    };

    // the constants of an enum type by JSON name
    static Map<String, Object> constants(Class<?> type) {
        return NAMES.get(type).constants();
    }

    // the JSON name of an enum constant; the enum type of a constant with a body is its declaring class
    static String name(Enum<?> constant) {
        return NAMES.get(constant.getDeclaringClass()).names().get(constant);
    }
}
