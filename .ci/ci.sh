#!/bin/bash

echo "SOMEVAR="
echo $SOMEVAR
echo "ASD="
echo $ASD

# Do the Gradle build
#./gradlew build || exit 1
#
#if [ "$TRAVIS_REPO_SLUG" == "diffplug/goomph" ] && [ "$TRAVIS_PULL_REQUEST" == "false" ] && [ "$TRAVIS_BRANCH" == "master" ]; then
#	# Publish the artifacts
#	./gradlew publish || exit 1
#	# Push the javadoc
#	./.ci/push-javadoc.sh
#fi
