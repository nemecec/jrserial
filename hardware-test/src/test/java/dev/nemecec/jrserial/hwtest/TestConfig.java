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
package dev.nemecec.jrserial.hwtest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Configuration model for RS-485 hardware tests.
 * Loaded from YAML configuration file.
 */
public class TestConfig {

  @JsonProperty("machines")
  private List<MachineConfig> machines;

  @JsonProperty("testJarPath")
  private String testJarPath;

  @JsonProperty("remoteWorkDir")
  private String remoteWorkDir = "/tmp/rs485-test";

  @JsonProperty("testTimeoutSeconds")
  private int testTimeoutSeconds = 30;

  @JsonProperty("javaExecutable")
  private String javaExecutable = "java";

  public List<MachineConfig> getMachines() {
    return machines;
  }

  public void setMachines(List<MachineConfig> machines) {
    this.machines = machines;
  }

  public String getTestJarPath() {
    return testJarPath;
  }

  public void setTestJarPath(String testJarPath) {
    this.testJarPath = testJarPath;
  }

  public String getRemoteWorkDir() {
    return remoteWorkDir;
  }

  public void setRemoteWorkDir(String remoteWorkDir) {
    this.remoteWorkDir = remoteWorkDir;
  }

  public int getTestTimeoutSeconds() {
    return testTimeoutSeconds;
  }

  public void setTestTimeoutSeconds(int testTimeoutSeconds) {
    this.testTimeoutSeconds = testTimeoutSeconds;
  }

  public String getJavaExecutable() {
    return javaExecutable;
  }

  public void setJavaExecutable(String javaExecutable) {
    this.javaExecutable = javaExecutable;
  }

  /**
   * Get sender machine configuration.
   */
  public MachineConfig getSender() {
    return machines.stream()
        .filter(m -> m.getRole() == MachineRole.SENDER || m.getRole() == MachineRole.BOTH)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No sender machine configured"));
  }

  /**
   * Get receiver machine configuration.
   */
  public MachineConfig getReceiver() {
    return machines.stream()
        .filter(m -> m.getRole() == MachineRole.RECEIVER || m.getRole() == MachineRole.BOTH)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No receiver machine configured"));
  }

  /**
   * Load configuration from a YAML file.
   */
  public static TestConfig load(File file) throws IOException {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    return mapper.readValue(file, TestConfig.class);
  }

  /**
   * Load configuration from an input stream.
   */
  public static TestConfig load(InputStream inputStream) throws IOException {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    return mapper.readValue(inputStream, TestConfig.class);
  }

  /**
   * Configuration for a single test machine.
   */
  public static class MachineConfig {

    @JsonProperty("name")
    private String name;

    @JsonProperty("host")
    private String host;

    @JsonProperty("port")
    private int port = 22;

    @JsonProperty("username")
    private String username;

    @JsonProperty("password")
    private String password;

    @JsonProperty("privateKeyPath")
    private String privateKeyPath;

    @JsonProperty("useAgent")
    private boolean useAgent = false;

    @JsonProperty("jumpHost")
    private String jumpHost;

    @JsonProperty("jumpPort")
    private int jumpPort = 22;

    @JsonProperty("jumpUsername")
    private String jumpUsername;

    @JsonProperty("useScp")
    private boolean useScp = false;

    @JsonProperty("serialPort")
    private String serialPort;

    @JsonProperty("role")
    private MachineRole role = MachineRole.BOTH;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getHost() {
      return host;
    }

    public void setHost(String host) {
      this.host = host;
    }

    public int getPort() {
      return port;
    }

    public void setPort(int port) {
      this.port = port;
    }

    public String getUsername() {
      return username;
    }

    public void setUsername(String username) {
      this.username = username;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(String password) {
      this.password = password;
    }

    public String getPrivateKeyPath() {
      return privateKeyPath;
    }

    public void setPrivateKeyPath(String privateKeyPath) {
      this.privateKeyPath = privateKeyPath;
    }

    public String getSerialPort() {
      return serialPort;
    }

    public void setSerialPort(String serialPort) {
      this.serialPort = serialPort;
    }

    public MachineRole getRole() {
      return role;
    }

    public void setRole(MachineRole role) {
      this.role = role;
    }

    /**
     * Check if this machine uses password authentication.
     */
    public boolean usesPasswordAuth() {
      return password != null && !password.isEmpty();
    }

    /**
     * Check if this machine uses key-based authentication.
     */
    public boolean usesKeyAuth() {
      return privateKeyPath != null && !privateKeyPath.isEmpty();
    }

    public boolean isUseAgent() {
      return useAgent;
    }

    public void setUseAgent(boolean useAgent) {
      this.useAgent = useAgent;
    }

    public String getJumpHost() {
      return jumpHost;
    }

    public void setJumpHost(String jumpHost) {
      this.jumpHost = jumpHost;
    }

    public int getJumpPort() {
      return jumpPort;
    }

    public void setJumpPort(int jumpPort) {
      this.jumpPort = jumpPort;
    }

    public String getJumpUsername() {
      return jumpUsername;
    }

    public void setJumpUsername(String jumpUsername) {
      this.jumpUsername = jumpUsername;
    }

    public boolean isUseScp() {
      return useScp;
    }

    public void setUseScp(boolean useScp) {
      this.useScp = useScp;
    }

    /**
     * Check if this machine requires a jump host.
     */
    public boolean hasJumpHost() {
      return jumpHost != null && !jumpHost.isEmpty();
    }

    /**
     * Get the resolved private key path (expands ~).
     */
    public String getResolvedPrivateKeyPath() {
      if (privateKeyPath == null) {
        return null;
      }
      if (privateKeyPath.startsWith("~")) {
        return System.getProperty("user.home") + privateKeyPath.substring(1);
      }
      return privateKeyPath;
    }
  }

  /**
   * Role of a machine in the test setup.
   */
  public enum MachineRole {
    SENDER,
    RECEIVER,
    BOTH
  }
}
