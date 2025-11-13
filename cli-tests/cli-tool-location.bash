SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
TSA_CLI_PATH="$SCRIPT_DIR/tsa-cli.jar"
[ -f "$TSA_CLI_PATH" ] || wget -q -O "$TSA_CLI_PATH" https://github.com/espritoxyz/tsa/releases/download/v0.4.27-dev/tsa-cli.jar

TSA_CLI=$(realpath "$TSA_CLI_PATH")
export TSA_CLI
