import java.io.File

plugins {
  java
  `java-test-fixtures`
  `maven-publish`
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
  withSourcesJar()
  withJavadocJar()
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(24))
  }
}

repositories {
  mavenCentral()
}

dependencies {
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.assertj.core)
  testImplementation(libs.slf4j.api)
  testRuntimeOnly(libs.junit.platform.launcher)
  testRuntimeOnly(libs.junit.platform.console.standalone)
  testRuntimeOnly(libs.slf4j.simple)

  // Test fixtures dependencies
  testFixturesImplementation(libs.junit.jupiter)
  testFixturesImplementation(libs.slf4j.api)
}

tasks.test {
  useJUnitPlatform()
}

// Task to copy test dependencies for Docker test
val copyTestDependencies by tasks.registering(Copy::class) {
  from(configurations.testRuntimeClasspath)
  into(layout.buildDirectory.dir("test-dependencies"))
}

// =============================================================================
// Native Library Build Configuration
// =============================================================================

// =============================================================================
// Target Configuration by Host Platform
// =============================================================================
// Targets are grouped by which host platform can build them:
// - Linux host: Linux targets (native + cross), Windows GNU (cross), FreeBSD (cross)
// - macOS host: macOS targets (native)
// - Windows host: Windows MSVC targets (native)

// Format: rustTarget -> Triple(resourceDir, libPrefix, libExtension)

// Targets that can be built on Linux (via cross-compilation)
val linuxHostTargets = mapOf(
  // Linux native/cross targets
  "x86_64-unknown-linux-gnu" to Triple("linux-x86_64", "lib", "so"),
  "i686-unknown-linux-gnu" to Triple("linux-x86", "lib", "so"),
  "aarch64-unknown-linux-gnu" to Triple("linux-aarch64", "lib", "so"),
  "armv5te-unknown-linux-gnueabi" to Triple("linux-arm", "lib", "so"),
  "arm-unknown-linux-gnueabi" to Triple("linux-arm", "lib", "so"),
  "arm-unknown-linux-gnueabihf" to Triple("linux-armhf", "lib", "so"),
  "armv7-unknown-linux-gnueabihf" to Triple("linux-armv7hf", "lib", "so"),
  // FreeBSD cross-compilation
  "x86_64-unknown-freebsd" to Triple("freebsd-x86_64", "lib", "so"),
)

// Windows targets using GNU toolchain (can be cross-compiled from Linux)
// Not included in CI - use MSVC builds from Windows runner instead
val windowsGnuTargets = mapOf(
  "x86_64-pc-windows-gnu" to Triple("windows-x86_64", "", "dll"),
  "i686-pc-windows-gnu" to Triple("windows-x86", "", "dll"),
)

// Targets that require macOS host
val macosHostTargets = mapOf(
  "aarch64-apple-darwin" to Triple("darwin-aarch64", "lib", "dylib"),
  "x86_64-apple-darwin" to Triple("darwin-x86_64", "lib", "dylib"),
)

// Targets that require Windows host (MSVC toolchain)
val windowsHostTargets = mapOf(
  "x86_64-pc-windows-msvc" to Triple("windows-x86_64", "", "dll"),
  "i686-pc-windows-msvc" to Triple("windows-x86", "", "dll"),
)

// Combined map of all targets (includes GNU for manual builds)
val rustTargets = linuxHostTargets + macosHostTargets + windowsHostTargets + windowsGnuTargets

// Detect the host Rust target
val hostRustTarget: String by lazy {
  try {
    val process = ProcessBuilder("rustc", "--version", "--verbose").start()
    val output = process.inputStream.bufferedReader().readText()
    process.waitFor()
    output.lines().find { it.startsWith("host:") }?.substringAfter("host:")?.trim() ?: ""
  } catch (e: Exception) {
    ""
  }
}

