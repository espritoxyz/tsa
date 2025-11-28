source ../../cli-tool-location.bash

OUTPUT_PATH="../../output/error-detection-func"
mkdir -p $OUTPUT_PATH

TSA_ARGS="func -i main.fc --fift-std ../../fiftstdlib --method 0 -o $OUTPUT_PATH/out.sarif"
java -jar $TSA_CLI $TSA_ARGS
