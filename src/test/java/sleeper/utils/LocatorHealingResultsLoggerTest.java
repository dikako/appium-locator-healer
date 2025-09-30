package sleeper.utils;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the LocatorHealingResultsLogger class, which is responsible for
 * appending healing results to a JSON file in an atomic and thread-safe manner.
 */
public class LocatorHealingResultsLoggerTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /**
   * Verify that the logger creates the file and writes the first entry.
   * We explicitly pass an output path under the TempDir to keep test hermetic.
   */
  @Test
  void appendCreatesFileAndWritesFirstEntry(@TempDir Path tmpDir) throws Exception {
    Path target = tmpDir.resolve("logs").resolve("resolved-elements.json");
    LocatorHealingResultsLogger logger = new LocatorHealingResultsLogger(target);

    Map<String, Object> entry = Map.of(
      "timestamp", 123L,
      "errorElementLocator", "By.id:login",
      "resolvedElementLocator", "accessibilityId",
      "detailAIResponse", "login_button"
    );

    logger.saveHealedElementLocator(entry);

    assertTrue(target.toFile().exists(), "file should be created");

    // read back using JavaType (avoids TypeReference import problems)
    JavaType mapType = MAPPER.getTypeFactory().constructMapType(Map.class, String.class, Object.class);
    JavaType listOfMapType = MAPPER.getTypeFactory().constructCollectionType(List.class, mapType);

    List<Map<String, Object>> read = MAPPER.readValue(target.toFile(), listOfMapType);
    assertNotNull(read);
    assertEquals(1, read.size(), "array must contain exactly one entry");
    assertEquals("login_button", read.get(0).get("resolvedLocator"));
  }

  /**
   * Verify that two sequential appends produce two entries in order.
   */
  @Test
  void appendTwiceAppendsTwoEntries(@TempDir Path tmpDir) throws Exception {
    Path target = tmpDir.resolve("output").resolve("my-resolved.json");
    LocatorHealingResultsLogger logger = new LocatorHealingResultsLogger(target);

    Map<String, Object> first = Map.of(
      "timestamp", 1L,
      "originalLocator", "By.id:one",
      "resolvedLocator", "one"
    );

    Map<String, Object> second = Map.of(
      "timestamp", 2L,
      "originalLocator", "By.id:two",
      "resolvedLocator", "two"
    );

    logger.saveHealedElementLocator(first);
    logger.saveHealedElementLocator(second);

    // read back using JavaType
    JavaType mapType = MAPPER.getTypeFactory().constructMapType(Map.class, String.class, Object.class);
    JavaType listOfMapType = MAPPER.getTypeFactory().constructCollectionType(List.class, mapType);

    List<Map<String, Object>> read = MAPPER.readValue(target.toFile(), listOfMapType);
    assertEquals(2, read.size(), "array must contain two entries");
    assertEquals("one", read.get(0).get("resolvedLocator"));
    assertEquals("two", read.get(1).get("resolvedLocator"));
  }
}
