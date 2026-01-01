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
 * Information about a serial port discovered during enumeration.
 *
 * <p>This class provides details about the port including whether it is a symbolic link,
 * pseudo-terminal (PTY) device, or Bluetooth serial port. PTY devices do not support
 * hardware flow control operations like RTS/DTR.
 */
public final class SerialPortInfo {

  private final String portName;
  private final boolean symlink;
  private final boolean pseudoTerminal;
  private final boolean bluetooth;

  SerialPortInfo(String portName, boolean symlink, boolean pseudoTerminal, boolean bluetooth) {
    this.portName = portName;
    this.symlink = symlink;
    this.pseudoTerminal = pseudoTerminal;
    this.bluetooth = bluetooth;
  }

  /**
   * Get the port name (e.g., "/dev/ttyUSB0" or "COM3").
   *
   * @return the port name
   */
  public String getPortName() {
    return portName;
  }

  /**
   * Check if this port is a symbolic link to another device.
   *
   * <p>On Unix systems, serial ports may be symlinks (e.g., /dev/serial/by-id/* links).
   * This is informational and does not affect functionality.
   *
   * @return true if the port path is a symbolic link
   */
  public boolean isSymlink() {
    return symlink;
  }

  /**
   * Check if this port is a pseudo-terminal (PTY) device.
   *
   * <p>PTY devices (like /dev/pts/*) are virtual terminals that do not support
   * hardware flow control. Operations like {@link SerialPort#setRTS(boolean)} and
   * {@link SerialPort#setDTR(boolean)} will fail on PTY devices.
   *
   * <p>Common PTY scenarios:
   * <ul>
   *   <li>Virtual serial ports created by socat or similar tools</li>
   *   <li>Serial port emulators</li>
   *   <li>USB-to-serial adapters that expose as PTYs (rare)</li>
   * </ul>
   *
   * @return true if the port is a pseudo-terminal
   */
  public boolean isPseudoTerminal() {
    return pseudoTerminal;
  }

  /**
   * Check if this port is a Bluetooth serial port.
   *
   * <p>Bluetooth serial ports are detected by pattern matching on the port name:
   * <ul>
   *   <li>macOS: /dev/cu.Bluetooth-*, /dev/tty.Bluetooth-*</li>
   *   <li>Linux: /dev/rfcomm*</li>
   * </ul>
   *
   * @return true if the port appears to be a Bluetooth serial port
   */
  public boolean isBluetooth() {
    return bluetooth;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(portName);
    if (symlink || pseudoTerminal || bluetooth) {
      sb.append(" (");
      boolean needComma = false;
      if (symlink) {
        sb.append("symlink");
        needComma = true;
      }
      if (pseudoTerminal) {
        if (needComma) sb.append(", ");
        sb.append("pty");
        needComma = true;
      }
      if (bluetooth) {
        if (needComma) sb.append(", ");
        sb.append("bluetooth");
      }
      sb.append(")");
    }
    return sb.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SerialPortInfo that = (SerialPortInfo) o;
    return portName.equals(that.portName);
  }

  @Override
  public int hashCode() {
    return portName.hashCode();
  }

}
