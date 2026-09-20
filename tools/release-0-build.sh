#!/usr/bin/env bash

# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------

DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Values shared with the other release scripts (Nexus staging profile, dist.apache.org URLs) and the
# ownership settings for the docker builds.
if [[ ! -f "$DIRECTORY/tools/release-common.sh" ]]; then
    echo "❌ '$DIRECTORY/tools/release-common.sh' not found, aborting."
    exit 1
fi
# shellcheck source=release-common.sh
source "$DIRECTORY/tools/release-common.sh"

########################################################################################################################
# 0. Check Docker Memory Availability
########################################################################################################################

# Minimum required memory in bytes (4 GB)
REQUIRED_MEM=$((4 * 1024 * 1024 * 1024))

# Extract total memory from `docker system info`
TOTAL_MEM=$(docker system info --format '{{.MemTotal}}')

# Check if TOTAL_MEM was retrieved successfully
if [[ -z "$TOTAL_MEM" || "$TOTAL_MEM" -eq 0 ]]; then
    echo "❌ Unable to determine total Docker memory. Is Docker running?"
    exit 1
fi

# Compare and exit if not enough memory
if (( TOTAL_MEM < REQUIRED_MEM )); then
    echo "❌ Docker runtime has insufficient memory: $(awk "BEGIN {printf \"%.2f\", $TOTAL_MEM/1024/1024/1024}") GB"
    echo "   At least 4 GB is required. Aborting."
    exit 1
fi

########################################################################################################################
# 1. Check if there are uncommitted changes as these would automatically be committed (local)
########################################################################################################################

if [[ $(git -C "$DIRECTORY" status --porcelain) ]]; then
  # Changes
  echo "❌ There are untracked files or changed files, aborting."
  exit 1
fi

########################################################################################################################
# 2. Delete the pre-exising "out" directory that contains the maven local repo and deployments (local)
########################################################################################################################

echo "Deleting the maven local repo and previous deployments"
rm -r "$DIRECTORY/out"

########################################################################################################################
# 3. Make sure the NOTICE file has the current year in the second line
########################################################################################################################

NOTICE_FILE="$DIRECTORY/NOTICE"
CURRENT_YEAR=$(date +%Y)
EXPECTED="Copyright 2017-${CURRENT_YEAR} The Apache Software Foundation"

# Extract the second line
SECOND_LINE=$(sed -n '2p' "$NOTICE_FILE")

if [[ "$SECOND_LINE" != "$EXPECTED" ]]; then
    echo "✏️  Updating $NOTICE_FILE"

    # Replace line 2 with the expected text
    awk -v expected="$EXPECTED" 'NR==2 {$0=expected} {print}' "$NOTICE_FILE" > "$NOTICE_FILE.tmp" &&
    mv "$NOTICE_FILE.tmp" "$NOTICE_FILE"
else
    echo "✅ $NOTICE_FILE is already up to date."
fi

########################################################################################################################
# 4 Run the maven build for all modules with enabled all "with-*" profiles enable (Docker container)
########################################################################################################################

# Build the container we'll use for releasing.
if ! docker compose -f "$DIRECTORY/tools/docker-compose.yaml" build; then
    echo "❌ Got non-0 exit code from building the release docker container, aborting."
    exit 1
else
    echo "✅ Docker container successfully built."
fi

# Run the main build.
if ! docker compose -f "$DIRECTORY/tools/docker-compose.yaml" run releaser \
        bash -c "/ws/mvnw -e -P with-c,with-go,with-java -Dmaven.repo.local=/ws/out/.repository clean install -DskipTests"; then
    echo "❌ Got non-0 exit code from running the build inside docker, aborting."
    exit 1
else
    echo "✅ Main repository compiled successfully."
fi


echo "✅ Pre-release updates complete. Please continue with 'release-1-create-branch.sh' next."
