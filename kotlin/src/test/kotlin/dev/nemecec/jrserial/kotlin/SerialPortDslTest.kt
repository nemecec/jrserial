package dev.nemecec.jrserial.kotlin

import dev.nemecec.jrserial.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SerialPortDslTest {

  @Test
  fun `serialPort DSL creates port with default values`() {
    val port = serialPort("/dev/ttyUSB0")

    assertThat(port.portName).isEqualTo("/dev/ttyUSB0")
    assertThat(port.baudRate).isEqualTo(9600)
    assertThat(port.dataBits).isEqualTo(DataBits.EIGHT)
    assertThat(port.stopBits).isEqualTo(StopBits.ONE)
    assertThat(port.parity).isEqualTo(Parity.NONE)
    assertThat(port.flowControl).isEqualTo(FlowControl.NONE)
  }

  @Test
  fun `serialPort DSL configures baud rate`() {
    val port = serialPort("/dev/ttyUSB0") {
      baudRate = 115200
    }

    assertThat(port.baudRate).isEqualTo(115200)
  }

  @Test
  fun `serialPort DSL configures all parameters`() {
    val port = serialPort("/dev/ttyUSB0") {
      baudRate = 57600
      dataBits = DataBits.SEVEN
      stopBits = StopBits.TWO
      parity = Parity.EVEN
      flowControl = FlowControl.HARDWARE
      timeout = 5000
      dtrOnOpen = false
    }

    assertThat(port.baudRate).isEqualTo(57600)
    assertThat(port.dataBits).isEqualTo(DataBits.SEVEN)
    assertThat(port.stopBits).isEqualTo(StopBits.TWO)
    assertThat(port.parity).isEqualTo(Parity.EVEN)
    assertThat(port.flowControl).isEqualTo(FlowControl.HARDWARE)
    assertThat(port.isDtrOnOpen).isFalse()
  }

  @Test
  fun `serialPort DSL configures RS-485 with defaults`() {
    val port = serialPort("/dev/ttyUSB0") {
      baudRate = 9600
      rs485()
    }

    assertThat(port.rs485Config).isNotNull
    assertThat(port.rs485Config.isEnabled).isTrue()
  }

  @Test
  fun `serialPort DSL configures RS-485 with nested DSL`() {
    val port = serialPort("/dev/ttyUSB0") {
      baudRate = 9600
      rs485 {
        controlPin = Rs485ControlPin.DTR
        rtsActiveHigh = false
        delayBeforeSendMicros = 100
        delayAfterSendMicros = 50
      }
    }

    assertThat(port.rs485Config).isNotNull
    assertThat(port.rs485Config.isEnabled).isTrue()
    assertThat(port.rs485Config.controlPin).isEqualTo(Rs485ControlPin.DTR)
    assertThat(port.rs485Config.isRtsActiveHigh).isFalse()
    assertThat(port.rs485Config.delayBeforeSendMicros).isEqualTo(100)
    assertThat(port.rs485Config.delayAfterSendMicros).isEqualTo(50)
  }

  @Test
  fun `SerialPortBuilder can be used directly`() {
    val builder = SerialPortBuilder("/dev/ttyS0")
    builder.baudRate = 19200
    builder.parity = Parity.ODD

    val port = serialPort("/dev/ttyS0") {
      baudRate = 19200
      parity = Parity.ODD
    }

    assertThat(port.baudRate).isEqualTo(19200)
    assertThat(port.parity).isEqualTo(Parity.ODD)
  }
}
