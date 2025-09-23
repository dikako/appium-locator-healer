package sleeper;

/**
 * Represents the structured response from the self-healing AI model.
 *
 * <p>Using a record provides an immutable, transparent carrier for this data,
 * with auto-generated constructors, getters, equals(), hashCode(), and toString() methods.
 *
 * @param failedElement       The original locator that failed.
 * @param newValidElementType The type of the new valid locator (e.g., "id", "xpath").
 * @param newValidElement     The value of the new valid locator.
 * @param reason              The AI's explanation for the chosen locator.
 * @param suggestion          The AI's recommendation for improving testability.
 */
public record HealedLocator(
    String failedElement,
    String newValidElementType,
    String newValidElement,
    String reason,
    String suggestion
) {}
