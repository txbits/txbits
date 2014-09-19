#!/bin/bash

# remember to start postgres on your own!

#killall bitcoind -w
#killall litecoind -w

./litecoin/src/litecoind -datadir=./litecoin_testnet
./bitcoin/src/bitcoind -datadir=./bitcoin_testnet

