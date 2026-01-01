plugins {
  alias(libs.plugins.kotlin.jvm)
  `maven-publish`
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
  jvmToolchain(24)
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
  }
}

repositories {
  mavenCentral()
}

dependencies {
  api(project(":"))
  implementation(libs.kotlinx.coroutines.core)

  testImplementation(libs.junit.jupiter)
  testImplementation(libs.assertj.core)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(testFixtures(project(":")))
  testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
  useJUnitPlatform()
}
