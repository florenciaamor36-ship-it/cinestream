#!/usr/bin/env bash
set +e
mkdir -p artifacts
gradle :app:connectedDebugAndroidTest --no-daemon
test_status=$?
adb logcat -d -v time -s CineStreamYouTube:D chromium:D WebView:D AndroidRuntime:E > artifacts/youtube-player-logcat.txt 2>&1
logcat_status=$?
adb shell dumpsys connectivity > artifacts/connectivity.txt 2>&1
connectivity_status=$?
printf 'connectedDebugAndroidTest=%s logcat=%s connectivity=%s\n' "$test_status" "$logcat_status" "$connectivity_status" > artifacts/diagnostics-status.txt
exit "$test_status"
