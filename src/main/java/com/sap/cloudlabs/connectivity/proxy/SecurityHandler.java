package com.sap.cloudlabs.connectivity.proxy;

import java.util.List;

/**
 * 
 * The abstract class SecurityHandler should be used by an application 
 * to implement security checks and restrictions which are specific for
 * the concrete application scenario. 
 * 
 * An example is to implement the blacklisting of response headers 
 * sent by a used backend system.
 *
 */
public abstract class SecurityHandler {
	
	/**
	 * 
	 * This method should return a list of header names which will
	 * be filtered out by the proxy servlet before sending the 
	 * response back to the browser.
	 * 
	 * ProxyServlet by default is black-listing JSESSIONID header
	 * If an application wants to provide additional headers to be 
	 * black listed, this class should be implemented.
	 *
	 */
	public abstract List<String> getResponseHeadersBlackList();
	
	 
}
