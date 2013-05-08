Connectivity Proxy
===================

The Connectivity Proxy component is based on the SAP HANA Cloud connectivity service and 
allows to proxy requests from JavaScript applications, which are obliged to the Same-Origin-Policy,  
to backend services. As it uses the SAP HANA Cloud connectivity service, it is also possible to make 
calls to on-premise backend systems using the SAP HANA Cloud Connector. See also 
https://help.hana.ondemand.com/help/frameset.htm?e54cc8fbbb571014beb5caaf6aa31280.html for more 
details on the SAP HANA Cloud connectivity service.

Quick Start
-----------

Clone the repository 'https://sap.github.io/sap/cloud-connectivityproxy.git' or [download the latest release](https://sap.github.io/sap/cloud-connectivityproxy/zipball/master). 

To build the project locally using Maven, you need to adopt the pom.xml file of the component as following: 
- Define the <nw.cloud.sdk.version> variable: it specifies the version of your locally used SAP HANA Cloud SDK; if you are not sure about the concrete version of your SDK, open the sdk.version file the root directory of the SDK and copy the value of the release.version property.
- Define the <nw.cloud.sdk.path> variable: the variable defines the path to the root directory of your local SAP HANA Cloud SDK, e.g. C:\sdk-1.25.4.

Afterwards, you should be able to build the component using "mvn clean install". 


Project Overview
----------------

The component consists of a single servlet com.sap.cloudlabs.connectivity.proxy.ProxyServlet. 
The servlet takes HTTP(S) GWT/PUT/POST/DELETE requests and forwards them to a backend system specified by a configured destination. 
The concrete destination is defined in the URL path following the subsequent pattern: 

  /<context-path>/<servlet-path>/<destination>/<relative-path-below-destination>


Component Usage
---------------

Option A: Deploy Connectivity Proxy as WAR file

To use the Connectivity Proxy, you need to 
1) Download the Connectivity Proxy from github and build it in your local environment. The github project is 
   prepared for Maven builds, and a "mvn install" generates a WAR file containing the proxy servlet in the projects "/target" folder. 
   The servlet-path for the proxy servlet specified in the project is “/proxy”. If you like to change this path, go into the 
   “/src/main/webapp/WEB-INF” folder of the project and change the url-pattern for the “ConnectivityProxy”. 
2) Deploy the WAR file containing the Connectivity Proxy in the SAP HANA Cloud application where you want to use it. 
   See https://help.hana.ondemand.com/help/frameset.htm?030863cd5d0d4dd3b742957970f8eec9.html for more details on how to 
   deploy multiple WAR files for a single application. After deployment, you are able to call the proxy servlet 
   via “/<application-name>/proxy”.
  
Option B: Copy the ProxyServlet into your Web application project
  
Alternatively, you can also copy the com.sap.cloudlabs.connectivity.proxy.ProxyServlet.java file directly into the sources fo your Web 
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
	<servlet-mapping>
		<servlet-name>ConnectivityProxy</servlet-name>
		<url-pattern>/proxy/*</url-pattern>
	</servlet-mapping>

	<!-- ============================================================== -->
	<!-- JNDI resource definition of DestinationFactory -->
	<!-- ============================================================== -->

	<resource-ref>
		<res-ref-name>connectivity/DestinationFactory</res-ref-name>
		<res-type>com.sap.core.connectivity.api.DestinationFactory</res-type>
	</resource-ref>
  

Versioning 
----------

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

+ http://twitter.com/tlakner
+ http://github.com/tlakner


Copyright and license
---------------------

Copyright 2012 SAP AG

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
