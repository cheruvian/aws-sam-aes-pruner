# aws-sam-aes-pruner
AWS SAM Template for Lambda that prunes AES clusters.

This Lambda function will delete indices in your Elasticsearch cluster older than 7 days.  

It also has a commented out sample for how to use the High level REST Client to do additional operations. 

## AWS Deployment

1. Configure your AWS credentials and region.
1. Set an environment variable named `S3_BUCKET`. This will be used for uploading your lambda function artifacts.
1. Simply run `bash bpd.sh` to `b`uild, `p`ackage and `d`eploy.   
