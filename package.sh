#!/usr/bin/env bash -e

if [ -z "$S3_BUCKET" ]; then
    echo "\$S3_BUCKET was not set. Set an S3 Bucket for uploading the SAM artifacts";
    exit 1;
fi

######## SWAGGER PACKAGING ########
SWAGGER_PATH=sam/swagger.yaml
MAPPING_TEMPLATE_PATH=sam/mapping-templates
if [ -f $SWAGGER_PATH ]; then
    if [ -z "$AWS_DEFAULT_REGION" ]; then
        echo "\$AWS_DEFAULT_REGION was not set. Set an AWS region (like us-east-1) to use in your API swagger document.";
        exit 1;
    fi

    # Use your credentials to get the AWS AccountId
    ACCOUNT_ID=$(aws sts get-caller-identity | grep Account | sed 's/.* "//' | sed 's/".*//')

	swagger=$(sed "s/<<region>>/$AWS_DEFAULT_REGION/g" $SWAGGER_PATH | sed "s/<<accountId>>/$ACCOUNT_ID/g")
	mapping_templates=$(echo $swagger| grep ::require:: | sed 's/.*::require:://' | sed 's/::.*//')
	for mapping_template in $mapping_templates; do 
		echo "Injecting $mapping_template into swagger";
		mapping=$(cat $MAPPING_TEMPLATE_PATH/$mapping_template)

		swagger=$(python escape-replace-templates.py "$swagger" $mapping_template "$mapping")
	done

	echo "$swagger" > target/swagger.yaml
fi

set -x

aws cloudformation package \
    --template-file sam/template.yaml \
    --s3-bucket $S3_BUCKET \
    --output-template-file target/packaged-template.yaml