# Contributing to JR-Serial

## Development Requirements

- Java 24 or higher (for building; the library targets Java 8 bytecode)
- Rust toolchain (`rustup`)
- Gradle 8.11+
- Docker (optional, for cross-compilation and testing)

## Building from Source

### Build for Current Platform

```bash
./gradlew build
```

This compiles the Rust native library for your current platform, builds the Java code, and runs tests.

### Build for Specific Targets

Build native libraries for specific platforms:

```bash
# Linux x86_64
./gradlew copyNative_x86_64_unknown_linux_gnu

# Linux ARM64
./gradlew copyNative_aarch64_unknown_linux_gnu

# macOS x86_64
./gradlew copyNative_x86_64_apple_darwin

# macOS ARM64 (Apple Silicon)
./gradlew copyNative_aarch64_apple_darwin

# Windows x86_64 (cross-compile with GNU toolchain)
./gradlew copyNative_x86_64_pc_windows_gnu
```

### Build by Host Platform (for CI)

Targets are grouped by which host platform can build them:

```bash
# Linux runner: Linux targets + FreeBSD
./gradlew buildLinuxHostTargets

# macOS runner: macOS targets only
./gradlew buildMacosHostTargets

# Windows runner: Windows MSVC targets only
./gradlew buildWindowsHostTargets
```

The CI workflow builds on all three platforms and combines the artifacts into a single JAR.

### Build for Custom Target

```bash
./gradlew copyNativeCustom -PrustTarget=armv7-unknown-linux-gnueabihf
```

## Cross-Compilation

Cross-compilation to different OS families requires the `cross` tool:

```bash
cargo install cross --git https://github.com/cross-rs/cross
```

The build system automatically uses `cross` when building for a different platform than the host, except:
- **macOS** can natively cross-compile between `x86_64-apple-darwin` and `aarch64-apple-darwin`
- **Windows** can natively cross-compile between `x86_64-pc-windows-msvc` and `i686-pc-windows-msvc`

For these same-OS cross-compilations, only the Rust target needs to be installed (e.g., `rustup target add x86_64-apple-darwin`).

### Supported Rust Targets

**Targets built in CI** (included in release JAR):

| Gradle Task | Rust Target | Platform | CI Runner |
|-------------|-------------|----------|-----------|
| `copyNative_x86_64_unknown_linux_gnu` | `x86_64-unknown-linux-gnu` | Linux x86_64 | Linux |
| `copyNative_i686_unknown_linux_gnu` | `i686-unknown-linux-gnu` | Linux x86 (32-bit) | Linux |
| `copyNative_aarch64_unknown_linux_gnu` | `aarch64-unknown-linux-gnu` | Linux ARM64 | Linux |
| `copyNative_armv5te_unknown_linux_gnueabi` | `armv5te-unknown-linux-gnueabi` | Linux ARMv5 (soft float) | Linux |
| `copyNative_arm_unknown_linux_gnueabi` | `arm-unknown-linux-gnueabi` | Linux ARM (soft float) | Linux |
| `copyNative_arm_unknown_linux_gnueabihf` | `arm-unknown-linux-gnueabihf` | Linux ARM (hard float) | Linux |
| `copyNative_armv7_unknown_linux_gnueabihf` | `armv7-unknown-linux-gnueabihf` | Linux ARMv7 (hard float) | Linux |
| `copyNative_x86_64_unknown_freebsd` | `x86_64-unknown-freebsd` | FreeBSD x86_64 | Linux |
| `copyNative_x86_64_apple_darwin` | `x86_64-apple-darwin` | macOS x86_64 | macOS |
| `copyNative_aarch64_apple_darwin` | `aarch64-apple-darwin` | macOS ARM64 | macOS |
| `copyNative_x86_64_pc_windows_msvc` | `x86_64-pc-windows-msvc` | Windows x64 (MSVC) | Windows |
| `copyNative_i686_pc_windows_msvc` | `i686-pc-windows-msvc` | Windows x86 (MSVC) | Windows |

**Manual targets** (for cross-compiling Windows from Linux):

| Gradle Task | Rust Target | Platform |
|-------------|-------------|----------|
| `copyNative_x86_64_pc_windows_gnu` | `x86_64-pc-windows-gnu` | Windows x64 (GNU) |
| `copyNative_i686_pc_windows_gnu` | `i686-pc-windows-gnu` | Windows x86 (GNU) |

The GNU toolchain Windows targets can be cross-compiled from Linux using `cross`. They are not included in CI because MSVC builds are preferred for Windows.

### Building Extra Targets Locally

By default, `./gradlew build` only builds for the host platform. To also build for additional targets during local development, create `extra-target-architectures.txt` in the project root (this file is git-ignored):

```bash
# extra-target-architectures.txt
# One Rust target per line. Lines starting with # are comments.
armv5te-unknown-linux-gnueabi
aarch64-unknown-linux-gnu
```

With this file present, `./gradlew build` will build both the host platform and all listed targets.

## Testing

### Quick Test with Hardware

To quickly validate serial communication with hardware:

```bash
# List available test programs
./gradlew :test-app:listTests

# Run loopback test (requires TX-RX jumper or two connected ports)
./gradlew :test-app:runLoopback --console=plain

# Run interactive terminal (for testing with devices)
./gradlew :test-app:runInteractive --console=plain

# Run simple example (lists ports)
./gradlew :test-app:runSimple --console=plain
```

