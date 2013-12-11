package org.geowebcache.filter.resource;

import static org.junit.Assert.*;
import static org.junit.Assume.*;
import static org.easymock.EasyMock.expect;
import static org.easymock.classextension.EasyMock.replay;
import static org.hamcrest.Matchers.*;

import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.File;
import java.io.InputStream;
import java.nio.channels.Channels;

import javax.imageio.ImageIO;
import javax.servlet.ServletContext;

import org.easymock.classextension.EasyMock;
import org.geowebcache.filter.resource.PNGCrushFilter;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.mime.MimeType;
import org.geowebcache.util.ApplicationContextProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.springframework.web.context.WebApplicationContext;

import com.google.common.io.Files;

@RunWith(Theories.class)
public class PNGCrushFilterTheoryTest {

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
    
    @Theory
    public void theoryResultNeverBigger(Data data) throws Exception {
        
        long oldSize = data.resource.getSize();
        
        filter.applyTo(data.resource, data.type);
        
        long newSize = data.resource.getSize();
        
        assertThat(newSize, lessThanOrEqualTo(oldSize));
    }
    
    @Theory
    @Ignore // Not happy when PNGCRUSH_PATH is not set as no datapoints will pass the assumptions
    public void theoryPNGsGetSmaller(Data data) throws Exception {
        assumeThat(filter.appliesTo(data.type), is(true));
        assumeThat(filter.hasOptimizerPath(), is(true));
        
        long oldSize = data.resource.getSize();
        
        filter.applyTo(data.resource, data.type);
        
        long newSize = data.resource.getSize();
        
        assertThat(newSize, lessThan(oldSize));
    }
    
    @Theory
    public void theoryResultIsValidImage(Data data) throws Exception {
        assumeThat(data.type, is(ImageMime.class));
        
        filter.applyTo(data.resource, data.type);
        
        ImageIO.read(data.resource.getInputStream());
        
    }
    @Theory
    public void theoryStagingAreaLeftEmpty(Data data) throws Exception {
        
        filter.applyTo(data.resource, data.type);
        
        assertThat(stagingArea.list().length, is(0));
        
    }
    
    @Theory
    public void theoryResultIsSameImage(Data data) throws Exception {
        // The optimized image should be identical to the original
        assumeThat(data.type, is(ImageMime.class));
        
        BufferedImage oldImage = ImageIO.read(data.resource.getInputStream());
        
        filter.applyTo(data.resource, data.type);
        
        BufferedImage newImage = ImageIO.read(data.resource.getInputStream());
        
        assertTrue(compareRaster(newImage.getData(), oldImage.getData()));
        
    }
    
    @DataPoints
    public static Data[] data() throws Exception {
        return new Data[] {
                makeData("noisyblue.png", ImageMime.png),
                makeData("noisyblue.png", ImageMime.png24),
                makeData("noisyblue.png", ImageMime.png_24),
                makeData("noisyblue8.png", ImageMime.png),
                makeData("noisyblue8.png", ImageMime.png8),
                makeData("noisyblue.jpg", ImageMime.jpeg)
        };
    }
    
    boolean compareRaster(Raster r1, Raster r2) {
        if(!r1.getBounds().equals(r2.getBounds())) return false;
        if(r1.getNumBands()!=r2.getNumBands()) return false;
        for(int b=0; b<r1.getNumBands(); b++) {
            for(int x=0; x<r1.getWidth(); x++) {
                for(int y=0; y<r1.getHeight(); y++) {
                    if(r1.getSample(x,y,b)!=r1.getSample(x,y,b)) return false;
                }
            }
        }
        return true;
    }
    
    static Data makeData(String name, MimeType type) throws Exception{
        Resource res = new ByteArrayResource();
        InputStream stream = PNGCrushFilterTheoryTest.class.getResourceAsStream(name);
        res.transferFrom(Channels.newChannel(stream));
        stream.close();
        Data data = new Data();
        data.resource = res;
        data.type = type;
        
        return data;
    }
    
    static class Data {
        public Resource resource;
        public MimeType type;
    }
}
