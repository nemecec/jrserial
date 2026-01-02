# JR-Serial Kotlin Extensions

Kotlin extensions for JR-Serial providing idiomatic Kotlin APIs including DSL builders, coroutines support, and Flow-based streaming.

## Installation

Add the Kotlin module alongside the core library:

```kotlin
dependencies {
    implementation("dev.nemecec.jrserial:jrserial:0.1.1")
    implementation("dev.nemecec.jrserial:jrserial-kotlin:0.1.1")
}
```

## Features

- **DSL Builder** - Idiomatic Kotlin configuration syntax
- **Coroutines** - Suspend functions for async I/O
- **Flow Streaming** - Reactive data streams
- **Resource Management** - `withOpen` utilities for safe port handling

## DSL Builder

Create and configure serial ports using a type-safe DSL:

```kotlin
import dev.nemecec.jrserial.kotlin.*

val port = serialPort("/dev/ttyUSB0") {
    baudRate = 115200
    dataBits = DataBits.EIGHT
    stopBits = StopBits.ONE
    parity = Parity.NONE
    flowControl = FlowControl.NONE
    timeout = 2000
    dtrOnOpen = false
}
```

### RS-485 Configuration

```kotlin
val rs485Port = serialPort("/dev/ttyUSB0") {
    baudRate = 9600
    rs485 {
        controlPin = Rs485ControlPin.RTS
        rtsActiveHigh = true
        delayBeforeSendMicros = 100
        delayAfterSendMicros = 50
    }
}

// Or with defaults
val simpleRs485 = serialPort("/dev/ttyUSB0") {
    baudRate = 9600
    rs485()  // Enable with default settings
}
```

## Resource Management

Use `withOpen` to ensure ports are always closed:

```kotlin
val port = serialPort("/dev/ttyUSB0") { baudRate = 115200 }

val response = port.withOpen {
    writeLine("AT")
    readLine()
}
// Port is automatically closed
```

## Coroutines Support

All blocking operations have suspending equivalents that run on `Dispatchers.IO`:

```kotlin
import kotlinx.coroutines.runBlocking

runBlocking {
    val port = serialPort("/dev/ttyUSB0") { baudRate = 115200 }

    port.withOpenAsync {
        writeLineAsync("Hello")
        val response = readLineAsync()
        println("Received: $response")
    }
}
```

### Available Suspend Functions

| Function | Description |
|----------|-------------|
| `readAsync(buffer)` | Read into buffer |
| `readExactlyAsync(length)` | Read exact number of bytes |
| `readLineAsync()` | Read until newline |
| `writeAsync(data)` | Write byte array |
| `writeStringAsync(text)` | Write string |
| `writeLineAsync(text)` | Write string with newline |

All functions also accept an optional `Charset` parameter.

## Flow-Based Streaming

Process serial data as Kotlin Flows for reactive handling:

### Byte Stream

```kotlin
port.bytes().collect { byte ->
    print(byte.toInt().toChar())
}
```

### Chunk Stream

```kotlin
port.chunks(bufferSize = 1024).collect { chunk ->
    println("Received ${chunk.size} bytes")
}
```

### Line Stream

```kotlin
port.lines().collect { line ->
    println("Received: $line")
}
```

### Flow Processing

Flows integrate with all Kotlin Flow operators:

```kotlin
port.lines()
    .filter { it.startsWith("DATA:") }
    .map { it.removePrefix("DATA:").trim() }
    .take(10)
    .collect { data ->
        processData(data)
    }
```

## Complete Example

```kotlin
import dev.nemecec.jrserial.kotlin.*
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val port = serialPort("/dev/ttyUSB0") {
        baudRate = 115200
        timeout = 1000
    }

    port.withOpenAsync {
        // Send command
        writeLineAsync("AT+VERSION")

        // Read response lines
        val responses = lines()
            .take(3)
            .toList()

        responses.forEach { println(it) }
    }
}
```

## API Reference

### SerialPortDsl.kt

- `serialPort(portName, block)` - Create port with DSL configuration
- `SerialPortBuilder` - DSL builder class
- `Rs485Builder` - RS-485 configuration DSL

### SerialPortCoroutines.kt

- `readAsync(buffer)` / `readAsync(buffer, offset, length)`
- `readExactlyAsync(buffer, offset, length)` / `readExactlyAsync(length)`
- `readLineAsync()` / `readLineAsync(charset)`
- `writeAsync(data)`
- `writeStringAsync(text)` / `writeStringAsync(text, charset)`
- `writeLineAsync(text)` / `writeLineAsync(text, charset)`

### SerialPortFlow.kt

- `bytes()` - Flow of individual bytes
- `chunks(bufferSize)` - Flow of byte arrays
- `lines()` / `lines(charset)` - Flow of lines

### SerialPortUtils.kt

- `withOpen(block)` - Execute block with port open
- `withOpenAsync(block)` - Suspending version of withOpen
