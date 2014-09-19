#!/bin/bash

cd $(dirname $0)

# litecoin
if [ ! -e litecoin ]; then
  git clone https://github.com/litecoin-project/litecoin.git
fi
pushd litecoin
git checkout v0.8.6.1
pushd src
make -f makefile.unix
popd
popd

# bitcoin
if [ ! -e bitcoin ]; then
  git clone https://github.com/bitcoin/bitcoin.git
fi
pushd bitcoin
git checkout v0.8.6
pushd src
make -f makefile.unix
popd
popd

rm -f .git/hooks/pre-commit
ln -s ../../dev/pre-commit .git/hooks/pre-commit
