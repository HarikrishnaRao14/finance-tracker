#!/usr/bin/env bash
# Runs the screen tests on the emulator and copies the screenshots out of the app.
set -u
PKG=com.harikrishnarao.financetracker.debug
mkdir -p shots
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class com.harikrishnarao.financetracker.ScreenshotTest \
  ${PKG}.test/androidx.test.runner.AndroidJUnitRunner | tee shots/instrumentation.txt
# A run counts as failed if any test failed or the process crashed.
if ! grep -q "OK (" shots/instrumentation.txt; then
  echo "failed" > shots/UI_TESTS_FAILED.txt
fi
adb exec-out run-as "$PKG" sh -c 'cd files/screenshots 2>/dev/null && tar cf - .' > shots/shots.tar || true
tar xf shots/shots.tar -C shots 2>/dev/null || true
rm -f shots/shots.tar
adb logcat -d -t 400 '*:E' > shots/logcat-errors.txt || true
ls -la shots
exit 0
