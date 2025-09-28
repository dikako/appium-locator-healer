package sleeper.locator;

import io.appium.java_client.ios.IOSDriver;
import org.openqa.selenium.By;

/**
 * Provides functionality to heal invalid locators in an iOS application by using a Gemini AI model.
 * The class relies on the Healing framework to find and return a valid locator for an element
 * whose locator is either obsolete or cannot be identified.
 */
public class IOSHealing {

  /**
   * Attempts to heal the locator for a UI element by leveraging the provided Gemini AI model.
   * This method uses the Healing framework to identify and return a valid locator
   * for an element that might have changed or become invalid.
   *
   * @param geminiModel the Gemini AI model used to heal the locator
   * @param iosDriver the iOSDriver instance used to interact with the mobile application
   * @param errorElementLocator the current, invalid locator of the element that needs healing
   * @param error the error details associated with the invalid element
   * @param uiLabel the UI label or descriptive text associated with the element, used to aid healing
   * @param timeout optional timeout (in milliseconds) to wait for the AI model to generate a response; defaults to 15000
   * @return a valid locator for the element that has been healed
   */
  public static By healLocator(
    String geminiModel,
    IOSDriver iosDriver,
    String errorElementLocator,
    String error,
    String uiLabel,
    int... timeout) {

    return Healing.healLocator(
      Healing.Platform.IOS,
      geminiModel,
      iosDriver,
      errorElementLocator,
      error,
      uiLabel,
      timeout.length == 0 ? 15000 : timeout[0]
    );
  }
}
