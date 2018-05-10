#!/usr/bin/env bash
HOME_DIR="/Users/vlad/src/git-file-case-fixer"
PLUGIN="git-file-case-fixer"

cd ${HOME_DIR}

# installed versions on selected products to latest
function UpdProduct() {
    for PRODUCT in "$@"
    do
        if [ -d /Users/vlad/Library/"Application Support"/${PRODUCT} ]; then
            echo updating ${PRODUCT} for latest ${PLUGIN}
            rm -fr /Users/vlad/Library/"Application Support"/${PRODUCT}/${PLUGIN}
            cp ${PLUGIN}.jar /Users/vlad/Library/"Application Support"/${PRODUCT}
        else
            echo product ${PRODUCT} does not exist in /Users/vlad/Library/"Application Support"/
        fi
    done
}

UpdProduct "IdeaIC2018-1-EAP" "IdeaIC2017.3" "IdeaIC2018.1" "IntelliJIdea2018.1" "PhpStorm2018.1" "WebStorm2018.1" "CLion2018.1"
