package org.geowebcache.util;

import java.util.Properties;

/**
 * Rule which allows a property to be set, and will return it to its original
 * value.
 *
 * @author Kevin Smith, Boundless
 */
public class PropertyRule extends org.junit.rules.ExternalResource {
    final Properties props;
    
    final String name;
    
    String oldValue;
    
    /**
     * Create a rule to override a system property
     * @param name
     * @return
     */
    public static PropertyRule system(String name) {
        return new PropertyRule(System.getProperties(), name);
    }
    
    /**
     * Create a rule to override a property in the given Properties
     * @param name
     * @return
     */
    public PropertyRule(Properties props, String name) {
        super();
        this.props = props;
        this.name = name;
    }
    
    /**
     * Set the original value of the property
     * @return
     */
    public Object getOldValue() {
        return oldValue;
    }
    
    /**
     * Set the value of the property
     * @return
     */
    public void setValue(String value) {
        props.setProperty(name, value);
    }
    
    /**
     * Get the name of the property
     * @return
     */
    public String getName() {
        return name;
    }
    
    @Override
    protected void before() throws Throwable {
        this.oldValue = props.getProperty(name);
    }
    
    @Override
    protected void after() {
        if (this.oldValue == null) {
            props.remove(name);
        } else {
            props.setProperty(name, oldValue);
        }
    }
}