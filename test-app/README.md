# JR-Serial Test Application

Easy-to-run test programs for validating serial port communication.

## Quick Start

### 1. List Available Tests

```bash
./gradlew :test-app:listTests
```

### 2. Run Loopback Test (Automated Testing)

For testing with two ports connected together or a single port with TX-RX jumper:

```bash
./gradlew :test-app:runLoopback --console=plain
```

**What it does:**

- Automatically tests data transmission
- Validates data integrity
- Tests text and binary data
- Reports pass/fail results

**Requirements:**

- **Option A:** Two serial ports physically connected (TX1→RX2, RX1→TX2, GND→GND)
- **Option B:** One port with TX and RX pins connected together (loopback)

### 3. Run Interactive Terminal

For manual testing with serial devices (Arduino, GPS, modem, etc.):

```bash
./gradlew :test-app:runInteractive --console=plain
```

**What it does:**

- Interactive command-line serial terminal
- Send text commands
- Send binary data (hex format)
- See responses in real-time
- View port information

**Features:**

- Type messages and press Enter to send
- Type `hex:01 02 FF` to send binary data
- Type `clear` to clear input buffer
- Type `info` to show port details
- Type `exit` to quit

### 4. Run Simple Example

Basic demonstration of library features:

```bash
./gradlew :test-app:runSimple --console=plain
```

**What it does:**

- Lists all available serial ports
- Opens a port (if available)
- Demonstrates basic write/read operations

## Hardware Setup

### Single Port Loopback

Connect TX to RX on the same port:

```
USB-to-Serial Adapter
┌─────────────┐
│  TX  ●──────┼──┐
│             │  │ Jumper Wire
│  RX  ●──────┼──┘
│             │
│  GND ●      │
└─────────────┘
```

### Two Port Connection

Connect two ports together:

```
Port 1             Port 2
┌────────┐        ┌────────┐
│  TX  ●─┼────────┼─● RX   │
│        │        │        │
│  RX  ●─┼────────┼─● TX   │
│        │        │        │
│  GND ●─┼────────┼─● GND  │
└────────┘        └────────┘
```

### Device Connection

Connect to a serial device (e.g., Arduino):

```
Computer          Arduino/Device
┌────────┐        ┌────────┐
│  TX  ●─┼────────┼─● RX   │
│        │        │        │
│  RX  ●─┼────────┼─● TX   │
│        │        │        │
│  GND ●─┼────────┼─● GND  │
└────────┘        └────────┘
```

## Usage Examples

### Example 1: Testing with Loopback

1. Connect TX to RX with a jumper wire
2. Run: `./gradlew :test-app:runLoopback --console=plain`
3. Select option `[1]` for single port loopback
4. Choose your port number
5. Watch the automated tests run

**Expected output:**

```
Test: Sending 'Hello, Serial!'
  Bytes available: 15
  Received: 'Hello, Serial!'
  ✓ PASSED

=== Test Results ===
Passed: 5/5
Failed: 0/5
```

### Example 2: Testing Two Connected Ports

1. Connect two USB-to-Serial adapters together
2. Run: `./gradlew :test-app:runLoopback --console=plain`
3. Select option `[2]` for two port test
4. Choose port 1 (sender)
5. Choose port 2 (receiver)
6. Watch bidirectional tests run

### Example 3: Testing with Arduino

1. Upload this sketch to Arduino:

```cpp
void setup() {
  Serial.begin(9600);
}

void loop() {
  if (Serial.available()) {
    Serial.write(Serial.read());  // Echo
  }
}
```

2. Run: `./gradlew :test-app:runInteractive --console=plain`
3. Select the Arduino's port
4. Enter baud rate: `9600`
5. Type messages and see them echoed back

### Example 4: Sending Binary Commands

Using the interactive terminal to send hex data:

```bash
./gradlew :test-app:runInteractive --console=plain
```

Then type:

```
hex:01 02 03 FF FE FD
```

This sends bytes `0x01 0x02 0x03 0xFF 0xFE 0xFD`

## Troubleshooting

### "No serial ports found"

**Linux/macOS:**

```bash
ls /dev/tty*     # or /dev/cu* on macOS
```

**Windows:**

- Check Device Manager → Ports (COM & LPT)

### "Permission denied" (Linux/macOS)

Add your user to the dialout group:

```bash
sudo usermod -a -G dialout $USER
```

Then log out and back in.

### "Port already in use"

Close other programs using the port:

- Arduino IDE Serial Monitor
- Screen, minicom
- Other terminal programs

### Architecture Mismatch (macOS)

If you see "incompatible architecture" errors:

```bash
# Rebuild the library for your architecture
./gradlew clean build
```

## How It Works

1. **Native library is packaged** inside the JAR file
2. **At runtime**, the library is extracted to a temporary directory
3. **Java loads** the library from the temp location
4. **Auto-cleanup**: temp file is deleted when the JVM exits

## Building from Source

Build everything:

```bash
./gradlew build
```

Build just the test app:

```bash
./gradlew :test-app:build
```

## Command Reference

| Command                                              | Description                  |
|------------------------------------------------------|------------------------------|
| `./gradlew :test-app:listTests`                      | Show available test programs |
| `./gradlew :test-app:runLoopback --console=plain`    | Run automated loopback tests |
| `./gradlew :test-app:runInteractive --console=plain` | Run interactive terminal     |
| `./gradlew :test-app:runSimple --console=plain`      | Run simple example           |

**Note:** The `--console=plain` flag is important for interactive programs to work properly!

## Tips

1. **Start simple:** Use `runSimple` to verify ports are detected
2. **Test loopback first:** Validates the library works before testing with devices
3. **Use interactive mode:** Great for debugging and device exploration
4. **Check baud rates:** Most devices use 9600, some use 115200
5. **Common settings:** 8 data bits, no parity, 1 stop bit (8-N-1)

## Common Baud Rates

- **9600** - Most common, works with Arduino, GPS modules
- **19200** - Some industrial devices
- **38400** - Some modems
- **57600** - Faster Arduino communication
- **115200** - High-speed devices, ESP32, modern microcontrollers

## Next Steps

Once tests pass:

- Integrate JR-Serial into your application
- See `../examples/` for more code examples
- Check `../README.md` for API documentation
- Read `../CONTRIBUTING.md` for development guide

## Getting Help

If you encounter issues:

1. Check the hardware wiring
2. Verify port permissions
3. Try a different baud rate
4. Test with a simple terminal program first (screen, minicom)
5. Report issues at: https://github.com/nemecec/jr-serial/issues
