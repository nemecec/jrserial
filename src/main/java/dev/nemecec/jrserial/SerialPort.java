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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A serial port for communicating with serial devices.
 * <p>
 * This class provides a high-level API for serial communication, with the actual implementation handled by native Rust
 * code via JNI.
 * <p>
 * <b>Thread Safety:</b> This class is NOT thread-safe. A single SerialPort instance should only be accessed from one
 * thread at a time. If you need to access the port from multiple threads, you must provide your own synchronization.
 * <p>
 * Example usage:
 * <pre>
 * SerialPort port = SerialPort.builder()
 *     .portName("/dev/ttyUSB0")
 *     .baudRate(9600)
 *     .build();
 *
 * try {
 *     port.open();
 *     port.write("Hello, World!".getBytes());
 *     byte[] buffer = new byte[256];
 *     int bytesRead = port.read(buffer);
 * } finally {
 *     port.close();
 * }
 * </pre>
 */
public class SerialPort implements Closeable {

  private final String portName;
  private final int baudRate;
  private final DataBits dataBits;
  private final StopBits stopBits;
  private final Parity parity;
  private final FlowControl flowControl;
  private final boolean dtrOnOpen;
  private final int timeoutMs;
  private final Rs485Config rs485Config;

  private long handle;
  private boolean isOpen;
  private SerialInputStream cachedInputStream;
  private SerialOutputStream cachedOutputStream;

  /**
   * Create an IOException with native error context if available.
   *
   * @param message the base error message
   * @return an IOException with native error details appended if available
   */
  private static IOException createIOException(String message) {
    String nativeError = NativeSerialPort.getLastError();
    if (nativeError != null) {
      NativeSerialPort.clearLastError();
      return new IOException(message + ": " + nativeError);
    }
    return new IOException(message);
  }

  private SerialPort(Builder builder) {
    this.portName = builder.portName;
    this.baudRate = builder.baudRate;
    this.dataBits = builder.dataBits;
    this.stopBits = builder.stopBits;
    this.parity = builder.parity;
    this.flowControl = builder.flowControl;
    this.dtrOnOpen = builder.dtrOnOpen;
    this.timeoutMs = builder.timeoutMs;
    this.rs485Config = builder.rs485Config;
    this.handle = 0;
    this.isOpen = false;
  }

  /**
   * Create a new builder for configuring a serial port.
   *
   * @return a new builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * List all available serial ports on the system with detailed information.
   *
   * <p>Returns information about each port including whether it is a symbolic link
   * or pseudo-terminal (PTY) device. This helps identify ports that may not support
   * hardware flow control.
   *
   * @return a list of port information objects
   */
  public static List<SerialPortInfo> listPorts() {
    String data = NativeSerialPort.listPorts();
    if (data == null || data.isEmpty()) {
      return Collections.emptyList();
    }

    // Parse tab-separated format: name\tsymlink\tpty\tbluetooth per line
    List<SerialPortInfo> ports = new ArrayList<>();
    for (String line : data.split("\n")) {
      String[] parts = line.split("\t");
      if (parts.length >= 4) {
        String name = parts[0];
        boolean symlink = "1".equals(parts[1]);
        boolean pty = "1".equals(parts[2]);
        boolean bluetooth = "1".equals(parts[3]);
        ports.add(new SerialPortInfo(name, symlink, pty, bluetooth));
      }
    }
    return ports;
  }

