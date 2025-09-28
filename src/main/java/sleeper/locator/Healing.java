package sleeper.locator;

import com.google.genai.types.GenerateContentResponse;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import sleeper.prompt.AndroidPrompts;
import sleeper.prompt.IOSPrompts;
import sleeper.prompt.WebPrompts;
import sleeper.utils.GeminiUtils;
import sleeper.utils.LocatorHealingResultsLogger;
import sleeper.utils.LocatorUtils;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * The Healing class provides functionality to heal invalid locators across different platforms
 * (Android, iOS, and Web) by using AI-driven models. It aims to assist in resolving issues
 * related to failing element locators by generating valid alternatives based on the provided
 * error details and context.
 */
public class Healing {
  private static final Logger logger = Logger.getLogger(Healing.class.getName());
  private static final LocatorHealingResultsLogger resultsLogger = new LocatorHealingResultsLogger();

  /**
   * A thread-safe cache that stores resolved locators for UI elements. The key is a string
   * representing the unique identifier of the element or the context, and the value is a
   * {@link By} object representing the corresponding locator.
   * <p>
   * This cache is utilized to improve performance by avoiding repeated computation or retrieval
   * of locators, particularly in scenarios where dynamic locator healing is performed. It is
   * maintained across different healing operations to facilitate reuse of previously resolved
   * locators.
   */
  private static final Map<String, By> locatorCache = new ConcurrentHashMap<>();

  /**
   * Represents the supported platforms for element healing operations.
   * This enumeration is used to specify the target platform context
   * (e.g., Android, iOS, or Web) when invoking healing methods.
   */
  public enum Platform {
    /**
     * Represents the Android platform in the context of element healing operations.
     * This constant is used to specify that the target platform for healing
     * invalid locators or elements is Android.
     */
    ANDROID,
    /**
     * Represents the iOS platform in the context of element healing operations.
     * This constant is used to specify that the target platform for healing
     * invalid locators or elements is iOS.
     */
    IOS,
    /**
     * Represents the Web platform in the context of element healing operations.
     * This constant is used to specify that the target platform for healing
     * invalid locators or elements is Web.
     */
    WEB
  }


  /**
   * Attempts to resolve and heal a failing element locator using AI-driven predictions
   * based on the provided context, error details, and platform.
   *
   * @param platform            the platform (ANDROID, IOS, or WEB) used for healing the element
   * @param geminiModel         the identifier of the Gemini AI model to use for healing
   * @param driver              the WebDriver instance providing access to the current page context
   * @param errorElementLocator the failing element locator that needs to be healed
   * @param errorDetails        additional error details (e.g., error messages, stack traces) relevant to the failure
   * @param uiLabel             an optional label describing the expected UI element for additional context
   * @param timeout             the timeout duration, in milliseconds, for the AI model response
   * @return the resolved and healed location of the failing element as a {@code By} object
   */
  public static By healLocator(
    Platform platform,
    String geminiModel,
    WebDriver driver,
    String errorElementLocator,
    String errorDetails,
    String uiLabel,
    int timeout) {

    String cacheKey = String.join("|",
      platform.toString(),
      geminiModel,
      errorElementLocator,
      errorDetails,
      uiLabel
    );

    if (locatorCache.containsKey(cacheKey)) {
      logger.info(String.format(
        "[%s Element Healing] Cache HIT. Returning previously resolved locator for: %s",
        platform, errorElementLocator
      ));
      return locatorCache.get(cacheKey);
    }

    logger.info(String.format("[%s Element Healing] Starting iOS element healing with model: %s", platform, geminiModel));

    GenerateContentResponse response = GeminiUtils.getResponse(
      geminiModel,
      getPrompt(platform, driver, errorElementLocator, errorDetails, uiLabel),
      timeout
    );

    String responseText = response.text();

    try {
      assert responseText != null;
    } catch (AssertionError e) {
      logger.warning("Response text is null, " + e.getMessage());
      responseText = "";
    }

    responseText = responseText
      .replace("```json", "")
      .replace("```", "")
      .trim();

    JsonElement responseTextToJsonElement = JsonParser.parseString(responseText);
    JsonObject responseJson = responseTextToJsonElement.getAsJsonObject();
    String locatorType = responseJson.get("newValidElementType").getAsString();
    String locator = responseJson.get("newValidElement").getAsString();
    // Resolved element locator
    By resolvedLocator = LocatorUtils.getByFromString(locatorType, locator);

    logger.info("""
      [iOS Element Healing]
      Element Before healing    -> %s
      Prompt Response           -> %s
      Resolved Element Locator  -> %s
      """.formatted(errorElementLocator, responseText, resolvedLocator.toString()));

    resultsLogger.saveHealedElementLocator(
      Map.of(
        "executedAt", Instant.now().toString(),
        "errorElementLocator", errorElementLocator,
        "resolvedElementLocator", resolvedLocator.toString(),
        "detailAIResponse", responseTextToJsonElement.toString()
      )
    );

    locatorCache.put(cacheKey, resolvedLocator);
    logger.info(String.format("[%s Element Healing] New locator has been cached.", platform));

    return resolvedLocator;
  }

  /**
   * Generates a prompt based on the provided platform, driving context, and error details.
   * The prompt is designed to assist with AI-driven healing of failing element locators
   * by providing a structured set of inputs relevant to the specific platform.
   *
   * @param platform            the platform (ANDROID, IOS, or WEB) determining the prompt logic
   * @param driver              the WebDriver instance used to extract page source
   * @param errorElementLocator the failing locator causing the error
   * @param errorDetails        additional details about the error (e.g., error message from the WebDriver)
   * @param uiLabel             an optional value representing the expected UI element or label
   * @return a structured prompt string specific to the given platform, containing all relevant information
   */
  private static String getPrompt(
    Platform platform,
    WebDriver driver,
    String errorElementLocator,
    String errorDetails,
    String uiLabel) {

    return switch (platform) {
      case ANDROID -> AndroidPrompts.prompt(
        driver.getPageSource(),
        errorElementLocator,
        errorDetails, uiLabel
      );
      case IOS -> IOSPrompts.prompt(
        driver.getPageSource(),
        errorElementLocator,
        errorDetails, uiLabel
      );
      case WEB -> WebPrompts.prompt(
        driver.getPageSource(),
        errorElementLocator,
        errorDetails, uiLabel
      );
    };
  }
}
