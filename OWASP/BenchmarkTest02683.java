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

@WebServlet(value="/xss-05/BenchmarkTest02683")
public class BenchmarkTest02683 extends HttpServlet {
	
	private static final long serialVersionUID = 1L;
	
	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doPost(request, response);
	}

	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("text/html;charset=UTF-8");

		org.owasp.benchmark.helpers.SeparateClassRequest scr = new org.owasp.benchmark.helpers.SeparateClassRequest( request );
		String param = scr.getTheValue("BenchmarkTest02683");

		String bar = doSomething(param);
		
response.setHeader("X-XSS-Protection", "0");
		response.getWriter().print(bar);
	}  // end doPost
	
		
	private static String doSomething(String param) throws ServletException, IOException {

		// Chain a bunch of propagators in sequence
		String a47309 = param; //assign
		StringBuilder b47309 = new StringBuilder(a47309);  // stick in stringbuilder
		b47309.append(" SafeStuff"); // append some safe content
		b47309.replace(b47309.length()-"Chars".length(),b47309.length(),"Chars"); //replace some of the end content
		java.util.HashMap<String,Object> map47309 = new java.util.HashMap<String,Object>();
		map47309.put("key47309", b47309.toString()); // put in a collection
		String c47309 = (String)map47309.get("key47309"); // get it back out
		String d47309 = c47309.substring(0,c47309.length()-1); // extract most of it
		String e47309 = new String( new sun.misc.BASE64Decoder().decodeBuffer( 
		    new sun.misc.BASE64Encoder().encode( d47309.getBytes() ) )); // B64 encode and decode it
		String f47309 = e47309.split(" ")[0]; // split it on a space
		org.owasp.benchmark.helpers.ThingInterface thing = org.owasp.benchmark.helpers.ThingFactory.createThing();
		String g47309 = "barbarians_at_the_gate";  // This is static so this whole flow is 'safe'
		String bar = thing.doSomething(g47309); // reflection
	
		return bar;	
	}
}
