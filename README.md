# JR-Serial

A Java/Kotlin serial communication library with a high-performance Rust native backend using JNI.

## Features

- Cross-platform support (Windows, macOS, Linux)
- High-performance native implementation in Rust
- Simple and intuitive Java API
- Compatible with Java 8+
- Builder pattern for easy configuration
- Stream-based I/O support
- Automatic native library loading
- [Kotlin extensions](kotlin/README.md) with DSL, coroutines, and Flow support

## Thread Safety

`SerialPort` instances are **not thread-safe**. A single port should only be accessed from one thread at a time. If you need to use a serial port from multiple threads, you must provide your own synchronization (e.g., using `synchronized` blocks or locks).

## Supported Platforms

- Windows (x86, x86_64)
- macOS (x86_64, ARM64/Apple Silicon)
- Linux (x86, x86_64, ARM64, ARM 32-bit)
- FreeBSD (x86_64)

## Installation

Add the dependency to your `build.gradle`:

```groovy
dependencies {
  implementation 'dev.nemecec.jrserial:jrserial:0.1.0-SNAPSHOT'
}
```

Or for Maven in `pom.xml`:

```xml
<dependency>
  <groupId>dev.nemecec.jrserial</groupId>
  <artifactId>jrserial</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### JAR Variants

The library is published with multiple JAR variants to suit different deployment scenarios.
This is particularly useful in constrained environments where JAR size matters (e.g. embedded systems).

| Variant           | Classifier           | Size    | Use Case                                |
|-------------------|----------------------|---------|-----------------------------------------|
| Full              | *(none)*             | ~1.1 MB | Default, includes all platform binaries |
| Lite              | `lite`               | ~22 KB  | User provides their own native binary   |
| Platform-specific | e.g., `linux-x86_64` | ~200 KB | Single target platform                  |

#### Full JAR (default)

Contains native binaries for all supported platforms. Use this when:
- Deploying to multiple platforms from the same artifact
- You don't have strict size constraints

#### Lite JAR

Contains no native binaries. Use this when:
- You want to provide a custom-built native library (based on the code in this repository)
- You're using an unsupported platform and ported the library yourself
- You need the smallest possible JAR and will configure `jrserial.library.path`

**Gradle:**
```groovy
implementation 'dev.nemecec.jrserial:jrserial:0.1.0-SNAPSHOT:lite'
```

**Maven:**
```xml
<dependency>
  <groupId>dev.nemecec.jrserial</groupId>
  <artifactId>jrserial</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <classifier>lite</classifier>
</dependency>
```

When using the lite JAR, you must configure the native library path:
```bash
java -Djrserial.library.path=/path/to/libjrserial.so -jar myapp.jar
```

#### Platform-Specific JARs

Contains only the native binary for a single platform. Use this when:
- You know the exact target platform at build time
- You want to minimize JAR size but still have automatic library loading

Available classifiers:
- `linux-x86_64`, `linux-x86`, `linux-aarch64`, `linux-arm`, `linux-armhf`, `linux-armv7hf`
- `darwin-x86_64`, `darwin-aarch64`
- `windows-x86_64`, `windows-x86`
- `freebsd-x86_64`

**Gradle:**
```groovy
implementation 'dev.nemecec.jrserial:jrserial:0.1.0-SNAPSHOT:linux-x86_64'
```

**Maven:**
```xml
<dependency>
  <groupId>dev.nemecec.jrserial</groupId>
  <artifactId>jrserial</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <classifier>linux-aarch64</classifier>
</dependency>
```

## Usage

### Basic Example

```java
import dev.nemecec.jrserial.SerialPort;

public class Example {

  public static void main(String[] args) throws Exception {
    // Create and configure a serial port
    SerialPort port = SerialPort.builder()
        .portName("/dev/ttyUSB0")  // or "COM3" on Windows
        .baudRate(9600)
        .build();

    try {
      // Open the port
      port.open();

      // Write data
      String message = "Hello, Serial!";
      port.write(message.getBytes());

      // Read data
      byte[] buffer = new byte[256];
      int bytesRead = port.read(buffer);
      if (bytesRead > 0) {
        String response = new String(buffer, 0, bytesRead);
        System.out.println("Received: " + response);
      }
    }
    finally {
      // Always close the port
      port.close();
    }
  }

}
```

### Advanced Configuration

```java
import dev.nemecec.jrserial.*;

