/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.core;

import java.io.*;

import javax.servlet.http.HttpServletRequest;

/**
 * Various static helper methods for I/O processing.
 */
public class IOUtilities {
	public static void pipe(InputStream inputStream, OutputStream outputStream) throws IOException {
		pipe(inputStream, outputStream, false, false);
	}

	public static void pipe(InputStream inputStream, OutputStream outputStream, boolean closeIn, boolean closeOut) throws IOException {
		byte[] buffer = new byte[4096];
		int read = 0;
		try {
			while ((read = inputStream.read(buffer)) != -1)
				outputStream.write(buffer, 0, read);
		} finally {
			if (closeIn)
				safeClose(inputStream);
			if (closeOut)
				safeClose(outputStream);
		}
	}

	public static void pipe(Reader input, Writer output) throws IOException {
		pipe(input, output, false, false);
	}

	public static void pipe(Reader reader, Writer writer, boolean closeReader, boolean closeWriter) throws IOException {
		try {
			char[] buffer = new char[4096];
			int read = 0;
			while ((read = reader.read(buffer)) != -1)
				writer.write(buffer, 0, read);
		} finally {
			if (closeReader)
				safeClose(reader);
			if (closeWriter)
				safeClose(writer);
		}
	}

	/**
	 * Returns the value of a request parameter as a String, or null if 
	 * the parameter does not exist in the query string.
	 * 
	 * Method is similar to HttpServletRequest.getParameter(String name) method, but it does not
	 * interfere with HttpServletRequest.getInputStream() and HttpServletRequest.getReader().  
	 * @param request a request object
	 * @param name a String specifying the name of the parameter
	 * @return a String representing the single value of the parameter
	 */
	public static String getQueryParameter(HttpServletRequest request, String name) {
		String queryString = request.getQueryString();
		if (queryString == null)
			return null;
		
		for (String paramString : queryString.split("&")) { 
			if (paramString.startsWith(name)) {
				String[] nameAndValue = paramString.split("=", 2);
				if (nameAndValue.length == 2)
					return nameAndValue[1];
				else
					return ""; // parameter has no value
			}
		}
		// parameter not found
		return null;
	}
	
	/**
	 * Closes a stream or reader and ignores any resulting exception. This is useful
	 * when doing cleanup in a finally block where secondary exceptions
	 * are not worth logging.
	 */
	public static void safeClose(Closeable closeable) {
		try {
			if (closeable != null)
				closeable.close();
		} catch (IOException e) {
			//ignore
		}
	}

	public static String toString(InputStream is) throws IOException {
		if (is == null)
			return ""; //$NON-NLS-1$
		StringWriter writer = new StringWriter();
		pipe(new InputStreamReader(is, "UTF-8"), writer, true, false); //$NON-NLS-1$
		return writer.toString();
	}
}
