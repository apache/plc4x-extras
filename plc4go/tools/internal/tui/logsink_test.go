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

package tui_test

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/apache/plc4x-extras/plc4go/tools/internal/tui"
)

func TestLineWriterEmitsWholeLinesOnly(t *testing.T) {
	var got []string
	writer := tui.NewLineWriter(func(line string) { got = append(got, line) })

	_, err := writer.Write([]byte("first\nsec"))
	require.NoError(t, err)
	assert.Equal(t, []string{"first"}, got, "a partial line has to be held back, not shown as a fragment")

	_, err = writer.Write([]byte("ond\nthird\n"))
	require.NoError(t, err)
	assert.Equal(t, []string{"first", "second", "third"}, got)

	_, err = writer.Write([]byte("tail"))
	require.NoError(t, err)
	writer.Flush()
	assert.Equal(t, []string{"first", "second", "third", "tail"}, got)
}

func TestChannelSinkNeverBlocks(t *testing.T) {
	ch := make(chan string, 2)
	sink := tui.ChannelSink(ch)
	for range 100 {
		sink("line")
	}
	assert.Len(t, ch, 2, "a full channel drops rather than throttling whatever is logging")
}
