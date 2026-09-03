/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package main

import (
	"os"

	"github.com/apache/plc4x-extras/plc4go/tools/plc4xbrowser/browser"
)

// version labels the status bar. It is a build-time value; the tool deliberately has no
// --version flag.
var version = "1.0.0-SNAPSHOT"

func main() {
	// Execute reports its own errors through fang's styled handler, so main only needs to set
	// the exit status.
	if err := browser.Execute(version); err != nil {
		os.Exit(1)
	}
}
