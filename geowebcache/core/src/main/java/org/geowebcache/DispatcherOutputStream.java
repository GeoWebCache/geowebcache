/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * <p>Copyright 2019
 */
package org.geowebcache;

import java.io.IOException;
import java.io.OutputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;

/**
 * A wrapper for a Dispatcher destination output stream that signals {@link IOException}s thrown
 * while writing to the underlying destination as ignorable for OWS exception reporting, by throwing
 * a {@link ClientStreamAbortedException}.
 *
 * @author Gabriel Roldan (TOPP)
 * @version $Id$
 * @since 1.6.x
 */
public final class DispatcherOutputStream extends ServletOutputStream {
    private final ServletOutputStream real;

    public DispatcherOutputStream(ServletOutputStream real) {
        this.real = real;
    }

    /** @see OutputStream#flush() */
    public void flush() throws ClientStreamAbortedException {
        try {
            real.flush();
        } catch (IOException e) {
            throw new ClientStreamAbortedException(e);
        }
    }

    /** @see OutputStream#write(byte[], int, int) */
    public void write(byte b[], int off, int len) throws ClientStreamAbortedException {
        try {
            real.write(b, off, len);
        } catch (IOException e) {
            throw new ClientStreamAbortedException(e);
        }
    }

    /** @see OutputStream#write(int) */
    public void write(int b) throws ClientStreamAbortedException {
        try {
            real.write(b);
        } catch (IOException e) {
            throw new ClientStreamAbortedException(e);
        }
    }

    /** @see OutputStream#close() */
    public void close() throws ClientStreamAbortedException {
        try {
            real.close();
        } catch (IOException e) {
            throw new ClientStreamAbortedException(e);
        }
    }

    @Override
    public boolean isReady() {
        return real.isReady();
    }

    @Override
    public void setWriteListener(WriteListener writeListener) {
        real.setWriteListener(writeListener);
    }
}
