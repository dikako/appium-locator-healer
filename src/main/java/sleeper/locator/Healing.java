package sleeper.locator;

import com.google.genai.types.GenerateContentResponse;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.LoggerFactory;
import sleeper.prompt.AndroidPrompts;
import sleeper.prompt.IOSPrompts;
import sleeper.prompt.WebPrompts;
import sleeper.utils.GeminiUtils;
import sleeper.utils.LocatorHealingResultsLogger;
import sleeper.utils.LocatorUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
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
  private static final org.slf4j.Logger log = LoggerFactory.getLogger(Healing.class);

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

  /**
   * Determines the timeout value to be used. If a timeout value is provided in the input, it
   * returns the first duration; otherwise, a default timeout of 5 seconds is used.
   *
   * @param timeout an optional varargs parameter representing one or more timeout durations.
   *                If no value is provided, a default duration is returned.
   * @return the first timeout value if provided; otherwise, a default duration of 5 seconds.
   */
  private static Duration getTimeout(Duration... timeout) {
    return timeout.length > 0 ? timeout[0] : Duration.ofSeconds(5);
  }

  /**
   * Waits until the specified condition is met or the timeout duration expires.
   *
   * @param driver     the WebDriver instance used to execute the wait
   * @param conditions the expected condition to wait for, which resolves to a WebElement
   * @param timeout    the maximum duration to wait before timing out
   * @return the WebElement that satisfies the specified condition
   */
  private static WebElement waitUntil(WebDriver driver, ExpectedCondition<WebElement> conditions, Duration timeout) {
    WebDriverWait wait = new WebDriverWait(driver, timeout);
    return wait.until(conditions);
  }

  /**
   * Waits until the specified element is clickable or the timeout duration expires.
   *
   * @param driver         the WebDriver instance used to execute the wait
   * @param elementLocator the locator for the element to wait for
   * @param timeout        the maximum duration to wait before timing out
   * @return the WebElement that becomes clickable
   */
  private static WebElement waitUntilClickable(WebDriver driver, By elementLocator, Duration timeout) {
    return waitUntil(driver, ExpectedConditions.elementToBeClickable(elementLocator), timeout);
  }

  /**
   * Waits until the specified element is visible or the timeout duration expires.
   *
   * @param driver         the WebDriver instance used to execute the wait
   * @param elementLocator the locator for the element to wait for
   * @param timeout        the maximum duration to wait before timing out
   * @return the visible WebElement targeted by the given locator
   */
  private static WebElement waitUntilVisible(WebDriver driver, By elementLocator, Duration timeout) {
    return waitUntil(driver, ExpectedConditions.visibilityOfElementLocated(elementLocator), timeout);
  }

  /**
   * Waits until the specified element is present in the DOM or the timeout duration expires.
   *
   * @param driver         the WebDriver instance used to execute the wait
   * @param elementLocator the locator for the element to wait for
   * @param timeout        the maximum duration to wait before timing out
   * @return the WebElement that becomes present in the DOM as identified by the locator
   */
  private static WebElement waitUntilPresent(WebDriver driver, By elementLocator, Duration timeout) {
    return waitUntil(driver, ExpectedConditions.presenceOfElementLocated(elementLocator), timeout);
  }

  /**
   * Waits until all elements matching the specified locator are present in the DOM or the given timeout period expires.
   *
   * @param driver         the WebDriver instance used to execute the wait
   * @param elementLocator the locator for the elements to wait for
   * @param timeout        the maximum duration to wait before timing out
   * @return a list of WebElements that match the specified locator and are present in the DOM
   */
  public static List<WebElement> waitAllUntilPresent(WebDriver driver, By elementLocator, Duration timeout) {
    WebDriverWait wait = new WebDriverWait(driver, timeout);
    return wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(elementLocator));
  }

  /**
   * Waits until the specified element is invisible on the page or the timeout duration expires.
   *
   * @param driver         the WebDriver instance used to execute the wait
   * @param elementLocator the locator for the element to wait for invisibility
   * @param timeout        the maximum duration to wait before timing out
   * @return true if the element becomes invisible within the timeout period, false otherwise
   */
  public static Boolean waitUntilInvisible(WebDriver driver, By elementLocator, Duration timeout) {
    WebDriverWait wait = new WebDriverWait(driver, timeout);
    return wait.until(ExpectedConditions.invisibilityOfElementLocated(elementLocator));
  }

  /**
   * Checks whether an element is present on the web page within a specified timeout duration.
   *
   * @param driver         the WebDriver instance used to execute browser commands
   * @param elementLocator the locator used to find the desired element
   * @param timeout        the maximum duration to wait for the element to be present
   */
  private static void isPresent(WebDriver driver, By elementLocator, Duration timeout) {
    WebDriverWait wait = new WebDriverWait(driver, timeout);
    wait.ignoring(NoSuchElementException.class)
      .ignoring(StaleElementReferenceException.class)
      .ignoring(NoSuchFrameException.class);

    wait.until(ExpectedConditions.presenceOfElementLocated(elementLocator));
  }

  /**
   * Clicks on a web element specified by the given locator after waiting for it to become clickable
   * within the specified timeout duration.
   *
   * @param driver         the WebDriver instance used to interact with the browser
   * @param elementLocator the locator used to identify the web element to be clicked
   * @param timeout        the maximum duration to wait for the element to become clickable
   */
  private static void clickOn(WebDriver driver, By elementLocator, Duration timeout) {
    waitUntilClickable(driver, elementLocator, timeout).click();
  }

  /**
   * Types the specified text into a web element identified by the given locator after waiting for it to become clickable.
   *
   * @param driver         the WebDriver instance used to interact with the web page
   * @param elementLocator the locator used to identify the target web element
   * @param inputText      the text to be typed into the target web element
   * @param timeout        the maximum duration to wait for the element to become clickable
   */
  private static void typeOn(WebDriver driver, By elementLocator, String inputText, Duration timeout) {
    waitUntilClickable(driver, elementLocator, timeout).sendKeys(inputText);
  }

  /**
   * Attempts to perform the provided action and, in case of a failure, applies a healing mechanism
   * to resolve the issue and retries the action. Healing is performed using AI-driven predictions
   * based on the platform, context, and error details.
   *
   * @param platform              the platform (ANDROID, IOS, or WEB) used for locating or healing the element
   * @param geminiModel           the identifier of the Gemini AI model to use for healing
   * @param driver                the WebDriver instance providing access to the current page context
   * @param elementLocator        the initial locator targeting the element to perform the action on
   * @param uiLabel               an optional descriptive label for the target UI element
   * @param timeoutGeminiResponse the timeout duration (in milliseconds) for the AI model's healing response
   * @param action                the action to be executed on the element, wrapped in a {@code Supplier} that returns a result
   * @param <T>                   the type of the result that the action produces
   * @return the result of the performed action, either from the initial attempt or after healing
   * @throws RuntimeException if the action fails both initially and after attempting healing
   */
  public static <T> T performWithHealing(
    Platform platform,
    String geminiModel,
    WebDriver driver,
    By elementLocator,
    String uiLabel,
    int timeoutGeminiResponse,
    Supplier<T> action
  ) {
    try {
      // First attempt
      return action.get();
    } catch (Exception e) {
      String errorMessage = e.getMessage();
      logger.warning(String.format("Action failed on element: %s, Error: %s", elementLocator, errorMessage));

      // Heal locator
      healLocator(
        platform,
        geminiModel,
        driver,
        elementLocator.toString(),
        errorMessage,
        uiLabel,
        timeoutGeminiResponse
      );

      try {
        // Retry after healing
        T result = action.get();
        logger.info(String.format("Action succeeded after healing: %s", elementLocator));
        return result;
      } catch (Exception retryEx) {
        logger.warning(String.format("Action still failed after healing: %s, Error: %s",
          elementLocator, retryEx.getMessage()));
        throw new RuntimeException(retryEx);
      }
    }
  }

  /**
   * Waits until a web element specified by the given locator is clickable within the specified timeout.
   * Provides an option for healing interactions based on the specified platform and Gemini model.
   *
   * @param platform              the platform on which the test is being performed
   * @param geminiModel           the Gemini model used for healing interactions
   * @param driver                the WebDriver instance used to interact with the browser
   * @param elementLocator        the locator used to identify the web element
   * @param uiLabel               the label associated with the UI element for logging or reporting purposes
   * @param timeout               the maximum duration to wait for the element to become clickable
   * @param timeoutGeminiResponse the timeout duration for Gemini response used in the healing process
   * @return the WebElement after it becomes clickable
   */
  private WebElement waitUntilClickable(
    Platform platform,
    String geminiModel,
    WebDriver driver,
    By elementLocator,
    String uiLabel,
    Duration timeout,
    int timeoutGeminiResponse
  ) {
    return performWithHealing(
      platform,
      geminiModel,
      driver,
      elementLocator,
      uiLabel,
      timeoutGeminiResponse,
      () -> waitUntilClickable(driver, elementLocator, timeout)
    );
  }

  private WebElement waitUntilVisible(
    Platform platform,
    String geminiModel,
    WebDriver driver,
    By elementLocator,
    String uiLabel,
    Duration timeout,
    int timeoutGeminiResponse
  ) {
    return performWithHealing(
      platform,
      geminiModel,
      driver,
      elementLocator,
      uiLabel,
      timeoutGeminiResponse,
      () -> waitUntilVisible(driver, elementLocator, timeout)
    );
  }

  /**
   * Waits until the specified web element is present on the page within the given timeout duration.
   * This method also incorporates a healing mechanism during the wait process.
   *
   * @param platform              the platform on which the operation is being performed
   * @param geminiModel           the gemini model identifier used for the operation
   * @param driver                the WebDriver instance used to interact with the web page
   * @param elementLocator        the locator used to find the desired web element
   * @param uiLabel               a human-readable label for the UI element being interacted with
   * @param timeout               the maximum duration to wait for the element to be present on the page
   * @param timeoutGeminiResponse the timeout duration for any required gemini response during operation
   * @return the WebElement that is present on the page, if found within the timeout
   */
  private WebElement waitUntilPresent(
    Platform platform,
    String geminiModel,
    WebDriver driver,
    By elementLocator,
    String uiLabel,
    Duration timeout,
    int timeoutGeminiResponse
  ) {
    return performWithHealing(
      platform,
      geminiModel,
      driver,
      elementLocator,
      uiLabel,
      timeoutGeminiResponse,
      () -> waitUntilPresent(driver, elementLocator, timeout)
    );
  }

  private WebElement waitAllUntilPresent(
    Platform platform,
    String geminiModel,
    WebDriver driver,
    By elementLocator,
    String uiLabel,
    Duration timeout,
    int timeoutGeminiResponse
  ) {
    return (WebElement) performWithHealing(
      platform,
      geminiModel,
      driver,
      elementLocator,
      uiLabel,
      timeoutGeminiResponse,
      () -> waitAllUntilPresent(driver, elementLocator, timeout)
    );
  }

  /**
   * Waits until the specified element becomes invisible within the defined timeout duration.
   * The process involves invoking an action with healing capabilities to handle dynamic conditions.
   *
   * @param platform              the platform on which the operation is being performed
   * @param geminiModel           the model used for healing operations
   * @param driver                the WebDriver instance used to interact with the web elements
   * @param elementLocator        the locator of the element to be monitored for invisibility
   * @param uiLabel               the label of the UI element for logging or debugging purposes
   * @param timeout               the maximum duration to wait until the element is invisible
   * @param timeoutGeminiResponse the timeout for Gemini response during the healing process
   * @return true if the element becomes invisible within the timeout, false otherwise
   */
  private Boolean waitUntilInvisible(
    Platform platform,
    String geminiModel,
    WebDriver driver,
    By elementLocator,
    String uiLabel,
    Duration timeout,
    int timeoutGeminiResponse
  ) {
    return performWithHealing(
      platform,
      geminiModel,
      driver,
      elementLocator,
      uiLabel,
      timeoutGeminiResponse,
      () -> waitUntilInvisible(driver, elementLocator, timeout)
    );
  }

  /**
   * Checks if a web element is present and performs healing logic if the element is not found.
   *
   * @param platform              the platform for which the check is being performed
   * @param geminiModel           the Gemini model associated with the element
   * @param driver                the WebDriver instance to interact with the web page
   * @param elementLocator        the locator used to find the element
   * @param uiLabel               a label used for logging or identifying the element in the UI
   * @param timeoutGeminiResponse the timeout in seconds for any Gemini response during healing
   * @param timeoutFindElement    optional timeout values for locating the element
   * @return true if the element is present, otherwise attempts healing and returns the result
   */
  public static boolean isPresent(
    Platform platform,
    String geminiModel,
    WebDriver driver,
    By elementLocator,
    String uiLabel,
    int timeoutGeminiResponse,
    Duration... timeoutFindElement
  ) {
    Duration timeout = getTimeout(timeoutFindElement);

    try {
      isPresent(driver, elementLocator, timeout);
      return true;
    } catch (Exception e) {
      String errorMessage = e.getMessage();
      logger.warning(String.format("Element not found: %s, Error: %s", elementLocator, errorMessage));

      // Try to heal the element locator
      return performWithHealing(
        platform,
        geminiModel,
        driver,
        elementLocator,
        uiLabel,
        timeoutGeminiResponse,
        () -> {
          isPresent(driver, elementLocator, timeout);
          return true;
        }
      );
    }
  }

  /**
   * Clicks on a specified element on a web page using the provided WebDriver and locator.
   * If the element is not found, attempts to heal the element through additional logic and retries.
   *
   * @param platform              the platform on which the operation is being performed
   * @param geminiModel           the Gemini model specifying contextual information
   * @param driver                the WebDriver instance used to interact with the web page
   * @param elementLocator        the locator used to identify the element to click on
   * @param uiLabel               a user-friendly label identifying the UI element being interacted with
   * @param timeoutGeminiResponse the timeout duration for Gemini healing-related activities in seconds
   * @param timeoutFindElement    an optional duration parameter(s) specifying timeout for finding the element
   */
  public static void clickOn(
    Platform platform,
    String geminiModel,
    WebDriver driver,
    By elementLocator,
    String uiLabel,
    int timeoutGeminiResponse,
    Duration... timeoutFindElement
  ) {
    Duration timeout = getTimeout(timeoutFindElement);

    try {
      clickOn(driver, elementLocator, timeout);
    } catch (Exception e) {
      String errorMessage = e.getMessage();
      logger.warning(String.format("Element not found: %s, Error: %s", elementLocator, errorMessage));

      // Try to heal the element locator
      performWithHealing(
        platform,
        geminiModel,
        driver,
        elementLocator,
        uiLabel,
        timeoutGeminiResponse,
        () -> {
          clickOn(driver, elementLocator, timeout);
          return null;
        }
      );
    }
  }

  /**
   * Attempts to click on a specified web element. If the element is not found or an exception occurs,
   * retries the operation by attempting to heal the locator and interact with the element.
   *
   * @param platform              The platform type, typically indicating the application platform (e.g., desktop, mobile).
   * @param geminiModel           The Gemini model name used for identifying or interacting with the application component.
   * @param driver                The WebDriver instance used to interact with the browser.
   * @param elementLocator        The locator used to identify the web element to be clicked.
   * @param uiLabel               A descriptive label for the UI element, primarily used for logging or debugging.
   * @param index                 The index of the element in the list if multiple elements match the locator.
   * @param timeoutGeminiResponse The timeout (in seconds or milliseconds) for receiving responses from the Gemini model.
   * @param timeoutFindElement    Optional timeout(s) used to wait for the web element to become present or interactable.
   */
  public static void clickOn(
    Platform platform,
    String geminiModel,
    WebDriver driver,
    By elementLocator,
    String uiLabel,
    int index,
    int timeoutGeminiResponse,
    Duration... timeoutFindElement
  ) {
    Duration timeout = getTimeout(timeoutFindElement);

    try {
      clickOn(driver, elementLocator, timeout);
    } catch (Exception e) {
      String errorMessage = e.getMessage();
      logger.warning(String.format("Element not found: %s, Error: %s", elementLocator, errorMessage));

      // Try to heal the element locator
      performWithHealing(
        platform,
        geminiModel,
        driver,
        elementLocator,
        uiLabel,
        timeoutGeminiResponse,
        () -> {
          WebElement element = waitAllUntilPresent(driver, elementLocator, timeout).get(index);
          element.click();
          return null;
        }
      );
    }
  }

  /**
   * Performs typing on a web element specified by the provided locator, with healing functionality to retry the action if the element is not found initially.
   *
   * @param platform              the platform on which the action is being performed.
   * @param geminiModel           the Gemini model identifier used for healing and validation.
   * @param driver                the WebDriver instance used to interact with the web application.
   * @param elementLocator        the locator used to find the web element to type into.
   * @param uiLabel               the label or identifier for the UI element, used for logging or debugging purposes.
   * @param timeoutGeminiResponse the timeout for responses from Gemini healing operations, in seconds.
   * @param index                 the index of the element in the list of elements found by the locator, used when multiple elements are matched.
   * @param inputText             the text to be typed into the web element.
   * @param timeoutFindElement    an optional vararg of timeout duration(s) used to override the default timeout for finding the element.
   */
  public static void typeOn(
    Platform platform,
    String geminiModel,
    WebDriver driver,
    By elementLocator,
    String uiLabel,
    int timeoutGeminiResponse,
    int index,
    String inputText,
    Duration... timeoutFindElement) {
    Duration timeout = getTimeout(timeoutFindElement);

    // Try to heal the element locator
    performWithHealing(
      platform,
      geminiModel,
      driver,
      elementLocator,
      uiLabel,
      timeoutGeminiResponse,
      () -> {
        WebElement element = waitAllUntilPresent(driver, elementLocator, timeout).get(index);
        element.clear();
        element.sendKeys(inputText);
        return null;
      }
    );
  }

  /**
   * Performs typing action on a web element specified by the provided locator, with additional
   * support for healing the element locator if necessary. The method includes functionality for
   * handling input text, timeout for Gemini responses, and element location.
   *
   * @param platform              the platform on which the driver is running (e.g., web, mobile, etc.)
   * @param geminiModel           the Gemini model name used for element healing functionality
   * @param driver                the WebDriver instance used to interact with the browser or application
   * @param elementLocator        the locator used to identify the web element to type on
   * @param uiLabel               a string used for labeling the UI element (useful for debugging/reporting)
   * @param timeoutGeminiResponse the maximum time in seconds to wait for the Gemini healing response
   * @param inputText             the text input that will be typed into the identified web element
   * @param timeoutFindElement    optional timeout configurations for finding the web element; if not provided, a default timeout will be used
   */
  public static void typeOn(
    Platform platform,
    String geminiModel,
    WebDriver driver,
    By elementLocator,
    String uiLabel,
    int timeoutGeminiResponse,
    String inputText,
    Duration... timeoutFindElement) {
    Duration timeout = getTimeout(timeoutFindElement);

    // Try to heal the element locator
    performWithHealing(
      platform,
      geminiModel,
      driver,
      elementLocator,
      uiLabel,
      timeoutGeminiResponse,
      () -> {
        typeOn(driver, elementLocator, inputText, timeout);
        return null;
      }
    );
  }
}
