# TSA Changelog

## v0.5.3 - v0.5.4

* Fixed CI (fix in int-blasting) by @tochilinak in https://github.com/espritoxyz/tsa/pull/309
* Fuller support for partial hash constraints by @tochilinak in https://github.com/espritoxyz/tsa/pull/311
* Implemented CDATASIZEQ by @metametamoon in https://github.com/espritoxyz/tsa/pull/308
* Fixed the dictionary concretization failure by @metametamoon in https://github.com/espritoxyz/tsa/pull/313
* Soft failures for symbolic C5 and BLESS by @tochilinak in https://github.com/espritoxyz/tsa/pull/314
* Reduced the number of forks during handling message without IgnoreError flag by @metametamoon in https://github.com/espritoxyz/tsa/pull/312
* SPLIT, SSKIPFIRST, SSKIPLAST, CONFIGPARAM, ADDRAND, RAND, SETRAND, RANDU256 instructions by @metametamoon in https://github.com/espritoxyz/tsa/pull/310

**Full Changelog**: https://github.com/espritoxyz/tsa/compare/v0.5.3...v0.5.4

## v0.5.2 - v0.5.3

* Optimized Z3 + several small optimizations by @tochilinak in https://github.com/espritoxyz/tsa/pull/306

**Full Changelog**: https://github.com/espritoxyz/tsa/compare/v0.5.2...v0.5.3

## v0.5.1 - v0.5.2

* Replay tests for tutorial by @metametamoon in https://github.com/espritoxyz/tsa/pull/285
* Several fixes and optimizations by @tochilinak in https://github.com/espritoxyz/tsa/pull/292
* Increased default solver timeout by @tochilinak in https://github.com/espritoxyz/tsa/pull/294
* Using TL-B memory in `TvmValueFixator` by @tochilinak in https://github.com/espritoxyz/tsa/pull/295
* Improved int-blasting usage by @tochilinak in https://github.com/espritoxyz/tsa/pull/297
* Fix CroutonFi test + solver timeout in CLI by @tochilinak in https://github.com/espritoxyz/tsa/pull/301
* Fixed the unsigned index in get_next on input dicts by @metametamoon in https://github.com/espritoxyz/tsa/pull/298
* Partial support of hash equality constraints by @tochilinak in https://github.com/espritoxyz/tsa/pull/303
* Support for reserve operations by @metametamoon in https://github.com/espritoxyz/tsa/pull/302
* Export fetched cells with option `--exported-inputs` by @tochilinak in https://github.com/espritoxyz/tsa/pull/305


**Full Changelog**: https://github.com/espritoxyz/tsa/compare/v0.5.1...v0.5.2

## v0.5.0 - v0.5.1

* Added soft constraints for balance and message value in https://github.com/espritoxyz/tsa/pull/288
* Added normalization for cell reads in https://github.com/espritoxyz/tsa/pull/289

**Full Changelog**: https://github.com/espritoxyz/tsa/compare/v0.5.0...v0.5.1

## v0.4.28-dev - v0.5.0

* Several new checker functions:
  -  `tsa_get_balance` in #150
  - `tsa_set_c4` in #198
  - `tsa_input_was_accepted` in #261
  -  `tsa_send_internal_message_with_body`, `tsa_send_external_message_with_body` in #264
  - New parameters in `on_out_message` in #163
  - Random addresses in #208
* New implementation of input dicts in #117, #280
* Fixes in #152, #147, #168, #183, #187, #177, #196, #202, #221, #227, #228, #223, #214, #237, #238, #242, #249, #252, #257, #248, #268, #269, #271, #276, #284
* Optimizations in #151, #258
* New instructions in #185, #232, #244, #245, #250, #251, #272, #275, #281
* CLI updates in #206, #211, #213, #217, #218, #222, #229, #235, #263, #277
* Supported  `SendIgnoreError` mode in #179
* Typed cell data in #212, #224
* Updated `tvm-spec` in #225

**Full Changelog**: https://github.com/espritoxyz/tsa/compare/v0.4.28-dev...v0.5.0

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