  /**
   * Open the serial port.
   *
   * @throws IOException if the port cannot be opened
   */
  public void open() throws IOException {
    if (isOpen) {
      throw new IOException("Port is already open");
    }

    // Determine RS-485 settings from config (or use defaults if not set)
    int rs485ModeValue = 0;  // NONE
    int rs485PinValue = 0;   // RTS
    boolean rtsActiveHigh = true;
    boolean rxDuringTx = false;
    boolean terminationEnabled = false;
    int delayBeforeMicros = 0;
    int delayAfterMicros = 0;

    if (rs485Config != null) {
      rs485ModeValue = rs485Config.isEnabled() ? 1 : 0;  // AUTO or NONE
      rs485PinValue = rs485Config.getControlPin().getValue();
      rtsActiveHigh = rs485Config.isRtsActiveHigh();
      rxDuringTx = rs485Config.isRxDuringTx();
      terminationEnabled = rs485Config.isTerminationEnabled();
      delayBeforeMicros = rs485Config.getDelayBeforeSendMicros();
      delayAfterMicros = rs485Config.getDelayAfterSendMicros();
    }

    handle = NativeSerialPort.openWithRs485Config(
        portName,
        baudRate,
        dataBits.getValue(),
        stopBits.getValue(),
        parity.getValue(),
        flowControl.getValue(),
        dtrOnOpen,
        timeoutMs,
        rs485ModeValue,
        rs485PinValue,
        rtsActiveHigh,
        rxDuringTx,
        terminationEnabled,
        delayBeforeMicros,
        delayAfterMicros
    );

    if (handle == 0) {
      throw createIOException("Failed to open serial port: " + portName);
    }

    isOpen = true;
  }

  /**
   * Close the serial port.
   */
  public void close() {
    if (isOpen) {
      NativeSerialPort.close(handle);
      handle = 0;
      isOpen = false;
    }
  }

  /**
   * Write data to the serial port.
   *
   * @param data the data to write
   * @return the number of bytes written
   * @throws IOException if the write fails or the port is not open
   */
  public int write(byte[] data) throws IOException {
    return write(data, 0, data.length);
  }

  /**
   * Write data to the serial port.
   *
   * @param data   the data to write
   * @param offset the offset in the data array
   * @param length the number of bytes to write
   * @return the number of bytes written
   * @throws IOException               if the write fails or the port is not open
   * @throws IndexOutOfBoundsException if offset or length are invalid
   * @throws NullPointerException      if data is null
   */
  public int write(byte[] data, int offset, int length) throws IOException {
    if (data == null) {
      throw new NullPointerException("data cannot be null");
    }
    if (offset < 0 || length < 0 || offset + length > data.length) {
      throw new IndexOutOfBoundsException(
          "offset=" + offset + ", length=" + length + ", data.length=" + data.length);
    }
    if (!isOpen) {
      throw new IOException("Port is not open");
    }

    int result = NativeSerialPort.write(handle, data, offset, length);
    if (result < 0) {
      throw createIOException("Failed to write to serial port");
    }

    return result;
  }

  /**
   * Write a string to the serial port using the specified charset.
   *
   * @param text    the string to write
   * @param charset the charset to use for encoding
   * @return the number of bytes written
   * @throws IOException if the write fails or the port is not open
   */
  public int writeString(String text, java.nio.charset.Charset charset) throws IOException {
    return write(text.getBytes(charset));
  }

  /**
   * Write a string to the serial port using UTF-8 encoding.
   *
   * @param text the string to write
   * @return the number of bytes written
   * @throws IOException if the write fails or the port is not open
   */
  public int writeString(String text) throws IOException {
    return writeString(text, java.nio.charset.StandardCharsets.UTF_8);
  }

  /**
   * Write a string followed by a newline to the serial port using the specified charset.
   *
   * @param text    the string to write (newline will be appended)
   * @param charset the charset to use for encoding
   * @return the number of bytes written
   * @throws IOException if the write fails or the port is not open
   */
  public int writeLine(String text, java.nio.charset.Charset charset) throws IOException {
    return writeString(text + "\n", charset);
  }

  /**
   * Write a string followed by a newline to the serial port using UTF-8 encoding.
   *
   * @param text the string to write (newline will be appended)
   * @return the number of bytes written
   * @throws IOException if the write fails or the port is not open
   */
  public int writeLine(String text) throws IOException {
    return writeLine(text, java.nio.charset.StandardCharsets.UTF_8);
  }

  /**
   * Read data from the serial port.
   *
   * @param buffer the buffer to read into
   * @return the number of bytes read, or 0 if no data is available
   * @throws IOException if the read fails or the port is not open
   */
  public int read(byte[] buffer) throws IOException {
    return read(buffer, 0, buffer.length);
  }

