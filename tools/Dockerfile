#  Licensed to the Apache Software Foundation (ASF) under one or more
#  contributor license agreements.  See the NOTICE file distributed with
#  this work for additional information regarding copyright ownership.
#  The ASF licenses this file to You under the Apache License, Version 2.0
#  (the "License"); you may not use this file except in compliance with
#  the License.  You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

##########################################################################################
# Build PLC4X
##########################################################################################

# This is the image we'll use to execute the build (and give it the name 'build').
# (This image is based on Ubuntu)
# Fixed version of this in order to have a fixed JDK version
FROM azul/zulu-openjdk:21 as build

# Install some stuff we need to run the build
RUN apt update -y

# Install general purpose tools
RUN apt install -y make libpcap-dev libc-dev git

# Required for "with-c" profile
RUN apt install -y build-essential

# Required for running on Windows systems
RUN apt install -y dos2unix

# The release scripts can run this image as the calling user, so the exception for the
# bind-mounted checkout has to live in the system config: a "--global" one lands in
# "/root/.gitconfig", which that user never reads, and git then refuses to touch "/ws".
RUN git config --system --add safe.directory /ws

# Change the working directory (where commands are executed) into the new "ws" directory
WORKDIR /ws