// Read extra targets from extra-target-architectures.txt (not checked in)
// Format: one Rust target per line, e.g., "armv5te-unknown-linux-gnueabi"
val localTargetsFile = file("extra-target-architectures.txt")
val localTargets: List<String> by lazy {
  if (localTargetsFile.exists()) {
    localTargetsFile.readLines()
      .map { it.trim() }
      .filter { it.isNotEmpty() && !it.startsWith("#") }
  } else {
    emptyList()
  }
}

// Find a binary (check common locations)
// Prefer full paths because Gradle daemon may have limited PATH
fun findBinary(name: String, additionalPaths: List<String> = emptyList()): String? {
  val possiblePaths = listOf(
    "${System.getProperty("user.home")}/.cargo/bin/$name",  // Cargo install location (preferred)
    "/usr/local/bin/$name",
    "/opt/homebrew/bin/$name",
  ) + additionalPaths + listOf(name)  // Fallback to PATH

  for (path in possiblePaths) {
    val file = File(path)
    if (file.isAbsolute && file.exists() && file.canExecute()) {
      return path
    }
    // For non-absolute paths, try to execute
    if (!file.isAbsolute) {
      try {
        val process = ProcessBuilder(path, "--version").start()
        if (process.waitFor() == 0) {
          return path
        }
      } catch (e: Exception) {
        // Try next path
      }
    }
  }
  return null
}

// Find the cross binary
fun findCrossBinary(): String? = findBinary("cross")

// Find the cargo binary
fun findCargoBinary(): String = findBinary("cargo") ?: "cargo"

// Check if cross-compilation tool is available
fun isCrossAvailable(): Boolean {
  return findCrossBinary() != null
}

// Determine if a target requires the 'cross' tool for cross-compilation
// Note: macOS and Windows can natively cross-compile between their own architectures
fun requiresCross(target: String): Boolean {
  if (target == hostRustTarget) return false
  // macOS can cross-compile between darwin architectures without 'cross'
  if (hostRustTarget.contains("apple-darwin") && target.contains("apple-darwin")) return false
  // Windows can cross-compile between windows-msvc architectures without 'cross'
  if (hostRustTarget.contains("windows-msvc") && target.contains("windows-msvc")) return false
  return true
}

// Get library info for a target (supports custom targets)
fun getLibraryInfo(target: String): Triple<String, String, String> {
  return rustTargets[target] ?: run {
    // Infer settings for custom targets
    val resourceDir = when {
      target.contains("apple-darwin") && target.contains("aarch64") -> "darwin-aarch64"
      target.contains("apple-darwin") -> "darwin-x86_64"
      target.contains("linux") && target.contains("aarch64") -> "linux-aarch64"
      target.contains("linux") && target.startsWith("armv7") && target.contains("hf") -> "linux-armv7hf"
      target.contains("linux") && target.startsWith("arm") && target.contains("hf") -> "linux-armhf"
      target.contains("linux") && target.startsWith("arm") -> "linux-arm"
      target.contains("linux") -> "linux-x86_64"
      target.contains("windows") -> "windows-x86_64"
      else -> target.replace("-", "_")
    }
    val libPrefix = if (target.contains("windows")) "" else "lib"
    val libExtension = when {
      target.contains("windows") -> "dll"
      target.contains("apple") -> "dylib"
      else -> "so"
    }
    Triple(resourceDir, libPrefix, libExtension)
  }
}

// Create build task for a specific Rust target
fun createBuildTask(target: String): TaskProvider<Exec> {
  val taskName = "buildNative_${target.replace("-", "_")}"
  return tasks.register<Exec>(taskName) {
    group = "native"
    description = "Build native library for $target"
    workingDir = file("native")

    val useCross = requiresCross(target)
    val tool = if (useCross) {
      findCrossBinary() ?: "cross"
    } else {
      findCargoBinary()
    }

    commandLine = listOf(tool, "build", "--release", "--target", target)

    doFirst {
      if (useCross && !isCrossAvailable()) {
        throw GradleException(
          "Cross-compilation required for $target but 'cross' is not installed.\n" +
          "Install with: cargo install cross --git https://github.com/cross-rs/cross"
        )
      }
      println("Building native library for $target using $tool...")
    }
  }
}

