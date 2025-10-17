/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * @author Arne Kepp, OpenGeo, Copyright 2009
 */
package org.geowebcache.util;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import java.io.IOException;

/**
 * This is just thin accounting wrapper for ServletOutputStream for purposes of accounting
 *
 * @author ak
 */
public class AccountingOutputStream extends ServletOutputStream {

    final ServletOutputStream os;

    int count = 0;

    public AccountingOutputStream(ServletOutputStream os) {
        this.os = os;
    }

    @Override
    public void write(int b) throws IOException {
        count++;
        os.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        count += b.length;
        os.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        count += len;
        os.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        os.flush();
    }

    @Override
    public void close() throws IOException {
        os.close();
    }

    public int getCount() {
        return count;
    }

    /**
     * This method can be used to determine if data can be written without blocking.
     *
     * @return <code>true</code> if a write to this <code>ServletOutputStream</code> will succeed, otherwise returns
     *     <code>false</code>.
     * @since Servlet 3.1
     */
    @Override
    public boolean isReady() {
        return false;
    }

    /**
     * Instructs the <code>ServletOutputStream</code> to invoke the provided {@link WriteListener} when it is possible
     * to write
     *
     * @param writeListener the {@link WriteListener} that should be notified when it's possible to write
     * @throws IllegalStateException if one of the following conditions is true
     *     <ul>
     *       <li>the associated request is neither upgraded nor the async started
     *       <li>setWriteListener is called more than once within the scope of the same request.
     *     </ul>
     *
     * @throws NullPointerException if writeListener is null
     * @since Servlet 3.1
     */
    @Override
    public void setWriteListener(WriteListener writeListener) {}
}
