/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the LICENSE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.sap.cloudlabs.connectivity.proxy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.DeflateDecompressingEntity;
import org.apache.http.client.entity.GzipDecompressingEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sap.cloudlabs.connectivity.proxy.SecurityHandler;
import com.sap.core.connectivity.api.DestinationException;
import com.sap.core.connectivity.api.DestinationFactory;
import com.sap.core.connectivity.api.http.HttpDestination;

/**
 * This servlet is used as connectivity proxy between a consuming agent, like a Web browser
 * application, and a backend service and can be seen as an add-on of the SAP HANA Cloud 
 * connectivity service. The backend service can either be an on-premise application  
 * which is accessed via SAP HANA Cloud connector, or an Internet-accessible service. For 
 * both cases, the servlet tries to access the remote service via a configured destination. 
 * The name of the destination has to be passed by the calling client of this servlet 
 * as part of the URL, following this pattern: 
 * <pre>
 *   /<context-path>/<servlet-path>/<destinationName>/<relative-path-to-backend-service>
 * </pre> 
 * <p>
 * Main purpose of the proxy servlet is to assure the same-origin-policy (SOP)
 * for JavaScript applications running in Web browsers.
 * 
 * @version 0.1
 */

/*
 * In case you want to manage servlet urlPatterns and security constraints 
 * with annotations you can replace web.xml file entries for urlPatterns and security constraints
 * <code><servlet-mapping>
 *			<servlet-name>ConnectivityProxy</servlet-name>
 *			<url-pattern>/proxy/yourDestinationName1/*</url-pattern>
 *			<url-pattern>/proxy/yourDestinationName2/*</url-pattern>
 *		</servlet-mapping>
 * </code>
 * with: 
 * <code>@WebServlet(name="ConnectivityProxy", urlPatterns={"/proxy/yourDestinationName1", "/proxy/yourDestinationName2"})
 * @ServletSecurity(@HttpConstraint(rolesAllowed = {"Administrator"}))
 * </code>
 */

public class ProxyServlet extends HttpServlet {
	private static final String ISO_8859_1 = "ISO-8859-1";

	private static final long serialVersionUID = 1L;

	/* headers which will be blocked from forwarding in backend request */
	private static String[] BLOCKED_REQUEST_HEADERS = { "host", "content-length", "SAP_SESSIONID_DT1_100", "MYSAPSSO2", "JSESSIONID" };
		
	/* buffer size for piping the content */
	private static final int IO_BUFFER_SIZE = 4 * 1024;

	private static final Logger LOGGER = LoggerFactory.getLogger(ProxyServlet.class);
	
	/*
	 * In case you want to manage servlet resources  
	 * with annotations you can replace web.xml  declaration
	 * <code> <resource-ref>
	 *	<res-ref-name>connectivity/DestinationFactory</res-ref-name>
	 *	<res-type>com.sap.core.connectivity.api.DestinationFactory</res-type>
	 *	</resource-ref> 
	 * </code>
	 * file with following annotations
	 * <code>	@Resource com.sap.core.connectivity.api.DestinationFactory destinationFactory;</code>		
	 * Then the destinationFactory declaration is obsolete, 
	 * as well as the lookup in method <code>getDestination(String)</code>
	 */
	private static DestinationFactory destinationFactory;
	
	private SecurityHandler securityHandler;
	
	/*
	 * @see javax.servlet.GenericServlet#init(javax.servlet.ServletConfig)
	 */
	public void init(ServletConfig servletConfig) throws ServletException {
		super.init(servletConfig);
		String securityHandlerName = servletConfig.getInitParameter("security.handler");
		
		if (securityHandlerName != null) {
			try {
				Class<?> clazz = Class.forName(securityHandlerName);
				
				if (SecurityHandler.class.isAssignableFrom(clazz)) {
					securityHandler = (SecurityHandler) clazz.newInstance();
				} else {
					LOGGER.debug("Provided security.handler " + securityHandlerName + " is not an implementation of SecurityHandler class: ");
				}
			// no exception will be thrown as the proxy servlet can work without security handler implementation
			} catch (ClassNotFoundException e) {
				LOGGER.error("Provided security.handler " + securityHandlerName + " cannot be loaded");
	
			} catch (InstantiationException e) {
				LOGGER.error("Provided security.handler " + securityHandlerName + " cannot be instantioated");
			} catch (IllegalAccessException e) {
				LOGGER.error("Provided security.handler " + securityHandlerName + " cannot be accessed");
			}
		}
	}

	protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException,
			IOException {

		LOGGER.debug(">>>>>>>>>>>> start request");
		
		// read destination and relative service path from URL
		String queryString = request.getQueryString();
		String destinationName = getDestinationFromUrl(request.getServletPath());
		String pathInfo = null;
		
		int contextPathLength = request.getContextPath().length();
		int servletPathLength = request.getServletPath().length();
		
		if (request.getRequestURI().endsWith(destinationName)) { 
			pathInfo = "";
		} else {
			pathInfo = request.getRequestURI().substring(servletPathLength + contextPathLength);
			
		}
		String urlToService = getRelativePathFromUrl(pathInfo, queryString);
	
		// get the http client for the destination
		HttpDestination dest = getDestination(destinationName);	
		HttpClient httpClient = null;
		try {
			httpClient = dest.createHttpClient();
		
			// create request to targeted backend service
		HttpRequestBase backendRequest = getBackendRequest(request, urlToService);

		// execute the backend request
		HttpResponse backendResponse = httpClient.execute(backendRequest);

		String rewriteUrl = getDestinationUrl(dest);  
		String proxyUrl = getProxyUrl(request);
					
		// process response from backend request and pipe it to origin response of client
		processBackendResponse(request, response, backendResponse, proxyUrl, rewriteUrl);
		} catch (DestinationException e) {
			throw new ServletException(e);
		} finally {
			if (httpClient != null) { 
				httpClient.getConnectionManager().shutdown();
			}
			
			LOGGER.debug(">>>>>>>>>>>> end request");			
		}		
	}

	
	/**
	 * Returns the URL specified in the given destination. 
	 */
	private String getDestinationUrl(HttpDestination destination) throws ServletException {
		try {
			String rewriteUrl = destination.getURI().toString();
			if (rewriteUrl.endsWith("/")) {
				rewriteUrl = rewriteUrl.substring(0, rewriteUrl.length()-1);
			}
			return rewriteUrl; 
		} catch (URISyntaxException e) {
			throw new ServletException(e);
		}
	}

	/**
	 * Returns the URL to the proxy servlet and used destination. 
	 */
	private String getProxyUrl(HttpServletRequest request) throws MalformedURLException {
 		URL url = new URL(request.getRequestURL().toString());
		String proxyUrl = 
				request.getScheme() + "://" + url.getAuthority() + 
				request.getContextPath() + request.getServletPath();
		return proxyUrl; 
	}
	
	/**
	 * Process response received from backend service and copy it to origin response of 
	 * client.
	 * 
	 * @param request
	 *            origin request of this Web application
	 * @param response
	 *            origin response of this Web application; this is where the
	 *            backend response is copied to
	 * @param backendResponse
	 *            the response of the backend service
	 * @param proxyUrl
	 * 			  the URL that should replace the <code>rewriteUrl</code>
	 * @param rewriteUrl
	 * 			  the URL that should be rewritten 
	 */
	private void processBackendResponse(HttpServletRequest request, HttpServletResponse response, HttpResponse backendResponse, String proxyUrl, String rewriteUrl)
			throws IOException, ServletException {
		// copy response status code
		int status = backendResponse.getStatusLine().getStatusCode();
		response.setStatus(status);
		LOGGER.debug("backend response status code: " + status);

		// filter the headers to suppress the authentication dialog (only for
		// 401 - unauthorized)
		List<String> blockedHeaders = null;
		if (status == HttpServletResponse.SC_UNAUTHORIZED && request.getHeader("authorization") != null
				&& request.getHeader("suppress-www-authenticate") != null) {
			blockedHeaders = Arrays.asList(new String[] { "www-authenticate" });
		} else {
			// for rewriting the URLs in the response, content-length, content-encoding 
			// and transfer-encoding (for chunked content) headers are removed and handled specially.
			blockedHeaders = Arrays.asList(new String[] { "content-length", "transfer-encoding", "content-encoding" });
		}

		// copy backend response headers and content
		LOGGER.debug("backend response headers: ");
		for (Header header : backendResponse.getAllHeaders()) {
			if (!blockedHeaders.contains(header.getName().toLowerCase())) {
				response.addHeader(header.getName(), header.getValue());
				LOGGER.debug("    => " + header.getName() + ": " + header.getValue());
			} else {
				LOGGER.debug("    => " + header.getName() + ": blocked response header");
			}
		}
				
		handleContentEncoding(backendResponse);
		
		// pipe and return the response
		HttpEntity entity = backendResponse.getEntity();
		if (entity != null) {							
			// rewrite URL in the content of the response to make sure that
			// internal URLs point to the proxy servlet as well
				
			// determine charset and content as String
			String charset = EntityUtils.getContentCharSet(entity);
			String content = EntityUtils.toString(entity); 
				
			LOGGER.debug("URL rewriting:"); 
			LOGGER.debug("    => rewriteUrl: " + rewriteUrl);
			LOGGER.debug("    => proxyUrl: " + proxyUrl);
				
			// replace the rewriteUrl with the targetUrl
			content = content.replaceAll(rewriteUrl, proxyUrl); 
			
			// get the bytes and open a stream (by default HttpClient uses ISO-8859-1)
		    byte[] contentBytes = charset != null ? content.getBytes(charset) : content.getBytes(ISO_8859_1);
			InputStream is = new ByteArrayInputStream(contentBytes);
		    
			// set the new content length
		    response.setContentLength(contentBytes.length);
		    
		    // return the modified content
		    pipe(is, response.getOutputStream());
		}
		
	}

