package sleeper.locator;

import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;

/**
 * The AndroidHealing class provides functionality for healing broken or invalid
 * locators for Android UI elements. It uses an AI-powered model to identify and
 * resolve locator-related issues dynamically at runtime.
 */
public class AndroidHealing {

  /**
   * Attempts to heal a broken locator for an Android UI element using an AI-powered model
   * to identify and resolve the issue.
   *
   * @param geminiModel the identifier of the AI model used for healing the locator.
   * @param androidDriver the AndroidDriver instance used for interacting with the Android application.
   * @param errorElementLocator the locator of the element that could not be located.
   * @param error the error details or message associated with the broken locator.
   * @param uiLabel a label or description identifying the UI element to aid in healing.
   * @param timeout an optional timeout value in milliseconds to control the healing process; defaults to 15000 if not provided.
   * @return a new By object representing the healed or resolved locator for the element.
   */
  public static By healLocator(
    String geminiModel,
    AndroidDriver androidDriver,
    String errorElementLocator,
    String error,
    String uiLabel,
    int... timeout) {

    return Healing.healLocator(
      Healing.Platform.ANDROID,
      geminiModel,
      androidDriver,
      errorElementLocator,
      error,
      uiLabel,
      timeout.length == 0 ? 15000 : timeout[0]
    );
  }
}
