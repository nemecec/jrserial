// Copyright 2025 Neeme Praks
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

use jni::objects::{JByteArray, JClass, JString};
use jni::sys::{jboolean, jint, jlong, jstring};
use jni::JNIEnv;
use serialport::{DataBits, FlowControl, Parity, SerialPortType, StopBits};
// On Linux, TTYPort requires SerialPort trait in scope for method calls
#[cfg(target_os = "linux")]
use serialport::SerialPort;
use std::cell::RefCell;
use std::io::{Read, Write};
use std::time::Duration;

// ============================================================================
// Error Context Tracking
// ============================================================================

/// Stores context about the last error that occurred in native code.
/// This provides detailed diagnostic information for debugging.
#[derive(Clone)]
struct ErrorContext {
    message: String,
    file: &'static str,
    line: u32,
}

thread_local! {
    static LAST_ERROR: RefCell<Option<ErrorContext>> = const { RefCell::new(None) };
}

/// Sets the last error with automatic file and line capture.
/// Use this macro at error sites to record diagnostic information.
macro_rules! set_error {
    ($msg:expr) => {
        LAST_ERROR.with(|e| {
            *e.borrow_mut() = Some(ErrorContext {
                message: $msg.to_string(),
                file: file!(),
                line: line!(),
            });
        });
    };
}

/// Clears the last error. Call this at the start of operations to ensure
/// stale errors don't persist.
fn clear_error() {
    LAST_ERROR.with(|e| {
        *e.borrow_mut() = None;
    });
}

/// Gets the last error as a formatted string, or None if no error.
fn get_last_error_string() -> Option<String> {
    LAST_ERROR.with(|e| {
        e.borrow().as_ref().map(|ctx| {
            format!("{} (at {}:{})", ctx.message, ctx.file, ctx.line)
        })
    })
}

// ============================================================================
// Platform-Specific Timeout Handling
// ============================================================================

/// Rounds timeout to platform-appropriate granularity.
///
/// On Linux/POSIX, serial port timeouts (via termios VTIME) only support
/// decisecond (100ms) precision. This function rounds UP to ensure we never
/// timeout earlier than requested. A value of 0 is preserved as it means
/// "blocking/no timeout" in POSIX.
///
/// On other platforms (Windows, macOS), the timeout is passed through unchanged.
fn normalize_timeout_ms(timeout_ms: u64) -> Duration {
    #[cfg(target_os = "linux")]
    {
        if timeout_ms == 0 {
            Duration::from_millis(0)
        } else {
            // Round up to nearest 100ms
            let rounded = ((timeout_ms + 99) / 100) * 100;
            Duration::from_millis(rounded)
        }
    }

    #[cfg(not(target_os = "linux"))]
    {
        Duration::from_millis(timeout_ms)
    }
}

/// RS-485 control mode
#[derive(Debug, Clone, Copy, PartialEq)]
enum Rs485ControlMode {
    /// No RS-485 control
    None,
    /// Automatic: use kernel mode on Linux, manual mode on other platforms
    Auto,
    /// Force manual RTS/DTR control even on Linux
    Manual,
}

/// Which pin to use for manual RS-485 control
#[derive(Debug, Clone, Copy, PartialEq)]
enum Rs485ControlPin {
    RTS,
    DTR,
}

// Platform-specific port wrapper implementations
// On Linux, we store TTYPort directly to access RS-485 kernel mode
// On other platforms, we use Box<dyn SerialPort>

#[cfg(target_os = "linux")]
#[path = "platform_linux.rs"]
mod platform;

#[cfg(not(target_os = "linux"))]
#[path = "platform_other.rs"]
mod platform;

use platform::PortWrapper;

/// Convert Java String to Rust String
fn jstring_to_string(env: &mut JNIEnv, jstr: JString) -> Result<String, String> {
    env.get_string(&jstr)
        .map(|s| s.into())
        .map_err(|e| format!("Failed to convert JString: {}", e))
}