	/**
	 * Returns the request that points to the backend service defined by the provided 
	 * <code>urlToService</code> URL. The headers of the origin request are copied to 
	 * the backend request, except of "host" and "content-length".   
	 * 
	 * @param request
	 *            original request to the Web application
	 * @param urlToService
	 *            URL to the targeted backend service
	 * @return initialized backend service request
	 * @throws IOException 
	 */
	private HttpRequestBase getBackendRequest(HttpServletRequest request, String urlToService)
			throws IOException {
		String method = request.getMethod();
		LOGGER.debug("HTTP method: " + method);
		
		HttpRequestBase backendRequest = null;
		if (HttpPost.METHOD_NAME.equals(method)) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			pipe(request.getInputStream(), out); 								
			ByteArrayEntity entity = new ByteArrayEntity(out.toByteArray());			
			entity.setContentType(request.getHeader("Content-Type"));
			HttpPost post = new HttpPost(urlToService);						
			post.setEntity(entity);							
			backendRequest = post;
		} else if (HttpGet.METHOD_NAME.equals(method)) {
			HttpGet get = new HttpGet(urlToService);
			backendRequest = get;
		} else if (HttpPut.METHOD_NAME.equals(method)) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			pipe(request.getInputStream(), out); 								
			ByteArrayEntity entity = new ByteArrayEntity(out.toByteArray());			
			entity.setContentType(request.getHeader("Content-Type"));
			HttpPut put = new HttpPut(urlToService);						
			put.setEntity(entity);							
			backendRequest = put;
		} else if (HttpDelete.METHOD_NAME.equals(method)) {
			HttpDelete delete = new HttpDelete(urlToService);
			backendRequest = delete;
		} 

		// copy headers from Web application request to backend request, while
		// filtering the blocked headers
		
		LOGGER.debug("backend request headers:");

		Collection<String> blockedHeaders = mergeLists(securityHandler, Arrays.asList(BLOCKED_REQUEST_HEADERS));
		
		Enumeration<String> setCookieHeaders = request.getHeaders("Cookie");
		while(setCookieHeaders.hasMoreElements()) {
			String cookieHeader = setCookieHeaders.nextElement();
			if (blockedHeaders.contains(cookieHeader.toLowerCase())) {
				String replacedCookie = removeJSessionID(cookieHeader);
				backendRequest.addHeader("Cookie", replacedCookie);
			}
			LOGGER.debug("Cookie header => " + cookieHeader);
		}
		
		for (Enumeration<String> e = request.getHeaderNames(); e.hasMoreElements();) {
			String headerName = e.nextElement().toString();
			if (!blockedHeaders.contains(headerName.toLowerCase())) {
				backendRequest.addHeader(headerName, request.getHeader(headerName));
				LOGGER.debug("    => " + headerName + ": " + request.getHeader(headerName));
			} else {
				LOGGER.debug("    => " + headerName + ": blocked request header");
			}
		}

		return backendRequest;
	}
	
	private String removeJSessionID(String cookieHeader) {
		int beginIndex = cookieHeader.indexOf("JSESSIONID");
		int endIndex = cookieHeader.indexOf(";", beginIndex+ 12);
		String jSeesionSubstring = cookieHeader.substring(beginIndex, endIndex);
		String result  = cookieHeader.replace(jSeesionSubstring +";", "");
		return result;
	}
	
	private Collection<String> mergeLists(SecurityHandler securityHandler, List<String> blockedHeaders) {
		Set<String> mergedHeadersList = new HashSet<String>();
		mergedHeadersList.addAll(blockedHeaders);
		if (securityHandler != null) {
			List<String> applicationBlackList = securityHandler.getResponseHeadersBlackList();
			mergedHeadersList.addAll(applicationBlackList);
		}
		
		return mergedHeadersList;
	}

	/**
	 * Returns an initialized HttpClient which points to the specified destination. 	 
	 */
	private HttpDestination getDestination(String destinationName) throws ServletException {
		try {
			/*
			 * In case the an annotation @Resource is used, the following if block is obsolete
			 */
			if (destinationFactory == null) {			
				Context ctx = new InitialContext();
				destinationFactory = (DestinationFactory) ctx.lookup(DestinationFactory.JNDI_NAME);
			}
			HttpDestination dest = (HttpDestination) destinationFactory.getDestination(destinationName);
			return dest; 
		} catch (Exception e) {
			throw new ServletException(writeMessage("Unable to resolve destination " + destinationName), e);
		}
	}

	/**
	 * Returns the destination name defined in the specified URL path. 
	 * It is assumed that the specified path consists of following parts:  
	 * <pre>
	 *  <destinationName>/relativePathToService
	 * </pre>
	 */
	  private String getDestinationFromUrl(String servletPath) throws ServletException {
	        String destinationName = null;
	        int index = servletPath.lastIndexOf("/");
	        if (index != -1) {
	        	destinationName = servletPath.substring(index + 1, servletPath.length());
	        }
	        if (destinationName == null) {
	        	throw new ServletException(writeMessage("No destination specified"));
	        }
	        LOGGER.debug("destination read from URL path: " + destinationName);
	        return destinationName;
	  }
	
	  
	/**
	 * Returns the relative path to the backend service. It assumes that the specified path is
	 * <pre>
	 *  <destinationName>/relativePathToService
	 * </pre> 
	 * and it returns relativePathToService?queryString.
	 */
	private String getRelativePathFromUrl(String path, String queryString) {
		// strip off first label in the path, as it specifies the destination name
		int index = path.indexOf("/");
		String relativePathToService = index != -1 ? path.substring(index + 1) : "";

		// replace spaces with %20 in the path
		relativePathToService = relativePathToService.replace(" ", "%20");

		if (queryString != null && !queryString.isEmpty()) {
			relativePathToService += "?" + queryString;
		}

		LOGGER.debug("relative path to service, incl. query string: " + relativePathToService);
		return relativePathToService;
	}

	private void handleContentEncoding(HttpResponse response) throws ServletException {
		HttpEntity entity = response.getEntity();
		if (entity != null) {
			Header contentEncodingHeader = entity.getContentEncoding();
			if (contentEncodingHeader != null) {
				HeaderElement[] codecs = contentEncodingHeader.getElements();
				LOGGER.debug("Content-Encoding in response:");
				for (HeaderElement codec : codecs) {
					String codecname = codec.getName().toLowerCase();
					LOGGER.debug("    => codec: " + codecname);
					if ("gzip".equals(codecname) || "x-gzip".equals(codecname)) {
						response.setEntity(new GzipDecompressingEntity(response.getEntity()));
						return;
					} else if ("deflate".equals(codecname)) {
						response.setEntity(new DeflateDecompressingEntity(response.getEntity()));
						return;
					} else if ("identity".equals(codecname)) {
						return;
					} else {
						throw new ServletException("Unsupported Content-Encoding: " + codecname);
					}
				}
			}
		}
	}		
	
	/**
	 * Pipes a given <code>InputStream</code> into the given
	 * <code>OutputStream</code>
	 * 
	 * @param in
	 *            <code>InputStream</code>
	 * @param out
	 *            <code>OutputStream</code>
	 * @throws IOException
	 */
	private static void pipe(InputStream in, OutputStream out) throws IOException {
		byte[] b = new byte[IO_BUFFER_SIZE];
		int read;
		while ((read = in.read(b)) != -1) {
			out.write(b, 0, read);
		}
		in.close();
		out.flush();
		out.close();
	}
	

	private String writeMessage(String message) {
		StringBuilder b = new StringBuilder();
		b.append("\nInvalid usage: ").append(message);
		b.append("\n");
		b.append("\nUsage of proxy servlet:");
		b.append("\n=======================");
		b.append("\nIt is assumed that the URL to the servlet follows the pattern ");
		b.append("\n==> /<context-path>/proxy/<destination-name>/<relative-path-below-destination-target>");
		b.append("\n");
		return b.toString();
	}
}