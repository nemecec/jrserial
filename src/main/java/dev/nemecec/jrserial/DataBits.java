/*
 * Copyright 2025 Neeme Praks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.nemecec.jrserial;

/**
 * Number of data bits in a serial communication frame.
 */
public enum DataBits {
  /**
   * 5 data bits.
   */
  FIVE(5),

  /**
   * 6 data bits.
   */
  SIX(6),

  /**
   * 7 data bits.
   */
  SEVEN(7),

  /**
   * 8 data bits (most common).
   */
  EIGHT(8);

  private final int value;

  DataBits(int value) {
    this.value = value;
  }

  /**
   * Get the numeric value of the data bits.
   *
   * @return the numeric value
   */
  public int getValue() {
    return value;
  }
}