SerialPort port = SerialPort.builder()
    .portName("/dev/ttyUSB0")
    .baudRate(115200)
    .dataBits(DataBits.EIGHT)
    .stopBits(StopBits.ONE)
    .parity(Parity.NONE)
    .flowControl(FlowControl.NONE)  // default
    .timeout(2000)  // 2 seconds
    .build();
```

### Using Streams

```java
import java.io.*;

SerialPort port = SerialPort.builder()
    .portName("/dev/ttyUSB0")
    .baudRate(9600)
    .build();

port.open();

    // Use as InputStream/OutputStream
    InputStream in=port.getInputStream();
    OutputStream out=port.getOutputStream();

    // Write using stream
    out.write("Hello\n".getBytes());
    out.flush();

    // Read using stream
    BufferedReader reader=new BufferedReader(new InputStreamReader(in));
    String line=reader.readLine();

    port.close();
```

**Note:** The `InputStream.read()` methods return `-1` when no data is available within the configured timeout. Unlike file streams where `-1` indicates end-of-file, for serial ports this simply means "no data yet" - you can continue reading from the stream.

### List Available Ports

```java
import dev.nemecec.jrserial.SerialPort;
import dev.nemecec.jrserial.SerialPortInfo;

import java.util.List;

List<SerialPortInfo> ports = SerialPort.listPorts();
for (SerialPortInfo port : ports) {
    System.out.println("Available port: " + port);
    // port.getPortName() - the device path (e.g., "/dev/ttyUSB0")
    // port.isSymlink() - true if the port is a symbolic link
    // port.isPseudoTerminal() - true if the port is a PTY device
    // port.isBluetooth() - true if the port is a Bluetooth serial port
}
```

### Buffer Management

```java
// Check available bytes
int available = port.available();

// Clear buffers
port.clearInput();   // Clear input buffer
port.clearOutput();  // Clear output buffer
port.clearAll();     // Clear both buffers

// Flush output
port.flush();
```

### Convenience Read Methods

```java
// Block until exactly N bytes are received (throws on timeout)
byte[] response = port.readExactly(10);

// Or read into existing buffer
byte[] buffer = new byte[256];
port.readExactly(buffer, 0, 10);

// With custom timeout (500ms) for this specific read
port.readExactly(buffer, 0, 10, 500);

// Read only what's currently available (non-blocking, returns immediately)
int bytesRead = port.readAvailable(buffer);

// Read a complete line (until \n)
String line = port.readLine();

// Write string without newline
port.writeString("AT");

// Write string with newline (equivalent to writeString(text + "\n"))
port.writeLine("AT+GMR");
```

### RS-485 Half-Duplex Communication

JR-Serial supports RS-485 half-duplex communication with automatic RTS/DTR control:

```java
import dev.nemecec.jrserial.*;

// Simple RS-485 with defaults (RTS control, active high)
SerialPort port = SerialPort.builder()
    .portName("/dev/ttyUSB0")
    .baudRate(9600)
    .rs485()  // Enable RS-485 with default settings
    .build();

port.open();

// Check if kernel RS-485 mode is active (Linux only)
if (port.isKernelRs485Active()) {
    System.out.println("Using kernel RS-485 mode for optimal timing");
}

// Write automatically handles RTS control
port.write("Hello RS-485!".getBytes());
```

#### Custom RS-485 Configuration

For advanced control, use `Rs485Config` to configure all RS-485 parameters:

```java
Rs485Config config = Rs485Config.builder()
    .enabled(true)
    .controlPin(Rs485ControlPin.RTS)  // or DTR
    .rtsActiveHigh(true)              // RTS polarity
    .rxDuringTx(false)                // Disable receiver during transmit
    .terminationEnabled(false)        // Bus termination (if hardware supports)
    .delayBeforeSendMicros(0)         // Delay before asserting RTS
    .delayAfterSendMicros(0)          // Delay after de-asserting RTS
    .build();