// Create copy task for a specific Rust target
fun createCopyTask(target: String, buildTask: TaskProvider<Exec>): TaskProvider<Copy> {
  val (resourceDir, libPrefix, libExtension) = getLibraryInfo(target)
  val taskName = "copyNative_${target.replace("-", "_")}"

  return tasks.register<Copy>(taskName) {
    group = "native"
    description = "Copy native library for $target to resources"
    dependsOn(buildTask)

    from("native/target/$target/release") {
      include("${libPrefix}jrserial.${libExtension}")
    }
    into("src/main/resources/native/$resourceDir")

    doFirst {
      println("Copying $target library to resources/native/$resourceDir")
    }
  }
}

// Register tasks for all predefined targets, grouped by host platform
val linuxHostCopyTasks = mutableListOf<TaskProvider<Copy>>()
val macosHostCopyTasks = mutableListOf<TaskProvider<Copy>>()
val windowsHostCopyTasks = mutableListOf<TaskProvider<Copy>>()

linuxHostTargets.keys.forEach { target ->
  val buildTask = createBuildTask(target)
  val copyTask = createCopyTask(target, buildTask)
  linuxHostCopyTasks.add(copyTask)
}

macosHostTargets.keys.forEach { target ->
  val buildTask = createBuildTask(target)
  val copyTask = createCopyTask(target, buildTask)
  macosHostCopyTasks.add(copyTask)
}

windowsHostTargets.keys.forEach { target ->
  val buildTask = createBuildTask(target)
  val copyTask = createCopyTask(target, buildTask)
  windowsHostCopyTasks.add(copyTask)
}

// Register tasks for Windows GNU targets (for manual cross-compilation from Linux, not used in CI)
windowsGnuTargets.keys.forEach { target ->
  createBuildTask(target)
  createCopyTask(target, tasks.named<Exec>("buildNative_${target.replace("-", "_")}"))
}

val allCopyTasks = linuxHostCopyTasks + macosHostCopyTasks + windowsHostCopyTasks

// Aggregate tasks for CI - build targets by host platform
val buildLinuxHostTargets by tasks.registering {
  group = "native"
  description = "Build all targets that can be built on Linux (Linux + FreeBSD)"
  dependsOn(linuxHostCopyTasks)
}

val buildMacosHostTargets by tasks.registering {
  group = "native"
  description = "Build all targets that require macOS host (darwin-*)"
  dependsOn(macosHostCopyTasks)
}

val buildWindowsHostTargets by tasks.registering {
  group = "native"
  description = "Build all targets that require Windows host (MSVC)"
  dependsOn(windowsHostCopyTasks)
}


// Task to build native library for the host platform (default behavior)
val buildRustLibrary by tasks.registering(Exec::class) {
  group = "native"
  description = "Build native library for the host platform"
  workingDir = file("native")
  commandLine = listOf(findCargoBinary(), "build", "--release")

  doFirst {
    println("Building Rust native library for host ($hostRustTarget)...")
  }
}

// Task to copy native library for the host platform
val copyNativeLibraries by tasks.registering(Copy::class) {
  group = "native"
  description = "Copy native library for host platform (and local targets) to resources"
  dependsOn(buildRustLibrary)

  val (resourceDir, libPrefix, libExtension) = getLibraryInfo(hostRustTarget)

  from("native/target/release") {
    include("${libPrefix}jrserial.${libExtension}")
  }
  into("src/main/resources/native/$resourceDir")

  // Also depend on copy tasks for any local targets
  doFirst {
    if (localTargets.isNotEmpty()) {
      println("Local targets configured: ${localTargets.joinToString(", ")}")
    }
  }
}

// Register copy tasks for local targets and make copyNativeLibraries depend on them
localTargets.forEach { target ->
  // Skip if target already has tasks registered (predefined targets)
  val copyTaskName = "copyNative_${target.replace("-", "_")}"
  val copyTask = if (tasks.names.contains(copyTaskName)) {
    tasks.named<Copy>(copyTaskName)
  } else {
    val buildTask = createBuildTask(target)
    createCopyTask(target, buildTask)
  }
  tasks.named("copyNativeLibraries") {
    dependsOn(copyTask)
  }
}

