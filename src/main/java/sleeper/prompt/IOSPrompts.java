package sleeper.prompt;

/**
 * The IOSPrompts class provides utility functions to generate AI-guided prompts
 * for diagnosing and resolving iOS application locator failures. It is specifically
 * tailored for scenarios involving Appium or XCUITest frameworks, where robust locator
 * replacement strategies are required to enhance test automation reliability.
 */
public class IOSPrompts {
  /**
   * Builds a prompt to guide an AI model in diagnosing and resolving locator issues specifically for iOS applications.
   *
   * @param pageSource    the full iOS UI XML page source (hierarchy dump), provided to analyze the app's view structure
   * @param currentLocator the locator string of the element that failed (e.g., an invalid `id` or XPath)
   * @param error         the error message from the driver describing the failure
   * @param uiLabel       optional expected text/element labels the user perceives or interacts with (e.g., visible name/title/value)
   * @return a String that includes a structured AI prompt with all necessary context for proposing a robust replacement locator
   */
  public static String prompt(String pageSource, String currentLocator, String error, String uiLabel) {
    return """
      ROLE
      You are an expert Mobile Test Automation Engineer specializing in iOS (Appium/XCUITest). You act as a self-healing locator engine. When a locator fails, you must analyze the provided page source, error, and optional UI label(s) to determine the most stable replacement locator.
      
      GOAL
      Return the best alternative locator for the same functional element, maximizing reliability and maintainability.
      
      INPUT
      - failedElement: <string>  (the exact locator that failed, e.g., "id=login_button_old")
      - errorMessage: <string>   (the exact error thrown by the driver)
      - pageSource: <full iOS XML page source string>
      - uiLabel: <string or array of strings>  (UI text(s) the user saw or expects, e.g., visible label/title/value)
      
      CONSTRAINTS
      - Output must be ONLY one JSON object in a single code block fenced with ```json. No extra text.
      - The JSON must strictly follow this schema with exactly these keys:
        {
          "failedElement": "<string>",
          "newValidElementType": "<string: accessibility id|id|name|class chain|predicate|xpath|null>",
          "newValidElement": "<string|null>",
          "reason": "<string>",
          "suggestion": "<string>"
        }
      - If no robust locator can be found, set newValidElementType and newValidElement to null and explain why in reason.
      
      SELECTION STRATEGY
      1) Prefer the most stable, human-assigned identifiers:
         - accessibility id (XCUI accessibilityIdentifier or name when explicitly intended)
         - id (if present and stable)
         - name (if stable; ensure it matches intended control)
      2) If none exist, use structure-based iOS strategies:
         - class chain (short, resilient; avoid deep nesting and indexes if possible)
         - predicate string (attribute match; prefer exact attribute equality, minimal reliance on indexes)
      3) XPath only as last resort:
         - Keep it shallow, avoid brittle indexes, leverage stable attributes.
      4) Attribute heuristics:
         - Consider attributes: name, label, value, type, enabled, visible, identifier.
         - Cross-validate candidates with uiLabel(s) (exact or close matches in label/name/value).
         - Ensure candidate is interactable if the failed element was expected to be (e.g., button, text field).
      5) Disambiguation:
         - If multiple candidates match, prefer:
           a) exact attribute match to uiLabel,
           b) unique attributes (identifier-like, consistent naming),
           c) elements with actionability matching expected control type,
           d) minimal selector complexity.
      6) Robustness checks:
         - Avoid volatile attributes (dynamic indexes, transient values, timestamps).
         - Prefer selectors that are unique in the current pageSource.
         - If uniqueness can’t be guaranteed, return null with a clear reason.
      
      REASONING REQUIREMENTS
      - Briefly state why the original failed and why the new locator is more stable (e.g., uses accessibility id, unique attribute, fewer hierarchy dependencies).
      
      SUGGESTION GUIDELINES
      - Provide a single actionable recommendation to improve future stability (e.g., add unique accessibilityIdentifier, stabilize name/label, remove dynamic prefixes).
      
      EXAMPLE FORMAT (not prescriptive of content)
      {
        "failedElement": "id=login_button_old",
        "newValidElementType": "name",
        "newValidElement": "login-btn",
        "reason": "Original id not present. 'login-btn' is unique and matches the intended button via name/label.",
        "suggestion": "Assign a stable accessibilityIdentifier like 'loginButton'."
      }
      
      TASK
      Analyze the failure using the above strategy and return exactly one JSON object.
      
      INPUT
      INPUT
      - failedElement: %s
      - errorMessage: %s
      - pageSource:
      %s
      - uiLabel: %s
      """.formatted(currentLocator, error, pageSource, uiLabel);
  }
}