SerialPort port = SerialPort.builder()
    .portName("/dev/ttyUSB0")
    .baudRate(9600)
    .rs485Config(config)
    .build();
```

#### Control Pins

- **RTS** - Request To Send (default, supported by kernel RS-485)
- **DTR** - Data Terminal Ready (software control only)

```java
// Use DTR instead of RTS for direction control
Rs485Config config = Rs485Config.builder()
    .controlPin(Rs485ControlPin.DTR)
    .build();

SerialPort port = SerialPort.builder()
    .portName("/dev/ttyUSB0")
    .baudRate(9600)
    .rs485Config(config)
    .build();
```

#### Runtime Configuration

RS-485 settings can be changed on an open port:

```java
port.open();

// Enable RS-485 after opening
port.setRs485Config(Rs485Config.enabled());

// Change configuration
Rs485Config newConfig = Rs485Config.builder()
    .controlPin(Rs485ControlPin.DTR)
    .rtsActiveHigh(false)
    .build();
port.setRs485Config(newConfig);
```

#### Linux Kernel RS-485 Mode

On Linux with supported hardware (native UART with RS-485 support), the library uses
kernel-level RS-485 mode via ioctl. This provides:

- Hardware-timed RTS transitions (microsecond precision)
- No race conditions between software and data transmission
- Configurable pre/post transmission delays

## API Reference

### SerialPort

Main class for serial communication.

#### Methods

- `static Builder builder()` - Create a new builder
- `static List<SerialPortInfo> listPorts()` - List available serial ports with type info
- `void open()` - Open the serial port
- `void close()` - Close the serial port
- `int write(byte[] data)` - Write data to the port
- `int writeString(String text)` - Write a UTF-8 string to the port
- `int writeString(String text, Charset charset)` - Write a string with specified charset
- `int writeLine(String text)` - Write a UTF-8 string followed by newline
- `int writeLine(String text, Charset charset)` - Write a string with newline using specified charset
- `int read(byte[] buffer)` - Read data from the port
- `int readExactly(byte[] buffer, int offset, int length)` - Block until exact bytes read
- `int readExactly(byte[] buffer, int offset, int length, long timeout)` - Block with custom timeout
- `byte[] readExactly(int length)` - Block until exact bytes read, returns new array
- `int readAvailable(byte[] buffer)` - Read only currently available bytes (non-blocking)
- `String readLine()` - Read until newline character (UTF-8)
- `String readLine(Charset charset)` - Read until newline with specified charset
- `int available()` - Get number of bytes available to read
- `void flush()` - Flush output buffer
- `void setTimeout(int ms)` - Set read timeout
- `void clearInput()` - Clear input buffer
- `void clearOutput()` - Clear output buffer
- `void clearAll()` - Clear both buffers
- `InputStream getInputStream()` - Get input stream
- `OutputStream getOutputStream()` - Get output stream
- `boolean isOpen()` - Check if port is open

### Builder

#### Methods

- `portName(String name)` - Set port name (required)
- `baudRate(int rate)` - Set baud rate (default: 9600)
- `dataBits(DataBits bits)` - Set data bits (default: EIGHT)
- `stopBits(StopBits bits)` - Set stop bits (default: ONE)
- `parity(Parity parity)` - Set parity (default: NONE)
- `flowControl(FlowControl fc)` - Set flow control (default: NONE)
- `dtrOnOpen(boolean)` - Control DTR on open (default: true)
- `timeout(int ms)` - Set timeout in milliseconds (default: 1000)
- `SerialPort build()` - Build the SerialPort instance

### Enums

#### DataBits

- `FIVE` - 5 data bits
- `SIX` - 6 data bits
- `SEVEN` - 7 data bits
- `EIGHT` - 8 data bits (most common)

#### StopBits

- `ONE` - 1 stop bit (most common)
- `TWO` - 2 stop bits

#### Parity

- `NONE` - No parity (most common)
- `ODD` - Odd parity
- `EVEN` - Even parity

#### FlowControl

- `NONE` - No flow control (default)
- `SOFTWARE` - Software flow control (XON/XOFF)
- `HARDWARE` - Hardware flow control (RTS/CTS)

## Native Library Loading

The library automatically loads the correct native binary for your platform. The loading order is:

1. **Explicit path** via system property `jrserial.library.path`
2. **System library path** (`java.library.path`)
3. **Bundled JAR resources** at `/native/<platform>/`

### Override the Native Library

You can override the bundled native library using the `jrserial.library.path` system property:

```bash
# Load from filesystem
java -Djrserial.library.path=/opt/custom/libjrserial.so -jar myapp.jar

