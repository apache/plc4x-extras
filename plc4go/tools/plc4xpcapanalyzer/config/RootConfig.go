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

package config

type RootConfig struct {
	CfgFile         string
	LogType         string
	LogLevel        string
	Verbosity       int
	HideProgressBar bool

	// Demo runs against a generated sample capture instead of a file supplied by the user, so
	// the tool can be tried out and manually debugged with no capture and no device to hand.
	Demo bool

	// Ascii forces the ASCII glyph set instead of the Unicode one, for terminals, fonts or
	// consoles that cannot render box-drawing characters. Left unset, the glyph set is guessed
	// from the locale.
	Ascii bool
}

var RootConfigInstance = RootConfig{}