// Task to build a custom target specified via -PrustTarget=xxx
if (project.hasProperty("rustTarget")) {
  val customTarget = project.property("rustTarget") as String

  val customBuildTask = tasks.register<Exec>("buildNativeCustom") {
    group = "native"
    description = "Build native library for custom target: $customTarget"
    workingDir = file("native")

    val useCross = requiresCross(customTarget)
    val tool = if (useCross) {
      findCrossBinary() ?: "cross"
    } else {
      findCargoBinary()
    }

    commandLine = listOf(tool, "build", "--release", "--target", customTarget)

    doFirst {
      if (useCross && !isCrossAvailable()) {
        throw GradleException(
          "Cross-compilation required for $customTarget but 'cross' is not installed.\n" +
          "Install with: cargo install cross --git https://github.com/cross-rs/cross"
        )
      }
      println("Building native library for custom target $customTarget using $tool...")
    }
  }

  val (resourceDir, libPrefix, libExtension) = getLibraryInfo(customTarget)

  tasks.register<Copy>("copyNativeCustom") {
    group = "native"
    description = "Copy native library for custom target: $customTarget"
    dependsOn(customBuildTask)

    from("native/target/$customTarget/release") {
      include("${libPrefix}jrserial.${libExtension}")
    }
    into("src/main/resources/native/$resourceDir")

    doFirst {
      println("Copying custom target $customTarget library to resources/native/$resourceDir")
    }
  }
}

// =============================================================================
// Standard Build Integration
// =============================================================================

tasks.processResources {
  dependsOn(copyNativeLibraries)
}

tasks.jar {
  dependsOn(copyNativeLibraries)
}

tasks.named("sourcesJar") {
  dependsOn(copyNativeLibraries)
}

// =============================================================================
// JAR variants (full, lite, per-platform)
// =============================================================================

// Collect all platform directories for per-platform JARs
val allPlatformDirs = (linuxHostTargets + macosHostTargets + windowsHostTargets)
  .values.map { it.first }.distinct()

// Lite JAR: No native binaries included
val liteJar by tasks.registering(Jar::class) {
  group = "build"
  description = "Create JAR without native binaries (user must provide their own)"
  archiveClassifier.set("lite")
  from(sourceSets.main.get().output)
  exclude("native/**")
}

// Per-platform JARs: one native binary each
val platformJarTasks = allPlatformDirs.associateWith { platformDir ->
  tasks.register<Jar>("${platformDir.replace("-", "")}Jar") {
    group = "build"
    description = "Create JAR with only $platformDir native binary"
    archiveClassifier.set(platformDir)
    from(sourceSets.main.get().output) {
      exclude("native/**")
    }
    from("src/main/resources/native/$platformDir") {
      into("native/$platformDir")
    }
  }
}

// Task to build all JAR variants
val buildAllJars by tasks.registering {
  group = "build"
  description = "Build all JAR variants (full, lite, and per-platform)"
  dependsOn(tasks.jar)
  dependsOn(liteJar)
  dependsOn(platformJarTasks.values)
}

// =============================================================================
// Publishing
// =============================================================================

publishing {
  publications {
    create<MavenPublication>("maven") {
      from(components["java"])

      // Add lite JAR as additional artifact with classifier
      artifact(liteJar)

      // Add per-platform JARs as additional artifacts with classifiers
      platformJarTasks.values.forEach { jarTask ->
        artifact(jarTask)
      }

      pom {
        name.set("JR-Serial")
        description.set("Java serial communication library with Rust native backend")
        url.set("https://github.com/nemecec/jr-serial")

        licenses {
          license {
            name.set("Apache License, Version 2.0")
            url.set("https://www.apache.org/licenses/LICENSE-2.0")
          }
        }

        developers {
          developer {
            id.set("nemecec")
            name.set("Neeme Praks")
          }
        }
      }
    }
  }
}
