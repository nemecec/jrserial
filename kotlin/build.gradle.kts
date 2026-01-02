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

// Tests require Java 17+ for JUnit 6
tasks.compileTestJava {
  options.release.set(17)
}

tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileTestKotlin") {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
  }
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