  /**
   * Read data from the serial port.
   *
   * @param buffer the buffer to read into
   * @param offset the offset in the buffer
   * @param length the maximum number of bytes to read
   * @return the number of bytes read, or 0 if no data is available
   * @throws IOException               if the read fails or the port is not open
   * @throws IndexOutOfBoundsException if offset or length are invalid
   * @throws NullPointerException      if buffer is null
   */
  public int read(byte[] buffer, int offset, int length) throws IOException {
    if (buffer == null) {
      throw new NullPointerException("buffer cannot be null");
    }
    if (offset < 0 || length < 0 || offset + length > buffer.length) {
      throw new IndexOutOfBoundsException(
          "offset=" + offset + ", length=" + length + ", buffer.length=" + buffer.length);
    }
    if (!isOpen) {
      throw new IOException("Port is not open");
    }

    int result = NativeSerialPort.read(handle, buffer, offset, length);
    if (result < 0) {
      throw createIOException("Failed to read from serial port");
    }

    return result;
  }

  /**
   * Get the number of bytes available to read.
   *
   * @return the number of bytes available
   * @throws IOException if the port is not open
   */
  public int available() throws IOException {
    if (!isOpen) {
      throw new IOException("Port is not open");
    }

    return NativeSerialPort.bytesAvailable(handle);
  }

  /**
   * Read exactly the specified number of bytes, blocking until all bytes are received.
   *
   * <p>This method will repeatedly call {@link #read(byte[], int, int)} until exactly
   * {@code length} bytes have been read. If a timeout occurs before all bytes are received,
   * an IOException is thrown.
   *
   * @param buffer the buffer to read into
   * @param offset the offset in the buffer
   * @param length the exact number of bytes to read
   * @return the number of bytes read (always equals length on success)
   * @throws IOException               if a timeout occurs or the port is not open
   * @throws IndexOutOfBoundsException if offset or length are invalid
   * @throws NullPointerException      if buffer is null
   */
  public int readExactly(byte[] buffer, int offset, int length) throws IOException {
    if (buffer == null) {
      throw new NullPointerException("buffer cannot be null");
    }
    if (offset < 0 || length < 0 || offset + length > buffer.length) {
      throw new IndexOutOfBoundsException(
          "offset=" + offset + ", length=" + length + ", buffer.length=" + buffer.length);
    }

    int total = 0;
    while (total < length) {
      int bytesRead = read(buffer, offset + total, length - total);
      if (bytesRead == 0) {
        throw new IOException("Timeout waiting for data (received " + total + " of " + length + " bytes)");
      }
      total += bytesRead;
    }
    return total;
  }

  /**
   * Read exactly the specified number of bytes into a new buffer.
   *
   * <p>This is a convenience method that creates a buffer and calls
   * {@link #readExactly(byte[], int, int)}.
   *
   * @param length the exact number of bytes to read
   * @return a new byte array containing exactly {@code length} bytes
   * @throws IOException if a timeout occurs or the port is not open
   */
  public byte[] readExactly(int length) throws IOException {
    byte[] buffer = new byte[length];
    readExactly(buffer, 0, length);
    return buffer;
  }

  /**
   * Read exactly the specified number of bytes with a custom timeout.
   *
   * <p>This method temporarily changes the port timeout for this operation,
   * then restores the original timeout when done.
   *
   * @param buffer  the buffer to read into
   * @param offset  the offset in the buffer
   * @param length  the exact number of bytes to read
   * @param timeout the timeout in milliseconds for this operation
   * @return the number of bytes read (always equals length on success)
   * @throws IOException               if a timeout occurs or the port is not open
   * @throws IndexOutOfBoundsException if offset or length are invalid
   * @throws NullPointerException      if buffer is null
   */
  public int readExactly(byte[] buffer, int offset, int length, long timeout) throws IOException {
    int originalTimeout = this.timeoutMs;
    try {
      setTimeout((int) timeout);
      return readExactly(buffer, offset, length);
    }
    finally {
      setTimeout(originalTimeout);
    }
  }

  /**
   * Read all currently available bytes without blocking.
   *
   * <p>This method returns immediately with whatever data is available in the
   * receive buffer. If no data is available, it returns 0 without waiting.
   *
   * @param buffer the buffer to read into
   * @return the number of bytes read, or 0 if no data is available
   * @throws IOException if the port is not open
   */
  public int readAvailable(byte[] buffer) throws IOException {
    return readAvailable(buffer, 0, buffer.length);
  }

