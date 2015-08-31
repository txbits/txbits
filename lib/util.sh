ME=`basename $0`

debug() {
  local level
  level=$1
  shift
  [ ${DEBUG:-0} -ge $level ] && error $@
}

error() {
  echo $@ >&2
}

die() {
  return=$1
  shift
  error $@
  exit $return
}

# vi: expandtab ts=2 sw=2