/// Create Java String from Rust String
fn string_to_jstring(env: &mut JNIEnv, s: &str) -> jstring {
    env.new_string(s)
        .map(|js| js.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

/// Open a serial port and return a pointer to the boxed PortWrapper
/// rs485_mode: 0 = None, 1 = Auto, 2 = Manual
/// rs485_pin: 0 = RTS, 1 = DTR
#[no_mangle]
pub extern "system" fn Java_dev_nemecec_jrserial_NativeSerialPort_open(
    mut env: JNIEnv,
    _class: JClass,
    port_name: JString,
    baud_rate: jint,
    data_bits: jint,
    stop_bits: jint,
    parity: jint,
    timeout_ms: jint,
    rs485_mode: jint,
    rs485_pin: jint,
) -> jlong {
    let port_name = match jstring_to_string(&mut env, port_name) {
        Ok(s) => s,
        Err(e) => {
            set_error!(format!("Invalid port name: {}", e));
            return 0;
        }
    };

    let data_bits = match data_bits {
        5 => DataBits::Five,
        6 => DataBits::Six,
        7 => DataBits::Seven,
        8 => DataBits::Eight,
        _ => DataBits::Eight,
    };

    let stop_bits = match stop_bits {
        1 => StopBits::One,
        2 => StopBits::Two,
        _ => StopBits::One,
    };

    let parity = match parity {
        0 => Parity::None,
        1 => Parity::Odd,
        2 => Parity::Even,
        _ => Parity::None,
    };

    let control_mode = match rs485_mode {
        0 => Rs485ControlMode::None,
        1 => Rs485ControlMode::Auto,
        2 => Rs485ControlMode::Manual,
        _ => Rs485ControlMode::None,
    };

    let control_pin = match rs485_pin {
        0 => Rs485ControlPin::RTS,
        1 => Rs485ControlPin::DTR,
        _ => Rs485ControlPin::RTS,
    };

    let timeout = normalize_timeout_ms(timeout_ms as u64);

    let builder = serialport::new(port_name, baud_rate as u32)
        .data_bits(data_bits)
        .stop_bits(stop_bits)
        .parity(parity)
        .flow_control(FlowControl::None)
        .timeout(timeout);

    // Platform-specific port opening
    #[cfg(target_os = "linux")]
    let port_result = builder.open_native();

    #[cfg(not(target_os = "linux"))]
    let port_result = builder.open();

    match port_result {
        Ok(port) => {
            let mut wrapper = PortWrapper::new(port);

            // Configure RS-485 mode if requested
            if control_mode != Rs485ControlMode::None {
                if let Err(e) = wrapper.configure_rs485(control_mode, control_pin) {
                    set_error!(format!("Failed to configure RS-485: {}", e));
                    return 0;
                }
            }

            let boxed = Box::new(wrapper);
            Box::into_raw(boxed) as jlong
        }
        Err(e) => {
            set_error!(format!("Failed to open port: {}", e));
            0
        }
    }
}

/// Close the serial port
#[no_mangle]
pub extern "system" fn Java_dev_nemecec_jrserial_NativeSerialPort_close(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle != 0 {
        unsafe {
            let _ = Box::from_raw(handle as *mut PortWrapper);
        }
    }
}

/// Write data to the serial port with automatic RS-485 control
#[no_mangle]
pub extern "system" fn Java_dev_nemecec_jrserial_NativeSerialPort_write(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    data: JByteArray,
    offset: jint,
    length: jint,
) -> jint {
    if handle == 0 {
        set_error!("Write failed: port handle is null");
        return -1;
    }

    let mut buffer = vec![0i8; length as usize];
    if let Err(e) = env.get_byte_array_region(&data, offset, &mut buffer[..]) {
        set_error!(format!("Write failed: could not read buffer: {}", e));
        return -1;
    }

    // Convert i8 to u8 for writing
    let u8_buffer: Vec<u8> = buffer.iter().map(|&b| b as u8).collect();

    unsafe {
        let wrapper = &mut *(handle as *mut PortWrapper);
        match wrapper.write_rs485(&u8_buffer) {
            Ok(n) => n as jint,
            Err(e) => {
                set_error!(format!("Write failed: {}", e));
                -1
            }
        }
    }
}

/// Read data from the serial port
#[no_mangle]
pub extern "system" fn Java_dev_nemecec_jrserial_NativeSerialPort_read(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    buffer: JByteArray,
    offset: jint,
    length: jint,
) -> jint {
    if handle == 0 {
        set_error!("Read failed: port handle is null");
        return -1;
    }

    let mut read_buffer = vec![0u8; length as usize];

    let bytes_read = unsafe {
        let wrapper = &mut *(handle as *mut PortWrapper);
        match wrapper.port.read(&mut read_buffer) {
            Ok(n) => n,
            Err(e) => {
                set_error!(format!("Read failed: {}", e));
                return -1;
            }
        }
    };

    if bytes_read > 0 {
        // Convert u8 to i8 for JNI
        let i8_buffer: Vec<i8> = read_buffer[..bytes_read].iter().map(|&b| b as i8).collect();

        if let Err(e) = env.set_byte_array_region(&buffer, offset, &i8_buffer) {
            set_error!(format!("Read failed: could not write to buffer: {}", e));
            return -1;
        }
    }

    bytes_read as jint
}

/// Get the number of bytes available to read
#[no_mangle]
pub extern "system" fn Java_dev_nemecec_jrserial_NativeSerialPort_bytesAvailable(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jint {
    if handle == 0 {
        return 0;
    }

    unsafe {
        let wrapper = &mut *(handle as *mut PortWrapper);
        match wrapper.port.bytes_to_read() {
            Ok(n) => n as jint,
            Err(e) => {
                set_error!(format!("Failed to get bytes available: {}", e));
                0
            }
        }
    }
}

/// Flush the output buffer
#[no_mangle]
pub extern "system" fn Java_dev_nemecec_jrserial_NativeSerialPort_flush(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jboolean {
    if handle == 0 {
        set_error!("Flush failed: port handle is null");
        return 0;
    }

    unsafe {
        let wrapper = &mut *(handle as *mut PortWrapper);
        match wrapper.port.flush() {
            Ok(_) => 1,
            Err(e) => {
                set_error!(format!("Flush failed: {}", e));
                0
            }
        }
    }
}

// ============================================================================
// Port Enumeration with Symlink/PTY/Bluetooth Detection
// ============================================================================

/// Information about a serial port's device type
struct PortTypeInfo {
    is_symlink: bool,
    is_pseudo_terminal: bool,
    is_bluetooth: bool,
}

/// Check if a path is a symlink and/or resolves to a pseudo-terminal.
/// Returns (is_symlink, is_pseudo_terminal).
#[cfg(unix)]
fn get_port_type_info(path: &str) -> PortTypeInfo {
    use std::fs;
    use std::path::Path;

    let path = Path::new(path);

    // Check if it's a symlink
    let is_symlink = fs::symlink_metadata(path)
        .map(|m| m.file_type().is_symlink())
        .unwrap_or(false);

    // Get the resolved path (follows symlinks)
    let resolved_path = if is_symlink {
        fs::read_link(path)
            .map(|p| p.to_string_lossy().to_string())
            .unwrap_or_default()
    } else {
        path.to_string_lossy().to_string()
    };

    // Check if the path (or resolved target) is a pseudo-terminal
    // PTYs are typically /dev/pts/N or /dev/pty*
    let path_str = path.to_string_lossy();
    let is_pseudo_terminal = resolved_path.contains("/dev/pts/")
        || resolved_path.contains("/dev/pt")
        || path_str.contains("/dev/pts/")
        || path_str.contains("/dev/pt");

    // Check if this is a Bluetooth serial port
    // macOS: /dev/cu.Bluetooth-*, /dev/tty.Bluetooth-*
    // Linux: /dev/rfcomm*
    let path_lower = path_str.to_lowercase();
    let is_bluetooth = path_lower.contains("bluetooth")
        || path_str.starts_with("/dev/rfcomm");

    PortTypeInfo {
        is_symlink,
        is_pseudo_terminal,
        is_bluetooth,
    }
}

/// On non-Unix platforms, symlink/PTY detection is not applicable
/// but Bluetooth detection still works via pattern matching
#[cfg(not(unix))]
fn get_port_type_info(path: &str) -> PortTypeInfo {
    let path_lower = path.to_lowercase();
    let is_bluetooth = path_lower.contains("bluetooth");

    PortTypeInfo {
        is_symlink: false,
        is_pseudo_terminal: false,
        is_bluetooth,
    }
}

/// List available serial ports with detailed info.
/// Returns tab-separated lines: name\tsymlink\tpty\tbluetooth\n
/// where each flag is "1" or "0"
#[no_mangle]
pub extern "system" fn Java_dev_nemecec_jrserial_NativeSerialPort_listPorts(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let ports = match serialport::available_ports() {
        Ok(ports) => ports,
        Err(e) => {
            set_error!(format!("Failed to list ports: {}", e));
            return std::ptr::null_mut();
        }
    };

    let result: String = ports
        .iter()
        .map(|p| {
            let info = get_port_type_info(&p.port_name);
            // Use native Bluetooth detection from serialport-rs, with pattern fallback
            let is_bluetooth = matches!(p.port_type, SerialPortType::BluetoothPort)
                || info.is_bluetooth;
            format!(
                "{}\t{}\t{}\t{}",
                p.port_name,
                if info.is_symlink { "1" } else { "0" },
                if info.is_pseudo_terminal { "1" } else { "0" },
                if is_bluetooth { "1" } else { "0" }
            )
        })
        .collect::<Vec<_>>()
        .join("\n");

    string_to_jstring(&mut env, &result)
}

/// Set timeout
#[no_mangle]
pub extern "system" fn Java_dev_nemecec_jrserial_NativeSerialPort_setTimeout(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    timeout_ms: jint,
) -> jboolean {
    if handle == 0 {
        set_error!("Set timeout failed: port handle is null");
        return 0;
    }

    let timeout = normalize_timeout_ms(timeout_ms as u64);

    unsafe {
        let wrapper = &mut *(handle as *mut PortWrapper);
        match wrapper.port.set_timeout(timeout) {
            Ok(_) => 1,
            Err(e) => {
                set_error!(format!("Set timeout failed: {}", e));
                0
            }
        }
    }
}

/// Clear input buffer
#[no_mangle]
pub extern "system" fn Java_dev_nemecec_jrserial_NativeSerialPort_clearInput(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jboolean {
    if handle == 0 {
        set_error!("Clear input failed: port handle is null");
        return 0;
    }

    unsafe {
        let wrapper = &mut *(handle as *mut PortWrapper);
        match wrapper.port.clear(serialport::ClearBuffer::Input) {
            Ok(_) => 1,
            Err(e) => {
                set_error!(format!("Clear input failed: {}", e));
                0
            }
        }
    }
}

/// Clear output buffer
#[no_mangle]
pub extern "system" fn Java_dev_nemecec_jrserial_NativeSerialPort_clearOutput(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jboolean {
    if handle == 0 {
        set_error!("Clear output failed: port handle is null");
        return 0;
    }

    unsafe {
        let wrapper = &mut *(handle as *mut PortWrapper);
        match wrapper.port.clear(serialport::ClearBuffer::Output) {
            Ok(_) => 1,
            Err(e) => {
                set_error!(format!("Clear output failed: {}", e));
                0
            }
        }
    }
}

/// Clear both input and output buffers
#[no_mangle]
pub extern "system" fn Java_dev_nemecec_jrserial_NativeSerialPort_clearAll(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jboolean {
    if handle == 0 {
        set_error!("Clear all failed: port handle is null");
        return 0;
    }

    unsafe {
        let wrapper = &mut *(handle as *mut PortWrapper);
        match wrapper.port.clear(serialport::ClearBuffer::All) {
            Ok(_) => 1,
            Err(e) => {
                set_error!(format!("Clear all failed: {}", e));
                0
            }
        }
    }
}

/// Set RTS (Request To Send) pin state - for manual RS-485 control
/// Note: This is only needed if you're NOT using automatic RS-485 control
/// Set to true before transmitting, false after transmitting
/// Returns: 1 on success, 0 on failure
#[no_mangle]
pub extern "system" fn Java_dev_nemecec_jrserial_NativeSerialPort_setRTS(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    level: jboolean,
) -> jboolean {
    if handle == 0 {
        set_error!("Set RTS failed: port handle is null");
        return 0;
    }

    unsafe {
        let wrapper = &mut *(handle as *mut PortWrapper);
        match wrapper.port.write_request_to_send(level != 0) {
            Ok(_) => 1,
            Err(e) => {
                set_error!(format!("Set RTS failed: {}", e));
                0
            }
        }
    }
}

/// Set DTR (Data Terminal Ready) pin state - alternative for manual RS-485 control
/// Note: This is only needed if you're NOT using automatic RS-485 control
/// Returns: 1 on success, 0 on failure
#[no_mangle]
pub extern "system" fn Java_dev_nemecec_jrserial_NativeSerialPort_setDTR(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    level: jboolean,
) -> jboolean {
    if handle == 0 {
        set_error!("Set DTR failed: port handle is null");
        return 0;
    }

    unsafe {
        let wrapper = &mut *(handle as *mut PortWrapper);
        match wrapper.port.write_data_terminal_ready(level != 0) {
            Ok(_) => 1,
            Err(e) => {
                set_error!(format!("Set DTR failed: {}", e));
                0
            }
        }
    }
}

/// Check if kernel RS-485 mode is active (Linux only)
/// Returns: 1 if kernel mode is active, 0 otherwise
#[no_mangle]
pub extern "system" fn Java_dev_nemecec_jrserial_NativeSerialPort_isKernelRs485Active(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jboolean {
    if handle == 0 {
        return 0;
    }

    unsafe {
        let wrapper = &mut *(handle as *mut PortWrapper);
        #[cfg(target_os = "linux")]
        {
            if wrapper.is_kernel_rs485_active() { 1 } else { 0 }
        }
        #[cfg(not(target_os = "linux"))]
        {
            let _ = wrapper; // Suppress unused warning
            0 // Kernel RS-485 is only available on Linux
        }
    }
}

/// Set RS-485 timing delays (Linux kernel mode only)
/// delay_before_send_micros: Delay in microseconds before sending (RTS assertion to data)
/// delay_after_send_micros: Delay in microseconds after sending (data to RTS de-assertion)
/// Returns: 1 on success, 0 on failure or if not on Linux
#[no_mangle]
pub extern "system" fn Java_dev_nemecec_jrserial_NativeSerialPort_setRs485Delays(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    delay_before_send_micros: jint,
    delay_after_send_micros: jint,
) -> jboolean {
    if handle == 0 {
        return 0;
    }

    unsafe {
        let wrapper = &mut *(handle as *mut PortWrapper);
        #[cfg(target_os = "linux")]
        {
            wrapper.set_rs485_delays(delay_before_send_micros as u32, delay_after_send_micros as u32);
            1
        }
        #[cfg(not(target_os = "linux"))]
        {
            let _ = (wrapper, delay_before_send_micros, delay_after_send_micros);
            0 // RS-485 delays only available on Linux with kernel mode
        }
    }
}

/// Open a serial port with extended RS-485 configuration
/// flow_control: 0 = None, 1 = Software (XON/XOFF), 2 = Hardware (RTS/CTS)
/// dtr_on_open: true to assert DTR on open, false to suppress (for Arduino)
/// rs485_mode: 0 = None, 1 = Auto, 2 = Manual
/// rs485_pin: 0 = RTS, 1 = DTR
/// rts_active_high: true if RTS is active high during transmission
/// rx_during_tx: true to enable receiving during transmission
/// termination_enabled: true to enable bus termination
/// delay_before_micros: delay in microseconds before sending
/// delay_after_micros: delay in microseconds after sending
#[no_mangle]
pub extern "system" fn Java_dev_nemecec_jrserial_NativeSerialPort_openWithRs485Config(
    mut env: JNIEnv,
    _class: JClass,
    port_name: JString,
    baud_rate: jint,
    data_bits: jint,
    stop_bits: jint,
    parity: jint,
    flow_control: jint,
    dtr_on_open: jboolean,
    timeout_ms: jint,
    rs485_mode: jint,
    rs485_pin: jint,
    rts_active_high: jboolean,
    rx_during_tx: jboolean,
    termination_enabled: jboolean,
    delay_before_micros: jint,
    delay_after_micros: jint,
) -> jlong {
    let port_name = match jstring_to_string(&mut env, port_name) {
        Ok(s) => s,
        Err(e) => {
            set_error!(format!("Invalid port name: {}", e));
            return 0;
        }
    };

    let data_bits = match data_bits {
        5 => DataBits::Five,
        6 => DataBits::Six,
        7 => DataBits::Seven,
        8 => DataBits::Eight,
        _ => DataBits::Eight,
    };

    let stop_bits = match stop_bits {
        1 => StopBits::One,
        2 => StopBits::Two,
        _ => StopBits::One,
    };

    let parity = match parity {
        0 => Parity::None,
        1 => Parity::Odd,
        2 => Parity::Even,
        _ => Parity::None,
    };

    let flow_control = match flow_control {
        0 => FlowControl::None,
        1 => FlowControl::Software,
        2 => FlowControl::Hardware,
        _ => FlowControl::None,
    };

    let control_mode = match rs485_mode {
        0 => Rs485ControlMode::None,
        1 => Rs485ControlMode::Auto,
        2 => Rs485ControlMode::Manual,
        _ => Rs485ControlMode::None,
    };

    let control_pin = match rs485_pin {
        0 => Rs485ControlPin::RTS,
        1 => Rs485ControlPin::DTR,
        _ => Rs485ControlPin::RTS,
    };

    let timeout = normalize_timeout_ms(timeout_ms as u64);

    let builder = serialport::new(port_name, baud_rate as u32)
        .data_bits(data_bits)
        .stop_bits(stop_bits)
        .parity(parity)
        .flow_control(flow_control)
        .timeout(timeout);

    // Platform-specific port opening
    #[cfg(target_os = "linux")]
    let port_result = builder.open_native();

    #[cfg(not(target_os = "linux"))]
    let port_result = builder.open();

    match port_result {
        Ok(port) => {
            let mut wrapper = PortWrapper::new(port);

            // Suppress DTR if requested (prevents Arduino reset)
            if dtr_on_open == 0 {
                if let Err(e) = wrapper.port.write_data_terminal_ready(false) {
                    set_error!(format!("Failed to suppress DTR: {}", e));
                    return 0;
                }
            }

            // Configure extended RS-485 mode if requested
            if control_mode != Rs485ControlMode::None {
                if let Err(e) = wrapper.configure_rs485_extended(
                    control_mode,
                    control_pin,
                    rts_active_high != 0,
                    rx_during_tx != 0,
                    termination_enabled != 0,
                    delay_before_micros as u32,
                    delay_after_micros as u32,
                ) {
                    set_error!(format!("Failed to configure RS-485: {}", e));
                    return 0;
                }
            }

            let boxed = Box::new(wrapper);
            Box::into_raw(boxed) as jlong
        }
        Err(e) => {
            set_error!(format!("Failed to open port: {}", e));
            0
        }
    }
}

/// Set RS-485 configuration at runtime
/// enabled: true to enable RS-485 mode
/// rs485_pin: 0 = RTS, 1 = DTR
/// rts_active_high: true if RTS is active high during transmission
/// rx_during_tx: true to enable receiving during transmission
/// termination_enabled: true to enable bus termination
/// delay_before_micros: delay in microseconds before sending
/// delay_after_micros: delay in microseconds after sending
/// Returns: 1 on success, 0 on failure
#[no_mangle]
pub extern "system" fn Java_dev_nemecec_jrserial_NativeSerialPort_setRs485Config(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    enabled: jboolean,
    rs485_pin: jint,
    rts_active_high: jboolean,
    rx_during_tx: jboolean,
    termination_enabled: jboolean,
    delay_before_micros: jint,
    delay_after_micros: jint,
) -> jboolean {
    if handle == 0 {
        return 0;
    }

    let control_mode = if enabled != 0 {
        Rs485ControlMode::Auto
    } else {
        Rs485ControlMode::None
    };

    let control_pin = match rs485_pin {
        0 => Rs485ControlPin::RTS,
        1 => Rs485ControlPin::DTR,
        _ => Rs485ControlPin::RTS,
    };

    unsafe {
        let wrapper = &mut *(handle as *mut PortWrapper);
        match wrapper.configure_rs485_extended(
            control_mode,
            control_pin,
            rts_active_high != 0,
            rx_during_tx != 0,
            termination_enabled != 0,
            delay_before_micros as u32,
            delay_after_micros as u32,
        ) {
            Ok(_) => 1,
            Err(e) => {
                set_error!(format!("Failed to set RS-485 config: {}", e));
                0
            }
        }
    }
}

/// Get the last error message from native code.
/// Returns null if no error has occurred.
/// The error includes the message and source location (file:line).
#[no_mangle]
pub extern "system" fn Java_dev_nemecec_jrserial_NativeSerialPort_getLastError(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    match get_last_error_string() {
        Some(msg) => string_to_jstring(&mut env, &msg),
        None => std::ptr::null_mut(),
    }
}

/// Clear the last error.
#[no_mangle]
pub extern "system" fn Java_dev_nemecec_jrserial_NativeSerialPort_clearLastError(
    _env: JNIEnv,
    _class: JClass,
) {
    clear_error();
}
