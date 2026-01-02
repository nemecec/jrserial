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
 * Configuration for RS-485 half-duplex serial communication.
 *
 * <p>RS-485 is a differential signaling standard commonly used in industrial environments
 * for multi-drop serial communication. It requires direction control to switch between
 * transmit and receive modes since only one device can transmit at a time.
 *
 * <p>Example usage:
 * <pre>
 * Rs485Config config = Rs485Config.builder()
 *     .enabled(true)
 *     .rtsActiveHigh(true)
 *     .delayBeforeSendMicros(100)
 *     .delayAfterSendMicros(100)
 *     .build();
 *
 * SerialPort port = SerialPort.builder()
 *     .portName("/dev/ttyUSB0")
 *     .baudRate(9600)
 *     .rs485Config(config)
 *     .build();
 * </pre>
 *
 * @see SerialPort.Builder#rs485Config(Rs485Config)
 */
public class Rs485Config {

  private final boolean enabled;
  private final Rs485ControlPin controlPin;
  private final boolean rtsActiveHigh;
  private final boolean rxDuringTx;
  private final boolean terminationEnabled;
  private final int delayBeforeSendMicros;
  private final int delayAfterSendMicros;

  private Rs485Config(Builder builder) {
    this.enabled = builder.enabled;
    this.controlPin = builder.controlPin;
    this.rtsActiveHigh = builder.rtsActiveHigh;
    this.rxDuringTx = builder.rxDuringTx;
    this.terminationEnabled = builder.terminationEnabled;
    this.delayBeforeSendMicros = builder.delayBeforeSendMicros;
    this.delayAfterSendMicros = builder.delayAfterSendMicros;
  }

  /**
   * Create a new builder for RS-485 configuration.
   *
   * @return a new builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Create a disabled RS-485 configuration.
   *
   * @return a disabled configuration
   */
  public static Rs485Config disabled() {
    return new Builder().enabled(false).build();
  }

  /**
   * Create a default enabled RS-485 configuration.
   *
   * <p>Uses RTS pin, active high polarity, no delays.
   *
   * @return a default enabled configuration
   */
  public static Rs485Config enabled() {
    return new Builder().enabled(true).build();
  }

  /**
   * Check if RS-485 mode is enabled.
   *
   * @return true if RS-485 mode is enabled
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Get the control pin used for direction switching.
   *
   * @return the control pin (RTS or DTR)
   */
  public Rs485ControlPin getControlPin() {
    return controlPin;
  }

  /**
   * Check if RTS is active high.
   *
   * <p>When true (default), RTS goes high during transmission.
   * When false, RTS goes low during transmission (inverted logic).
   *
   * @return true if RTS is active high
   */
  public boolean isRtsActiveHigh() {
    return rtsActiveHigh;
  }

  /**
   * Check if receiving during transmission is enabled.
   *
   * <p>When enabled, the receiver remains active during transmission,
   * allowing detection of local echo or collisions.
   *
   * @return true if RX during TX is enabled
   */
  public boolean isRxDuringTx() {
    return rxDuringTx;
  }

  /**
   * Check if bus termination is enabled.
   *
   * <p>Note: This is only supported on hardware that has software-controlled
   * termination resistors. Most RS-485 adapters use physical DIP switches
   * for termination control.
   *
   * @return true if termination is enabled
   */
  public boolean isTerminationEnabled() {
    return terminationEnabled;
  }

  /**
   * Get the delay before sending in microseconds.
   *
   * <p>This is the delay between asserting RTS and starting transmission.
   * Allows the transceiver to stabilize before data is sent.
   *
   * @return delay in microseconds
   */
  public int getDelayBeforeSendMicros() {
    return delayBeforeSendMicros;
  }

  /**
   * Get the delay after sending in microseconds.
   *
   * <p>This is the delay between end of transmission and de-asserting RTS.
   * Allows the last bit to be fully transmitted before switching to receive mode.
   *
   * @return delay in microseconds
   */
  public int getDelayAfterSendMicros() {
    return delayAfterSendMicros;
  }

  /**
   * Builder for RS-485 configuration.
   */
  public static class Builder {

    private boolean enabled = true;
    private Rs485ControlPin controlPin = Rs485ControlPin.RTS;
    private boolean rtsActiveHigh = true;
    private boolean rxDuringTx = false;
    private boolean terminationEnabled = false;
    private int delayBeforeSendMicros = 0;
    private int delayAfterSendMicros = 0;

    /**
     * Enable or disable RS-485 mode.
     *
     * @param enabled true to enable RS-485 mode
     * @return this builder
     */
    public Builder enabled(boolean enabled) {
      this.enabled = enabled;
      return this;
    }

    /**
     * Set the control pin for direction switching.
     *
     * @param controlPin the control pin (default: RTS)
     * @return this builder
     */
    public Builder controlPin(Rs485ControlPin controlPin) {
      this.controlPin = controlPin;
      return this;
    }

    /**
     * Set the RTS polarity.
     *
     * <p>When true (default), RTS goes high during transmission.
     * When false, RTS goes low during transmission (inverted logic).
     *
     * @param rtsActiveHigh true for active high, false for active low
     * @return this builder
     */
    public Builder rtsActiveHigh(boolean rtsActiveHigh) {
      this.rtsActiveHigh = rtsActiveHigh;
      return this;
    }

    /**
     * Enable receiving during transmission.
     *
     * <p>When enabled, the receiver remains active during transmission,
     * allowing detection of local echo or collisions.
     *
     * @param rxDuringTx true to enable RX during TX
     * @return this builder
     */
    public Builder rxDuringTx(boolean rxDuringTx) {
      this.rxDuringTx = rxDuringTx;
      return this;
    }

    /**
     * Enable bus termination.
     *
     * <p>Note: This is only supported on hardware that has software-controlled
     * termination resistors. Most RS-485 adapters use physical DIP switches.
     *
     * @param terminationEnabled true to enable termination
     * @return this builder
     */
    public Builder terminationEnabled(boolean terminationEnabled) {
      this.terminationEnabled = terminationEnabled;
      return this;
    }

    /**
     * Set the delay before sending.
     *
     * <p>This is the delay between asserting RTS and starting transmission.
     *
     * @param delayMicros delay in microseconds (default: 0)
     * @return this builder
     */
    public Builder delayBeforeSendMicros(int delayMicros) {
      this.delayBeforeSendMicros = delayMicros;
      return this;
    }

    /**
     * Set the delay after sending.
     *
     * <p>This is the delay between end of transmission and de-asserting RTS.
     *
     * @param delayMicros delay in microseconds (default: 0)
     * @return this builder
     */
    public Builder delayAfterSendMicros(int delayMicros) {
      this.delayAfterSendMicros = delayMicros;
      return this;
    }

    /**
     * Build the RS-485 configuration.
     *
     * @return a new Rs485Config instance
     */
    public Rs485Config build() {
      return new Rs485Config(this);
    }

  }

}
