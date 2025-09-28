package sleeper.locator;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

/**
 * The WebHealing class provides functionality to heal failing web element locators by leveraging
 * AI-driven models. It is specifically designed to resolve issues related to invalid locators
 * encountered in web platforms by suggesting new valid locators based on the provided error details
 * and context.
 */
public class WebHealing {

  /**
   * Attempts to heal a failing element locator by leveraging an AI-driven model to generate a new
   * valid locator based on the error details and context provided. This method is specific to web platforms.
   *
   * @param geminiModel          the name or identifier of the AI model used for generating the new locator
   * @param webDriver            the WebDriver instance used to interact with the web application
   * @param errorElementLocator  the locator of the element that failed and needs to be healed
   * @param error                details of the error that occurred (e.g., error message or context)
   * @param uiLabel              an optional UI label or element description used for additional context
   * @param timeout              optional timeout value (in milliseconds) for waiting for the AI model's response; defaults to 15000 if none provided
   * @return a {@link By} object representing the new valid locator for the element
   */
  public static By healLocator(
    String geminiModel,
    WebDriver webDriver,
    String errorElementLocator,
    String error,
    String uiLabel,
    int... timeout) {

    return Healing.healLocator(
      Healing.Platform.WEB,
      geminiModel,
      webDriver,
      errorElementLocator,
      error,
      uiLabel,
      timeout.length == 0 ? 15000 : timeout[0]
    );
  }
}
