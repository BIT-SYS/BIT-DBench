/**
* OWASP Benchmark Project v1.3alpha
*
* This file is part of the Open Web Application Security Project (OWASP)
* Benchmark Project. For details, please see
* <a href="https://www.owasp.org/index.php/Benchmark">https://www.owasp.org/index.php/Benchmark</a>.
*
* The OWASP Benchmark is free software: you can redistribute it and/or modify it under the terms
* of the GNU General Public License as published by the Free Software Foundation, version 2.
*
* The OWASP Benchmark is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
* even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* @author Nick Sanidas <a href="https://www.aspectsecurity.com">Aspect Security</a>
* @created 2015
*/

package org.owasp.benchmark.testcode;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet(value="/xss-03/BenchmarkTest02048")
public class BenchmarkTest02048 extends HttpServlet {
	
	private static final long serialVersionUID = 1L;
	
	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doPost(request, response);
	}

	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("text/html;charset=UTF-8");

		String param = "";
		java.util.Enumeration<String> headers = request.getHeaders("Referer");
		
		if (headers != null && headers.hasMoreElements()) {
			param = headers.nextElement(); // just grab first element
		}
		
		// URL Decode the header value since req.getHeaders() doesn't. Unlike req.getParameters().
		param = java.net.URLDecoder.decode(param, "UTF-8");

		String bar = doSomething(param);
		
response.setHeader("X-XSS-Protection", "0");
		Object[] obj = { "a", bar};
		response.getWriter().printf(java.util.Locale.US,"Formatted like: %1$s and %2$s.",obj);
	}  // end doPost
	
		
	private static String doSomething(String param) throws ServletException, IOException {

		// Chain a bunch of propagators in sequence
		String a96053 = param; //assign
		StringBuilder b96053 = new StringBuilder(a96053);  // stick in stringbuilder
		b96053.append(" SafeStuff"); // append some safe content
		b96053.replace(b96053.length()-"Chars".length(),b96053.length(),"Chars"); //replace some of the end content
		java.util.HashMap<String,Object> map96053 = new java.util.HashMap<String,Object>();
		map96053.put("key96053", b96053.toString()); // put in a collection
		String c96053 = (String)map96053.get("key96053"); // get it back out
		String d96053 = c96053.substring(0,c96053.length()-1); // extract most of it
		String e96053 = new String( new sun.misc.BASE64Decoder().decodeBuffer( 
		    new sun.misc.BASE64Encoder().encode( d96053.getBytes() ) )); // B64 encode and decode it
		String f96053 = e96053.split(" ")[0]; // split it on a space
		org.owasp.benchmark.helpers.ThingInterface thing = org.owasp.benchmark.helpers.ThingFactory.createThing();
		String g96053 = "barbarians_at_the_gate";  // This is static so this whole flow is 'safe'
		String bar = thing.doSomething(g96053); // reflection
	
		return bar;	
	}
}
