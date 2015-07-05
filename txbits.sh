#!/bin/bash
pushd $(dirname $0)/txbits
../activator-1.3.5-minimal/activator $@
popd
