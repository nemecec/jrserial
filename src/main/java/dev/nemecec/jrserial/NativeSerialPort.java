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

/**
 * JNI interface to the Rust native serial port implementation.
 */
class NativeSerialPort {

  static {
    NativeLibraryLoader.loadLibrary();
  }

  private NativeSerialPort() {}

  /**
   * Open a serial port.
   *
   * @param portName   the name of the port (e.g., "COM1" or "/dev/ttyUSB0")
   * @param baudRate   the baud rate
   * @param dataBits   the number of data bits (5, 6, 7, or 8)
   * @param stopBits   the number of stop bits (1 or 2)
   * @param parity     the parity (0=None, 1=Odd, 2=Even)
   * @param timeoutMs  the timeout in milliseconds
   * @param rs485Mode  RS-485 mode (0=None, 1=Auto, 2=Manual)
   * @param rs485Pin   RS-485 control pin (0=RTS, 1=DTR)
   * @return a handle to the native serial port, or 0 if failed
   */
  static native long open(
      String portName,
      int baudRate,
      int dataBits,
      int stopBits,
      int parity,
      int timeoutMs,
      int rs485Mode,
      int rs485Pin
  );

  /**
   * Close a serial port.
   *
   * @param handle the handle to the native serial port
   */
  static native void close(long handle);

  /**
   * Write data to a serial port.
   *
   * @param handle the handle to the native serial port
   * @param data   the data to write
   * @param offset the offset in the data array
   * @param length the number of bytes to write
   * @return the number of bytes written, or -1 if failed
   */
  static native int write(long handle, byte[] data, int offset, int length);

  /**
   * Read data from a serial port.
   *
   * @param handle the handle to the native serial port
   * @param buffer the buffer to read into
   * @param offset the offset in the buffer
   * @param length the maximum number of bytes to read
   * @return the number of bytes read, or -1 if failed
   */
  static native int read(long handle, byte[] buffer, int offset, int length);

  /**
   * Get the number of bytes available to read.
   *
   * @param handle the handle to the native serial port
   * @return the number of bytes available
   */
  static native int bytesAvailable(long handle);

  /**
   * Flush the output buffer.
   *
   * @param handle the handle to the native serial port
   * @return true if successful, false otherwise
   */
  static native boolean flush(long handle);

  /**
   * List available serial ports.
   *
   * @return a comma-separated list of port names
   */
  static native String listPorts();

  /**
   * Set the timeout for read operations.
   *
   * @param handle    the handle to the native serial port
   * @param timeoutMs the timeout in milliseconds
   * @return true if successful, false otherwise
   */
  static native boolean setTimeout(long handle, int timeoutMs);

  /**
   * Clear the input buffer.
   *
   * @param handle the handle to the native serial port
   * @return true if successful, false otherwise
   */
  static native boolean clearInput(long handle);

  /**
   * Clear the output buffer.
   *
   * @param handle the handle to the native serial port
   * @return true if successful, false otherwise
   */
  static native boolean clearOutput(long handle);

  /**
   * Clear both input and output buffers.
   *
   * @param handle the handle to the native serial port
   * @return true if successful, false otherwise
   */
  static native boolean clearAll(long handle);

  /**
   * Set the RTS pin state.
   *
   * @param handle the handle to the native serial port
   * @param level  true for high, false for low
   * @return true if successful, false otherwise
   */
  static native boolean setRTS(long handle, boolean level);

  /**
   * Set the DTR pin state.
   *
   * @param handle the handle to the native serial port
   * @param level  true for high, false for low
   * @return true if successful, false otherwise
   */
  static native boolean setDTR(long handle, boolean level);

  /**
   * Check if kernel RS-485 mode is active (Linux only).
   *
   * @param handle the handle to the native serial port
   * @return true if kernel RS-485 mode is active, false otherwise
   */
  static native boolean isKernelRs485Active(long handle);

  /**
   * Set RS-485 timing delays (Linux kernel mode only).
   *
   * @param handle                 the handle to the native serial port
   * @param delayBeforeSendMicros  delay before sending in microseconds
   * @param delayAfterSendMicros   delay after sending in microseconds
   * @return true if successful, false otherwise
   */
  static native boolean setRs485Delays(long handle, int delayBeforeSendMicros, int delayAfterSendMicros);

  /**
   * Open a serial port with extended RS-485 configuration.
   *
   * @param portName           the name of the port (e.g., "COM1" or "/dev/ttyUSB0")
   * @param baudRate           the baud rate
   * @param dataBits           the number of data bits (5, 6, 7, or 8)
   * @param stopBits           the number of stop bits (1 or 2)
   * @param parity             the parity (0=None, 1=Odd, 2=Even)
   * @param flowControl        the flow control mode (0=None, 1=Software, 2=Hardware)
   * @param dtrOnOpen          true to assert DTR on open, false to suppress
   * @param timeoutMs          the timeout in milliseconds
   * @param rs485Mode          RS-485 mode (0=None, 1=Auto, 2=Manual)
   * @param rs485Pin           RS-485 control pin (0=RTS, 1=DTR)
   * @param rtsActiveHigh      true if RTS is active high, false for active low
   * @param rxDuringTx         true to enable receiving during transmission
   * @param terminationEnabled true to enable bus termination (hardware-dependent)
   * @param delayBeforeMicros  delay before sending in microseconds
   * @param delayAfterMicros   delay after sending in microseconds
   * @return a handle to the native serial port, or 0 if failed
   */
  static native long openWithRs485Config(
      String portName,
      int baudRate,
      int dataBits,
      int stopBits,
      int parity,
      int flowControl,
      boolean dtrOnOpen,
      int timeoutMs,
      int rs485Mode,
      int rs485Pin,
      boolean rtsActiveHigh,
      boolean rxDuringTx,
      boolean terminationEnabled,
      int delayBeforeMicros,
      int delayAfterMicros
  );

  /**
   * Set RS-485 configuration at runtime.
   *
   * @param handle             the handle to the native serial port
   * @param enabled            true to enable RS-485 mode
   * @param rs485Pin           RS-485 control pin (0=RTS, 1=DTR)
   * @param rtsActiveHigh      true if RTS is active high, false for active low
   * @param rxDuringTx         true to enable receiving during transmission
   * @param terminationEnabled true to enable bus termination (hardware-dependent)
   * @param delayBeforeMicros  delay before sending in microseconds
   * @param delayAfterMicros   delay after sending in microseconds
   * @return true if successful, false otherwise
   */
  static native boolean setRs485Config(
      long handle,
      boolean enabled,
      int rs485Pin,
      boolean rtsActiveHigh,
      boolean rxDuringTx,
      boolean terminationEnabled,
      int delayBeforeMicros,
      int delayAfterMicros
  );

  /**
   * Get the last error message from native code.
   * <p>
   * Returns detailed error information including the source location where
   * the error occurred in the native code. This is useful for debugging.
   *
   * @return the last error message, or null if no error has occurred
   */
  static native String getLastError();

  /**
   * Clear the last error.
   */
  static native void clearLastError();

}
