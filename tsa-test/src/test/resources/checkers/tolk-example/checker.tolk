import "../../imports/tsa_functions"


fun main() {
    tsa_send_internal_message(1, 0);
    throw(401);
}
