package sleeper.utils;

import io.appium.java_client.AppiumBy;
import org.openqa.selenium.By;

/**
 * Utility class for creating {@link By} locators from string inputs. This class supports a wide range
 * of locator strategies, including both standard Selenium locators and platform-specific Appium locators
 * for iOS and Android.
 * <p>
 * The supported strategies include:
 * - Accessibility ID: For identifying mobile elements by their accessibility label.
 * - iOS-specific locators: Class chain and predicate string notation.
 * - Android-specific locators: UIAutomator and ViewTag strategies.
 * - General locators: ID, name, CSS selector, XPath, class name, link text, partial link text, and tag name.
 * <p>
 * This class is designed to standardize and simplify the creation of {@link By} instances from various
 * string representations, enabling dynamic locator generation based on runtime inputs.
 * <p>
 * Example use case includes generating locators dynamically based on AI-generated outputs, as seen in
 * scenarios such as healing locators in automated testing frameworks.
 * <p>
 * Note: This is a utility class with only static methods and cannot be instantiated.
 * <p>
 * Methods:
 * - {@code getByFromString(String locatorType, String locatorValue)}:
 * Constructs a {@link By} object based on the given locator type and value. This method supports
 * multiple aliases for commonly used locator strategies to enhance user accessibility.
 * <p>
 * Throws:
 * - {@link UnsupportedOperationException}: If the specified locator type is not supported.
 */
public class LocatorUtils {

  private LocatorUtils() {
  }

  /**
   * Creates a {@link By} locator instance based on the provided locator type and value.
   * Supports standard Selenium locator strategies as well as platform-specific Appium locators
   * for iOS and Android.
   *
   * @param locatorType the type of the locator strategy (e.g., "id", "xpath", "css", "accessibility id", etc.)
   * @param locatorValue the value associated with the specified locator type
   * @return a {@link By} instance corresponding to the specified locator type and value
   * @throws UnsupportedOperationException if the provided locator type is not supported
   */
  public static By getByFromString(String locatorType, String locatorValue) {
    return switch (locatorType.toLowerCase()) {
      // Mobile only
      case "accessibility id", "accessibility" -> AppiumBy.accessibilityId(locatorValue);

      // iOS
      case "-ios class chain", "class chain", "chain" -> AppiumBy.iOSClassChain(locatorValue);
      case "-ios predicate string", "predicate string", "predicate" -> AppiumBy.iOSNsPredicateString(locatorValue);

      // Android
      case "-android uiautomator", "uiautomator" -> new AppiumBy.ByAndroidUIAutomator(locatorValue);
      case "-android viewtag", "viewtag" -> new AppiumBy.ByAndroidViewTag(locatorValue);

      // General
      case "id" -> By.id(locatorValue);
      case "name" -> By.name(locatorValue);
      case "css selector", "css" -> By.cssSelector(locatorValue);
      case "xpath" -> By.xpath(locatorValue);
      case "class name", "classname", "class" -> By.className(locatorValue);
      case "link text", "link" -> By.linkText(locatorValue);
      case "partial link text", "partial link" -> By.partialLinkText(locatorValue);
      case "tag name", "tag" -> By.tagName(locatorValue);
      default -> throw new UnsupportedOperationException("Locator strategy not supported: " + locatorType);
    };
  }
}
