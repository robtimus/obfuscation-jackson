/*
 * CharSequenceReader.java
 * Copyright 2019 Rob Spoor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.robtimus.obfuscation.jackson;

import static com.github.robtimus.obfuscation.ObfuscatorUtils.checkBounds;
import java.io.IOException;
import java.io.Reader;

final class CharSequenceReader extends Reader {

    private final CharSequence s;
    private final int end;

    private int index;
    private int mark;

    CharSequenceReader(CharSequence s, int start, int end) {
        this.s = s;
        this.end = end;

        index = start;
        mark = index;
    }

    @Override
    public int read() throws IOException {
        return index < end ? s.charAt(index++) : -1;
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        checkBounds(cbuf, off, off + len);
        if (len == 0) {
            return 0;
        }
        if (index >= end) {
            return -1;
        }
        int read = Math.min(len, end - index);
        for (int i = 0, j = off; i < len && index < end; i++, j++, index++) {
            cbuf[j] = s.charAt(index);
        }
        return read;
    }

    @Override
    public long skip(long n) throws IOException {
        if (n < 0) {
            throw new IllegalArgumentException(n + " < 0"); //$NON-NLS-1$
        }
        if (n == 0 || index >= end) {
            return 0;
        }
        int newIndex = (int) Math.min(end, index + Math.min(n, Integer.MAX_VALUE));
        long skipped = newIndex - index;
        index = newIndex;
        return skipped;
    }

    @Override
    public boolean ready() throws IOException {
        return index < end;
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public void mark(int readAheadLimit) throws IOException {
        mark = index;
    }

    @Override
    public void reset() throws IOException {
        index = mark;
    }

    @Override
    public void close() throws IOException {
        // does nothing
    }
}
