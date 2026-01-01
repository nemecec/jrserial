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

//! Linux-specific serial port wrapper with kernel RS-485 support.

use crate::{Rs485ControlMode, Rs485ControlPin};
use serialport::{SerialPort, TTYPort};
use std::io::Write;
use std::os::unix::io::AsRawFd;

// Linux kernel RS-485 ioctl constants
// From linux/serial.h
const TIOCGRS485: libc::c_ulong = 0x542E;
const TIOCSRS485: libc::c_ulong = 0x542F;

// RS-485 flags from linux/serial.h
const SER_RS485_ENABLED: u32 = 1 << 0;
const SER_RS485_RTS_ON_SEND: u32 = 1 << 1;
const SER_RS485_RTS_AFTER_SEND: u32 = 1 << 2;
const SER_RS485_RX_DURING_TX: u32 = 1 << 4;
const SER_RS485_TERMINATE_BUS: u32 = 1 << 5;

/// Linux kernel serial_rs485 structure
/// Matches struct serial_rs485 from linux/serial.h
#[repr(C)]
#[derive(Debug, Clone, Copy, Default)]
struct SerialRs485 {
    flags: u32,
    delay_rts_before_send: u32,
    delay_rts_after_send: u32,
    padding: [u32; 5],
}

pub struct PortWrapper {
    pub port: TTYPort,
    pub control_mode: Rs485ControlMode,
    pub control_pin: Rs485ControlPin,
    /// True if kernel RS-485 mode was successfully enabled
    kernel_rs485_active: bool,
    /// True if RTS should be active high during transmission
    rts_active_high: bool,
    /// True to enable receiving during transmission
    rx_during_tx: bool,
    /// True to enable bus termination (if hardware supports it)
    termination_enabled: bool,
    /// Delay in microseconds before sending (for kernel mode)
    delay_before_send_micros: u32,
    /// Delay in microseconds after sending (for kernel mode)
    delay_after_send_micros: u32,
}

impl PortWrapper {
    pub fn new(port: TTYPort) -> Self {
        Self {
            port,
            control_mode: Rs485ControlMode::None,
            control_pin: Rs485ControlPin::RTS,
            kernel_rs485_active: false,
            rts_active_high: true,
            rx_during_tx: false,
            termination_enabled: false,
            delay_before_send_micros: 0,
            delay_after_send_micros: 0,
        }
    }

    /// Try to enable kernel RS-485 mode via ioctl
    fn try_enable_kernel_rs485(&mut self) -> bool {
        let fd = self.port.as_raw_fd();

        // Build flags based on configuration
        let mut flags = SER_RS485_ENABLED;

        // RTS polarity: active high means RTS_ON_SEND, active low means RTS_AFTER_SEND
        if self.rts_active_high {
            flags |= SER_RS485_RTS_ON_SEND;
        } else {
            flags |= SER_RS485_RTS_AFTER_SEND;
        }

        // Enable RX during TX if requested
        if self.rx_during_tx {
            flags |= SER_RS485_RX_DURING_TX;
        }

        // Enable bus termination if requested
        if self.termination_enabled {
            flags |= SER_RS485_TERMINATE_BUS;
        }

        // Linux kernel uses milliseconds for delays, convert from microseconds
        let delay_before_ms = self.delay_before_send_micros / 1000;
        let delay_after_ms = self.delay_after_send_micros / 1000;

        let mut config = SerialRs485 {
            flags,
            delay_rts_before_send: delay_before_ms,
            delay_rts_after_send: delay_after_ms,
            padding: [0; 5],
        };

        // Try to set RS-485 mode
        let result = unsafe { libc::ioctl(fd, TIOCSRS485, &mut config as *mut SerialRs485) };

        if result == 0 {
            // Verify it was set by reading back
            let mut verify = SerialRs485::default();
            let verify_result =
                unsafe { libc::ioctl(fd, TIOCGRS485, &mut verify as *mut SerialRs485) };

            if verify_result == 0 && (verify.flags & SER_RS485_ENABLED) != 0 {
                return true;
            }
        }

        false
    }

