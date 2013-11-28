package org.geowebcache.service;

import junit.framework.TestCase;

import org.owasp.encoder.Encode;

/**
 * Unit test for {@link OWSException}
 * This fails if the encoding of the Exception Text or locator is not done
 *
 * @author Thijs Brentjens (thijs@brentjensgeoict.nl)
 * @version $Id$
 */

public class OWSExceptionEncodingTest extends TestCase {

	int httpCode;
	String exceptionCode = "OperationNotSupported";
	String textToEncode = "<'text>\"";

	// The exception locator and text are vulnerable for XSS for example and should be escaped. Other parts of the Exception are fine.
    public void testLocator() throws Exception {
        OWSException xssExceptionLocator = new OWSException(httpCode, exceptionCode, textToEncode, "exceptionText");
        assertTrue(isXmlEncoded(xssExceptionLocator));
    }

    public void testText() throws Exception {
		OWSException xssExceptionText = new OWSException(httpCode, exceptionCode, "locator", textToEncode);
        assertTrue(isXmlEncoded(xssExceptionText));
    }

    private boolean isXmlEncoded(OWSException exception) throws Exception{
		// We should find the text to test back as an encoded string.
		String text = exception.toString();
		String encoded = Encode.forXml(textToEncode);
		return text.contains(encoded);
    }
}
