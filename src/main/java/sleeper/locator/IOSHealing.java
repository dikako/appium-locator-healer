package sleeper.locator;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.appium.java_client.ios.IOSDriver;
import sleeper.prompt.IOSPrompts;

import java.util.logging.Logger;

public class IOSHealing {

  private static final Logger logger = Logger.getLogger(IOSHealing.class.getName());

  public static JsonObject healLocator(String geminiModel, IOSDriver driver, String currentLocator, String error, String uiLabel) {
    Client geminiClient = new Client();

    String prompt = IOSPrompts.prompt(
      driver.getPageSource(),
      currentLocator,
      error,
      uiLabel
    );

    GenerateContentResponse response =
      geminiClient.models.generateContent(
        geminiModel,
        prompt,
        null
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

    logger.info("Response text: " + responseText);

    JsonElement responseTextToJsonElement = JsonParser.parseString(responseText);
    JsonObject responseJson = responseTextToJsonElement.getAsJsonObject();
    String locatorType = responseJson.get("newValidElementType").getAsString();
    String locator = responseJson.get("newValidElement").getAsString();

    logger.info("""
      Element Type: %s
      Element Locator: %s
      """.formatted(locatorType, locator));

    return responseJson;
  }
}
