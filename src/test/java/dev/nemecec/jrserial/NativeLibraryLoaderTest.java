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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("NativeLibraryLoader platform detection")
class NativeLibraryLoaderTest {

  @ParameterizedTest(name = "os.name=\"{0}\", os.arch=\"{1}\" -> {2}")
  @CsvSource({
      // macOS
      "Mac OS X, x86_64, darwin-x86_64",
      "Mac OS X, amd64, darwin-x86_64",
      "Mac OS X, aarch64, darwin-aarch64",
      "Mac OS X, arm64, darwin-aarch64",
      "Darwin, x86_64, darwin-x86_64",
      "Darwin, aarch64, darwin-aarch64",

      // Linux x86
      "Linux, x86_64, linux-x86_64",
      "Linux, amd64, linux-x86_64",
      "Linux, i386, linux-x86",
      "Linux, i486, linux-x86",
      "Linux, i586, linux-x86",
      "Linux, i686, linux-x86",
      "Linux, x86, linux-x86",

      // Linux ARM64
      "Linux, aarch64, linux-aarch64",
      "Linux, arm64, linux-aarch64",

      // Linux ARM 32-bit
      "Linux, arm, linux-arm",
      "Linux, armv5, linux-arm",
      "Linux, armv5te, linux-arm",
      "Linux, armv6, linux-arm",
      "Linux, armv6l, linux-arm",
      "Linux, armv7, linux-arm",
      "Linux, armv7l, linux-arm",

      // Windows
      "Windows 10, x86_64, windows-x86_64",
      "Windows 10, amd64, windows-x86_64",
      "Windows 11, amd64, windows-x86_64",
      "Windows Server 2019, amd64, windows-x86_64",
      "Windows 10, x86, windows-x86",
      "Windows 10, i386, windows-x86",

      // FreeBSD
      "FreeBSD, x86_64, freebsd-x86_64",
      "FreeBSD, amd64, freebsd-x86_64",
  })
  @DisplayName("detects platform correctly")
  void detectsPlatformCorrectly(String osName, String osArch, String expectedPlatform) {
    String platform = NativeLibraryLoader.getPlatform(osName.toLowerCase(), osArch.toLowerCase());
    assertThat(platform).isEqualTo(expectedPlatform);
  }

  @ParameterizedTest(name = "os.name=\"{0}\" is unsupported")
  @CsvSource({
      "SunOS, x86_64",
      "Solaris, sparc",
      "AIX, ppc64",
      "HP-UX, ia64",
      "OpenBSD, amd64",
  })
  @DisplayName("throws for unsupported operating systems")
  void throwsForUnsupportedOs(String osName, String osArch) {
    assertThatThrownBy(() -> NativeLibraryLoader.getPlatform(osName.toLowerCase(), osArch.toLowerCase()))
        .isInstanceOf(UnsatisfiedLinkError.class)
        .hasMessageContaining("Unsupported operating system");
  }

  @ParameterizedTest(name = "os.arch=\"{1}\" is unsupported")
  @CsvSource({
      "Linux, sparc",
      "Linux, ppc64",
      "Linux, ppc64le",
      "Linux, mips",
      "Linux, riscv64",
      "Windows 10, ia64",
  })
  @DisplayName("throws for unsupported architectures")
  void throwsForUnsupportedArch(String osName, String osArch) {
    assertThatThrownBy(() -> NativeLibraryLoader.getPlatform(osName.toLowerCase(), osArch.toLowerCase()))
        .isInstanceOf(UnsatisfiedLinkError.class)
        .hasMessageContaining("Unsupported architecture");
  }
}
