#!/usr/bin/env sh

#
# Copyright 2015 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

##############################################################################
##
##  Gradle start up script for UN*X
##
##############################################################################

# Attempt to set APP_HOME
# Resolve links: $0 may be a symlink
PRG="$0"
# Need this for relative symlinks.
while [ -h "$PRG" ] ; do
    ls -ld "$PRG"
    link=`ls -l "$PRG" | awk '{print $NF}'`
    case $link in
        /*) PRG="$link" ;;
        *) PRG=`dirname "$PRG"`"/$link" ;;
    esac
done
SAVED="$(cd "$(dirname \"$PRG\")" && pwd)"
cd "$SAVED" || exit
APP_HOME=$(pwd -P) || exit
cd "$OLDPWD" || exit

APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`

# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS='\" -Xmx64m -Xms64m \"'

# Use the maximum available, or set MAX_FD != maximum.
MAX_FD="maximum"

warn () {
    echo "$*"
} >&2

die () {
    echo
    echo "$*"
    echo
    exit 1
} >&2

# OS specific support (must be 'true' or 'false').
darwin=false
msys=false
cygwin=false
mingw=false
case \"$(uname)\" in
  CYGWIN* )
    cygwin=true
    ;;
  Darwin* )
    darwin=true
    ;;
  MSYS* )
    msys=true
    ;;
  MINGW* )
    mingw=true
    ;;
esac

# Determine the Java command to use to start the JVM.
if [ -n \"$JAVA_HOME\" ] ; then
    if [ -x \"$JAVA_HOME/jre/sh/java\" ] ; then
        # IBM's JDK on AIX uses strange locations for the executables
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x \"$JAVACMD\" ] ; then
        die \"ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation.\"
    fi
else
    JAVACMD="java"
    if ! command -v java >/dev/null 2>&1
    then
        die \"ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation.\"
    fi
fi

# Increase the maximum file descriptors if we can.
if ! \"$cygwin\" && ! \"$darwin\" && ! \"$msys\" ; then
    case $- in
      *i*) ;;
      *) set -o nounset ;;
    esac
    ulimit -n 262144
fi

# Escape application args
save () {
    for i do printf %s\\n \"$i\" | sed \"s/'/'\\\\\\\\''/g;1s/^/'/;\$s/\$/'\" ; done
    echo \" \"
}
APP_ARGS=$(save \"$@\")

# Collect all arguments for the java command, stacking in reverse order:
set -- \
        \"-Dorg.gradle.appname=$APP_BASE_NAME\" \
        -classpath \"$APP_HOME/gradle/wrapper/gradle-wrapper.jar\" \
        org.gradle.wrapper.GradleWrapperMain \
        \"$APP_ARGS\"

# by default we should be in the correct project dir, but when run from Finder on Mac, the cwd is wrong
if [ \"$(uname)\" = \"Darwin\" ] && [ \"$HOME\" = \"$PWD\" ] ; then
    cd \"$(dirname \"$0\")\" || exit
fi

exec \"$JAVACMD\" \"$@\"
