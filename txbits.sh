#!/bin/bash
pushd $(dirname $0)/txbits
../activator-1.2.10-minimal/activator $@
popd
