#!/usr/bin/env bash
# Retry Maven rate limits in disposable CI jobs; preserve all other failures.
set -euo pipefail
log_file=$(mktemp)
trap 'rm -f "$log_file"' EXIT

for attempt in 1 2 3; do
    if ./gradlew "$@" 2>&1 | tee "$log_file"; then
        exit 0
    else
        gradle_status=${PIPESTATUS[0]}
    fi

    if (( attempt == 3 )) || ! grep -q 'Received status code 429 from server' "$log_file"; then
        exit "$gradle_status"
    fi
    delay=$((attempt * 20))
    echo "::warning::Dependency repository rate limited Gradle; retrying in ${delay}s (attempt $((attempt + 1))/3)."
    sleep "$delay"
done
