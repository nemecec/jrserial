@file:JvmName("SerialPortDsl")

package dev.nemecec.jrserial.kotlin

import dev.nemecec.jrserial.*

/**
 * Creates and configures a [SerialPort] using a DSL builder.
 *
 * Example:
 * ```kotlin
 * val port = serialPort("/dev/ttyUSB0") {
 *     baudRate = 115200
 *     dataBits = DataBits.EIGHT
 *     stopBits = StopBits.ONE
 *     parity = Parity.NONE
 *     timeout = 2000
 * }
 * ```
 */
fun serialPort(portName: String, block: SerialPortBuilder.() -> Unit = {}): SerialPort {
  return SerialPortBuilder(portName).apply(block).build()
}

/**
 * DSL builder for [SerialPort] configuration.
 */
class SerialPortBuilder(private val portName: String) {
  /** Baud rate (default: 9600) */
  var baudRate: Int = 9600

  /** Data bits (default: EIGHT) */
  var dataBits: DataBits = DataBits.EIGHT

  /** Stop bits (default: ONE) */
  var stopBits: StopBits = StopBits.ONE

  /** Parity (default: NONE) */
  var parity: Parity = Parity.NONE

  /** Flow control (default: NONE) */
  var flowControl: FlowControl = FlowControl.NONE

  /** Read timeout in milliseconds (default: 1000) */
  var timeout: Int = 1000

  /** DTR on open (default: true) */
  var dtrOnOpen: Boolean = true

  private var rs485Config: Rs485Config? = null

  /**
   * Configure RS-485 mode using a DSL builder.
   *
   * Example:
   * ```kotlin
   * rs485 {
   *     controlPin = Rs485ControlPin.RTS
   *     rtsActiveHigh = true
   * }
   * ```
   */
  fun rs485(block: Rs485Builder.() -> Unit) {
    rs485Config = Rs485Builder().apply(block).build()
  }

  /**
   * Enable RS-485 with default settings.
   */
  fun rs485() {
    rs485Config = Rs485Config.enabled()
  }

  internal fun build(): SerialPort {
    val builder = SerialPort.builder()
      .portName(portName)
      .baudRate(baudRate)
      .dataBits(dataBits)
      .stopBits(stopBits)
      .parity(parity)
      .flowControl(flowControl)
      .timeout(timeout)
      .dtrOnOpen(dtrOnOpen)

    rs485Config?.let { builder.rs485Config(it) }

    return builder.build()
  }
}

/**
 * DSL builder for [Rs485Config].
 */
class Rs485Builder {
  /** Control pin to use for direction switching */
  var controlPin: Rs485ControlPin = Rs485ControlPin.RTS

  /** RTS active high (true) or active low (false) */
  var rtsActiveHigh: Boolean = true

  /** Enable receiver during transmission */
  var rxDuringTx: Boolean = false

  /** Enable bus termination (if hardware supports) */
  var terminationEnabled: Boolean = false

  /** Delay before sending in microseconds */
  var delayBeforeSendMicros: Int = 0

  /** Delay after sending in microseconds */
  var delayAfterSendMicros: Int = 0

  internal fun build(): Rs485Config {
    return Rs485Config.builder()
      .enabled(true)
      .controlPin(controlPin)
      .rtsActiveHigh(rtsActiveHigh)
      .rxDuringTx(rxDuringTx)
      .terminationEnabled(terminationEnabled)
      .delayBeforeSendMicros(delayBeforeSendMicros)
      .delayAfterSendMicros(delayAfterSendMicros)
      .build()
  }
}
