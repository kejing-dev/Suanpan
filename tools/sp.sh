#!/usr/bin/env bash
# Suanpan debug helper: sp.sh "<debug command>"   (broadcast to the debug receiver; see DebugReceiver.kt)
#   DEV defaults to emulator-5554 (AVD Pico_Emulator_6_1_c on kj-home-pc)
DEV="${DEV:-emulator-5554}"
ADB="${ADB:-adb}"
"$ADB" -s "$DEV" shell "am broadcast -a io.embodit.suanpan.DEBUG -p io.embodit.suanpan --es cmd '$*'" | tail -1 | tr -d '\r'
