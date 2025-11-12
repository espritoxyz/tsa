---
layout: default
title: Stopping
parent: Reference
nav_order: 3
---

# Strategies for Stopping Analysis

The analyzed contracts often have too many possible execution paths to be fully analyzed.
In this case, you can use one of the strategies to stop the analysis.

## Timeout

Stop the analysis after the given amount of time. Set with `--timeout` CLI option.

By default, analysis has no timeout.

## Iteration Limit and Maximum Recursion Depth

- Iteration limit. Skip executions where the number of iterations in a loop exceeds the given limit. Can be set with `--iteration-limit` CLI option. To remove the limit, use `--no-iteration-limit` flag.
- Maximum recursion depth. Skip executions where some method occurs in a call stack more times then the given limit. Can be set with `--max-recursion-depth` CLI option. To remove the limit, use `--no-recursion-depth-limit` flag.

**By default, the iteration limit and maximum recursion depth are set.** To get concrete values, use `--help` CLI option.

## Exploring Specific Exit Codes

If the goal of the analysis is to find some execution with specific exit code, you can use `--stop-when-exit-codes-found` CLI option. 
This option can be used several times for different exit codes. 
Stop the analysis right after executions with all required exit codes are found.
