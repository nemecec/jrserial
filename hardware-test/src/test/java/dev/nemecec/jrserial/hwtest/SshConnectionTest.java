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
package dev.nemecec.jrserial.hwtest;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Simple test to verify SSH connection and file upload work correctly.
 * Run with: ./gradlew :hardware-test:test -DhardwareTest=true --tests SshConnectionTest
 */
class SshConnectionTest {

  private static final Logger LOG = LoggerFactory.getLogger(SshConnectionTest.class);
  private static final String CONFIG_FILE = "rs485-test-config.yaml";

  @Test
  void testSshConnection() throws Exception {
    assumeTrue(
        "true".equals(System.getProperty("hardwareTest")),
        "Hardware tests disabled. Enable with -DhardwareTest=true"
    );

    TestConfig config = loadConfiguration();

    // Test all configured machines
    for (TestConfig.MachineConfig machineConfig : config.getMachines()) {
      testMachineConnection(config, machineConfig);
    }
  }

  private void testMachineConnection(TestConfig config, TestConfig.MachineConfig machineConfig) throws Exception {
    LOG.info("Testing SSH connection to: {}", machineConfig.getName());
    LOG.info("  Host: {}", machineConfig.getHost());
    LOG.info("  Jump host: {}", machineConfig.getJumpHost());
    LOG.info("  Use agent: {}", machineConfig.isUseAgent());
    LOG.info("  Use SCP: {}", machineConfig.isUseScp());

    try (SshExecutor executor = new SshExecutor(machineConfig, config)) {
      // Test connection
      executor.connect();
      assertThat(executor.isConnected()).isTrue();
      LOG.info("SSH connection successful!");

      // Test command execution (use echo which is always available)
      SshExecutor.CommandResult result = executor.executeCommand("echo 'SSH_OK'", 10);
      LOG.info("Remote echo result: {}", result.stdout.trim());
      assertThat(result.exitCode).isEqualTo(0);
      assertThat(result.stdout.trim()).isEqualTo("SSH_OK");

      // Test Java availability
      LOG.info("Checking Java at: {}", config.getJavaExecutable());
      boolean javaAvailable = executor.checkJavaAvailable();
      LOG.info("Java available: {}", javaAvailable);
      if (!javaAvailable) {
        // Try to find Java location
        SshExecutor.CommandResult whichResult = executor.executeCommand("which java 2>/dev/null || ls -la /sympower/jvm/bin/ 2>/dev/null || echo 'Java not found'", 10);
        LOG.warn("Java lookup result: {}", whichResult.stdout.trim());
      }
      assertThat(javaAvailable).as("Java must be available on " + machineConfig.getName()).isTrue();

      // Test file upload
      File testJar = findTestJar(config);
      if (testJar != null) {
        LOG.info("Testing file upload: {} ({} bytes)", testJar.getName(), testJar.length());
        executor.uploadFile(testJar, config.getRemoteWorkDir());

        // Verify file exists on remote
        SshExecutor.CommandResult lsResult = executor.executeCommand(
            "ls -la " + config.getRemoteWorkDir() + "/" + testJar.getName(), 10);
        LOG.info("Remote file listing:\n{}", lsResult.stdout);
        assertThat(lsResult.exitCode).isEqualTo(0);
        assertThat(lsResult.stdout).contains(testJar.getName());

        LOG.info("File upload successful!");
      } else {
        LOG.warn("Test JAR not found, skipping upload test. Build with: ./gradlew :hardware-test:testAppJar");
      }

      LOG.info("All tests passed for {}!", machineConfig.getName());
    }
  }

  private TestConfig loadConfiguration() throws Exception {
    String configPath = System.getProperty("rs485.config");
    if (configPath != null) {
      File configFile = new File(configPath);
      if (configFile.exists()) {
        LOG.info("Loading config from: {}", configPath);
        return TestConfig.load(configFile);
      }
    }

    try (InputStream is = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
      if (is != null) {
        LOG.info("Loading config from classpath: {}", CONFIG_FILE);
        return TestConfig.load(is);
      }
    }

    throw new IllegalStateException(
        "Configuration file not found. Create src/test/resources/" + CONFIG_FILE
    );
  }

  private File findTestJar(TestConfig config) {
    LOG.info("Working directory: {}", new File(".").getAbsolutePath());

    if (config.getTestJarPath() != null) {
      File path = new File(config.getTestJarPath());
      LOG.info("Checking config path: {} (exists: {}, isDir: {})",
          path.getAbsolutePath(), path.exists(), path.isDirectory());
      if (path.isDirectory()) {
        File[] jars = path.listFiles((dir, name) ->
            name.startsWith("rs485-test-app") && name.endsWith("-all.jar"));
        if (jars != null && jars.length > 0) {
          LOG.debug("Found JAR: {}", jars[0].getAbsolutePath());
          return jars[0];
        }
      } else if (path.isFile()) {
        return path;
      }
    }

    File defaultDir = new File("hardware-test/build/libs");
    LOG.debug("Checking default path: {} (exists: {}, isDir: {})",
        defaultDir.getAbsolutePath(), defaultDir.exists(), defaultDir.isDirectory());
    if (defaultDir.isDirectory()) {
      File[] jars = defaultDir.listFiles((dir, name) ->
          name.startsWith("rs485-test-app") && name.endsWith("-all.jar"));
      if (jars != null && jars.length > 0) {
        LOG.debug("Found JAR: {}", jars[0].getAbsolutePath());
        return jars[0];
      }
    }

    return null;
  }
}
