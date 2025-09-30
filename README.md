# Self-Healing Elements for Java (Appium/Selenium)

> A Java library/package that adds self-healing capabilities to your UI automation. When a locator breaks (e.g., DOM change, attribute drift), the library automatically attempts to heal the locator and re-run the action—improving test stability, reducing flaky failures, and boosting productivity.

### Key features:
* Automatic retry with healed locators on failure
* Caching of successful healed locators to speed up future runs
* Pluggable healing strategy powered by model-based suggestions
* Non-invasive functional API: wrap any element action with healing
* Platform-aware healing (web, mobile-web, native wrappers)
* Java 17 compatible

### Coming soon:
* Unified helpers to handle all interactions consistently:
  * Click, type, clear, select, hover, submit, drag-and-drop, wait-and-click

## Why self-healing?
* DOM evolves, tests shouldn’t break on every minor change
* Fewer reruns and less manual locator maintenance
* Faster feedback and more reliable CI pipelines

## Installation
### Maven
##### Add to pom.xml
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

#### Add to dependencies
```xml
<dependency>
    <groupId>com.github.dikako</groupId>
    <artifactId>appium-locator-healer</artifactId>
    <version>v0.0.11</version>
</dependency>
```

### Gradle
##### Add it in your root settings.gradle at the end of repositories:
```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

#### Add to dependencies
```groovy
dependencies {
    implementation 'com.github.dikako:appium-locator-healer:v0.0.11'
}
```

## Quick Start
1. Initialize your WebDriver as usual.
2. Wrap your element action with the healing helper.

### Example (click with healing)
```java
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import java.util.function.Function;

By locator = By.cssSelector("[data-test='login-button']");

String result = Healing.performWithHealing(
  Platform.IOS,                   // or ANDROID, WEB
  "gemini-2.5-flash",             // model name/identifier used by your healer
  driver,
  locator,
  "Login Button",                 // helpful UI label for context
  15,                             // timeout for model response (seconds)
  (Function<By, String>) by -> {  // action to perform on the element
    driver.findElement(by).click();
    return "clicked";
  }
);
```
You can wrap any action returning any type:
* Clicks, sendKeys, getText, getAttribute, complex flows
* Page object methods using a By locator
* Custom conditions/actions

## How it works
* First attempt: runs your provided action with the original locator.
* If it fails: logs the error, checks healing cache.
  * If a cached healed locator exists: retries with it; if it now fails, cache entry is removed.
  * If not cached: invokes the healing strategy to compute a new locator, retries, and caches on success.

The cache improves performance and consistency across subsequent steps or runs.

## API overview
* performWithHealing(Platform platform, String model, WebDriver driver, By elementLocator, String uiLabel, int timeoutSeconds, Function<By, T> action): T
  * Wraps any element action. If the original locator fails, attempts a healed locator and retries.

## Page Object integration
Wrap element actions inside page object methods:
```java
public class LoginPage {
  private final WebDriver driver;
  private final By username = By.id("user");
  private final By password = By.id("pass");
  private final By submit = By.cssSelector("[data-test='login-button']");

  public LoginPage(WebDriver driver) { this.driver = driver; }

  public void login(String u, String p) {
    Healing.performWithHealing(Platform.IOS, "gemini-2.5-flash", driver, username, "Username Field", 10, by -> {
      driver.findElement(by).clear();
      driver.findElement(by).sendKeys(u);
      return null;
    });

    Healing.performWithHealing(Platform.IOS, "gemini-2.5-flash", driver, password, "Password Field", 10, by -> {
      driver.findElement(by).clear();
      driver.findElement(by).sendKeys(p);
      return null;
    });

    Healing.performWithHealing(Platform.IOS, "gemini-2.5-flash", driver, submit, "Login Button", 10, by -> {
      driver.findElement(by).click();
      return null;
    });
  }
}
```

## Healer strategy
The default healing flow:
* Collects failure context (original By, error message, UI label, platform)
* Requests a healed locator from the model/strategy using the provided model identifier and timeout
* Validates by retrying the action; on success, caches the healed locator

You can swap or extend the strategy by:
* Providing a custom model adapter
* Tuning platform hints and UI labels for better proposals
* Managing cache policies (size, TTL)

## Best practices
* Always pass a meaningful uiLabel (e.g., “Login Button”) to improve healing quality.
* Prefer resilient base locators (data-test attributes, accessibility ids).
* Keep timeouts reasonable; healing should be a fast fallback, not the primary path.
* Monitor logs to identify frequently-healed elements and fix at source when needed.
* Store the healing cache between runs if your CI benefits from cross-run stability.

## Contributing
* Fork, create a feature branch, add tests, open a PR.
* Follow conventional commits and ensure spotless/format rules pass.
