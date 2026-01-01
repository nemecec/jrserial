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

//! Non-Linux serial port wrapper with manual RS-485 control only.

use crate::{Rs485ControlMode, Rs485ControlPin};
use serialport::SerialPort;
use std::io::Write;

pub struct PortWrapper {
    pub port: Box<dyn SerialPort>,
    pub control_mode: Rs485ControlMode,
    pub control_pin: Rs485ControlPin,
    /// True if RTS should be active high during transmission
    rts_active_high: bool,
}

impl PortWrapper {
    pub fn new(port: Box<dyn SerialPort>) -> Self {
        Self {
            port,
            control_mode: Rs485ControlMode::None,
            control_pin: Rs485ControlPin::RTS,
            rts_active_high: true,
        }
    }

    pub fn configure_rs485(
        &mut self,
        mode: Rs485ControlMode,
        pin: Rs485ControlPin,
    ) -> Result<(), serialport::Error> {
        self.control_mode = mode;
        self.control_pin = pin;
        // On non-Linux platforms, we only support manual mode
        Ok(())
    }

    /// Configure extended RS-485 settings (non-Linux platforms only support manual control)
    pub fn configure_rs485_extended(
        &mut self,
        mode: Rs485ControlMode,
        pin: Rs485ControlPin,
        rts_active_high: bool,
        _rx_during_tx: bool,        // Not supported on non-Linux
        _termination_enabled: bool, // Not supported on non-Linux
        _delay_before_micros: u32,  // Not supported on non-Linux
        _delay_after_micros: u32,   // Not supported on non-Linux
    ) -> Result<(), serialport::Error> {
        self.rts_active_high = rts_active_high;
        self.configure_rs485(mode, pin)
    }

    pub fn write_rs485(&mut self, data: &[u8]) -> Result<usize, std::io::Error> {
        // Manual mode on non-Linux platforms
        if self.control_mode != Rs485ControlMode::None {
            // Enable transmit (respecting polarity)
            let transmit_level = self.rts_active_high;
            match self.control_pin {
                Rs485ControlPin::RTS => self.port.write_request_to_send(transmit_level)?,
                Rs485ControlPin::DTR => self.port.write_data_terminal_ready(transmit_level)?,
            }

            // Write data
            let result = self.port.write(data);

            // Flush to ensure data is sent
            let _ = self.port.flush();

            // Disable transmit (back to receive mode)
            let receive_level = !self.rts_active_high;
            match self.control_pin {
                Rs485ControlPin::RTS => self.port.write_request_to_send(receive_level)?,
                Rs485ControlPin::DTR => self.port.write_data_terminal_ready(receive_level)?,
            }

            result
        } else {
            // No RS-485 control, just write normally
            self.port.write(data)
        }
    }
}
