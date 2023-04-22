#!/usr/bin/env bash
VERSION="1.0.20"
PLUGIN="git-file-case-fixer"
HOME_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
OLD_PLUGIN=
IDE_LIST=(
#    191
#    192
#    193
#    201
#    202
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

# copy the versioned file to un-versioned one.
cp ./build/libs/${PLUGIN}-${VERSION}.jar ./${PLUGIN}.jar
cp ./build/libs/${PLUGIN}-${VERSION}.jar ./dist

echo updating "/Volumes/Pegasus/Data" for latest "${PLUGIN}.jar"
cp "${PLUGIN}.jar" "/Volumes/Pegasus/Data"

../update-plugin.sh "${HOME_DIR}" "${PLUGIN}" "${PLUGIN_JAR}" "${OLD_PLUGIN}" "${IDE_LIST[@]}"
