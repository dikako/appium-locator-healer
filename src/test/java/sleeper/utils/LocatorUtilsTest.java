package sleeper.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for logging of Map.of(...).toString() output.
 */
public class LocatorUtilsTest {
  private static final Logger TEST_LOGGER = Logger.getLogger("sleeper.utils.MapLoggingTest");
  private TestHandler handler;

  @BeforeEach
  void setUp() {
    // create and attach handler to capture log messages
    handler = new TestHandler();
    handler.setLevel(Level.ALL);
    TEST_LOGGER.addHandler(handler);
    TEST_LOGGER.setLevel(Level.ALL);
    // prevent parent handlers from also printing (keeps test output clean)
    TEST_LOGGER.setUseParentHandlers(false);
  }

  @AfterEach
  void tearDown() {
    TEST_LOGGER.removeHandler(handler);
    TEST_LOGGER.setUseParentHandlers(true);
  }

  @Test
  void logMapContainsAllExpectedEntriesAndExecutableInstant() {
    // given - deterministic sample inputs
    String currentLocator = "By.id:login_button";
    String resolvedLocator = "By.accessibilityId:login";
    String responseText = "{\"resolvedType\":\"accessibilityId\",\"resolvedLocator\":\"login\"}";

    // when - build map and log it (this mirrors the snippet you showed)
    TEST_LOGGER.info(Map.of(
      "executedAt", Instant.now(),
      "errorElementLocator", currentLocator,
      "resolvedElementLocator", resolvedLocator,
      "detailAIResponse", responseText
    ).toString());

    // capture logged message
    String logged = handler.getLastMessage();
    assertNotNull(logged, "Expected a logged message");

    // then - check presence of known values
    assertTrue(logged.contains("errorElementLocator=" + currentLocator) ||
        logged.contains("errorElementLocator=" + currentLocator.replace("=", "\\=")),
      "Logged message should contain the errorElementLocator value");
    assertTrue(logged.contains("resolvedElementLocator=" + resolvedLocator),
      "Logged message should contain the resolvedElementLocator value");
    assertTrue(logged.contains("detailAIResponse=" + responseText) ||
        logged.contains("detailAIResponse=" + responseText.replace("\"", "")),
      "Logged message should contain the detailAIResponse value");

    // extract executedAt value using regex: executedAt=VALUE (stops at comma or closing brace)
    Pattern p = Pattern.compile("\\bexecutedAt=([^,}]+)");
    Matcher m = p.matcher(logged);
    assertTrue(m.find(), "executedAt field should be present in the logged map");
    String executedAtText = m.group(1).trim();

    // executedAt should be a valid Instant string (Instant.toString -> ISO-8601 with 'Z')
    // Remove surrounding quotes if any (Map.toString may include Instant.toString without quotes)
    if (executedAtText.startsWith("\"") && executedAtText.endsWith("\"")) {
      executedAtText = executedAtText.substring(1, executedAtText.length() - 1);
    }

    Instant parsed = Instant.parse(executedAtText);
    assertNotNull(parsed, "executedAt should parse to an Instant");
  }

  /**
   * Simple in-memory handler to capture last log message.
   */
  private static class TestHandler extends Handler {
    private String lastMessage;

    @Override
    public void publish(LogRecord record) {
      if (record == null) return;
      String msg = record.getMessage();
      // If there are parameters, format them (the code under test uses toString so no params expected)
      if (record.getParameters() != null && record.getParameters().length > 0) {
        try {
          msg = String.format(msg, record.getParameters());
        } catch (Exception ignored) {}
      }
      lastMessage = msg;
    }

    @Override
    public void flush() {}

    @Override
    public void close() throws SecurityException {}

    String getLastMessage() {
      return lastMessage;
    }
  }
}
