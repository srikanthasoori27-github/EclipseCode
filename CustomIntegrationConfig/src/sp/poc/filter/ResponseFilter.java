
package sp.poc.filter;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Custom;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/*
   <filter>
        <filter-name>ResponseFilter</filter-name>
        <filter-class>sp.poc.filter.ResponseFilter</filter-class>
        <init-param>
          <param-name>itemName</param-name>
          <param-value>email</param-value>
        </init-param>
    </filter>
    
    <filter-mapping>
       <filter-name>ResponseFilter</filter-name>
       <url-pattern>/identity/linkDetails.jsf*</url-pattern>
    </filter-mapping>
*/

public class ResponseFilter implements Filter {

	public static Logger logger = Logger.getLogger(ResponseFilter.class);

	private FilterConfig filterConfig = null;
	private SailPointContext context = null;

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		this.filterConfig = filterConfig;
		this.context= context;
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {
		logger.debug("--- ResponseFilter - doFilter : start ---");

		HttpServletRequest httpRequest = (HttpServletRequest) request;
		HttpServletResponse httpResponse = (HttpServletResponse) response;

		String itemName = this.filterConfig.getInitParameter("itemName");
		List attributesToHide=new ArrayList();
		try {
			 attributesToHide = getAttributesListToHide();
		} catch (GeneralException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		logger.debug("itemName: " + itemName);

		CharResponseWrapper wrapper = new CharResponseWrapper((HttpServletResponse) response);
		filterChain.doFilter(request, wrapper);

		String newResponse = convertResponse(wrapper, attributesToHide);
		response.setContentLength(newResponse.length());
		PrintWriter responseWriter = response.getWriter();
		responseWriter.write(newResponse);

		logger.debug("--- ResponseFilter - doFilter : end ---");
	}

	private static String convertResponse(CharResponseWrapper wrapper, List attributesToHide) throws ServletException {
		logger.debug("--- convertResponse start ---");
		logger.debug("Attributes to Hide list "+attributesToHide);
		boolean changed = false;
		String newResponse = wrapper.toString();
		
		try {
			Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(wrapper.toString())));
			XPath xpath = XPathFactory.newInstance().newXPath();

			NodeList trs = (NodeList) xpath.evaluate("/html/body/table/tr", doc, XPathConstants.NODESET);
			if (trs != null) {
				// logger.debug("trs size: " + trs.getLength());

				for (int i = 0; i < trs.getLength(); i++) {
					Element tr = (Element) trs.item(i);
					if (tr != null) {
						boolean needToDelete = false;
						NodeList tds = (NodeList) xpath.evaluate("td", tr, XPathConstants.NODESET);
						if (tds != null) {
							for (int j = 0; j < tds.getLength(); j++) {
								Element td = (Element) tds.item(j);
								if (td != null) {
									String content = td.getTextContent();
									logger.debug("content: " + content);
						
									if (attributesToHide.contains(content)) {
										logger.debug("Content found "+content);
										needToDelete = true;
										break;
									}
								}
							}
						}
						if (needToDelete) {
							logger.debug("content found");
							tr.getParentNode().removeChild(tr);
							changed = true;
						}
					}
				}
			}

			if (changed) {
				newResponse = getConvertedHtml(doc);
			}
		} catch (XPathExpressionException | DOMException | SAXException | IOException | ParserConfigurationException | TransformerFactoryConfigurationError | TransformerException e) {
			logger.error("Error happened in ResponseFilter: " + e);
			throw new ServletException(e);
		}
		logger.debug("--- convertResponse end ---");
		return newResponse;
	}

	private static String getConvertedHtml(Document doc) throws TransformerFactoryConfigurationError, TransformerConfigurationException, TransformerException {
		logger.debug("--- getConvertedHtml start ---");

		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer transformer = tf.newTransformer();
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty(OutputKeys.METHOD, "html");

		StringWriter writer = new StringWriter();
		transformer.transform(new DOMSource(doc), new StreamResult(writer));
		String result = writer.getBuffer().toString();
		logger.debug("convert result html: " + result);

		logger.debug("--- getConvertedHtml end ---");
		return result;
	}

	@Override
	public void destroy() {
		this.filterConfig = null;
	}
	
	private List getAttributesListToHide() throws GeneralException
	{
		List attributesToHide = new ArrayList();
		if(context!=null)
		{
		Custom customObj = context.getObjectByName(Custom.class,"Govtech-Common-Settings");
		if(customObj!=null)
		{
		String attrs = (String) customObj.get("APPLICATION_ATTRIBUTES_TO_HIDE_COMMA_SEPERATED");
		if(Util.isNotNullOrEmpty(attrs))
		
			attributesToHide=Util.csvToList(attrs);
		
		}
		}
		return attributesToHide;
		
	}
	
}
