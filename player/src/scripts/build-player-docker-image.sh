#!/bin/bash

set -euxo pipefail

CHRONICLER_ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )/../../../" &> /dev/null && pwd )"

if [[ ! -f "$CHRONICLER_ROOT_DIR/player/build/libs/player-all.jar" ]]; then
    echo "Artifact is not found. This script has to be run after 'gradle player:shadowJar'."
    exit 1
fi

# Prepare cash-ci
CASH_CI_DIR=$CHRONICLER_ROOT_DIR/cash-ci
if [[ ! -d "$CASH_CI_DIR" ]]; then
    echo "cash-ci dir is not found. This script has to be run after cash-ci dir is initialized."
    exit 1
fi

pushd "$CHRONICLER_ROOT_DIR" || exit 1
  export GIT_COMMIT=${GIT_COMMIT:-`git rev-parse HEAD`}
  export GIT_BRANCH=${GIT_BRANCH:-`git branch --show-current`}

  docker build -t "chronicler-player:${GIT_COMMIT}" \
  -f player/src/scripts/Dockerfile \
  --build-arg APP_NAME=chronicler-player \
  --build-arg SHA="${GIT_COMMIT}" \
  .

popd || exit 1

# Push to ECR?
export PUSH=""
if [[ $GIT_BRANCH == "master" ]]; then
   PUSH="-s -p"
elif [[ -n "${KOCHIKU_CANARY_BUILD:-}" ]]; then
   PUSH="-s"
fi

if [[ -n "$PUSH" ]]; then
  "$CASH_CI_DIR"/cash-docker-push -r chronicler-player -t "${GIT_COMMIT}" $PUSH
fi