See [test-app/README.md](test-app/README.md) for detailed hardware setup and usage.

### Run Unit Tests

```bash
./gradlew test
```

### Testing in Docker

Run integration tests in a Linux Docker container with virtual serial ports:

```bash
./docker-test.sh
```

This script:
1. Cross-compiles the native library for Linux x86_64
2. Builds the Java code
3. Runs tests in a Docker container using `socat` for virtual serial ports

The Docker tests verify that the native library works correctly on Linux, even when developing on macOS or Windows.

### Hardware Testing (RS-485)

For testing with real RS-485 hardware, a separate test framework is available that can execute tests on remote machines via SSH.

#### Prerequisites

- Two machines connected via RS-485 (or one machine with loopback adapter)
- SSH access to the test machines
- Java installed on test machines

#### Setup

1. Build the test application JAR:
   ```bash
   ./gradlew :hardware-test:testAppJar
   ```

2. Create configuration file:
   ```bash
   cp hardware-test/src/test/resources/rs485-test-config.example.yaml \
      hardware-test/src/test/resources/rs485-test-config.yaml
   ```

3. Edit `rs485-test-config.yaml` with your machine details (SSH credentials, serial ports, etc.)

4. Run the hardware tests:
   ```bash
   ./run-hardware-test.sh
   ```

   Or manually:
   ```bash
   ./gradlew :hardware-test:testAppJar
   ./gradlew :hardware-test:test -DhardwareTest=true
   ```

#### Configuration Options

The configuration file supports:
- Multiple machines with SENDER, RECEIVER, or BOTH roles
- Password or SSH key authentication

See `rs485-test-config.example.yaml` for all options.

#### Security Note

The `rs485-test-config.yaml` file is gitignored because it might contain SSH credentials.

## Architecture

JR-Serial uses a hybrid architecture:

```
┌─────────────────────────────────────┐
│         Java Application            │
├─────────────────────────────────────┤
│    dev.nemecec.jrserial (Java)      │
│    - SerialPort, Builder, Enums     │
│    - Stream wrappers                │
│    - Native library loader          │
├─────────────────────────────────────┤
│           JNI Bridge                │
├─────────────────────────────────────┤
│      native/ (Rust)                 │
│    - serialport crate               │
│    - RS-485 support                 │
└─────────────────────────────────────┘
```

### Key Components

- **Java Layer** (`src/main/java/dev/nemecec/jrserial/`)
  - `SerialPort` - Main API class with builder pattern
  - `NativeSerialPort` - JNI native method declarations
  - `NativeLibraryLoader` - Platform detection and library loading

- **Rust Layer** (`native/src/`)
  - `lib.rs` - JNI bindings using the `jni` crate
  - `platform_linux.rs` - Linux-specific serial port wrapper with kernel RS-485 ioctl support
  - `platform_other.rs` - Non-Linux serial port wrapper with manual RTS/DTR control
  - Uses the `serialport` crate for basic serial I/O

### RS-485 Implementation

The RS-485 support has platform-specific implementations:

**Linux:**
```
┌─────────────────────────────────────┐
│    Rs485Config.enabled == true      │
├─────────────────────────────────────┤
│  Try kernel RS-485 via ioctl        │
│  (TIOCSRS485 / TIOCGRS485)          │
├─────────────────────────────────────┤
│  Success?                           │
│  ├─ Yes: Use kernel mode            │
│  │       (hardware-timed RTS)       │
│  └─ No:  Fall back to manual        │
│          (software RTS/DTR control) │
└─────────────────────────────────────┘
```

**Non-Linux (macOS, Windows):**
- Only manual RTS/DTR control is available
- Software toggles the pin before/after each write

**Key files:**
- `native/src/lib.rs` - JNI exports and port handle management
- `native/src/platform_linux.rs` - Linux `PortWrapper` using `TTYPort` with kernel RS-485 ioctl
- `native/src/platform_other.rs` - Non-Linux `PortWrapper` using `Box<dyn SerialPort>` with manual control
- `libc` crate (Linux only) - For `ioctl()` system calls

**Kernel RS-485 flags used:**
- `SER_RS485_ENABLED` - Enable RS-485 mode
- `SER_RS485_RTS_ON_SEND` - RTS high during transmit (if rtsActiveHigh)
- `SER_RS485_RTS_AFTER_SEND` - RTS low after transmit
- `SER_RS485_RX_DURING_TX` - Enable receiver during transmit
- `SER_RS485_TERMINATE_BUS` - Enable bus termination

### Native Library Resources

Built native libraries are placed in:
```
src/main/resources/native/
├── darwin-aarch64/libjrserial.dylib
├── darwin-x86_64/libjrserial.dylib
├── linux-aarch64/libjrserial.so
├── linux-x86_64/libjrserial.so
└── windows-x86_64/jrserial.dll
```

## Code Style

- Java: Follow standard Java conventions
- Rust: Use `cargo fmt` before committing

## Submitting Changes

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run tests: `./gradlew test`
5. Submit a pull request

## Troubleshooting Build Issues

### Rust toolchain not found

```bash
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
```

### Cross not found

```bash
cargo install cross --git https://github.com/cross-rs/cross
```

### Docker not running (for cross-compilation)

Cross-compilation with `cross` requires Docker. Make sure Docker Desktop is running.

### Gradle daemon PATH issues

The Gradle daemon may not have `~/.cargo/bin` in PATH. The build script automatically searches common locations for `cargo` and `cross` binaries.
