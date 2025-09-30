package sleeper.locator;

import org.openqa.selenium.By;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * A thread-safe Singleton cache to store healed locators.
 * Key: The original, broken locator as a String.
 * Value: The new, working 'By' locator.
 */
public class LocatorCache {
  /**
   * Singleton instance of the LocatorCache class.
   * This provides access to the globally accessible, thread-safe cache for storing
   * mappings between original, broken locators and the corresponding healed 'By' locators.
   */
  private static final LocatorCache INSTANCE = new LocatorCache();

  /**
   * A thread-safe map that caches mappings between original, broken locators
   * (stored as Strings) and their corresponding healed 'By' locators.
   * This map is used to store locators that have been updated
   * or repaired for continued usability in automated testing workflows.
   */
  private final ConcurrentHashMap<String, By> healedLocators = new ConcurrentHashMap<>();

  private final static Logger logger = Logger.getLogger(LocatorCache.class.getName());

  // Private constructor to prevent instantiation
  private LocatorCache() {
  }

  /**
   * Provides access to the Singleton instance of the LocatorCache class. This ensures
   * a globally accessible, thread-safe cache for storing mappings between original,
   * broken locators and their corresponding healed 'By' locators.
   *
   * @return the Singleton instance of the LocatorCache class.
   */
  public static LocatorCache getInstance() {
    return INSTANCE;
  }

  /**
   * Retrieves the healed locator corresponding to the provided original locator, if present.
   * The healed locator is a corrected or functional replacement for a previously broken locator.
   *
   * @param originalLocator the original locator which may be broken or no longer functional.
   * @return an Optional containing the healed locator if it exists in the cache; otherwise, an empty Optional.
   */
  public Optional<By> getHealedLocator(By originalLocator) {
    return Optional.ofNullable(healedLocators.get(originalLocator.toString()));
  }

  /**
   * Adds a healed locator to the cache for the specified original locator.
   * The original locator is converted to a string and used as the key, and
   * the corresponding healed locator is stored as the value.
   * This allows maintaining a mapping of broken locators to their repaired counterparts.
   *
   * @param originalLocator the original broken locator to be used as the key in the cache.
   * @param healedLocator the healed or corrected locator to be stored as the corresponding value.
   */
  public void addHealedLocator(By originalLocator, By healedLocator) {
    String originalLocatorString = originalLocator.toString();
    logger.info("[Cache Update] Adding healed locator: " + originalLocatorString);
    healedLocators.put(originalLocatorString, healedLocator);
  }

  /**
   * Removes a stale or outdated locator from the cache.
   * This method ensures that the cache no longer references a locator
   * that is no longer valid or required.
   *
   * @param originalLocator the original locator to be removed from the cache.
   *                        It is converted to a string and used as the key for removal.
   */
  public void removeStaleLocator(By originalLocator) {
    logger.info("[Cache Update] Removing stale locator: " + originalLocator.toString());
    healedLocators.remove(originalLocator.toString());
  }
}