  /**
   * Read all currently available bytes without blocking.
   *
   * <p>This method returns immediately with whatever data is available in the
   * receive buffer, up to {@code length} bytes. If no data is available, it
   * returns 0 without waiting.
   *
   * @param buffer the buffer to read into
   * @param offset the offset in the buffer
   * @param length the maximum number of bytes to read
   * @return the number of bytes read, or 0 if no data is available
   * @throws IOException               if the port is not open
   * @throws IndexOutOfBoundsException if offset or length are invalid
   */
  public int readAvailable(byte[] buffer, int offset, int length) throws IOException {
    int avail = available();
    if (avail == 0) {
      return 0;
    }
    return read(buffer, offset, Math.min(avail, length));
  }

  /**
   * Read a line of text, terminated by a newline character, using the specified charset.
   *
   * <p>This method reads bytes until a newline character ({@code \n}) is encountered.
   * The newline character is not included in the returned string. Carriage return
   * characters ({@code \r}) immediately before the newline are also stripped.
   *
   * <p>If a timeout occurs before a complete line is received, an IOException is thrown.
   *
   * @param charset the charset to use for decoding
   * @return the line of text without the line terminator
   * @throws IOException if a timeout occurs or the port is not open
   */
  public String readLine(java.nio.charset.Charset charset) throws IOException {
    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
    byte[] single = new byte[1];

    while (true) {
      int bytesRead = read(single, 0, 1);
      if (bytesRead == 0) {
        if (baos.size() == 0) {
          throw new IOException("Timeout waiting for line data");
        }
        // Return partial line on timeout if we have some data
        break;
      }

      byte b = single[0];
      if (b == '\n') {
        break;
      }
      if (b != '\r') {
        baos.write(b);
      }
    }

    return baos.toString(charset.name());
  }

  /**
   * Read a line of text, terminated by a newline character, using UTF-8 encoding.
   *
   * <p>This method reads bytes until a newline character ({@code \n}) is encountered.
   * The newline character is not included in the returned string. Carriage return
   * characters ({@code \r}) immediately before the newline are also stripped.
   *
   * <p>If a timeout occurs before a complete line is received, an IOException is thrown.
   *
   * @return the line of text without the line terminator
   * @throws IOException if a timeout occurs or the port is not open
   */
  public String readLine() throws IOException {
    return readLine(java.nio.charset.StandardCharsets.UTF_8);
  }

  /**
   * Flush the output buffer, ensuring all data is written.
   *
   * @throws IOException if the flush fails or the port is not open
   */
  public void flush() throws IOException {
    if (!isOpen) {
      throw new IOException("Port is not open");
    }

    if (!NativeSerialPort.flush(handle)) {
      throw createIOException("Failed to flush serial port");
    }
  }

  /**
   * Set the timeout for read operations.
   *
   * <p><b>Note:</b> On Linux, timeouts are rounded up to the nearest 100ms due to
   * POSIX termios limitations. For example, 50ms becomes 100ms, and 150ms becomes 200ms.
   *
   * @param timeoutMs the timeout in milliseconds
   * @throws IOException if the operation fails or the port is not open
   * @see <a href="#platform-notes">Platform Notes</a>
   */
  public void setTimeout(int timeoutMs) throws IOException {
    if (!isOpen) {
      throw new IOException("Port is not open");
    }

    if (!NativeSerialPort.setTimeout(handle, timeoutMs)) {
      throw createIOException("Failed to set timeout");
    }
  }

  /**
   * Clear the input buffer.
   *
   * @throws IOException if the operation fails or the port is not open
   */
  public void clearInput() throws IOException {
    if (!isOpen) {
      throw new IOException("Port is not open");
    }

    if (!NativeSerialPort.clearInput(handle)) {
      throw createIOException("Failed to clear input buffer");
    }
  }

