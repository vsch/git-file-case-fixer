#!/usr/bin/env bash
PLUGIN="git-file-case-fixer"
HOME_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
OLD_PLUGIN=
SANDBOX_NAME=
IDE_VERSION=
IDE_VERSIONS=(
#    191
#    192
    193
    201
    202
    203
    211
    212
    213
    221
    222
    223
    231
    232
    233
)
SANDBOX_IDE=


cd "${HOME_DIR}" || exit

echo updating "/Volumes/Pegasus/Data" for latest "${PLUGIN}.jar"
cp "${PLUGIN}.jar" "/Volumes/Pegasus/Data"

../update-plugin.sh "${HOME_DIR}" "${PLUGIN}" "${PLUGIN_JAR}" "${OLD_PLUGIN}" "${IDE_VERSION}" "${SANDBOX_NAME}" "${SANDBOX_IDE}" "${IDE_VERSIONS[@]}"
