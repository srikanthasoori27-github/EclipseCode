
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

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
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

public class rrrr implements Filter {

	

	private FilterConfig filterConfig = null;

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		this.filterConfig = filterConfig;
		
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {
		System.out.println("--- ResponseFilter - doFilter : start ---");

		HttpServletRequest httpRequest = (HttpServletRequest) request;
		HttpServletResponse httpResponse = (HttpServletResponse) response;

		String itemName = this.filterConfig.getInitParameter("itemName");
		List attributesToHide=new ArrayList();
		try {
			 attributesToHide = getAttributesListToHide();
			 System.out.println("The attributes list is "+attributesToHide);
		} catch (GeneralException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("itemName: " + itemName);

		CharResponseWrapper wrapper = new CharResponseWrapper((HttpServletResponse) response);
		filterChain.doFilter(request, wrapper);

		String newResponse = convertResponse(wrapper, attributesToHide);
		response.setContentLength(newResponse.length());
		PrintWriter responseWriter = response.getWriter();
		responseWriter.write(newResponse);

		System.out.println("--- ResponseFilter - doFilter : end ---");
	}

	private static String convertResponse(CharResponseWrapper wrapper, List<String> attributesToHide) throws ServletException {
		System.out.println("--- convertResponse start ---");
		System.out.println("Attributes to Hide list "+attributesToHide);
		boolean changed = false;
		String newResponse = wrapper.toString();
		
		System.out.println("New response is "+newResponse);
		
		try {
			Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(wrapper.toString())));
			XPath xpath = XPathFactory.newInstance().newXPath();
			List<Node>hidingList= new ArrayList();
			NodeList trs = (NodeList) xpath.evaluate("/html/body/table/tr", doc, XPathConstants.NODESET);
			if (trs != null) {
				// System.out.println("trs size: " + trs.getLength());

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
									System.out.println("content: " + content);
									
									for(String temp : attributesToHide)
									{
									if (StringUtils.contains(content, temp)) {
										System.out.println("Content found "+content);
										hidingList.add(tr);
										
									}
									}
								}
							}
						}
						if (hidingList!=null && hidingList.size()>0) {
							System.out.println("content found");
							for(Node obj : hidingList) {
							//tr.getParentNode().removeChild(obj);
							}
							changed = true;
						}
					}
				}
			}

			if (changed) {
				newResponse = getConvertedHtml(doc);
			}
		} catch (XPathExpressionException | DOMException | SAXException | IOException | ParserConfigurationException | TransformerFactoryConfigurationError | TransformerException e) {
			System.out.println("Error happened in ResponseFilter: " + e);
			throw new ServletException(e);
		}
		System.out.println("--- convertResponse end ---");
		return newResponse;
	}

	private static String getConvertedHtml(Document doc) throws TransformerFactoryConfigurationError, TransformerConfigurationException, TransformerException {
		System.out.println("--- getConvertedHtml start ---");

		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer transformer = tf.newTransformer();
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty(OutputKeys.METHOD, "html");

		StringWriter writer = new StringWriter();
		transformer.transform(new DOMSource(doc), new StreamResult(writer));
		String result = writer.getBuffer().toString();
		System.out.println("convert result html: " + result);

		System.out.println("--- getConvertedHtml end ---");
		return result;
	}

	@Override
	public void destroy() {
		this.filterConfig = null;
	}
	
	
	
	private static List getAttributesListToHide() throws GeneralException{
		System.out.println("--- getAttributesListToHide start ---");
		List attributesToHide = new ArrayList();
		try {
			SailPointContext context = SailPointFactory.getCurrentContext();
			Custom custom = context.getObjectByName(Custom.class,"Govtech-Common-Settings");
			System.out.println("custom == null? " + (custom == null));
			if (custom != null) {
				String attrs = (String) custom.get("APPLICATION_ATTRIBUTES_TO_HIDE_COMMA_SEPERATED");
				if(Util.isNotNullOrEmpty(attrs))
					attributesToHide=Util.csvToList(attrs);	
			}
		} catch (GeneralException e) {
			System.out.println("Error happened in trySailPointContextAccess: " + e);
			throw new GeneralException(e);
		}
		System.out.println("--- attributesToHide  ---"+ attributesToHide);
		System.out.println("--- getAttributesListToHide end ---");
		return attributesToHide;
	}
	
	
	public static String tempCode(String newResponse, List<String> attributesToHide) throws ServletException {
		System.out.println("--- convertResponse start ---");
		System.out.println("Attributes to Hide list "+attributesToHide);
		boolean changed = false;
		//String newResponse = wrapper.toString();
		
		System.out.println("New response is "+newResponse);
		
		try {
			Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(newResponse)));
			XPath xpath = XPathFactory.newInstance().newXPath();
			List<Node>hidingList= new ArrayList();
			NodeList trs = (NodeList) xpath.evaluate("/html/body/table/tr", doc, XPathConstants.NODESET);
			if (trs != null) {
				// System.out.println("trs size: " + trs.getLength());

				for (int i = 0; i < trs.getLength(); i++) {
					Element tr = (Element) trs.item(i);
					if (tr != null) {
						boolean needToDelete = false;
						NodeList tds = (NodeList) xpath.evaluate("td", tr, XPathConstants.NODESET);
						if (tds != null) {
							for (int j = 0; j < tds.getLength(); j++) {
								Element td = (Element) tds.item(j);
								if (td != null) {
									String content = td.getTextContent().trim();
									System.out.println("content: " + content);
									
									
									if (attributesToHide.contains(content)) {
										System.out.println("Content found "+content);
										System.out.println("deleted tr is "+tr);
										tr.getParentNode().removeChild(tr);
										changed = true;
										
									
									}
								}
							}
						}
						
					}
				}
			}

			if (changed) {
				newResponse = getConvertedHtml(doc);
			}
		} catch (XPathExpressionException | DOMException | SAXException | IOException | ParserConfigurationException | TransformerFactoryConfigurationError | TransformerException e) {
			System.out.println("Error happened in ResponseFilter: " + e);
			throw new ServletException(e);
		}
		System.out.println("--- convertResponse end ---");
		return newResponse;
	}
	
	
}
