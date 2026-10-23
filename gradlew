#!/usr/bin/env bash
export GRADLE_USER_HOME="$HOME/.gradle"

BIN_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$BIN_DIR/.."

UNIX_TIME=$(date +%s%N)

# Default gradle user home
DEFAULT_GRADLE_USER_HOME="$HOME/.gradle"

# Find gradle distribution
if [ -n "$GRADLE_DISTRIBUTION_URL" ]; then
  GRADLE_DIST="$GRADLE_DISTRIBUTION_URL"
elif [ -d "$GRADLE_USER_HOME/wrapper/dists" ]; then
  # Use existing wrapper distribution
  GRADLE_DIST=$(ls "$GRADLE_USER_HOME/wrapper/dists" 2>/dev/null | head -1)
  GRADLE_DIST_URL="file://$GRADLE_USER_HOME/wrapper/dists/$GRADLE_DIST"
else
  # Try to download
  GRADLE_DIST_URL="https://services.gradle.org/distributions/gradle-8.4-all.zip"
fi

# ... (rest of the script would be complex)

# Simple version: just call gradle if available
if command -v gradle &>/dev/null; then
  exec gradle "$@"
fi

# Fallback
echo "Gradle wrapper script - please use 'gradle' command directly or ensure proper setup"
exit 1