# Load from classpath (e.g., from another JAR)
java -Djrserial.library.path=classpath:/native/custom/libjrserial.so -jar myapp.jar
```

### Force a Specific Platform

Use `jrserial.platform` to force loading a specific platform's library from bundled resources:

```bash
java -Djrserial.platform=linux-x86_64 -jar myapp.jar
```

Available platform values:
- `darwin-x86_64`, `darwin-aarch64` - macOS
- `linux-x86_64`, `linux-x86`, `linux-aarch64` - Linux x86/x64
- `linux-arm` - Linux ARM 32-bit (soft float, ARMv5 compatible)
- `linux-armhf` - Linux ARM 32-bit (hard float)
- `linux-armv7hf` - Linux ARMv7 (hard float, optimized)
- `windows-x86_64`, `windows-x86` - Windows
- `freebsd-x86_64` - FreeBSD

On ARM 32-bit systems, the library auto-detects and loads `linux-arm` (soft float) for maximum compatibility. If your system supports hard float, you can force a more optimized build:

```bash
# For ARMv7 with hard float (e.g., Raspberry Pi 2/3 32-bit OS)
java -Djrserial.platform=linux-armv7hf -jar myapp.jar

# For ARMv6 with hard float (e.g., Raspberry Pi 1/Zero)
java -Djrserial.platform=linux-armhf -jar myapp.jar
```

### Using java.library.path

Place your custom library in a directory and add it to `java.library.path`:

```bash
java -Djava.library.path=/opt/mylibs -jar myapp.jar
```

The library file should be named `libjrserial.so` (Linux), `libjrserial.dylib` (macOS), or `jrserial.dll` (Windows).

### Arduino (Preventing auto-reset)

Arduino boards use the DTR signal connected to the reset pin through a capacitor.
When a serial port is opened, DTR is typically asserted, which resets the Arduino.

To prevent this automatic reset:

```java
SerialPort port = SerialPort.builder()
    .portName("/dev/ttyUSB0")
    .baudRate(9600)
    .dtrOnOpen(false)  // Suppress DTR to prevent Arduino reset
    .build();
```

## Platform Notes

### Linux

**Timeout Granularity**: Linux serial port timeouts use POSIX termios, which only supports
decisecond (100ms) precision via the VTIME parameter. Timeouts are automatically rounded UP
to the nearest 100ms to ensure the timeout never fires earlier than requested:
- 50ms → 100ms
- 150ms → 200ms
- 1000ms → 1000ms (no change)

**Kernel RS-485 Mode**: On Linux systems with supported UART hardware, the library uses
kernel-level RS-485 mode via ioctl for optimal timing. This provides hardware-timed RTS
transitions with microsecond precision. Use `isKernelRs485Active()` to check if this mode
is active.

### macOS

**RS-485**: Only manual (software) RTS/DTR control is available. The library toggles the
control pin in software before and after each write operation.

### Windows

**RS-485**: Only manual (software) RTS/DTR control is available. Works similarly to macOS.

**Port Names**: Use the format `COM1`, `COM3`, etc.

### FreeBSD

**RS-485**: Only manual (software) RTS/DTR control is available.

## Troubleshooting

### Library Not Found

If you get a `UnsatisfiedLinkError`, ensure that:

1. The native library for your platform is included in the JAR
2. You have the necessary permissions to create temporary files
3. Your system architecture is supported

### Port Access Denied

On Linux/macOS, you may need to add your user to the appropriate group:

```bash
# Linux
sudo usermod -a -G dialout $USER

# macOS - grant permission in System Preferences > Security & Privacy
```

## License

Apache License 2.0

## Contributing

Contributions are welcome! See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup and build instructions.
