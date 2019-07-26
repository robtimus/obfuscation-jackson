/*
 * CopyingReader.java
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

import java.io.IOException;
import java.io.Reader;

final class CopyingReader extends Reader {

    private final Reader delegate;
    final StringBuilder contents;

    private int mark;

    CopyingReader(Reader delegate) {
        this.delegate = delegate;
        contents = new StringBuilder();

        mark = 0;
    }

    @Override
    public int read() throws IOException {
        int read = delegate.read();
        if (read != -1) {
            contents.append((char) read);
        }
        return read;
    }

    @Override
    public int read(char[] cbuf) throws IOException {
        int read = delegate.read(cbuf);
        if (read > 0) {
            contents.append(cbuf, 0, read);
        }
        return read;
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        int read = delegate.read(cbuf, off, len);
        if (read > 0) {
            contents.append(cbuf, off, read);
        }
        return read;
    }

    // read(CharBuffer) calls read(char[], int, int) to do the actual reading

    // skip(long) calls read(char[], int, int) to do the actual skipping

    @Override
    public boolean ready() throws IOException {
        return delegate.ready();
    }

    @Override
    public boolean markSupported() {
        return delegate.markSupported();
    }

    @Override
    public void mark(int readAheadLimit) throws IOException {
        delegate.mark(readAheadLimit);
        mark = contents.length();
    }

    @Override
    public void reset() throws IOException {
        delegate.reset();
        contents.delete(mark, contents.length());
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