  /**
   * Clear the output buffer.
   *
   * @throws IOException if the operation fails or the port is not open
   */
  public void clearOutput() throws IOException {
    if (!isOpen) {
      throw new IOException("Port is not open");
    }

    if (!NativeSerialPort.clearOutput(handle)) {
      throw createIOException("Failed to clear output buffer");
    }
  }

  /**
   * Clear both input and output buffers.
   *
   * @throws IOException if the operation fails or the port is not open
   */
  public void clearAll() throws IOException {
    if (!isOpen) {
      throw new IOException("Port is not open");
    }

    if (!NativeSerialPort.clearAll(handle)) {
      throw createIOException("Failed to clear buffers");
    }
  }

  /**
   * Get an InputStream for reading from the serial port.
   * <p>
   * The same InputStream instance is returned on subsequent calls. The stream's read methods
   * will return -1 when no data is available within the configured timeout.
   *
   * @return an InputStream
   */
  public InputStream getInputStream() {
    if (cachedInputStream == null) {
      cachedInputStream = new SerialInputStream(this);
    }
    return cachedInputStream;
  }

  /**
   * Get an OutputStream for writing to the serial port.
   * <p>
   * The same OutputStream instance is returned on subsequent calls.
   *
   * @return an OutputStream
   */
  public OutputStream getOutputStream() {
    if (cachedOutputStream == null) {
      cachedOutputStream = new SerialOutputStream(this);
    }
    return cachedOutputStream;
  }

  /**
   * Check if the port is open.
   *
   * @return true if the port is open
   */
  public boolean isOpen() {
    return isOpen;
  }

  /**
   * Get the port name.
   *
   * @return the port name
   */
  public String getPortName() {
    return portName;
  }

  /**
   * Get the baud rate.
   *
   * @return the baud rate
   */
  public int getBaudRate() {
    return baudRate;
  }

  /**
   * Get the data bits.
   *
   * @return the data bits
   */
  public DataBits getDataBits() {
    return dataBits;
  }

  /**
   * Get the stop bits.
   *
   * @return the stop bits
   */
  public StopBits getStopBits() {
    return stopBits;
  }

  /**
   * Get the parity.
   *
   * @return the parity
   */
  public Parity getParity() {
    return parity;
  }

  /**
   * Get the flow control mode.
   *
   * @return the flow control mode
   */
  public FlowControl getFlowControl() {
    return flowControl;
  }

  /**
   * Check if DTR is asserted when opening the port.
   *
   * @return true if DTR is asserted on open (default), false if suppressed
   */
  public boolean isDtrOnOpen() {
    return dtrOnOpen;
  }

  /**
   * Get the RS-485 configuration.
   *
   * @return the RS-485 configuration, or null if not set
   */
  public Rs485Config getRs485Config() {
    return rs485Config;
  }

  /**
   * Check if kernel RS-485 mode is active.
   *
   * <p>This returns true only on Linux when the hardware supports kernel-level RS-485
   * and it was successfully enabled. When kernel mode is active, the hardware handles
   * RTS timing automatically for optimal performance.
   *
   * @return true if kernel RS-485 mode is active
   * @throws IOException if the port is not open
   */
  public boolean isKernelRs485Active() throws IOException {
    if (!isOpen) {
      throw new IOException("Port is not open");
    }
    return NativeSerialPort.isKernelRs485Active(handle);
  }

  /**
   * Set RS-485 timing delays (Linux kernel mode only).
   *
   * <p>These delays control the timing of RTS signal transitions relative to data
   * transmission. They are only effective when kernel RS-485 mode is active.
   *
   * @param delayBeforeSendMicros delay in microseconds before sending (RTS assertion to first data bit)
   * @param delayAfterSendMicros  delay in microseconds after sending (last data bit to RTS de-assertion)
   * @throws IOException if the operation fails or the port is not open
   */
  public void setRs485Delays(int delayBeforeSendMicros, int delayAfterSendMicros) throws IOException {
    if (!isOpen) {
      throw new IOException("Port is not open");
    }
    if (!NativeSerialPort.setRs485Delays(handle, delayBeforeSendMicros, delayAfterSendMicros)) {
      throw createIOException("Failed to set RS-485 delays");
    }
  }

