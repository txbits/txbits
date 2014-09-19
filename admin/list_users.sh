#!/bin/bash
# Copyright (c) 2014 Viktor Stanchev & Kirk Zathey.
# This file is licensed under the Affero General Public License version 3 or later,
# see the accompanying file COPYING or http://www.gnu.org/licenses/agpl.html.

db=txbits
sql="sudo -u postgres psql"

if [ -n "$1" ]; then
  db=$1
fi

if [ -n "$2" ]; then
  sql=$2
fi

$sql $db -c "select * from users"

