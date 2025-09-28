package sleeper.utils;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;

/**
 * Utility class for interacting with the Gemini AI content generation model.
 * Specifically provides methods to generate a response based on a given model, prompt,
 * and configurable timeout parameters. This class is designed to work with client instances
 * that communicate with the Gemini AI services to retrieve generated content.
 * <p>
 * This class contains only static methods and is not intended to be instantiated.
 */
public class GeminiUtils {

  /**
   * Generates a content response based on the specified model and prompt, with a configurable timeout.
   *
   * @param model   the identifier of the specific model to use for content generation
   * @param prompt  the input prompt or instruction for which the model generates content
   * @param timeout the timeout duration, in milliseconds, for the client request
   * @return a {@code GenerateContentResponse} object containing the generated content
   */
  public static GenerateContentResponse getResponse(String model, String prompt, int timeout) {
    Client client = Client.builder()
      .httpOptions(HttpOptions.builder().timeout(timeout).build())
      .build();

    return client.models.generateContent(
      model,
      prompt,
      null
    );
  }
}
