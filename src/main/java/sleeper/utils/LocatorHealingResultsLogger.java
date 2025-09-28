package sleeper.utils;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A utility class for logging locator healing results into a JSON file. This class enables
 * logging of healed element locators to a specified JSON file in a thread-safe manner.
 * The logger appends locator results to a JSON array stored in the file, creating the
 * file or its parent directories if they do not exist. If the file is unreadable or
 * corrupted, it is backed up automatically, and a new file is started.
 * <p>
 * Features:
 * - Default logging file path: "logs/resolved-elements.json"
 * - Custom file paths can be specified during instantiation.
 * - Thread-safe appending of locator results.
 * - Automatic creation of missing directories and file handling.
 * <p>
 * Designed for use in automated UI testing frameworks that rely on locator healing,
 * aiding in scenarios such as logging AI-generated or dynamically resolved locators.
 * <p>
 * Instances of this class are immutable and can be safely shared between threads.
 */
public record LocatorHealingResultsLogger(Path file) {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final ObjectWriter WRITER = MAPPER.writerWithDefaultPrettyPrinter();

  // Default file path: ./logs/resolved-element.json
  private static final Path DEFAULT_FILE = Path.of("logs", "resolved-elements.json");

  /**
   * Creates a logger that writes to the default path (logs/resolved-element.json).
   */
  public LocatorHealingResultsLogger() {
    this(DEFAULT_FILE);
  }

  /**
   * Creates a logger that writes to a custom file.
   *
   * @param file path to the JSON file to append to (created if missing)
   */
  public LocatorHealingResultsLogger(Path file) {
    this.file = Objects.requireNonNull(file, "file must not be null");
  }

  /**
   * Saves the provided healing result (a map of key-value pairs) into the target JSON file.
   * The method ensures thread-safety, manages directory creation, performs atomic file updates,
   * and handles error scenarios gracefully, such as backing up corrupted files and ensuring
   * cleanup of temporary files.
   *
   * @param result the healing result to be stored, represented as a map with string keys and object values
   */
  public synchronized void saveHealedElementLocator(Map<String, Object> result) {
    try {
      // Ensure parent directory exists
      Path parent = file.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }

      // Prepare list of existing results
      List<Map<String, Object>> results = readExistingArrayOrEmpty();

      // Add new result
      results.add(result);

      // Write to temp file then move atomically (best-effort)
      Path tempFile = Files.createTempFile(parent != null ? parent : Path.of("."), "resolved-element", ".tmp");
      try {
        WRITER.writeValue(tempFile.toFile(), results);

        // Try atomic move first; fallback to non-atomic move if not supported
        try {
          Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
          Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING);
        }
      } finally {
        // Clean up temp if it still exists
        try {
          Files.deleteIfExists(tempFile);
        } catch (Exception ignored) {
        }
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to save healing result", e);
    }
  }

  /**
   * Reads the contents of the JSON file into a list of maps if the file exists and is non-empty.
   * If the file does not exist or is empty, returns an empty list.
   * In case of an I/O error or data corruption, backs up the corrupt file and returns an empty list.
   *
   * @return a list of maps representing the JSON array in the file, or an empty list if the file does not exist,
   *         is empty, or cannot be read successfully.
   * @throws IOException if an error occurs while checking the file or creating directories.
   */
  private List<Map<String, Object>> readExistingArrayOrEmpty() throws IOException {
    if (Files.exists(file) && Files.size(file) > 0) {
      try {
        // Build JavaType for List<Map<String,Object>>
        JavaType mapType = MAPPER.getTypeFactory().constructMapType(Map.class, String.class, Object.class);
        JavaType listOfMapType = MAPPER.getTypeFactory().constructCollectionType(List.class, mapType);

        List<Map<String, Object>> loaded = MAPPER.readValue(file.toFile(), listOfMapType);
        return loaded != null ? loaded : new ArrayList<>();
      } catch (IOException e) {
        // If file is corrupted or unreadable, back up the bad file and start fresh (safer for tests)
        Path backup = file.resolveSibling(file.getFileName().toString() + ".broken." + System.currentTimeMillis());
        try {
          Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
        }
        return new ArrayList<>();
      }
    } else {
      return new ArrayList<>();
    }
  }
}
