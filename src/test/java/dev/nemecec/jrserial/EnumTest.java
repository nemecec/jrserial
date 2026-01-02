/*
 * Copyright (C) 2026 Neeme Praks
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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for enum types.
 */
class EnumTest {

  @Test
  void testDataBitsValues() {
    assertThat(DataBits.FIVE.getValue()).isEqualTo(5);
    assertThat(DataBits.SIX.getValue()).isEqualTo(6);
    assertThat(DataBits.SEVEN.getValue()).isEqualTo(7);
    assertThat(DataBits.EIGHT.getValue()).isEqualTo(8);
  }

  @Test
  void testStopBitsValues() {
    assertThat(StopBits.ONE.getValue()).isEqualTo(1);
    assertThat(StopBits.TWO.getValue()).isEqualTo(2);
  }

  @Test
  void testParityValues() {
    assertThat(Parity.NONE.getValue()).isZero();
    assertThat(Parity.ODD.getValue()).isEqualTo(1);
    assertThat(Parity.EVEN.getValue()).isEqualTo(2);
  }

}
