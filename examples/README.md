# JR-Serial Examples

This directory contains example programs demonstrating various features of the JR-Serial library.

## Examples

### SimpleExample.java

Basic serial port usage demonstrating:

- Listing available ports
- Opening a port
- Writing data
- Reading data
- Closing a port

Run with:

```bash
java -cp jrserial.jar examples.SimpleExample
```

### StreamExample.java

Stream-based I/O demonstrating:

- Using InputStream/OutputStream
- BufferedReader for line-based reading
- Reading AT commands and responses
- Non-blocking availability checks

Run with:

```bash
java -cp jrserial.jar examples.StreamExample
```

### AdvancedExample.java

Advanced features demonstrating:

- Custom port configuration (baud rate, data bits, etc.)
- Binary data transfer
- Hex dump of received data
- Partial reads and writes with offsets
- Buffer management (clear, flush)
- Dynamic timeout changes
- Large data transfers in chunks

Run with:

```bash
java -cp jrserial.jar examples.AdvancedExample
```

## Requirements

- A serial port device (USB-to-Serial adapter, Arduino, etc.)
- Appropriate permissions to access serial ports
- JR-Serial library on the classpath

## Notes

These examples assume you have at least one serial port available on your system. If you don't have a physical serial
device, you can create virtual serial port pairs for testing:

### Linux/macOS

Use `socat`:

```bash
socat -d -d pty,raw,echo=0 pty,raw,echo=0
```

### Windows

Use com0com or similar virtual serial port software.

## Troubleshooting

### No Ports Found

If no ports are found:

- Check that your serial device is connected
- Verify permissions (on Linux/macOS, add user to `dialout` group)
- Check device manager (Windows) or `ls /dev/tty*` (Linux/macOS)

### Access Denied

On Linux/macOS:

```bash
sudo usermod -a -G dialout $USER
# Log out and back in for changes to take effect
```

### Port Already in Use

Make sure no other application is using the serial port. Close any serial monitor applications or other programs that
might have the port open.
