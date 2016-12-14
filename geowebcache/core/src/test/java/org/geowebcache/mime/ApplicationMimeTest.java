package org.geowebcache.mime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApplicationMimeTest {
    
    @Parameters(name = "{index}: {0}")
    public static Collection<Object[]> data() {
        List<Object[]> data = new ArrayList<>();
        for (MimeType mt : ApplicationMime.ALL) {
            data.add(new Object[] {mt.getFormat(), mt});
        }
        return data;
    }

    private MimeType mimeType;
    
    public ApplicationMimeTest(String format, MimeType mt) {
        this.mimeType = mt;
    }

     @Test
     public void testMimeType() throws MimeException {
         MimeType result = MimeType.createFromFormat(mimeType.getFormat());
         // Ensure it is not null
         assertNotNull(result);
         // Ensure it is the same instance
         assertEquals(mimeType, result);
         // get The MimeType from Extension
         result = MimeType.createFromExtension(mimeType.getFileExtension());
         // Ensure it is the same instance
         assertEquals(mimeType, result);
     }

}
