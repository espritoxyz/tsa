source ../../cli-tool-location.bash

OUTPUT_PATH="../output/error-detection-tact"
mkdir -p $OUTPUT_PATH

TSA_ARGS="tact --project=DividerProject --config=tact.config.json --input Divider -o $OUTPUT_PATH/out.sarif"
java -jar $TSA_CLI $TSA_ARGS
