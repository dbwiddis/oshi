#!/bin/sh
# Fork-only ratio collection loop. Usage: sh ratio-loop.sh <name> <iterations> [extra mvn args]
name=$1; n=$2; shift 2
out=ratios-$name
mkdir -p "$out"
./mvnw install -B -q -DskipTests -Djacoco.skip=true -Ddependency-check.skip=true -Dmaven.gitcommitid.skip=true "$@" || exit 1
i=1
while [ "$i" -le "$n" ]; do
  if ./mvnw -B -o -pl oshi-benchmark test -Paggregate-coverage -Djacoco.skip=true -Ddependency-check.skip=true \
      -Dmaven.gitcommitid.skip=true "$@" > iter.log 2>&1; then rc=0; else rc=$?; fi
  tr -d '\033' < iter.log | sed 's/\[[0-9;]*m//g' > iter.clean
  sed -n 's/.*\(RATIOLOG|.*\)/\1/p' iter.clean | sed "s/^/$i|/" >> "$out/ratios.txt"
  grep -E '\[X\]|expected|Expecting' iter.clean | sed "s/^/$i|/" >> "$out/failures.txt"
  echo "$i|rc=$rc" >> "$out/runs.txt"
  echo "iteration $i rc=$rc"
  i=$((i + 1))
done
echo "lines: $(wc -l < "$out/ratios.txt")"
exit 0
