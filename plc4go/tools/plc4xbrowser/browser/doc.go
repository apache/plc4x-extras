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

// Package browser is the terminal user interface of plc4xbrowser.
//
// It replaces a tview implementation whose command actions wrote their results directly into
// package-level widgets from background goroutines. That arrangement had three consequences
// this package is shaped to avoid.
//
// Nothing could be tested: an action returned an error and otherwise communicated only by
// mutating a widget, so there was no value to assert on. Here a command is data with a Run
// function that takes a [plcsession.Session] and returns a [Result]; the model turns results
// into state and the view renders state. Each of those three steps is separately testable, and
// none of them needs a terminal.
//
// Four advertised commands were never finished. read, write, browse and subscribe each
// answered "mode switch not yet implemented", because the mechanism they needed - a modal
// editor - was never built. It is built here, as the request composer.
//
// And the widgets were mutated off the event loop, which is a data race by construction. No
// goroutine in this package touches model state. Every stream, whether a subscription, the log
// or a command in flight, is drained by a tea.Cmd that reads one value and returns a message
// that re-arms itself.
package browser
