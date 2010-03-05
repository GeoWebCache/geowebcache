/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Arne Kepp, OpenGeo, Copyright 2009
 */
package org.geowebcache.util;

import java.io.IOException;

import javax.servlet.ServletOutputStream;

/**
 * This is just thin accounting wrapper for ServletOutputStream for
 * purposes of accounting
 * 
 * @author ak
 *
 */
public class AccountingOutputStream extends ServletOutputStream {

    final ServletOutputStream os;
    
    int count = 0;
    
    public AccountingOutputStream(ServletOutputStream os) {
        this.os = os;
    }
    
    public void write(int b) throws IOException {
        count++;
        os.write(b);
    }
    
    public void write(byte[] b) throws IOException {
        count+= b.length;
        os.write(b);
    }
    
    public void write(byte[] b, int off,int len) throws IOException {
        count += len;
        os.write(b, off, len);
    }

    public void flush() throws IOException {
        os.flush();
    }
    
    public void close() throws IOException {
        os.close();
    }
    
    public int getCount() {
        return count;
    }
}
