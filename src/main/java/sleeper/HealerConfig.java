package sleeper;

/**
 * Configuration for the LocatorHealer.
 *
 * @param geminiApiKey Your API key for the Gemini service.
 * @param modelName The specific model to use (e.g., "gemini-1.5-flash-latest").
 * @param verificationTimeoutSecs The time in seconds to wait when verifying a new locator.
 */
public record HealerConfig(
    String geminiApiKey,
    String modelName,
    int verificationTimeoutSecs
) {
  /**
   * A convenience constructor with default values for the model and timeout.
   * @param geminiApiKey Your API key for the Gemini service.
   */
  public HealerConfig(String geminiApiKey) {
    this(geminiApiKey, "gemini-2.5-flash", 5);
  }
}