source ../../cli-tool-location.bash

OUTPUT_PATH="../../output/iteration-limit"
mkdir -p $OUTPUT_PATH

TSA_ARGS_3="func -i main.fc --fift-std ../../fiftstdlib --method 0 --iteration-limit 2 -o $OUTPUT_PATH/iter-limit-2.sarif"
TSA_ARGS_7="func -i main.fc --fift-std ../../fiftstdlib --method 0 --iteration-limit 8 -o $OUTPUT_PATH/iter-limit-8.sarif"
TSA_ARGS_0="func -i main.fc --fift-std ../../fiftstdlib --method 0 -o $OUTPUT_PATH/iter-limit-0.sarif"
java -jar $TSA_CLI $TSA_ARGS_3
java -jar $TSA_CLI $TSA_ARGS_7
java -jar $TSA_CLI $TSA_ARGS_0
