package sp.poc.filter;

import java.io.CharArrayWriter;
import java.io.PrintWriter;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

public class CharResponseWrapper extends HttpServletResponseWrapper {
	private CharArrayWriter writer = null;

	public CharResponseWrapper(HttpServletResponse response) {
		super(response);
		this.writer = new CharArrayWriter();
	}

	public PrintWriter getWriter() {
		return new PrintWriter(this.writer);
	}

	public String toString() {
		return this.writer.toString();
	}

}