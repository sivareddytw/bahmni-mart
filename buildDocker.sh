#!/usr/bin/env bash

export COMMIT=${TRAVIS_COMMIT::8}

docker login -u $DOCKER_USER -p $DOCKER_PASS
export REPO=anallytics/bahmni-mart
export TAG=$(if [ "$TRAVIS_BRANCH" == "master" ]; then echo "latest"; else echo $TRAVIS_BRANCH ; fi)
docker build -f Dockerfile -t $REPO:$COMMIT .
docker tag $REPO:$COMMIT $REPO:$TAG
docker tag $REPO:$COMMIT $REPO:travis-$TRAVIS_BUILD_NUMBER
docker push $REPO

if [ "$?" != 0 ]; then
   exit "$?"
fi