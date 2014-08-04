package org.geowebcache.service.wms;

import junit.framework.Assert;

import org.geowebcache.io.ImageEncoderContainer;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;


public class TestSpring{

    @Test
    public void testBeanSelection() {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("appContextTest2.xml");
        Object obj = context.getBean("BMPEncoder");
        Assert.assertNotNull(obj);
    }
    
    @Test
    public void testContainer() {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("appContextTest2.xml");
        ImageEncoderContainer container = context.getBean(ImageEncoderContainer.class);
        Assert.assertNotNull(container);
    }

}
