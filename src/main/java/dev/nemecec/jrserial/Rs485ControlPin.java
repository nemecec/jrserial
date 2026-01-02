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
package dev.nemecec.jrserial;

/**
 * Pin used for RS-485 direction control.
 *
 * <p>RS-485 transceivers typically have an enable pin that controls whether the device
 * is transmitting or receiving. This pin is usually controlled by either RTS or DTR
 * from the serial port.
 */
public enum Rs485ControlPin {

  /**
   * Use RTS (Request To Send) pin for direction control.
   * This is the most common choice and is supported by Linux kernel RS-485 mode.
   */
  RTS(0),

  /**
   * Use DTR (Data Terminal Ready) pin for direction control.
   * Use this if your hardware is wired to use DTR instead of RTS.
   * Note: DTR is only supported in manual mode, not kernel mode.
   */
  DTR(1);

  private final int value;

  Rs485ControlPin(int value) {
    this.value = value;
  }

  /**
   * Get the native value for this pin.
   *
   * @return the native value
   */
  public int getValue() {
    return value;
  }

}
