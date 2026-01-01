/*
 * Copyright 2025 Neeme Praks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.nemecec.jrserial;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;

/**
 * Utility class for loading native libraries.
 *
 * <p>The loader tries to find the native library in the following order:
 * <ol>
 *   <li>Explicit path via system property {@code jrserial.library.path}</li>
 *   <li>System library path ({@code java.library.path})</li>
 *   <li>JAR resources at {@code /native/<platform>/}</li>
 * </ol>
 *
 * <p>Users can override the bundled library by setting {@code jrserial.library.path}:
 * <ul>
 *   <li>{@code -Djrserial.library.path=/path/to/libjrserial.so} - load from filesystem</li>
 *   <li>{@code -Djrserial.library.path=classpath:/custom/libjrserial.so} - load from classpath</li>
 * </ul>
 *
 * <p>Other override options:
 * <ul>
 *   <li>Place library in a directory on {@code java.library.path}</li>
 *   <li>Use {@code -Djrserial.platform=linux-x86_64} to force a specific platform from bundled resources</li>
 * </ul>
 */
class NativeLibraryLoader {

  /** System property to specify explicit library path. */
  public static final String LIBRARY_PATH_PROPERTY = "jrserial.library.path";

  /** System property to specify platform override (e.g., "linux-x86_64"). */
  public static final String PLATFORM_OVERRIDE_PROPERTY = "jrserial.platform";

  /** Prefix for classpath resources in library path. */
  private static final String CLASSPATH_PREFIX = "classpath:";

  public static final String WINDOWS = "windows";
  public static final String X_86_64 = "x86_64";
  public static final String DARWIN = "darwin";
  public static final String MAC = "mac";
  public static final String DARWIN_X_86_64 = "darwin-x86_64";
  public static final String DARWIN_AARCH_64 = "darwin-aarch64";

  private static boolean loaded = false;

  private NativeLibraryLoader() {}

  /**
   * Load the native library for the current platform.
   *
   * @throws UnsatisfiedLinkError if the native library cannot be loaded
   */
  static synchronized void loadLibrary() {
    if (loaded) {
      return;
    }

    // 1. Check for explicit path override
    String explicitPath = System.getProperty(LIBRARY_PATH_PROPERTY);
    if (explicitPath != null && !explicitPath.isEmpty()) {
      loadFromExplicitPath(explicitPath);
      return;
    }

    String osName = System.getProperty("os.name").toLowerCase();
    String osArch = System.getProperty("os.arch").toLowerCase();

    // Allow platform override for testing or custom builds
    String platformOverride = System.getProperty(PLATFORM_OVERRIDE_PROPERTY);
    String platform = platformOverride != null ? platformOverride : getPlatform(osName, osArch);
    String libraryName = getLibraryName(osName);

    // 2. Try system library path first (allows user override)
    if (tryLoadFromSystemPath()) {
      return;
    }

    // 3. Try loading from JAR resources
    String[] platformsToTry = getPlatformsToTry(osName, platform);
    IOException lastException = tryLoadFromResources(platformsToTry, libraryName);

    if (!loaded) {
      throwLoadError(libraryName, platform, platformsToTry, lastException);
    }
  }

  private static void loadFromExplicitPath(String explicitPath) {
    if (explicitPath.startsWith(CLASSPATH_PREFIX)) {
      // Load from classpath
      String resourcePath = explicitPath.substring(CLASSPATH_PREFIX.length());
      loadFromClasspathResource(resourcePath);
    }
    else {
      // Load from filesystem
      loadFromFilesystem(explicitPath);
    }
  }

  private static void loadFromClasspathResource(String resourcePath) {
    // Ensure path starts with /
    if (!resourcePath.startsWith("/")) {
      resourcePath = "/" + resourcePath;
    }

    String libraryName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);

