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


# BSD and GNU sed disagree about "-i": BSD wants a backup suffix as a separate argument, GNU
# treats that argument as the expression. Editing through a temporary file works on both, and
# leaves nothing behind if the edit fails - a stray backup file would otherwise trip the check
# for a clean working tree the next time one of these scripts runs.
#   $1 the file to edit, the rest are passed to sed unchanged
sed_in_place() {
  local file="$1"; shift
  if ! sed "$@" "$file" > "$file.sed.tmp"; then
      rm -f "$file.sed.tmp"
      return 1
  fi
  mv "$file.sed.tmp" "$file"
}

########################################################################################################################
# 0. Check if there are uncommitted changes as these would automatically be committed (local)
########################################################################################################################

if [[ $(git -C "$DIRECTORY" status --porcelain) ]]; then
  # Changes
  echo "❌ There are untracked files or changed files, aborting."
  exit 1
fi

########################################################################################################################
# 1. Get and calculate the current version (local)
########################################################################################################################

# Maven 4 prefixes even quiet output with "[INFO] [stdout] ", so take the last token of the
# last line rather than the whole output.
PROJECT_VERSION=$("$DIRECTORY/mvnw" -f "$DIRECTORY/pom.xml" -q --non-recursive -Dexpression=project.version -DforceStdout help:evaluate | tail -n 1 | awk '{print $NF}')
if [[ ! "$PROJECT_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-SNAPSHOT)?$ ]]; then
    echo "❌ Could not read a usable project version, got '$PROJECT_VERSION'."
    echo "   Everything below derives the branch and tag names from it, so aborting."
    exit 1
fi
RELEASE_VERSION=${PROJECT_VERSION%"-SNAPSHOT"}
RELEASE_SHORT_VERSION=${RELEASE_VERSION%".0"}
BRANCH_NAME="rel/$RELEASE_SHORT_VERSION"
IFS='.' read -ra VERSION_SEGMENTS <<< "$RELEASE_VERSION"
NEW_VERSION="${VERSION_SEGMENTS[0]}.$((VERSION_SEGMENTS[1] + 1)).0-SNAPSHOT"
echo "Current Version: '$PROJECT_VERSION'"
echo "Release Version: '$RELEASE_VERSION'"
echo "Release Branch Name: '$BRANCH_NAME'"
echo "New develop Version: '$NEW_VERSION'"

########################################################################################################################
# 3. Ask if the RELEASE_NOTES have been filled out at all (local)
########################################################################################################################

read -r -p "Have the RELEASE_NOTES been updated for this version? (yes/no) " yn
case $yn in
	yes ) echo continuing with the process;;
	no ) echo Please update the RELEASE_NOTES first;
		exit 1;;
	* ) echo invalid response;
		exit 1;;
esac

########################################################################################################################
# 4 Remove the "(Unreleased)" prefix from the current version of the RELEASE_NOTES file (local)
########################################################################################################################

if ! sed_in_place "$DIRECTORY/RELEASE_NOTES" "s/(Unreleased) Apache PLC4X Extras $PROJECT_VERSION*/Apache PLC4X Extras $RELEASE_VERSION/"; then
    echo "❌ Got non-0 exit code from updating RELEASE_NOTES, aborting."
    exit 1
fi

# Commit this change to git.
git -C "$DIRECTORY" add --all
git -C "$DIRECTORY" commit -m "chore: updated generated code"

########################################################################################################################
# 5. Do a simple maven branch command with pushChanges=false
########################################################################################################################

# Attempt to read user.name and user.email (local first, then global)
GIT_USER_NAME=$(git config user.name || git config --global user.name)
GIT_USER_EMAIL=$(git config user.email || git config --global user.email)

# Check if either is still unset
if [[ -z "$GIT_USER_NAME" || -z "$GIT_USER_EMAIL" ]]; then
  echo "❌ Git user.name and/or user.email not configured."
  echo
  echo "Please run one of the following commands:"
  echo "  git config --global user.name \"Your Name\""
  echo "  git config --global user.email \"you@example.com\""
  echo
  echo "Or configure them just for this repository:"
  echo "  git config user.name \"Your Name\""
  echo "  git config user.email \"you@example.com\""
  exit 1
fi

# The container mounts this repository, so git inside it reads the very same ".git/config".
# If that enables commit/tag signing, every commit the release plugin creates fails with
# "gpg failed to sign the data", as the container has neither the key nor a gpg-agent. Passing
# the identity and the signing switches as GIT_CONFIG_* environment variables overrides the
# config files for the container only, so nothing is written back into the user's repository.
if ! docker compose -f "$DIRECTORY/tools/docker-compose.yaml" run releaser \
        bash -c "export GIT_CONFIG_COUNT=4 \
             GIT_CONFIG_KEY_0=user.name GIT_CONFIG_VALUE_0=\"$GIT_USER_NAME\" \
             GIT_CONFIG_KEY_1=user.email GIT_CONFIG_VALUE_1=\"$GIT_USER_EMAIL\" \
             GIT_CONFIG_KEY_2=commit.gpgsign GIT_CONFIG_VALUE_2=false \
             GIT_CONFIG_KEY_3=tag.gpgsign GIT_CONFIG_VALUE_3=false && \
           /ws/mvnw -e -P with-c,with-go,with-java -Dmaven.repo.local=/ws/out/.repository release:branch -DautoVersionSubmodules=true -DpushChanges=false -DdevelopmentVersion='$NEW_VERSION' -DbranchName='$BRANCH_NAME'"; then
    echo "❌ Got non-0 exit code from docker compose, aborting."
    exit 1
fi

########################################################################################################################
# 6. Add a new section for the new version to the RELEASE_NOTES file (local)
########################################################################################################################

NEW_HEADER="==============================================================\n\
(Unreleased) Apache PLC4X Extras $NEW_VERSION\n\
==============================================================\n\
\n\
New Features\n\
------------\n\
\n\
Incompatible changes\n\
--------------------\n\
\n\
Bug Fixes\n\
---------\n\
\n\
==============================================================\
"
echo NEW_VERSION
if ! sed_in_place "$DIRECTORY/RELEASE_NOTES" "1s/.*/$NEW_HEADER/"; then
    echo "❌ Got non-0 exit code from adding a new header to RELEASE_NOTES, aborting."
    exit 1
fi

########################################################################################################################
# 7. Commit the change (local)
########################################################################################################################

if ! git -C "$DIRECTORY" add --all; then
    echo "❌ Got non-0 exit code from adding all changes files, aborting."
    exit 1
fi
if ! git -C "$DIRECTORY" commit -m "chore: prepared the RELEASE_NOTES and the documentation version for the next version."; then
    echo "❌ Got non-0 exit code from committing changes files, aborting."
    exit 1
fi

########################################################################################################################
# 8. Push the changes (local)
########################################################################################################################

if ! git -C "$DIRECTORY" push; then
    echo "❌ Got non-0 exit code from pushing changes, aborting."
    exit 1
fi

########################################################################################################################
# 9. Switch to the release branch (local)
########################################################################################################################

if ! git -C "$DIRECTORY" checkout "$BRANCH_NAME"; then
    echo "❌ Got non-0 exit code from switching branches to the release branch, aborting."
    exit 1
fi


# Make sure the release branch is also pushed to the remote.
if ! git -C "$DIRECTORY" push --set-upstream origin "$BRANCH_NAME"; then
    echo "❌ Got non-0 exit code from pushing changes, aborting."
    exit 1
fi

echo "✅ Release branch creation complete. We have switched the local branch to the release branch. Please continue with 'release-2-prepare-release.sh' as soon as the release branch is ready for being released."
