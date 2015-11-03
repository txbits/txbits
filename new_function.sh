#!/bin/sh

BASEDIR=`dirname $0`
if ! . $BASEDIR/lib/util.sh; then
  echo "FATAL: error sourcing $BASEDIR/lib/util.sh" 1>&2
  exit 99
fi

usage() {
  cat << _EOF_
Creates a new function file and opens in editor.

Usage:

$ME function_name
_EOF_
   exit 1
}

[ $# -eq 1 ] || usage

name=`echo $1 | sed -e 's/\.sql$//'`
echo $name | grep -q / && error "WARNING: '/' detected in function name ($1)"

file="$BASEDIR/functions/$name.sql"

[ -e "$file" ] && die 2 "ERROR: $file already exists"

cat << _EOF_ >> $file # Be paranoid about not over-writing
CREATE OR REPLACE FUNCTION $name(
) RETURNS  LANGUAGE plpgsql AS \$body$
DECLARE
BEGIN
END
\$body$;

SELECT ddl_tools.test_function(
  '_test_$name'
  , $body$
  s CONSTANT name := bs;
  f CONSTANT name := fn;
BEGIN
END
$body$
);

-- vi: expandtab ts=2 sw=2
_EOF_

git add $file

if [ -n "$VISUAL" ]; then
    $VISUAL $file &
elif [ -n "$EDITOR" ]; then
    $EDITOR file
fi

cat << _EOF_
Don't forget to add

\i $file
_EOF_
