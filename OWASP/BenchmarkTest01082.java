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
* @author Dave Wichers <a href="https://www.aspectsecurity.com">Aspect Security</a>
* @created 2015
*/

package org.owasp.benchmark.testcode;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet(value="/trustbound-00/BenchmarkTest01082")
public class BenchmarkTest01082 extends HttpServlet {
	
	private static final long serialVersionUID = 1L;
	
	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doPost(request, response);
	}

	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("text/html;charset=UTF-8");
	
		String param = "";
		if (request.getHeader("BenchmarkTest01082") != null) {
			param = request.getHeader("BenchmarkTest01082");
		}
		
		// URL Decode the header value since req.getHeader() doesn't. Unlike req.getParameter().
		param = java.net.URLDecoder.decode(param, "UTF-8");

		String bar = new Test().doSomething(param);
		
		// javax.servlet.http.HttpSession.setAttribute(java.lang.String^,java.lang.Object)
		request.getSession().setAttribute( bar, "10340");
				
		response.getWriter().println(
"Item: '" + org.owasp.benchmark.helpers.Utils.encodeForHTML(bar)
			+ "' with value: '10340' saved in session."
);
	}  // end doPost

	
    private class Test {

        public String doSomething(String param) throws ServletException, IOException {

		String bar = param;
		if (param != null && param.length() > 1) {
		    bar = param.substring(0,param.length()-1);
		}

            return bar;
        }
    } // end innerclass Test

} // end DataflowThruInnerClass
