package dev.json.records;

/**
 * The strategy that derives the name of the JSON object member from the name of a record
 * component, configured with {@link RecordMapper.Builder#naming(JsonNaming)}. A
 * {@link JsonName} annotation on a component takes precedence over the strategy.
 * <p>
 * {@link #SNAKE_CASE} and {@link #KEBAB_CASE} split a camel case name into words, at the
 * transition from a lower case letter or a digit to an upper case letter and before the
 * last letter of a sequence of upper case letters that is followed by a lower case letter,
 * and join the words in lower case:
 * <table class="striped">
 *   <caption>Examples</caption>
 *   <thead>
 *     <tr><th scope="col">Component</th><th scope="col">SNAKE_CASE</th><th scope="col">KEBAB_CASE</th></tr>
 *   </thead>
 *   <tbody>
 *     <tr><th scope="row">{@code firstName}</th><td>{@code first_name}</td><td>{@code first-name}</td></tr>
 *     <tr><th scope="row">{@code userID}</th><td>{@code user_id}</td><td>{@code user-id}</td></tr>
 *     <tr><th scope="row">{@code URLValue}</th><td>{@code url_value}</td><td>{@code url-value}</td></tr>
 *     <tr><th scope="row">{@code address2Line}</th><td>{@code address2_line}</td><td>{@code address2-line}</td></tr>
 *   </tbody>
 * </table>
 */
public enum JsonNaming {

    /**
     * The member name is the component name, the default.
     */
    IDENTITY {
        @Override
        public String jsonName(String componentName) {
            return componentName;
        }
    },

    /**
     * The member name is the component name in snake case, e.g. {@code first_name}.
     */
    SNAKE_CASE {
        @Override
        public String jsonName(String componentName) {
            return separated(componentName, '_');
        }
    },

    /**
     * The member name is the component name in kebab case, e.g. {@code first-name}.
     */
    KEBAB_CASE {
        @Override
        public String jsonName(String componentName) {
            return separated(componentName, '-');
        }
    };

    /**
     * {@return the name of the JSON object member for the given record component name}
     *
     * @param componentName the name of a record component
     */
    public abstract String jsonName(String componentName);

    private static String separated(String name, char separator) {
        var result = new StringBuilder(name.length() + 4);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                boolean afterLowerCaseOrDigit = i > 0
                        && (Character.isLowerCase(name.charAt(i - 1)) || Character.isDigit(name.charAt(i - 1)));
                boolean endOfUpperCaseSequence = i > 0 && i + 1 < name.length()
                        && Character.isUpperCase(name.charAt(i - 1)) && Character.isLowerCase(name.charAt(i + 1));
                if (afterLowerCaseOrDigit || endOfUpperCaseSequence) {
                    result.append(separator);
                }
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