  /**
   * Set RS-485 configuration at runtime.
   *
   * <p>This allows changing all RS-485 parameters on an open port, including
   * RTS polarity, RX during TX, termination, and timing delays.
   *
   * <p>Note: This is only fully supported on Linux with kernel RS-485 support.
   * On other platforms, only the delay values may be effective.
   *
   * @param config the RS-485 configuration to apply
   * @throws IOException if the operation fails or the port is not open
   */
  public void setRs485Config(Rs485Config config) throws IOException {
    if (!isOpen) {
      throw new IOException("Port is not open");
    }
    if (config == null) {
      throw new IllegalArgumentException("Config cannot be null");
    }
    if (!NativeSerialPort.setRs485Config(
        handle,
        config.isEnabled(),
        config.getControlPin().getValue(),
        config.isRtsActiveHigh(),
        config.isRxDuringTx(),
        config.isTerminationEnabled(),
        config.getDelayBeforeSendMicros(),
        config.getDelayAfterSendMicros())) {
      throw createIOException("Failed to set RS-485 config");
    }
  }

  /**
   * Set the RTS (Request To Send) pin state.
   *
   * <p>This is useful for manual RS-485 control or other applications that need
   * direct control of the RTS pin.
   *
   * @param level true to set RTS high, false to set RTS low
   * @throws IOException if the operation fails or the port is not open
   */
  public void setRTS(boolean level) throws IOException {
    if (!isOpen) {
      throw new IOException("Port is not open");
    }
    if (!NativeSerialPort.setRTS(handle, level)) {
      throw createIOException("Failed to set RTS");
    }
  }

  /**
   * Set the DTR (Data Terminal Ready) pin state.
   *
   * <p>This is useful for manual RS-485 control when DTR is used instead of RTS,
   * or for other applications that need direct control of the DTR pin.
   *
   * @param level true to set DTR high, false to set DTR low
   * @throws IOException if the operation fails or the port is not open
   */
  public void setDTR(boolean level) throws IOException {
    if (!isOpen) {
      throw new IOException("Port is not open");
    }
    if (!NativeSerialPort.setDTR(handle, level)) {
      throw createIOException("Failed to set DTR");
    }
  }

  /**
   * Builder for configuring a SerialPort.
   */
  public static class Builder {

    private String portName;
    private int baudRate = 9600;
    private DataBits dataBits = DataBits.EIGHT;
    private StopBits stopBits = StopBits.ONE;
    private Parity parity = Parity.NONE;
    private FlowControl flowControl = FlowControl.NONE;
    private boolean dtrOnOpen = true;
    private int timeoutMs = 1000;
    private Rs485Config rs485Config = null;

    /**
     * Set the port name.
     *
     * @param portName the port name (e.g., "COM1" or "/dev/ttyUSB0")
     * @return this builder
     */
    public Builder portName(String portName) {
      this.portName = portName;
      return this;
    }

    /**
     * Set the baud rate.
     *
     * @param baudRate the baud rate (default: 9600)
     * @return this builder
     * @throws IllegalArgumentException if baudRate is not positive
     */
    public Builder baudRate(int baudRate) {
      if (baudRate <= 0) {
        throw new IllegalArgumentException("baudRate must be positive");
      }
      this.baudRate = baudRate;
      return this;
    }

    /**
     * Set the data bits.
     *
     * @param dataBits the data bits (default: EIGHT)
     * @return this builder
     * @throws IllegalArgumentException if dataBits is null
     */
    public Builder dataBits(DataBits dataBits) {
      if (dataBits == null) {
        throw new IllegalArgumentException("dataBits cannot be null");
      }
      this.dataBits = dataBits;
      return this;
    }

    /**
     * Set the stop bits.
     *
     * @param stopBits the stop bits (default: ONE)
     * @return this builder
     * @throws IllegalArgumentException if stopBits is null
     */
    public Builder stopBits(StopBits stopBits) {
      if (stopBits == null) {
        throw new IllegalArgumentException("stopBits cannot be null");
      }
      this.stopBits = stopBits;
      return this;
    }

