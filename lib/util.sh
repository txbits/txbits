ME=`basename $0`

debug_sanity() {
  # Ensure that DEBUG is set
  if [ ${DEBUG:-0} = 0 ] ; then
    [ -z "$1" ] && error "WARNING: \$DEBUG not set"
    DEBUG=0
  fi
}
debug_sanity "first time"


debug() {
  local level=$1
  shift
  if [ $level -le $DEBUG ] ; then
    unset IFS
    # Output debug level if it's over threashold
    if [ $DEBUG -ge ${DEBUGEXTRA:-10} ]; then
      echo "${level}: $@" 1>&2
    else
      echo "$@" 1>&2
    fi
  fi
}

debug_vars () {
  level=$1
  shift
  local out=''
  local value=''
  for variable in $*; do
    eval value=\$$variable
    out="$out $variable=$value"
  done
  debug $level $out
}


error() {
  while [ "$1" = "-c" -o "$1" = "-n" ]; do
    if [ "$1" = "-n" ]; then
      lineno=$2
      shift 2
    fi
    if [ "$1" = "-c" ]; then
      local stack=0
      shift
    fi
  done

  echo "$@" 1>&2
  if [ -n "$stack" ]; then
    stacktrace 1 # Skip our own frame
  else
    [ -n "$lineno" ] && echo "File \"$0\", line $lineno" 1>&2
  fi
}

die() {
  return=$1
  shift
  error "$@"
  [ $DEBUG -gt 0 ] && stacktrace 1
  if [ -n "$DIE_EXTRA" ]; then
    local lineno=''
    error
    error $DIE_EXTRA
  fi
  exit $return
}


stacktrace () {
  debug 200 "stacktrace( $@ )"
  local frame=${1:-0}
  local line=''
  local file=''
  debug_vars 200 frame line file

  # NOTE the stderr redirect below!
  (
    echo
    echo Stacktrace:
    while caller $frame; do
      frame=$(( $frame + 1 ))
    done | while read line function file; do
      if [ -z "$function" -o "$function" = main ]; then
        echo "$file: line $line"
      else
        echo "$file: line $line: function $function"
      fi
    done
  ) 1>&2
}


# vi: expandtab ts=2 sw=2
