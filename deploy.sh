#!/usr/bin/env bash

set -x
aws cloudformation deploy \
    --stack-name aws-sam-aes-pruner \
    --capabilities CAPABILITY_NAMED_IAM \
    --template-file target/packaged-template.yaml