    try {
      loadLibraryFromResource(resourcePath, libraryName);
      loaded = true;
    }
    catch (IOException e) {
      throw new UnsatisfiedLinkError(
          "Failed to load native library from classpath: " + resourcePath + ": " + e.getMessage());
    }
  }

  private static void loadFromFilesystem(String path) {
    File libraryFile = new File(path);
    if (!libraryFile.exists()) {
      throw new UnsatisfiedLinkError(
          "Native library not found at specified path: " + path);
    }
    if (!libraryFile.canRead()) {
      throw new UnsatisfiedLinkError(
          "Native library not readable at specified path: " + path);
    }
    try {
      System.load(libraryFile.getAbsolutePath());
      loaded = true;
    }
    catch (UnsatisfiedLinkError e) {
      throw new UnsatisfiedLinkError(
          "Failed to load native library from " + path + ": " + e.getMessage());
    }
  }

  private static boolean tryLoadFromSystemPath() {
    try {
      System.loadLibrary("jrserial");
      loaded = true;
      return true;
    }
    catch (UnsatisfiedLinkError e) {
      // Not found in system path, continue to JAR resources
      return false;
    }
  }

  private static String[] getPlatformsToTry(String osName, String platform) {
    if (osName.contains(MAC) || osName.contains(DARWIN)) {
      return platform.contains(X_86_64)
          ? new String[] { DARWIN_X_86_64, DARWIN_AARCH_64 }
          : new String[] { DARWIN_AARCH_64, DARWIN_X_86_64 };
    }
    return new String[] { platform };
  }

  private static IOException tryLoadFromResources(String[] platformsToTry, String libraryName) {
    IOException lastException = null;
    for (String plt : platformsToTry) {
      String path = Paths.get("/native").resolve(plt).resolve(libraryName).toString();
      try {
        loadLibraryFromResource(path, libraryName);
        loaded = true;
        break;
      }
      catch (IOException e) {
        lastException = e;
      }
    }
    return lastException;
  }

  private static void throwLoadError(String libraryName, String platform,
      String[] platformsToTry, IOException lastException) {
    String errorMsg = lastException != null ? lastException.getMessage() : "unknown";
    throw new UnsatisfiedLinkError(
        "Failed to load native library: " + libraryName +
        " for platform: " + platform +
        ". Tried: (1) system property " + LIBRARY_PATH_PROPERTY +
        ", (2) java.library.path" +
        ", (3) JAR resources at: " + String.join(", ", platformsToTry) +
        ". Error: " + errorMsg
    );
  }

  // Package-private for testing
  static String getPlatform(String osName, String osArch) {
    String os;
    if (osName.contains(WINDOWS)) {
      os = WINDOWS;
    }
    else if (osName.contains(MAC) || osName.contains(DARWIN)) {
      os = DARWIN;
    }
    else if (osName.contains("linux")) {
      os = "linux";
    }
    else if (osName.contains("freebsd")) {
      os = "freebsd";
    }
    else {
      throw new UnsatisfiedLinkError("Unsupported operating system: " + osName);
    }

    String arch;
    if (osArch.contains("aarch64") || osArch.contains("arm64")) {
      arch = "aarch64";
    }
    else if (osArch.contains("amd64") || osArch.contains(X_86_64)) {
      arch = X_86_64;
    }
    else if (osArch.equals("x86") || osArch.equals("i386") || osArch.equals("i486")
        || osArch.equals("i586") || osArch.equals("i686")) {
      arch = "x86";
    }
    else if (osArch.equals("arm") || osArch.contains("armv5") || osArch.contains("armv6") || osArch.contains("armv7")) {
      // ARM 32-bit (ARMv5, ARMv6, ARMv7 - all use soft-float ABI for maximum compatibility)
      arch = "arm";
    }
    else {
      throw new UnsatisfiedLinkError("Unsupported architecture: " + osArch);
    }

    return os + "-" + arch;
  }

  private static String getLibraryName(String osName) {
    if (osName.contains(WINDOWS)) {
      return "jrserial.dll";
    }
    else if (osName.contains(MAC) || osName.contains(DARWIN)) {
      return "libjrserial.dylib";
    }
    else {
      return "libjrserial.so";
    }
  }

  private static void loadLibraryFromResource(String resourcePath, String libraryName)
      throws IOException {
    InputStream in = NativeLibraryLoader.class.getResourceAsStream(resourcePath);
    if (in == null) {
      throw new IOException("Native library not found in JAR: " + resourcePath);
    }

    // Create temporary file
    File tempFile = File.createTempFile("jrserial", getSuffix(libraryName));
    tempFile.deleteOnExit();

    // Copy library to temporary file
    try (OutputStream out = new FileOutputStream(tempFile)) {
      byte[] buffer = new byte[8192];
      int bytesRead;
      while ((bytesRead = in.read(buffer)) != -1) {
        out.write(buffer, 0, bytesRead);
      }
    }
    finally {
      in.close();
    }

    // Load the library
    System.load(tempFile.getAbsolutePath());
  }

  private static String getSuffix(String libraryName) {
    int dotIndex = libraryName.lastIndexOf('.');
    if (dotIndex >= 0) {
      return libraryName.substring(dotIndex);
    }
    return "";
  }

}
