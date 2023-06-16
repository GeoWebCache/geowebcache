package org.geowebcache.util;

import java.util.Objects;
import java.util.Properties;

/**
 * Rule which allows a property to be set, and will return it to its original value.
 *
 * @author Kevin Smith, Boundless
 */
public class PropertyRule extends SetSingletonRule<String> {
    final Properties props;

    final String name;

    /** Create a rule to override a system property */
    public static PropertyRule system(String name) {
        return new PropertyRule(System.getProperties(), name);
    }

    /** Create a rule to override a property in the given Properties */
    public PropertyRule(Properties props, String name) {
        super();
        this.props = props;
        this.name = name;
    }

    /** Set the original value of the property */
    @Override
    public String getOldValue() {
        return oldValue;
    }

    /** Set the value of the property */
    @Override
    public void setValue(String value) {
        if (Objects.nonNull(value)) {
            props.setProperty(name, value);
        } else {
            props.remove(name);
        }
    }

    /** Get the name of the property */
    public String getName() {
        return name;
    }

    /** Set the current value of the property */
    @Override
    public String getValue() {
        return props.getProperty(name);
    }
}
