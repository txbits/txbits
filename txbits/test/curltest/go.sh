#!/bin/sh
date
for i in {1..50}
do
curl -d '{"pair":"LTC/CAD","amount":1,"price":1}' -b tmp -c tmp https://beta.newgox.com/api/1/bid -H 'Content-Type:application/json' 2>/dev/null > /dev/null --insecure
curl -d '{"pair":"LTC/CAD","amount":1,"price":1}' -b tmp -c tmp https://beta.newgox.com/api/1/ask -H 'Content-Type:application/json' 2>/dev/null > /dev/null --insecure
done
date
