![](https://img.shields.io/badge/STATUS-NOT%20CURRENTLY%20MAINTAINED-red.svg?longCache=true&style=flat)

# Important Notice
This public repository is read-only and no longer maintained.

Connectivity Proxy
===================

The Connectivity Proxy component is based on the SAP HANA Cloud connectivity service and 
allows to proxy requests from JavaScript applications, which are obliged to the Same-Origin-Policy,  
to backend services. As it uses the SAP HANA Cloud connectivity service, it is also possible to make 
calls to on-premise backend systems using the SAP HANA Cloud Connector. See also 
https://help.hana.ondemand.com/help/frameset.htm?e54cc8fbbb571014beb5caaf6aa31280.html for more 
details on the SAP HANA Cloud connectivity service.


Quick Start
===========

1. Clone the repository 'https://sap.github.io/sap/cloud-connectivityproxy.git' or [download the latest release](https://sap.github.io/sap/cloud-connectivityproxy/zipball/master). 
2. Import the project as existing Maven project into your local Eclipse environment which has been setup for HCP usage
3. Build the project in Eclipse by running a "mvn clean install". The build should pass successfully.   


Project Overview
================

The component consists of a single servlet com.sap.cloudlabs.connectivity.proxy.ProxyServlet. 
The servlet takes HTTP(S) GET/PUT/POST/DELETE requests and forwards them to a remote system specified by 
a configured destination. The concrete destination is defined in the URL path following the subsequent pattern: 

`\context-path\servlet-path\destination\relative-path-appended-to-the-destination`


How to use the connectivity proxy 
=================================

Option 1: Deploy Connectivity Proxy as WAR file
-----------------------------------------------

To use the Connectivity Proxy, you need to 

1. The github project is prepared for Web applications, i.e. a WAR file is generated by default when running a "mvn install". 
   The servlet-path of the proxy servlet is "/proxy/<yourDestinationName>". 

2. Deploy the WAR file in the SAP HANA Cloud application where you want to use it. 
   See https://help.hana.ondemand.com/help/frameset.htm?030863cd5d0d4dd3b742957970f8eec9.html for more details on how to 
   deploy multiple WAR files for a single HCP application. After deployment, you are able to call the proxy servlet 
   via `/application-name/proxy/<yourDestinationName>`.
  
Option B: Copy the ProxyServlet into your Web application project
-----------------------------------------------------------------
  
Alternatively, you can also copy the com.sap.cloudlabs.connectivity.proxy.ProxyServlet.java file directly into the sources of your Web 
application. In this case, you also need to modify the web.xml of your Web application to define the Connectivity Proxy servlet, as well as 
define the DestinationFactory as a JNDI resource: 

    <!-- ============================================================== -->
	<!-- Connectivity Proxy servlet  -->
	<!-- ============================================================== -->

	<servlet>
		<display-name>ConnectivityProxy</display-name>
		<servlet-name>ConnectivityProxy</servlet-name>
		<servlet-class>com.sap.cloudlabs.connectivity.proxy.ProxyServlet</servlet-class>
	</servlet>
	
	<!-- ============================================================== -->
	<!-- Replace yourDestinationName1 with real destination name  -->
	<!-- Add additional <url-pattern> for more destinations  -->
	<!-- ============================================================== -->
	
	<servlet-mapping>
		<servlet-name>ConnectivityProxy</servlet-name>
		<url-pattern>/proxy/<yourDestinationName1>/*</url-pattern>
		<url-pattern>/proxy/<yourDestinationName2>/*</url-pattern>
	</servlet-mapping>
		

	<!-- ============================================================== -->
	<!-- JNDI resource definition of DestinationFactory -->
	<!-- ============================================================== -->

	<resource-ref>
		<res-ref-name>connectivity/DestinationFactory</res-ref-name>
		<res-type>com.sap.core.connectivity.api.DestinationFactory</res-type>
	</resource-ref>


Security notes
==============

1. **Restricting with user roles**
Destination access can be further restricted with roles. You can do this with adding user/roles for your servlet. 
An example is added as commented code in web.xml.


        <security-constraint>
	        <web-resource-collection>
                <web-resource-name>Access to yourDestinationName1</web-resource-name>
    	    	<url-pattern>/proxy/yourDestinationName1/*</url-pattern>
    		</web-resource-collection>
	    	<auth-constraint>
		       	<role-name>Administrator</role-name>
    		</auth-constraint>
        </security-constraint>
Replace Administrator with the role you have. The role should be assigned to the user who wants to access the application. This can be 
done in HCP Cloud Cockpit. For more information: https://help.hana.ondemand.com/help/frameset.htm?db8175b9d976101484e6fa303b108acd.html. 

2. **Blacklisting of Headers.**
Not all response headers from the remote system should be forwarded to the JavaScript client. Therefore we have a static list of headers 
which will be not forwarded: "host", "content-length", "SAP_SESSIONID_DT1_100", "MYSAPSSO2", "JSESSIONID".
If the user of the proxy servlet wants to add additional headers she/he should add an implementation of abstract class SecurityHandler.
And declare its name as servlet init-param, like shown in following example:

        <init-param>
	        <param-name>security.handler</param-name>
	        <param-value>com.sap.cloudlabs.connectivity.proxy.MySecurityHandler</param-value>
        </init-param>

Users should take in mind that destination end-points shall be trusted by the application and by the application end-users 
(ProxyServlet can get access to file system, credentials, sensitive cookies, execute HTTP requests on behalf of the user, etc.). 


Versioning 
==========

For transparency and insight into our release cycle, and for striving to maintain backward compatibility, the Connectivity Proxy 
project will be maintained under the Semantic Versioning guidelines as much as possible, see http://semver.org/.

Releases will be numbered with the following format:

`<major>.<minor>.<patch>`

And constructed with the following guidelines:

* Breaking backward compatibility bumps the major (and resets the minor and patch)
* New additions without breaking backward compatibility bumps the minor (and resets the patch)
* Bug fixes and misc changes bumps the patch

 
Authors
-------

**Timo Lakner**

**Nace Sapundziev**

**Rositza Andreeva**

+ http://twitter.com/tlakner
+ http://github.com/tlakner
+ http://github.com/sapundziev
+ http://twitter.com/sapunce


Copyright and license
---------------------

Copyright 2013 SAP AG

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this work except in compliance with the License.
You may obtain a copy of the License in the LICENSE file, or at:

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

Find the project description in Connectivity_Proxy_Documentation.pdf.
