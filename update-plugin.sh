#!/usr/bin/env bash
PLUGIN="git-file-case-fixer"
HOME_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
PLUGIN_JAR=
OLD_PLUGIN=
IDE_VERSION=
SANDBOX_NAME=

cd "${HOME_DIR}" || exit

../update-plugin.sh "${HOME_DIR}" "${PLUGIN}" "${PLUGIN_JAR}" "${OLD_PLUGIN}" "${IDE_VERSION}" "${SANDBOX_NAME}"
