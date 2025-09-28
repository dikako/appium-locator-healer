package sleeper.prompt;

/**
 * A utility class for generating prompts aimed at diagnosing and healing
 * failing locators in Android test automation. This class specifically produces
 * detailed debugging instructions and context in a structured format suitable
 * for AI-based self-healing solutions.
 */
public class AndroidPrompts {
  /**
   * Build a prompt for Android element healing.
   *
   * @param pageSource    the full Android UI XML/page source (hierarchy dump)
   * @param currentLocator the failing locator (string representation)
   * @param error         the driver error message received
   * @param uiLabel       optional expected text/label seen by user (String or JSON array string)
   * @return the prompt to send to the AI model
   */
  public static String prompt(String pageSource, String currentLocator, String error, String uiLabel) {
    return """
      ROLE
      You are an expert Mobile Test Automation Engineer specializing in Android (Appium / UiAutomator2). You act as a self-healing locator engine. When a locator fails, analyze the provided page source, error message, and optional UI label(s) to determine the most stable replacement locator.
      
      GOAL
      Return the best alternative locator for the same functional element, maximizing reliability and maintainability on Android.
      
      INPUT
      - failedElement: <string>  (the exact locator that failed, e.g., "id=com.example:id/login_old")
      - errorMessage: <string>   (the exact error thrown by the driver)
      - pageSource: <full Android UI XML page source string>
      - uiLabel: <string or array of strings>  (UI text(s) the user saw or expects)
      
      CONSTRAINTS
      - Output must be ONLY one JSON object in a single code block fenced with ```json. No extra text.
      - The JSON must strictly follow this schema with exactly these keys:
        {
          "failedElement": "<string>",
          "newValidElementType": "<string: accessibility id|resource-id|text|class|uiautomator|xpath|null>",
          "newValidElement": "<string|null>",
          "reason": "<string>",
          "suggestion": "<string>"
        }
      - If no robust locator can be found, set newValidElementType and newValidElement to null and explain why in reason.
      
      SELECTION STRATEGY (Android-specific)
      1) Prefer stable, developer-assigned identifiers:
         - resource-id (Android view id, e.g., com.example:id/login_button)
         - accessibility id / content-desc (use when content-desc is intentionally set for interaction)
         - text (visible text) when it is stable and unique
      2) If none exist or are stable, use native Android strategies:
         - UiAutomator (UiSelector) that matches resource-id/content-desc/text/class/description; prefer concise selectors
         - class + attribute filters (e.g., android.widget.Button[@text='OK']), but avoid deep index-based selectors
      3) XPath only as last resort:
         - Keep it shallow, avoid brittle indexes, and prefer attribute equality
      4) Attribute heuristics:
         - Consider attributes: resource-id, content-desc, text, class, clickable, enabled, displayed
         - Cross-validate candidates with uiLabel(s) (exact or close matches)
         - Ensure candidate is interactable if the failed element was expected to be (e.g., clickable for button)
      5) Disambiguation:
         - If multiple candidates match, prefer:
           a) exact attribute match to uiLabel or resource-id,
           b) unique resource-id or content-desc,
           c) elements with actionability matching expected control type,
           d) minimal selector complexity.
      6) Robustness checks:
         - Avoid volatile attributes (dynamic indexes, generated IDs with timestamps)
         - Prefer selectors unique in the current pageSource
         - If uniqueness can’t be guaranteed, return null with a clear reason
      
      REASONING REQUIREMENTS
      - Briefly state why the original failed and why the new locator is more stable (e.g., uses resource-id, content-desc, or unique text).
      
      SUGGESTION GUIDELINES
      - Provide a single actionable recommendation to improve future stability (e.g., add stable resource-id, set content-desc for important controls, avoid dynamic prefixes in ids).
      
      EXAMPLE FORMAT (not prescriptive)
      {
        "failedElement": "id=com.example:id/login_old",
        "newValidElementType": "resource-id",
        "newValidElement": "com.example:id/login_button",
        "reason": "Original id not present. 'com.example:id/login_button' exists and is unique.",
        "suggestion": "Use stable resource-id or set content-desc for critical actions."
      }
      
      TASK
      Analyze the failure using the above strategy and return exactly one JSON object.
      
      INPUT
      - failedElement: %s
      - errorMessage: %s
      - pageSource:
      %s
      - uiLabel: %s
      """.formatted(currentLocator, error, pageSource, uiLabel);
  }
}
