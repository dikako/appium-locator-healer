package sleeper;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A resilient element finder that automatically triggers self-healing
 * when a locator fails, using an AI model to find and verify a new one.
 */
public class LocatorHealer {
  private static final Pattern JSON_PATTERN =
      Pattern.compile("```json\\s*([\\s\\S]*?)\\s*```");

  private final AppiumDriver driver;
  private final HealerConfig config;
  private final Client geminiClient;
  private final Gson gson = new Gson();

  public LocatorHealer(AppiumDriver driver, HealerConfig config) {
    this.driver = driver;
    this.config = config;
    this.geminiClient = new Client();
  }

  /**
   * A resilient replacement for driver.findElement().
   * It first tries to find the element with the original locator. If it fails,
   * it triggers the self-healing process to find and verify a new locator.
   *
   * @param originalLocator The initial 'By' locator that might fail.
   * @return The found WebElement.
   * @throws NoSuchElementException if both the original and healed locators fail.
   */
  public WebElement findAndHeal(By originalLocator) {
    try {
      return driver.findElement(originalLocator);
    } catch (NoSuchElementException e) {
      System.out.println("INFO: Locator failed: " + originalLocator + ". Initiating self-healing...");

      Optional<HealedLocator> healed = attemptHealing(originalLocator.toString(), e.getMessage());

      if (healed.isPresent() && healed.get().newValidElement() != null) {
        HealedLocator suggestion = healed.get();
        By newLocator = buildByFromSuggestion(suggestion);

        System.out.println("INFO: Healing suggested a new locator: " + newLocator);

        try {
          WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(config.verificationTimeoutSecs()));
          return wait.until(ExpectedConditions.presenceOfElementLocated(newLocator));
        } catch (Exception verificationEx) {
          throw new NoSuchElementException("Self-healing failed. Original locator: '" + originalLocator + "' and new locator: '" + newLocator + "' could not find the element.", e);
        }
      } else {
        System.err.println("ERROR: Self-healing did not provide a valid new locator.");
        throw e; // Re-throw the original exception if healing fails
      }
    }
  }

  /**
   * Calls the Gemini API to analyze the page source and find a new locator.
   */
  private Optional<HealedLocator> attemptHealing(String failedLocator, String errorMessage) {
    String pageSource = driver.getPageSource();
    String prompt = buildSelfHealingPrompt(failedLocator, errorMessage, pageSource);

    try {
      GenerateContentResponse response = geminiClient.models.generateContent(config.modelName(), prompt, null);
      String rawText = response.text();
      String jsonText = extractJson(rawText);

      if (jsonText.isEmpty()) {
        System.err.println("WARN: Gemini response was empty or contained no JSON block.");
        return Optional.empty();
      }

      HealedLocator healed = gson.fromJson(jsonText, HealedLocator.class);
      return Optional.of(healed);

    } catch (JsonSyntaxException e) {
      System.err.println("ERROR: Failed to parse JSON response from Gemini. Invalid JSON. Details: " + e.getMessage());
      return Optional.empty();
    } catch (Exception e) {
      System.err.println("ERROR: An unexpected error occurred during the self-healing API call. Details: " + e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * Converts the string-based locator from the AI response into a Selenium 'By' object.
   */
  private By buildByFromSuggestion(HealedLocator suggestion) {
    String type = suggestion.newValidElementType().toLowerCase().trim();
    String value = suggestion.newValidElement();

    return switch (type) {
      case "id" -> By.id(value);
      case "accessibility id" -> By.cssSelector("[accessibility-id='" + value + "']");
      case "name" -> By.name(value);
      case "xpath" -> By.xpath(value);
      case "class chain" -> By.className(value); // Note: This might need a specific Appium By if available
      case "predicate" -> By.className(value); // Note: This might need a specific Appium By if available
      default -> throw new IllegalArgumentException("Unsupported locator type from AI: " + type);
    };
  }

  /**
   * Extracts a JSON object from a string, trimming markdown fences if present.
   */
  private String extractJson(String text) {
    if (text == null || text.isBlank()) {
      return "";
    }
    Matcher matcher = JSON_PATTERN.matcher(text);
    return matcher.find() ? matcher.group(1).trim() : text.trim();
  }

  /**
   * Builds the prompt with detailed instructions, rules, and a few-shot example.
   */
  private static String buildSelfHealingPrompt(String failedLocator, String errorMessage, String pageSource) {
    return """
        **ROLE**
        You are an expert Test Automation Engineer specializing in mobile UI automation. Your task is to act as a "self-healing" engine. When a UI element locator fails, you must analyze the provided page source and error to find the best alternative locator.
        
        **CONTEXT**
        A test script failed to find an element using the `failed_locator`. Your goal is to provide a stable, alternative locator based on the current `page_source`.
        
        **RULES**
        1.  **Locator Priority:** Prioritize locators in this order: `accessibility id` > `id` > `name` > `class chain` (iOS) > `predicate string` (iOS) > `xpath`. Use XPath only as a last resort.
        2.  **JSON Output Only:** Your response MUST be a single, valid JSON object enclosed in ```json ... ```. Do NOT include any text or explanation outside the JSON block.
        3.  **JSON Schema:** The JSON object must conform to this exact schema with 5 keys:
            ```json
            {
              "failedElement": "<string>",
              "newValidElementType": "<string: accessibility id|id|name|class chain|predicate|xpath>",
              "newValidElement": "<string>",
              "reason": "<string: concise explanation of why the original failed and why the new one was chosen>",
              "suggestion": "<string: recommendation for developers to improve testability, e.g., 'Add a unique accessibility ID.'>"
            }
            ```
        4.  **No Locator Found:** If you cannot find a suitable new locator, `newValidElement` and `newValidElementType` must be `null`, and the `reason` field should explain why.
        
        ---
        
        **EXAMPLE**
        *Input:*
        - `failed_locator`: "id=login_button_old"
        - `error_message`: "An element could not be located on the page using the given search parameters."
        - `page_source`:
        ```xml
        <XCUIElementTypeApplication name="MyApp">
          <XCUIElementTypeWindow>
            <XCUIElementTypeTextField value="username"/>
            <XCUIElementTypeButton type="XCUIElementTypeButton" name="login-btn" label="Sign In" />
          </XCUIElementTypeWindow>
        </XCUIElementTypeApplication>
        ```
        *Output:*
        ```json
        {
          "failedElement": "id=login_button_old",
          "newValidElementType": "name",
          "newValidElement": "login-btn",
          "reason": "The original locator 'id=login_button_old' was not found. A stable element with the name 'login-btn' and label 'Sign In' appears to be the correct replacement.",
          "suggestion": "Consider replacing the 'name' attribute with a unique and static 'accessibility id' like 'loginButton' for improved test stability."
        }
        ```
        
        ---
        
        **TASK**
        Analyze the following failure and generate the JSON output.
        
        *Input:*
        - `failed_locator`: "%s"
        - `error_message`: "%s"
        - `page_source`:
        ```xml
        %s
        ```
        """.formatted(failedLocator, errorMessage, pageSource);
  }
}
