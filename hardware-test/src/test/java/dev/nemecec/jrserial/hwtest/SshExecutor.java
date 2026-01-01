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

import com.jcraft.jsch.AgentConnector;
import com.jcraft.jsch.AgentIdentityRepository;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.SSHAgentConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;
/**
 * SSH executor for running commands on remote machines using JSch.
 * Supports jump hosts, SSH agent, and SCP file transfer.
 */
public class SshExecutor implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(SshExecutor.class);

  private final TestConfig.MachineConfig config;
  private final TestConfig testConfig;
  private final JSch jsch;
  private Session jumpSession;
  private Session targetSession;

  public SshExecutor(TestConfig.MachineConfig config, TestConfig testConfig) {
    this.config = config;
    this.testConfig = testConfig;
    this.jsch = new JSch();
  }

  /**
   * Connect to the remote machine (possibly through a jump host).
   */
  public void connect() throws JSchException {
    // Configure SSH agent if requested
    if (config.isUseAgent()) {
      try {
        AgentConnector connector = new SSHAgentConnector();
        jsch.setIdentityRepository(new AgentIdentityRepository(connector));
        LOG.info("SSH agent configured");
      } catch (Exception e) {
        LOG.warn("Failed to connect to SSH agent: {}", e.getMessage());
        // Fall back to key file if agent fails
        if (config.usesKeyAuth()) {
          String keyPath = config.getResolvedPrivateKeyPath();
          LOG.info("Falling back to key file: {}", keyPath);
          jsch.addIdentity(keyPath);
        }
      }
    } else if (config.usesKeyAuth()) {
      // Add identity from key file
      String keyPath = config.getResolvedPrivateKeyPath();
      LOG.info("Adding identity: {}", keyPath);
      jsch.addIdentity(keyPath);
    }

    if (config.hasJumpHost()) {
      connectViaJumpHost();
    } else {
      connectDirect();
    }
  }

  private void connectDirect() throws JSchException {
    LOG.info("Connecting directly to {}@{}:{}", config.getUsername(), config.getHost(), config.getPort());

    targetSession = jsch.getSession(config.getUsername(), config.getHost(), config.getPort());
    configureSession(targetSession);

    if (config.usesPasswordAuth()) {
      targetSession.setPassword(config.getPassword());
    }

    targetSession.connect(30000);
    LOG.info("Connected to {}", config.getName());
  }

  private void connectViaJumpHost() throws JSchException {
    String jumpUsername = config.getJumpUsername() != null ? config.getJumpUsername() : config.getUsername();
    LOG.info("Connecting via jump host {}@{}:{}", jumpUsername, config.getJumpHost(), config.getJumpPort());

    // Connect to jump host first
    jumpSession = jsch.getSession(jumpUsername, config.getJumpHost(), config.getJumpPort());
    configureSession(jumpSession);
    jumpSession.connect(30000);
    LOG.info("Connected to jump host");

    // Set up port forwarding through jump host
    int forwardedPort = jumpSession.setPortForwardingL(0, config.getHost(), config.getPort());
    LOG.info("Port forwarding established: localhost:{} -> {}:{}", forwardedPort, config.getHost(), config.getPort());

    // Connect to target through the forwarded port
    targetSession = jsch.getSession(config.getUsername(), "127.0.0.1", forwardedPort);
    configureSession(targetSession);

    if (config.usesPasswordAuth()) {
      targetSession.setPassword(config.getPassword());
    }

    targetSession.connect(30000);
    LOG.info("Connected to {} via jump host", config.getName());
  }

  private void configureSession(Session session) {
    // Disable strict host key checking for test environments
    session.setConfig("StrictHostKeyChecking", "no");
    session.setConfig("PreferredAuthentications", "publickey,keyboard-interactive,password");
  }

  /**
   * Upload a file to the remote machine using SCP or SFTP.
   */
  public void uploadFile(File localFile, String remoteDir) throws JSchException, IOException {
    LOG.info("Uploading {} to {}:{}", localFile.getName(), config.getName(), remoteDir);

    // First create the remote directory
    executeCommand("mkdir -p " + remoteDir, 10);

    String remoteFile = remoteDir + "/" + localFile.getName();

    if (config.isUseScp()) {
      uploadViaScp(localFile, remoteFile);
    } else {
      uploadViaSftp(localFile, remoteDir);
    }

    LOG.info("Upload complete: {}", remoteFile);
  }

  /**
   * Upload a file only if it doesn't exist or has changed (based on MD5 hash).
   * @return true if file was uploaded, false if skipped (already up-to-date)
   */
  public boolean uploadFileIfChanged(File localFile, String remoteDir) throws JSchException, IOException {
    String remoteFile = remoteDir + "/" + localFile.getName();

    // Calculate local MD5
    String localMd5 = calculateMd5(localFile);

    // Get remote MD5 (if file exists)
    String remoteMd5 = getRemoteMd5(remoteFile);

    if (localMd5.equals(remoteMd5)) {
      LOG.info("File {} on {} is up-to-date (MD5: {})", localFile.getName(), config.getName(), localMd5.substring(0, 8));
      return false;
    }

    if (remoteMd5 == null) {
      LOG.info("File {} does not exist on {}, uploading...", localFile.getName(), config.getName());
    } else {
      LOG.info("File {} changed on {} (local: {}, remote: {}), uploading...",
          localFile.getName(), config.getName(), localMd5.substring(0, 8), remoteMd5.substring(0, 8));
    }

    uploadFile(localFile, remoteDir);
    return true;
  }

  /**
   * Calculate MD5 hash of a local file.
   */
  private String calculateMd5(File file) throws IOException {
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      byte[] buffer = new byte[8192];
      try (FileInputStream fis = new FileInputStream(file)) {
        int len;
        while ((len = fis.read(buffer)) > 0) {
          md.update(buffer, 0, len);
        }
      }
      byte[] digest = md.digest();
      StringBuilder sb = new StringBuilder();
      for (byte b : digest) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IOException("MD5 algorithm not available", e);
    }
  }

  /**
   * Get MD5 hash of a remote file, or null if file doesn't exist.
   */
  private String getRemoteMd5(String remoteFile) throws JSchException, IOException {
    // BusyBox md5sum outputs: "hash  filename"
    CommandResult result = executeCommand("md5sum " + remoteFile + " 2>/dev/null", 10);
    if (result.exitCode != 0 || result.stdout.trim().isEmpty()) {
      return null;
    }
    // Extract just the hash (first 32 characters)
    String output = result.stdout.trim();
    if (output.length() >= 32) {
      return output.substring(0, 32).toLowerCase();
    }
    return null;
  }

  /**
   * Delete a directory and all its contents on the remote machine.
   */
  public void deleteDirectory(String remoteDir) throws JSchException, IOException {
    LOG.info("Deleting directory {} on {}", remoteDir, config.getName());
    CommandResult result = executeCommand("rm -rf " + remoteDir, 30);
    if (result.exitCode != 0) {
      LOG.warn("Failed to delete {}: {}", remoteDir, result.stderr);
    }
  }

  private void uploadViaScp(File localFile, String remoteFile) throws JSchException, IOException {
    LOG.debug("Using SCP for file transfer");

    // SCP command: scp -t (sink mode)
    String command = "scp -t " + remoteFile;
    ChannelExec channel = (ChannelExec) targetSession.openChannel("exec");

    try {
      channel.setCommand(command);

      OutputStream out = channel.getOutputStream();
      InputStream in = channel.getInputStream();

      channel.connect();

      // Wait for acknowledgment
      checkAck(in);

      // Send file info: "C0644 filesize filename\n"
      long fileSize = localFile.length();
      String header = "C0644 " + fileSize + " " + localFile.getName() + "\n";
      out.write(header.getBytes());
      out.flush();

      checkAck(in);

      // Send file content
      byte[] buffer = new byte[8192];
      try (FileInputStream fis = new FileInputStream(localFile)) {
        int len;
        while ((len = fis.read(buffer)) > 0) {
          out.write(buffer, 0, len);
        }
      }

      // Send EOF
      out.write(0);
      out.flush();

      checkAck(in);

    } finally {
      channel.disconnect();
    }
  }

  private void checkAck(InputStream in) throws IOException {
    int b = in.read();
    if (b == 0) {
      return; // success
    }
    if (b == -1) {
      throw new IOException("SCP: unexpected end of stream");
    }

    // Error or warning
    StringBuilder sb = new StringBuilder();
    int c;
    while ((c = in.read()) != '\n' && c != -1) {
      sb.append((char) c);
    }

    if (b == 1) { // error
      throw new IOException("SCP error: " + sb.toString());
    }
    if (b == 2) { // fatal error
      throw new IOException("SCP fatal error: " + sb.toString());
    }
  }

  private void uploadViaSftp(File localFile, String remoteDir) throws JSchException, IOException {
    LOG.debug("Using SFTP for file transfer");

    ChannelSftp sftp = (ChannelSftp) targetSession.openChannel("sftp");
    try {
      sftp.connect();
      sftp.cd(remoteDir);

      try (FileInputStream fis = new FileInputStream(localFile)) {
        sftp.put(fis, localFile.getName());
      } catch (SftpException e) {
        throw new IOException("SFTP upload failed: " + e.getMessage(), e);
      }
    } catch (SftpException e) {
      throw new IOException("SFTP error: " + e.getMessage(), e);
    } finally {
      sftp.disconnect();
    }
  }

  /**
   * Execute a command on the remote machine.
   */
  public CommandResult executeCommand(String command, int timeoutSeconds) throws JSchException, IOException {
    ChannelExec channel = (ChannelExec) targetSession.openChannel("exec");
    try {
      channel.setCommand(command);

      ByteArrayOutputStream stdout = new ByteArrayOutputStream();
      ByteArrayOutputStream stderr = new ByteArrayOutputStream();
      channel.setOutputStream(stdout);
      channel.setErrStream(stderr);

      channel.connect();

      long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds);
      while (!channel.isClosed() && System.currentTimeMillis() < deadline) {
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }

      if (!channel.isClosed()) {
        LOG.warn("Command timed out after {} seconds", timeoutSeconds);
      }

      return new CommandResult(
          channel.getExitStatus(),
          stdout.toString("UTF-8"),
          stderr.toString("UTF-8")
      );
    } finally {
      channel.disconnect();
    }
  }

  /**
   * Check if Java is available on the remote machine.
   */
  public boolean checkJavaAvailable() throws JSchException, IOException {
    CommandResult result = executeCommand(testConfig.getJavaExecutable() + " -version", 10);
    return result.exitCode == 0;
  }

  /**
   * Check if the connection is established.
   */
  public boolean isConnected() {
    return targetSession != null && targetSession.isConnected();
  }

  /**
   * Get the target SSH session for port forwarding.
   * Returns null if not connected.
   */
  public Session getTargetSession() {
    return targetSession;
  }

  /**
   * Get the machine configuration.
   */
  public TestConfig.MachineConfig getConfig() {
    return config;
  }

  @Override
  public void close() {
    if (targetSession != null && targetSession.isConnected()) {
      targetSession.disconnect();
      LOG.info("Disconnected from {}", config.getName());
    }
    if (jumpSession != null && jumpSession.isConnected()) {
      jumpSession.disconnect();
      LOG.info("Disconnected from jump host");
    }
  }

  /**
   * Result of a command execution.
   */
  public static class CommandResult {
    public final int exitCode;
    public final String stdout;
    public final String stderr;

    public CommandResult(int exitCode, String stdout, String stderr) {
      this.exitCode = exitCode;
      this.stdout = stdout;
      this.stderr = stderr;
    }
  }
}
