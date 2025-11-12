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

TODO

**By default, the iteration limit and maximum recursion depth are set.** To get concrete values, use `--help` CLI option.

## Exploring Specific Exit Codes

TODO