    /**
     * Set the parity.
     *
     * @param parity the parity (default: NONE)
     * @return this builder
     * @throws IllegalArgumentException if parity is null
     */
    public Builder parity(Parity parity) {
      if (parity == null) {
        throw new IllegalArgumentException("parity cannot be null");
      }
      this.parity = parity;
      return this;
    }

    /**
     * Set the flow control mode.
     *
     * @param flowControl the flow control mode (default: NONE)
     * @return this builder
     * @throws IllegalArgumentException if flowControl is null
     */
    public Builder flowControl(FlowControl flowControl) {
      if (flowControl == null) {
        throw new IllegalArgumentException("flowControl cannot be null");
      }
      this.flowControl = flowControl;
      return this;
    }

    /**
     * Control whether DTR (Data Terminal Ready) is asserted when opening the port.
     *
     * <p>By default, DTR is asserted when a serial port is opened. This can cause
     * Arduino and similar boards to reset, as they use DTR connected to the reset
     * pin through a capacitor.
     *
     * <p>Set to {@code false} to suppress DTR assertion, preventing the automatic
     * reset on Arduino boards.
     *
     * @param dtrOnOpen true to assert DTR on open (default), false to suppress
     * @return this builder
     */
    public Builder dtrOnOpen(boolean dtrOnOpen) {
      this.dtrOnOpen = dtrOnOpen;
      return this;
    }

    /**
     * Set the timeout for read operations.
     *
     * <p><b>Note:</b> On Linux, timeouts are rounded up to the nearest 100ms due to
     * POSIX termios limitations. For example, 50ms becomes 100ms, and 150ms becomes 200ms.
     *
     * @param timeoutMs the timeout in milliseconds (default: 1000)
     * @return this builder
     */
    public Builder timeout(int timeoutMs) {
      this.timeoutMs = timeoutMs;
      return this;
    }

    /**
     * Set the RS-485 configuration.
     *
     * <p>RS-485 is a half-duplex communication standard. When enabled, the library
     * automatically controls the RTS or DTR pin to switch between transmit and receive modes.
     *
     * <p>This provides access to all RS-485 parameters including control pin,
     * RTS polarity, RX during TX, termination, and timing delays.
     *
     * @param config the RS-485 configuration
     * @return this builder
     * @see Rs485Config
     */
    public Builder rs485Config(Rs485Config config) {
      this.rs485Config = config;
      return this;
    }

    /**
     * Enable RS-485 mode with default settings.
     *
     * <p>This is a convenience method that enables RS-485 with:
     * <ul>
     *   <li>RTS control pin</li>
     *   <li>Active high polarity</li>
     *   <li>No delays</li>
     * </ul>
     *
     * <p>On Linux, this attempts to use kernel-level RS-485 support for optimal timing.
     * On other platforms or if kernel mode is unavailable, it falls back to manual control.
     *
     * @return this builder
     */
    public Builder rs485() {
      this.rs485Config = Rs485Config.enabled();
      return this;
    }

    /**
     * Build the SerialPort.
     *
     * @return a new SerialPort instance
     * @throws IllegalArgumentException if the port name is not set
     */
    public SerialPort build() {
      if (portName == null || portName.isEmpty()) {
        throw new IllegalArgumentException("Port name must be set");
      }
      return new SerialPort(this);
    }

  }

  /**
   * InputStream wrapper for SerialPort.
   */
  private static class SerialInputStream extends InputStream {

    private final SerialPort port;

    SerialInputStream(SerialPort port) {
      this.port = port;
    }

    @Override
    public int read() throws IOException {
      byte[] buffer = new byte[1];
      int result = port.read(buffer, 0, 1);
      if (result <= 0) {
        return -1;
      }
      return buffer[0] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      int result = port.read(b, off, len);
      return result == 0 ? -1 : result;
    }

    @Override
    public int available() throws IOException {
      return port.available();
    }

  }

  /**
   * OutputStream wrapper for SerialPort.
   */
  private static class SerialOutputStream extends OutputStream {

    private final SerialPort port;

    SerialOutputStream(SerialPort port) {
      this.port = port;
    }

    @Override
    public void write(int b) throws IOException {
      byte[] buffer = new byte[] { (byte) b };
      port.write(buffer, 0, 1);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      port.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
      port.flush();
    }

  }

}
