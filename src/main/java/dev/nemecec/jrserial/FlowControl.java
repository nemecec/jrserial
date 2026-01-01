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
 * Flow control mode for serial communication.
 *
 * <p>Flow control prevents data loss when the sender transmits faster than the
 * receiver can process. There are two main approaches:
 * <ul>
 *   <li><b>Software (XON/XOFF)</b>: Uses special characters to pause/resume transmission.
 *       Works over any serial connection but uses bandwidth.</li>
 *   <li><b>Hardware (RTS/CTS)</b>: Uses dedicated signal lines for flow control.
 *       More efficient but requires hardware support.</li>
 * </ul>
 */
public enum FlowControl {
  /**
   * No flow control (default).
   *
   * <p>Data is sent without any flow control mechanism. This is suitable for
   * most modern serial communication where buffer overflows are unlikely.
   */
  NONE(0),

  /**
   * Software flow control using XON/XOFF characters.
   *
   * <p>The receiver sends XOFF (0x13) to pause transmission and XON (0x11)
   * to resume. This method works over any serial connection but uses some
   * bandwidth and cannot be used with binary data that may contain these characters.
   */
  SOFTWARE(1),

  /**
   * Hardware flow control using RTS/CTS signal lines.
   *
   * <p>The RTS (Request To Send) and CTS (Clear To Send) lines are used to
   * coordinate data transmission. This is more efficient than software flow
   * control but requires a cable with these signal lines connected.
   */
  HARDWARE(2);

  private final int value;

  FlowControl(int value) {
    this.value = value;
  }

  /**
   * Get the numeric value of the flow control mode.
   *
   * @return the numeric value (0=None, 1=Software, 2=Hardware)
   */
  public int getValue() {
    return value;
  }
}
