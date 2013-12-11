package org.geowebcache.filter.resource;

import static org.junit.Assert.*;
import static org.easymock.classextension.EasyMock.*;


import java.io.File;

import javax.servlet.ServletContext;

import org.apache.commons.exec.CommandLine;
import org.easymock.classextension.EasyMock;
import org.geowebcache.util.ApplicationContextProvider;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.web.context.WebApplicationContext;


import com.google.common.io.Files;

public class PNGCrushFilterTest {
    
    PNGCrushFilter filter;
    
    File stagingArea;
    File pngCrushPath;
    
    @Before
    public void setUp() throws Exception {
        
        stagingArea = Files.createTempDir();
        
        ServletContext sctxt = EasyMock.createMock(ServletContext.class);
        WebApplicationContext wactxt = EasyMock.createMock(WebApplicationContext.class);
        expect(wactxt.getServletContext()).andStubReturn(sctxt);
        expect(sctxt.getInitParameter("PNGCRUSH_PATH")).andStubReturn(null);
        expect(sctxt.getInitParameter("PNGCRUSH_OPTIONS")).andStubReturn(null);
        
        ApplicationContextProvider acp = new ApplicationContextProvider();
        acp.setApplicationContext(wactxt);
        
        replay(sctxt, wactxt);
        
        filter = new PNGCrushFilter(stagingArea, acp);
    }
    
    @After
    public void tearDown() throws Exception {
        stagingArea.delete();
    }
    
    @Test
    public void test() {
        filter.setPngCrushPath("/usr/bin/arbitrary space/pngcrush");
        filter.setOptions("-a -b -c\"foo bar\" -d \"quux quam\"");

        String[] result = filter.getCommandLineArguments(new File("/test/blah1"), new File("/test/blah 2")).toStrings();
        String[] expected = CommandLine.parse("/usr/bin/\"arbitrary space\"/pngcrush -a -b -c\"foo bar\" -d \"quux quam\" /test/blah1 \"/test/blah 2\"").toStrings();
        
        assertThat(result, Matchers.equalTo(expected));
    }

}
