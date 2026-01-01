plugins {
  application
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(24))
  }
}

repositories {
  mavenCentral()
}

dependencies {
  implementation(project(":"))
  implementation(libs.slf4j.api)
  runtimeOnly(libs.slf4j.simple)
}

application {
  mainClass.set("dev.nemecec.jrserial.testapp.TestApp")
}

// Create tasks for each test program
val runLoopback by tasks.registering(JavaExec::class) {
  group = "test-app"
  description = "Run the loopback test program"
  classpath = sourceSets.main.get().runtimeClasspath
  mainClass.set("dev.nemecec.jrserial.testapp.LoopbackTest")
  standardInput = System.`in`
  javaLauncher.set(javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(24))
  })
}

val runInteractive by tasks.registering(JavaExec::class) {
  group = "test-app"
  description = "Run the interactive terminal"
  classpath = sourceSets.main.get().runtimeClasspath
  mainClass.set("dev.nemecec.jrserial.testapp.InteractiveTest")
  standardInput = System.`in`
  javaLauncher.set(javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(24))
  })
}

val runSimple by tasks.registering(JavaExec::class) {
  group = "test-app"
  description = "Run the simple example"
  classpath = sourceSets.main.get().runtimeClasspath
  mainClass.set("dev.nemecec.jrserial.testapp.SimpleExample")
  javaLauncher.set(javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(24))
  })
}

// Task to show available test programs
val listTests by tasks.registering {
  group = "test-app"
  description = "List available test programs"
  doLast {
    println(
      """
        |
        |=== JR-Serial Test Programs ===
        |
        |Available test programs:
        |
        |  ./gradlew :test-app:runLoopback
        |      Automated loopback test for single port or two connected ports
        |      Tests data transmission and validates correctness
        |
        |  ./gradlew :test-app:runInteractive
        |      Interactive serial terminal
        |      Send/receive data manually, useful for testing with devices
        |
        |  ./gradlew :test-app:runSimple
        |      Simple example showing basic port operations
        |      Lists ports and demonstrates basic read/write
        |
        |For more information, see TESTING.md
        |
        """.trimMargin()
    )
  }
}
