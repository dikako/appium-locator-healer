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
import java.util.logging.Logger;

/**
 * The Healing class provides functionality to heal invalid locators across different platforms
 * (Android, iOS, and Web) by using AI-driven models. It aims to assist in resolving issues
 * related to failing element locators by generating valid alternatives based on the provided
 * error details and context.
 */
public class Healing {
  private static final Logger logger = Logger.getLogger(IOSHealing.class.getName());
  private static final LocatorHealingResultsLogger resultsLogger = new LocatorHealingResultsLogger();

  /**
   * Represents the supported platforms for element healing operations.
   * This enumeration is used to specify the target platform context
   * (e.g., Android, iOS, or Web) when invoking healing methods.
   */
  public enum Platform {
    ANDROID,
    IOS,
    WEB
  }


  /**
   * Attempts to heal a failing element locator by utilizing an AI-driven model to generate a new
   * valid locator based on the provided error details and platform-specific context. The method
   * returns a resolved {@link By} object representing the new locator.
   *
   * @param platform             the platform for which the healing is being performed (ANDROID, IOS, or WEB)
   * @param geminiModel          the name or identifier of the AI model used for generating the new locator
   * @param iosDriver            the WebDriver instance used to interact with iOS applications
   * @param errorElementLocator  the locator of the element that failed and needs to be healed
   * @param errorDetails         details of the error that occurred (e.g., error message or context)
   * @param uiLabel              an optional UI label or element description used for additional context
   * @param timeout              the maximum amount of time (in seconds) to wait for the AI model's response
   * @return a {@link By} object representing the new valid locator for the element
   */
  public static By healLocator(
    Platform platform,
    String geminiModel,
    WebDriver iosDriver,
    String errorElementLocator,
    String errorDetails,
    String uiLabel,
    int timeout) {
    logger.info("[" + platform.toString() + " Element Healing] Starting iOS element healing with model: " + geminiModel);

    GenerateContentResponse response =
      GeminiUtils.getResponse(
        geminiModel,
        getPrompt(platform, iosDriver, errorElementLocator, errorDetails, uiLabel),
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
    resultsLogger.saveHealedElementLocator(
      Map.of(
        "executedAt", Instant.now(),
        "errorElementLocator", errorElementLocator,
        "resolvedElementLocator", resolvedLocator,
        "detailAIResponse", responseTextToJsonElement
      )
    );

    logger.info("""
      [iOS Element Healing]
      Element Before healing    -> %s
      Prompt Response           -> %s
      Resolved Element Locator  -> %s
      """.formatted(errorElementLocator, responseText, resolvedLocator.toString()));

    return resolvedLocator;
  }

  /**
   * Generates a prompt based on the provided platform, driving context, and error details.
   * The prompt is designed to assist with AI-driven healing of failing element locators
   * by providing a structured set of inputs relevant to the specific platform.
   *
   * @param platform             the platform (ANDROID, IOS, or WEB) determining the prompt logic
   * @param driver               the WebDriver instance used to extract page source
   * @param errorElementLocator  the failing locator causing the error
   * @param errorDetails         additional details about the error (e.g., error message from the WebDriver)
   * @param uiLabel              an optional value representing the expected UI element or label
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
