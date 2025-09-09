package org.geowebcache.io.codec;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Test class ensuring that the GWC beans are correctly loaded from a test application context.
 *
 * @author Nicola Lagomarsini geosolutions
 */
public class TestSpring {

    @Test
    public void testBeanSelection() {
        // Selection of the Test Application Context
        try (ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("appContextTest2.xml")) {
            // Ensure that a decoder is present
            Object obj2 = context.getBean("TIFFDecoder");
            Assert.assertNotNull(obj2);
            Assert.assertTrue(obj2 instanceof ImageDecoderImpl);

            // Test if the container has been created
            ImageDecoderContainer container = context.getBean(ImageDecoderContainer.class);
            Assert.assertNotNull(container);
        }
    }
}
