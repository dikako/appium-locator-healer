package sleeper.locator;

import com.google.genai.types.GenerateContentResponse;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import sleeper.prompt.AndroidPrompts;
import sleeper.prompt.IOSPrompts;
import sleeper.prompt.WebPrompts;
import sleeper.utils.GeminiUtils;
import sleeper.utils.LocatorUtils;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * The Healing class provides functionality to heal invalid locators across different platforms
 * (Android, iOS, and Web) by using AI-driven models. It aims to assist in resolving issues
 * related to failing element locators by generating valid alternatives based on the provided
 * error details and context.
 */
public class Healing {
  /**
   * A static final Logger instance used for logging messages and debugging
   * information in the application. It is associated with the Healing class
   * for tracking and recording runtime events, error messages, or informational
   * data specific to the execution of the class's functionality.
   */
  private static final Logger logger = Logger.getLogger(Healing.class.getName());


  /**
   * A final instance of LocatorCache reused throughout the application to manage and store
   * locational data in memory for improved performance and quick access.
   * This cache is implemented as a singleton to ensure only one instance exists globally.
   */
  private static final LocatorCache cache = LocatorCache.getInstance();

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

    logger.info(String.format(
      "[%s Element Healing] Cache HIT. Returning previously resolved locator for: %s",
      platform, errorElementLocator
    ));

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
   * Attempts to perform the specified action on a web element and retries using a healed locator
   * if the initial attempt fails. The healing mechanism tries to resolve issues related to
   * element locators dynamically.
   *
   * @param platform the platform information to assist in the locator healing process
   * @param geminiModel the Gemini model identifier used for generating healed locators
   * @param driver the WebDriver instance used for interacting with the web application
   * @param elementLocator the original locator for the web element
   * @param uiLabel a readable identifier for the user interface element
   * @param timeoutGeminiResponse timeout duration for the Gemini response in seconds
   * @param action the action to perform on the web element, expressed as a function
   * @param <T> the type of the action's return value
   * @return the result of the action performed on the web element, or throws an exception if
   *         the action fails even after trying to heal the locator
   */
  public static <T> T performWithHealing(
    Platform platform,
    String geminiModel,
    WebDriver driver,
    By elementLocator,
    String uiLabel,
    int timeoutGeminiResponse,
    Function<By, T> action
  ) {
    try {
      // First attempt
      return action.apply(elementLocator);
    } catch (Exception e) {
      String errorMessage = e.getMessage();
      logger.warning(String.format("Action failed on element: %s, Error: %s", elementLocator, errorMessage));

      Optional<By> cachedLocator = cache.getHealedLocator(elementLocator);
      if (cachedLocator.isPresent()) {
        By cachedHealedLocatorValue = cachedLocator.get();

        try {
          logger.info(String.format("Healed locator cached: %s", cachedHealedLocatorValue));

          // Retry after healing
          T result = action.apply(cachedHealedLocatorValue);
          logger.info(String.format("Action succeeded after use Healed locator cached: %s", cachedHealedLocatorValue));
          return result;
        } catch (Exception retryEx) {
          logger.warning(String.format("Action still failed after use Healed locator cached: %s, Error: %s",
            cachedHealedLocatorValue, retryEx.getMessage()));

          // The cached locator is also broken now, so remove it
          cache.removeStaleLocator(elementLocator);
          throw retryEx;
        }
      } else {
        // Heal locator
        By healedLocator = healLocator(
          platform,
          geminiModel,
          driver,
          elementLocator.toString(),
          errorMessage,
          uiLabel,
          timeoutGeminiResponse
        );

        String healedLocatorString = healedLocator.toString();

        try {
          // Retry after healing
          T result = action.apply(healedLocator);
          logger.info(String.format("Action succeeded after healing: %s", healedLocatorString));
          return result;
        } catch (Exception retryEx) {
          logger.warning(String.format("Action still failed after healing: %s, Error: %s",
            healedLocatorString, retryEx.getMessage()));

          // The cached locator is also broken now, so remove it
          cache.removeStaleLocator(elementLocator);
          throw retryEx;
        }
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
      (By locator) -> waitUntilClickable(driver, locator, timeout)
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
      (By locator) -> waitUntilVisible(driver, locator, timeout)
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
      (By locator) -> waitUntilPresent(driver, locator, timeout)
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
      (By locator) -> waitAllUntilPresent(driver, locator, timeout)
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
      (By locator) -> waitUntilInvisible(driver, locator, timeout)
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
        (By locator) -> {
          isPresent(driver, locator, timeout);
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
        (By locator) -> {
          clickOn(driver, locator, timeout);
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
        (By locator) -> {
          WebElement element = waitAllUntilPresent(driver, locator, timeout).get(index);
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
      (By locator) -> {
        WebElement element = waitAllUntilPresent(driver, locator, timeout).get(index);
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
      (By locator) -> {
        typeOn(driver, locator, inputText, timeout);
        return null;
      }
    );
  }
}
