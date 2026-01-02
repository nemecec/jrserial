/*
 * Copyright (C) 2026 Neeme Praks
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

plugins {
  java
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(24))
  }
}

// Main code targets Java 8
tasks.compileJava {
  options.release.set(8)
}

// Tests require Java 17+ for JUnit 6
tasks.compileTestJava {
  options.release.set(17)
}

// Configure test configurations to resolve Java 17 compatible dependencies (for JUnit 6)
configurations {
  testCompileClasspath {
    attributes { attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 17) }
  }
  testRuntimeClasspath {
    attributes { attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 17) }
  }
}

repositories {
  mavenCentral()
}

dependencies {
  // Main library dependency (for Rs485TestApp)
  implementation(project(":"))

  // JSON serialization (for TestResult, used by Rs485TestApp)
  implementation(libs.jackson.databind)

  // Logging
  implementation(libs.slf4j.api)
  runtimeOnly(libs.slf4j.simple)

  // Testing - JUnit orchestrator dependencies
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.assertj.core)
  testImplementation(libs.jsch)                    // SSH connectivity (SshExecutor)
  testImplementation(libs.jackson.dataformat.yaml) // YAML config parsing (TestConfig)
  testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
  useJUnitPlatform()

  // Hardware tests are disabled by default
  // Enable with: ./gradlew :hardware-test:test -DhardwareTest=true
  val hardwareTestEnabled = System.getProperty("hardwareTest") == "true"

  onlyIf {
    hardwareTestEnabled
  }

  // Pass system properties to the test JVM
  if (hardwareTestEnabled) {
    systemProperty("hardwareTest", "true")
    listOf("rs485.config", "rs485.testJar").forEach { prop ->
      System.getProperty(prop)?.let { systemProperty(prop, it) }
    }
  }

  // Show test output
  testLogging {
    events("passed", "skipped", "failed")
    showStandardStreams = true
  }
}

// Task to create a fat JAR with the test application for deployment to remote machines
val testAppJar by tasks.registering(Jar::class) {
  group = "build"
  description = "Create test application JAR for remote deployment"
  archiveBaseName.set("rs485-test-app")
  archiveClassifier.set("all")

  manifest {
    attributes(
      "Main-Class" to "dev.nemecec.jrserial.hwtest.Rs485TestApp"
    )
  }

  // Include all dependencies
  from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
  from(sourceSets.main.get().output)

  // Include the main library's native resources
  from(project(":").file("src/main/resources"))

  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Task to run hardware tests
val runHardwareTest by tasks.registering(Test::class) {
  group = "verification"
  description = "Run RS-485 hardware tests against remote machines"
  useJUnitPlatform()

  systemProperty("hardwareTest", "true")

  // Pass through any configuration properties
  listOf("rs485.config", "rs485.testJar").forEach { prop ->
    System.getProperty(prop)?.let { systemProperty(prop, it) }
  }
}
