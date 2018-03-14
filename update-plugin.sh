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

#UpdJar CodeGlance "IdeaIC16" "IntelliJIdea16" "IdeaIC15" "IntelliJIdea15" "RubyMine80" "PyCharm40" "PyCharm50" "Webide100" "Webide110"
#UpdZip CodeGlance "IdeaIC16" "IntelliJIdea16" "IdeaIC15" "IntelliJIdea15" "RubyMine80" "PyCharm40" "PyCharm50" "Webide100" "Webide110"
UpdProduct "IdeaIC2018-1-EAP" "IdeaIC2017-3" "IdeaIC2017.3" "IdeaIC2018.1" "IntelliJIdea2017.3" "IntelliJIdea2018.1" "PhpStorm2017.3" "PhpStorm2018.1" "WebStorm2017.3" "WebStorm2018.1" "CLion2017.3" "CLion2018.1"

# Update Intellij Community build/debug
#PRODUCT=/Users/vlad/src/intellij-community/config/plugins
#if [ -d ${PRODUCT} ]; then
#    echo updating ${PRODUCT} for latest idea-multimarkdown
#    rm -fr "${PRODUCT}"/idea-multimarkdown
#    unzip -bq ../idea-multimarkdown.zip -d "${PRODUCT}"
#else
#    echo product ${PRODUCT} does not exist
#fi
