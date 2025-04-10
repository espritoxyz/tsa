---
layout: default
title: TSA checker functions 
parent: Core mechanisms design
nav_order: 3
---

# Numeration of functions in `tsa_functions.fc`

Ordinary checker functions are numerated from 1.

A special case is `tsa_call_X_Y` functions. `Y` is the number of input parameters and `X` is the number of output parameters.

These functions' ids start at 10000. The last 2 digits correspond to `Y`, the previous 2 digits correspond to `X`.
