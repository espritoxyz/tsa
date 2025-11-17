# TSA Changelog

## v0.4.27-dev - v0.4.28-dev

* Strategy for stopping analysis right after executions with required exit codes are found in https://github.com/espritoxyz/tsa/pull/134
* More CLI options in https://github.com/espritoxyz/tsa/pull/135:
  - `--max-recursion-depth`
  - `--no-recursion-depth-limit`
  - `--iteration-limit`
  - `--no-iteration-limit`
  - `--stop-when-exit-codes-found`
* Implemented precise calculation of `fwd_fee` in https://github.com/espritoxyz/tsa/pull/128
* TSA core optimizations, improvements and fixes in https://github.com/espritoxyz/tsa/pull/121, https://github.com/espritoxyz/tsa/pull/122, https://github.com/espritoxyz/tsa/pull/125, https://github.com/espritoxyz/tsa/pull/133, https://github.com/espritoxyz/tsa/pull/127,  https://github.com/espritoxyz/tsa/pull/137


**Full Changelog**: https://github.com/espritoxyz/tsa/compare/v0.4.27-dev...v0.4.28-dev

## v0.4.26-dev - v0.4.27-dev

- New handlers for checkers: `on_out_message` and `on_compute_phase_exit`
- New format for communication scheme
- Support some sending modes: `SendRemainingBalance`, `SendRemainingValue`, `SendFwdFeesSeparately`
- CLI option `--covered-instructions-list`
- Optimizations and fixes in TSA core

## v0.4.25-dev - v0.4.26-dev

- CLI option `--continue-on-contract-exception`
- Implemented bouncing of messages
- Logging contract events (compute phase results for each received message)
- Several optimizations and fixes in core
- Implemented `STONES` instruction
- Fixed printing slices in SARIF report

## v0.4.24-dev - v0.4.25-dev

- Checker function `tsa_send_external_message`
- Fixes in TSA core

## v0.4.21-dev - v0.4.24-dev

- Implemented checker function `tsa_get_c4`
- Fixes in TSA core

## v0.4.20-dev - v0.4.21-dev

- Logging step errors

## v0.4.19-dev - v0.4.20-dev

- New functionality in checker language: `tsa_send_internal_message`

## v0.4.18-dev - v0.4.19-dev

- Fix in working with directories

## v0.4.17-dev - v0.4.18-dev

- Now TSA should be working on Windows
- Added `--timeout` option for checkers

## v0.4.16-dev - v0.4.17-dev

- Removed option `--func-std` in CLI
- Optimizations in TSA core

## v0.4.15-dev - v0.4.16-dev

- Fix in CLI

## v0.4.14-dev - v0.4.15-dev

- Added option for concrete C4 in custom checker mode

## v0.4.13-dev.1 - v0.4.14-dev

- Fixed getting currect directory in `TactAnalyzer`
- Improvements in TSA core

## v0.4.12-dev.1 - v0.4.13-dev.1

- Improvements in TSA core

## v0.4.11-dev - v0.4.12-dev.1

- Fixed bug with Tact `--config` option
- Some new instructions

## v0.4.10-dev.1 - v0.4.11-dev

- `--timeout` option in CLI
- Enable quiet mode in CLI runs

## v0.4.9-dev.1 - v0.4.10-dev.1

- New format of instruction location in SARIF report
- Modifications in CLI options

## v0.4.8-dev.1 - v0.4.9-dev.1

- Added option `--tact` for specifying Tact executable.

## v0.4.7-dev.1 - v0.4.8-dev.1

- Fixes in TL-B parsing rules, process bounced messaged separately.
