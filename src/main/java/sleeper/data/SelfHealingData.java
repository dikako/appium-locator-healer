package sleeper.data;

/**
 * The SelfHealingData class serves as a container for storing and retrieving
 * information related to the original and healed locators and their associated errors.
 * This class facilitates the process of tracking locator changes, particularly
 * in scenarios involving automated healing of locators.
 */
public class SelfHealingData {

  private static String originalLocator = "";
  private static String originalError = "";
  private static String healedLocator = "";
  private static String healedError = "";

  /**
   * Retrieves the value of the original locator.
   *
   * @return the original locator as a String
   */
  public static String getOriginalLocator() {
    return originalLocator;
  }

  /**
   * Sets the original locator value within the SelfHealingData context.
   * This method is used to define the initial locator, typically during
   * the starting phase of an operation, to facilitate tracking and debugging
   * in locator self-healing processes.
   *
   * @param originalLocator the original locator as a string, representing
   *                        the initial method to identify the element
   *                        before any healing attempts.
   */
  public static void setOriginalLocator(String originalLocator) {
    SelfHealingData.originalLocator = originalLocator;
  }

  /**
   * Retrieves the healed locator value within the SelfHealingData context.
   * The healed locator represents the potentially modified or updated
   * mechanism for identifying an element after an automated healing
   * process has been performed.
   *
   * @return the healed locator as a String
   */
  public static String getHealedLocator() {
    return healedLocator;
  }

  /**
   * Sets the healed locator value within the SelfHealingData context.
   * The healed locator represents the identifier used to locate an element
   * after it has undergone an automated healing process. This method facilitates
   * storing the updated locator for further use or debugging purposes.
   *
   * @param healedLocator the healed locator as a string, representing the
   *                      updated mechanism for identifying an element
   */
  public static void setHealedLocator(String healedLocator) {
    SelfHealingData.healedLocator = healedLocator;
  }

  /**
   * Retrieves the original error value stored within the SelfHealingData context.
   * The original error represents the error message that occurred before any
   * self-healing process was applied.
   *
   * @return the original error as a String
   */
  public static String getOriginalError() {
    return originalError;
  }

  /**
   * Sets the original error value within the SelfHealingData context.
   * This method is used to store the initial error message for tracking and
   * debugging purposes, particularly in scenarios involving the self-healing
   * of locators.
   *
   * @param originalError the original error as a string, representing the
   *                      error message encountered prior to any self-healing
   *                      attempts
   */
  public static void setOriginalError(String originalError) {
    SelfHealingData.originalError = originalError;
  }

  /**
   * Retrieves the healed error value stored within the SelfHealingData context.
   * The healed error represents the error message, typically after a self-healing
   * process has been applied, to identify any issues encountered or resolved.
   *
   * @return the healed error as a String
   */
  public static String getHealedError() {
    return healedError;
  }

  /**
   * Sets the healed error value within the SelfHealingData context.
   * The healed error represents the error message, typically after a self-healing
   * process has been applied, to identify any issues encountered or resolved.
   *
   * @param healedError the healed error as a string, representing the error message
   *                    encountered or updated after any self-healing attempts
   */
  public static void setHealedError(String healedError) {
    SelfHealingData.healedError = healedError;
  }
}