    /// Disable kernel RS-485 mode
    fn disable_kernel_rs485(&mut self) -> bool {
        let fd = self.port.as_raw_fd();

        let mut config = SerialRs485::default();
        // flags = 0 means disabled

        let result = unsafe { libc::ioctl(fd, TIOCSRS485, &mut config as *mut SerialRs485) };
        result == 0
    }

    pub fn configure_rs485(
        &mut self,
        mode: Rs485ControlMode,
        pin: Rs485ControlPin,
    ) -> Result<(), serialport::Error> {
        // First, disable any existing kernel RS-485 mode
        if self.kernel_rs485_active {
            self.disable_kernel_rs485();
            self.kernel_rs485_active = false;
        }

        self.control_mode = mode;
        self.control_pin = pin;

        match mode {
            Rs485ControlMode::None => {
                // Nothing to do
            }
            Rs485ControlMode::Auto => {
                // Try kernel mode first (only works with RTS, not DTR)
                if pin == Rs485ControlPin::RTS {
                    if self.try_enable_kernel_rs485() {
                        self.kernel_rs485_active = true;
                        // Kernel mode enabled, no manual control needed
                    }
                    // If kernel mode fails, fall back to manual (no error)
                }
                // For DTR, always use manual mode (kernel doesn't support it)
            }
            Rs485ControlMode::Manual => {
                // Explicitly use manual mode, don't try kernel
            }
        }

        Ok(())
    }

    /// Configure extended RS-485 settings
    pub fn configure_rs485_extended(
        &mut self,
        mode: Rs485ControlMode,
        pin: Rs485ControlPin,
        rts_active_high: bool,
        rx_during_tx: bool,
        termination_enabled: bool,
        delay_before_micros: u32,
        delay_after_micros: u32,
    ) -> Result<(), serialport::Error> {
        // Store extended configuration
        self.rts_active_high = rts_active_high;
        self.rx_during_tx = rx_during_tx;
        self.termination_enabled = termination_enabled;
        self.delay_before_send_micros = delay_before_micros;
        self.delay_after_send_micros = delay_after_micros;

        // Now configure RS-485 mode
        self.configure_rs485(mode, pin)
    }

    /// Set RS-485 timing delays in microseconds
    pub fn set_rs485_delays(&mut self, before_send_micros: u32, after_send_micros: u32) {
        self.delay_before_send_micros = before_send_micros;
        self.delay_after_send_micros = after_send_micros;

        // If kernel mode is already active, reconfigure with new delays
        if self.kernel_rs485_active {
            self.try_enable_kernel_rs485();
        }
    }

    /// Check if kernel RS-485 mode is active
    pub fn is_kernel_rs485_active(&self) -> bool {
        self.kernel_rs485_active
    }

    pub fn write_rs485(&mut self, data: &[u8]) -> Result<usize, std::io::Error> {
        match self.control_mode {
            Rs485ControlMode::None => {
                // No RS-485 control, just write normally
                self.port.write(data)
            }
            Rs485ControlMode::Auto if self.kernel_rs485_active => {
                // Kernel handles RTS automatically, just write
                let result = self.port.write(data);
                // Still flush to ensure data is sent before kernel toggles RTS
                let _ = self.port.flush();
                result
            }
            Rs485ControlMode::Auto | Rs485ControlMode::Manual => {
                // Manual RTS/DTR control
                // Enable transmit
                match self.control_pin {
                    Rs485ControlPin::RTS => self.port.write_request_to_send(true)?,
                    Rs485ControlPin::DTR => self.port.write_data_terminal_ready(true)?,
                }

                // Write data
                let result = self.port.write(data);

                // Flush to ensure data is sent
                let _ = self.port.flush();

                // Disable transmit (back to receive mode)
                match self.control_pin {
                    Rs485ControlPin::RTS => self.port.write_request_to_send(false)?,
                    Rs485ControlPin::DTR => self.port.write_data_terminal_ready(false)?,
                }

                result
            }
        }
    }
}
