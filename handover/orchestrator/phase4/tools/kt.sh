#!/bin/bash
# Local Kotlin compile + JUnit run (no Android). Usage:
#   kt.sh <outdir> <test-class-or-"none"> <file.kt|dir> ...
# Compiles Room stubs + given files with stdlib, coroutines, junit; runs JUnitCore on the test class(es) (comma list).
L=/opt/gradle-8.14.3/lib; T=$(dirname "$0")
CP="$L/kotlin-stdlib-2.0.21.jar:$L/kotlinx-coroutines-core-jvm-1.6.4.jar:$L/junit-4.13.2.jar:$L/hamcrest-core-1.3.jar"
OUT=$1; TESTS=$2; shift 2
rm -rf "$OUT"; mkdir -p "$OUT"
java -cp "$(ls $L/*.jar | tr '\n' ':')" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  "$T/stubs" "$@" -d "$OUT" -no-stdlib -classpath "$CP" -jvm-target 17 -nowarn 2>&1 | grep -v JAVA_TOOL_OPTIONS
rc=${PIPESTATUS[0]}
[ $rc -ne 0 ] && { echo "COMPILE FAILED rc=$rc"; exit $rc; }
echo "COMPILE OK"
[ "$TESTS" = "none" ] && exit 0
java -cp "$OUT:$CP" org.junit.runner.JUnitCore ${TESTS//,/ } 2>&1 | grep -v -E "JAVA_TOOL_OPTIONS|^\s+at " | tail -25
exit ${PIPESTATUS[0]}
