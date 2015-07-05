#!/bin/bash
# TxBits - An open source Bitcoin and crypto currency exchange
# Copyright (C) 2014-2015  Viktor Stanchev & Kirk Zathey
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>.

db=txbits
sql="sudo -u postgres psql"

if [ -n "$1" ]; then
  db=$1
fi

if [ -n "$2" ]; then
  sql=$2
fi

$sql $db -c "select * from users"

