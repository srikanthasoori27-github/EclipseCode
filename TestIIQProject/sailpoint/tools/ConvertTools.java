/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

//
// jsl - removed HttpServletRequest stuff so we don't have
// a J2EE dependency on the browserServerManager core.  May
// not be a big deal.  Moved them to sailpoint.common.tools.ServletTools
// over in adminApplication.
//

package sailpoint.tools;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Iterator;
import java.util.Properties;

// jsl - removing these temporarily so we don't introduce a
// dependency on J2EE for the server code
//import javax.servlet.http.HttpServletRequest;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;


public class ConvertTools
{
	// ========================================
	//
	// methods: primitive types
	//
	// ========================================

	public static String convert(String s, String d)
	{
		if (s == null)
		{
			return d;
		}
		return s;
	}

	// ========================================

	public static long convert(String s, long d)
	{
		long r = d;
		try
		{
			r = Long.parseLong(s);
		}
		catch (Exception ex)
		{
		}
		return r;
	}

	// ========================================

	public static int convert(String s, int d)
	{
		int r = d;
		try
		{
			r = Integer.parseInt(s);
		}
		catch (Exception ex)
		{
		}
		return r;
	}

	// ========================================

	public static boolean convert(String s, boolean d)
	{
		boolean r = d;
		try
		{
			r = Boolean.parseBoolean(s);
		}
		catch (Exception ex)
		{
		}
		return r;
	}

	// ========================================

	public static float convert(String s, float d)
	{
		float r = d;
		try
		{
			r = Float.parseFloat(s);
		}
		catch (Exception ex)
		{
		}
		return r;
	}

	// ========================================

	public static double convert(String s, double d)
	{
		double r = d;
		try
		{
			r = Double.parseDouble(s);
		}
		catch (Exception ex)
		{
		}
		return r;
	}
    
    // ========================================

    /**
     * Convert an Object into a given class.
     */
    public static Object convert(Object o, Class clazz)
    {
        Object r = o;
        try
        {
            if (o instanceof String)
            {
                String s = (String) o;
                if ((clazz == Integer.class) || (clazz == Integer.TYPE))
                    r = Integer.parseInt(s);
                else if ((clazz == Long.class) || (clazz == Long.TYPE))
                    r = Long.parseLong(s);
                else if ((clazz == Boolean.class) || (clazz == Boolean.TYPE))
                    r = Boolean.valueOf(s);
                else if ((clazz == Float.class) || (clazz == Float.TYPE))
                    r = Float.parseFloat(s);
                else if ((clazz == Double.class) || (clazz == Double.TYPE))
                    r = Double.valueOf(s);
            }
            // TODO: More non-string conversions.
        }
        catch (Exception ex)
        {
        }
        return r;
    }

    // ========================================
    //
    // methods: ordinals
    //
    // ========================================

    /**
     * Returns the ordinal form of the integer that was passed in.
     * If no integer can be parsed from the String, this method returns
     * null
     * @param s The String that is being converted
     */ 
    public static String convertToOrdinal(String s)
    {
        String ordinal;
        
        int i;
        try {
            i = Integer.parseInt(s);
            ordinal = convertToOrdinal(i);
        } catch (NumberFormatException e) {
            ordinal = null;
        }
        
        return ordinal;
    }

    // ========================================

    /**
     * Returns the ordinal form of the integer that was passed in.
     * @param i The integer that is being converted
     */ 
    public static String convertToOrdinal(int i)
    {
        String ordinal;
        switch(i) 
        {
            case 1: ordinal = "first";
                break;
            case 2: ordinal = "second";
                break;
            case 3: ordinal = "third";
                break;
            case 4: ordinal = "fourth";
                break;
            case 5: ordinal = "fifth";
                break;
            case 6: ordinal = "sixth";
                break;
            case 7: ordinal = "seventh";
                break;
            case 8: ordinal = "eighth";
                break;
            case 9: ordinal = "ninth";
                break;
            case 10: ordinal = "tenth";
                break;
            case 11: ordinal = "eleventh";
                break;
            case 12: ordinal = "twelfth";
                break;
            case 13: ordinal = "thirteenth";
                break;
            case 14: ordinal = "fourteenth";
                break;
            case 15: ordinal = "fifteenth";
                break;
            case 16: ordinal = "sixteenth";
                break;
            case 17: ordinal = "seventeenth";
                break;
            case 18: ordinal = "eighteenth";
                break;
            case 19: ordinal = "nineteenth";
                break;
            case 20: ordinal = "twentieth";
                break;
            default: ordinal = String.valueOf(i) + (i % 100 == 11 ? "th" : (i % 10 == 1 ? "st" : "th"));
                break;
        }
        
        return ordinal;
    }

    // ========================================
    /**
     * Returns the ordinal form of the double that was passed in.
     * @param d The double that is being converted
     */ 
    public static String convertToOrdinal(double d)
    {
        int i = new Double(d).intValue();
        return convertToOrdinal(i);
    }

    // ========================================
    /**
     * Returns the ordinal form of the long that was passed in.
     * @param l The long that is being converted
     */ 
    public static String convertToOrdinal(long l)
    {
        int i = new Long(l).intValue();
        return convertToOrdinal(i);
    }

    // ========================================
    /**
     * Returns the ordinal form of the float that was passed in.
     * @param d The float that is being converted
     */
    public static String convertToOrdinal(float d)
    {
        int i = new Float(d).intValue();
        return convertToOrdinal(i);
    }
    

	// ========================================
	//
	// methods: Properties
	//
	// ========================================

	public static String convert(Properties p, String s, String d)
	{
		if (p != null && p.containsKey(s))
		{
			return convert(p.getProperty(s), d);
		}
		return d;
	}

	// ========================================

	public static long convert(Properties p, String s, long d)
	{
		if (p != null && p.containsKey(s))
		{
			return convert(p.getProperty(s), d);
		}
		return d;
	}

	// ========================================

	public static int convert(Properties p, String s, int d)
	{
		if (p != null && p.containsKey(s))
		{
			return convert(p.getProperty(s), d);
		}
		return d;
	}

	// ========================================

	public static boolean convert(Properties p, String s, boolean d)
	{
		if (p != null && p.containsKey(s))
		{
			return convert(p.getProperty(s), d);
		}
		return d;
	}

	// ========================================

	public static float convert(Properties p, String s, float d)
	{
		if (p != null && p.containsKey(s))
		{
			return convert(p.getProperty(s), d);
		}
		return d;
	}

	// ========================================

	public static double convert(Properties p, String s, double d)
	{
		if (p != null && p.containsKey(s))
		{
			return convert(p.getProperty(s), d);
		}
		return d;
	}

	// ========================================

	public static String convert(Properties p, Properties p2, String s, String d)
	{
		if (p != null && p.containsKey(s))
		{
			return convert(p.getProperty(s), d);
		}
		if (p2 != null && p2.containsKey(s))
		{
			return convert(p2.getProperty(s), d);
		}
		return d;
	}

	// ========================================

	public static long convert(Properties p, Properties p2, String s, long d)
	{
		if (p != null && p.containsKey(s))
		{
			return convert(p.getProperty(s), d);
		}
		if (p2 != null && p2.containsKey(s))
		{
			return convert(p2.getProperty(s), d);
		}
		return d;
	}

	// ========================================

	public static int convert(Properties p, Properties p2, String s, int d)
	{
		if (p != null && p.containsKey(s))
		{
			return convert(p.getProperty(s), d);
		}
		if (p2 != null && p2.containsKey(s))
		{
			return convert(p2.getProperty(s), d);
		}
		return d;
	}

	// ========================================

	public static boolean convert(Properties p, Properties p2, String s, boolean d)
	{
		if (p != null && p.containsKey(s))
		{
			return convert(p.getProperty(s), d);
		}
		if (p2 != null && p2.containsKey(s))
		{
			return convert(p2.getProperty(s), d);
		}
		return d;
	}

	// ========================================

	public static float convert(Properties p, Properties p2, String s, float d)
	{
		if (p != null && p.containsKey(s))
		{
			return convert(p.getProperty(s), d);
		}
		if (p2 != null && p2.containsKey(s))
		{
			return convert(p2.getProperty(s), d);
		}
		return d;
	}

	// ========================================

	public static double convert(Properties p, Properties p2, String s, double d)
	{
		if (p != null && p.containsKey(s))
		{
			return convert(p.getProperty(s), d);
		}
		if (p2 != null && p2.containsKey(s))
		{
			return convert(p2.getProperty(s), d);
		}
		return d;
	}

	// ========================================
	//
	// methods: HttpServletRequest
	//
	// ========================================

    /* jsl - commented out **************************************************
	public static String convert(HttpServletRequest r, String s, String d)
	{
		if (r.getParameter(s) != null)
		{
			return convert(r.getParameter(s), d);
		}
		return d;
	}

	// ========================================

	public static long convert(HttpServletRequest r, String s, long d)
	{
		if (r.getParameter(s) != null)
		{
			return convert(r.getParameter(s), d);
		}
		return d;
	}

	// ========================================

	public static int convert(HttpServletRequest r, String s, int d)
	{
		if (r.getParameter(s) != null)
		{
			return convert(r.getParameter(s), d);
		}
		return d;
	}

	// ========================================

	public static boolean convert(HttpServletRequest r, String s, boolean d)
	{
		if (r.getParameter(s) != null)
		{
			return convert(r.getParameter(s), d);
		}
		return d;
	}

	// ========================================

	public static float convert(HttpServletRequest r, String s, float d)
	{
		if (r.getParameter(s) != null)
		{
			return convert(r.getParameter(s), d);
		}
		return d;
	}

	// ========================================

	public static double convert(HttpServletRequest r, String s, double d)
	{
		if (r.getParameter(s) != null)
		{
			return convert(r.getParameter(s), d);
		}
		return d;
	}

    **********************************************************/

	// ========================================
	//
	// methods: Element
	//
	// ========================================

	public static String convert(Element e, String x, String d)
	{
		try
		{
			XPath xpath = XPathFactory.newInstance().newXPath();
			String s = (String) xpath.evaluate(x, e, XPathConstants.STRING);
			return convert(s, d);
		}
		catch (Exception ex)
		{
		}
		return d;
	}

	// ========================================

	public static long convert(Element e, String x, long d)
	{
		try
		{
			XPath xpath = XPathFactory.newInstance().newXPath();
			String s = (String) xpath.evaluate(x, e, XPathConstants.STRING);
			return convert(s, d);
		}
		catch (Exception ex)
		{
		}
		return d;
	}

	// ========================================

	public static int convert(Element e, String x, int d)
	{
		try
		{
			XPath xpath = XPathFactory.newInstance().newXPath();
			String s = (String) xpath.evaluate(x, e, XPathConstants.STRING);
			return convert(s, d);
		}
		catch (Exception ex)
		{
		}
		return d;
	}

	// ========================================

	public static boolean convert(Element e, String x, boolean d)
	{
		try
		{
			XPath xpath = XPathFactory.newInstance().newXPath();
			String s = (String) xpath.evaluate(x, e, XPathConstants.STRING);
			return convert(s, d);
		}
		catch (Exception ex)
		{
		}
		return d;
	}

	// ========================================

	public static float convert(Element e, String x, float d)
	{
		try
		{
			XPath xpath = XPathFactory.newInstance().newXPath();
			String s = (String) xpath.evaluate(x, e, XPathConstants.STRING);
			return convert(s, d);
		}
		catch (Exception ex)
		{
		}
		return d;
	}

	// ========================================

	public static double convert(Element e, String x, double d)
	{
		try
		{
			XPath xpath = XPathFactory.newInstance().newXPath();
			String s = (String) xpath.evaluate(x, e, XPathConstants.STRING);
			return convert(s, d);
		}
		catch (Exception ex)
		{
		}
		return d;
	}

	// ========================================
	//
	// methods: other
	//
	// ========================================

	public static String convertByteListToHexString2(byte[] byteList)
	{
		StringBuffer sb = new StringBuffer();

		for (int i = 0; i < byteList.length; i++)
		{
			String hex = Integer.toHexString(0x0100 + (byteList[i] & 0x00FF)).substring(1);
			sb.append((hex.length() < 2 ? "0" : "") + hex);
		}

		return sb.toString();
	}

	// ========================================

	public static String convertByteListToHexString(byte[] byteList)
	{
		StringBuffer sb = new StringBuffer();

		for (int i = 0; i < byteList.length; i++)
		{
			int b = ((int) byteList[i] & 0x000000ff);
			if (b < 16)
			{
				sb.append("0");
			}
			sb.append(Integer.toHexString(b).toUpperCase());
		}

		return sb.toString();
	}

	// ========================================

	public static String convertByteListToHexString(byte[] byteList, int length)
	{
		StringBuffer sb = new StringBuffer();

		for (int i = 0; i < length; i++)
		{
			int b = ((int) byteList[i] & 0x000000ff);
			if (b < 16)
			{
				sb.append("0");
			}
			sb.append(Integer.toHexString(b).toUpperCase());
		}

		return sb.toString();
	}

	// ========================================

	public static String convertByteListToHexString(byte[] byteList, int start, int length)
	{
		StringBuffer sb = new StringBuffer();

		for (int i = start; i < start + length; i++)
		{
			int b = ((int) byteList[i] & 0x000000ff);
			if (b < 16)
			{
				sb.append("0");
			}
			sb.append(Integer.toHexString(b).toUpperCase());
		}

		return sb.toString();
	}

	// ========================================

	public static byte[] convertHexStringToByteList(String inputString)
	{
		byte[] byteList = new byte[inputString.length() / 2];
		// byteList = new BigInteger(inputString, 16).toByteArray();

		for (int i = 0; i < byteList.length; i++)
		{
			byteList[i] = (byte) Integer.parseInt(inputString.substring(2 * i, 2 * i + 2), 16);
		}

		return byteList;
	}

	// ========================================

	public static String convertStringToMD5String(String inputString)
	{
		try
		{
			byte[] byteList = MessageDigest.getInstance("MD5").digest(inputString.getBytes());
			String s = convertByteListToHexString(byteList);
			return s;
		}
		catch (Exception ex)
		{
		}
		return "";
	}

	// ========================================

	public static String convertStringToSHA1String(String inputString)
	{
		try
		{
			byte[] byteList = MessageDigest.getInstance("SHA1").digest(inputString.getBytes());
			String s = convertByteListToHexString(byteList);
			return s;
		}
		catch (Exception ex)
		{
		}
		return "";
	}

	// ========================================

	public static byte[] convertStringToMD5ByteList(String inputString)
	{
		try
		{
			byte[] byteList = MessageDigest.getInstance("MD5").digest(inputString.getBytes());
			return byteList;
		}
		catch (Exception ex)
		{
		}
		return null;
	}

	// ========================================

	public static byte[] convertStringToSHA1ByteList(String inputString)
	{
		try
		{
			byte[] byteList = MessageDigest.getInstance("SHA1").digest(inputString.getBytes());
			return byteList;
		}
		catch (Exception ex)
		{
		}
		return null;
	}

	// ========================================

	public static String convertPropertiesToXmlString(Properties p)
	{
		String xml = "";
		xml += "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n";
		xml += "<!DOCTYPE properties SYSTEM \"http://java.sun.com/dtd/properties.dtd\">\r\n";
		xml += "<properties>\r\n";
		xml += "<comment></comment>\r\n";
		xml += "</properties>\r\n";

		try
		{
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			p.storeToXML(baos, "");
			xml = baos.toString();
		}
		catch (Exception ex)
		{
		}

		return xml;
	}

	// ========================================

	public static Properties convertXmlStringToProperties(String xml)
	{
		Properties p = new Properties();

		try
		{
			p.loadFromXML(new ByteArrayInputStream(xml.getBytes()));
		}
		catch (Exception ex)
		{
		}

		return p;
	}

	// ========================================
	//
	// methods: convertTo - primitive types
	//
	// ========================================

	private static void __________PrimitiveTypes__________()
	{
	}

	// ========================================

	public static String convertToString(String s, String d)
	{
		if (s == null)
		{
			return d;
		}
		return s;
	}

	// ========================================

	public static long convertToLong(String s, long d)
	{
		long r = d;
		try
		{
			r = Long.parseLong(s);
		}
		catch (Exception ex)
		{
		}
		return r;
	}

	// ========================================

	public static int convertToInt(String s, int d)
	{
		int r = d;
		try
		{
			r = Integer.parseInt(s);
		}
		catch (Exception ex)
		{
		}
		return r;
	}

	// ========================================

	public static boolean convertToBoolean(String s, boolean d)
	{
		boolean r = d;
		try
		{
			r = Boolean.parseBoolean(s);
		}
		catch (Exception ex)
		{
		}
		return r;
	}

	// ========================================

	public static float convertToFloat(String s, float d)
	{
		float r = d;
		try
		{
			r = Float.parseFloat(s);
		}
		catch (Exception ex)
		{
		}
		return r;
	}

	// ========================================

	public static double convertToDouble(String s, double d)
	{
		double r = d;
		try
		{
			r = Double.parseDouble(s);
		}
		catch (Exception ex)
		{
		}
		return r;
	}

	// ========================================
	//
	// methods: Properties
	//
	// ========================================

	private static void __________Properties__________()
	{
	}

	// ========================================

	public static String convertToString(Properties p, String key, String d)
	{
		if (p != null && p.containsKey(key))
		{
			return convert(p.getProperty(key), d);
		}
		return d;
	}

	// ========================================

	public static long convertToLong(Properties p, String key, long d)
	{
		return ConvertTools.convertToLong(convertToString(p, key, Long.toString(d)), d);
	}

	// ========================================

	public static int convertToInt(Properties p, String key, int d)
	{
		return ConvertTools.convertToInt(convertToString(p, key, Integer.toString(d)), d);
	}

	// ========================================

	public static boolean convertToBoolean(Properties p, String key, boolean d)
	{
		return ConvertTools.convertToBoolean(convertToString(p, key, Boolean.toString(d)), d);
	}

	// ========================================

	public static float convertToFloat(Properties p, String key, float d)
	{
		return ConvertTools.convertToFloat(convertToString(p, key, Float.toString(d)), d);
	}

	// ========================================

	public static double convertToDouble(Properties p, String key, double d)
	{
		return ConvertTools.convertToDouble(convertToString(p, key, Double.toString(d)), d);
	}

	// ========================================
	//
	// methods: PropertiesProperties
	//
	// ========================================

	private static void __________PropertiesProperties__________()
	{
	}

	// ========================================

	public static String convertToString(Properties p, Properties p2, String key, String d)
	{
		if (p != null && p.containsKey(key))
		{
			return convertToString(p.getProperty(key), d);
		}
		if (p2 != null && p2.containsKey(key))
		{
			return convertToString(p2.getProperty(key), d);
		}
		return d;
	}

	// ========================================

	public static long convertToLong(Properties p, Properties p2, String key, long d)
	{
		return ConvertTools.convertToLong(convertToString(p, key, Long.toString(d)), d);
	}

	// ========================================

	public static int convertToInt(Properties p, Properties p2, String key, int d)
	{
		return ConvertTools.convertToInt(convertToString(p, key, Integer.toString(d)), d);
	}

	// ========================================

	public static boolean convertToBoolean(Properties p, Properties p2, String key, boolean d)
	{
		return ConvertTools.convertToBoolean(convertToString(p, key, Boolean.toString(d)), d);
	}

	// ========================================

	public static float convertToFloat(Properties p, Properties p2, String key, float d)
	{
		return ConvertTools.convertToFloat(convertToString(p, key, Float.toString(d)), d);
	}

	// ========================================

	public static double convertToDouble(Properties p, Properties p2, String key, double d)
	{
		return ConvertTools.convertToDouble(convertToString(p, key, Double.toString(d)), d);
	}

	// ========================================
	//
	// methods: HttpServletRequest
	//
	// ========================================

	// ========================================

    // jsl - commented out
    /********************************************************************
	private static void __________HttpServletRequest__________()
	{
	}

	public static String convertToString(HttpServletRequest r, String key, String d)
	{
		if (r.getParameter(key) != null)
		{
			return convertToString(r.getParameter(key), d);
		}
		return d;
	}

	// ========================================

	public static long convertToLong(HttpServletRequest r, String key, long d)
	{
		return ConvertTools.convertToLong(convertToString(r, key, Long.toString(d)), d);
	}

	// ========================================

	public static int convertToInt(HttpServletRequest r, String key, int d)
	{
		return ConvertTools.convertToInt(convertToString(r, key, Integer.toString(d)), d);
	}

	// ========================================

	public static boolean convertToBoolean(HttpServletRequest r, String key, boolean d)
	{
		return ConvertTools.convertToBoolean(convertToString(r, key, Boolean.toString(d)), d);
	}

	// ========================================

	public static float convertToFloat(HttpServletRequest r, String key, float d)
	{
		return ConvertTools.convertToFloat(convertToString(r, key, Float.toString(d)), d);
	}

	// ========================================

	public static double convertToDouble(HttpServletRequest r, String key, double d)
	{
		return ConvertTools.convertToDouble(convertToString(r, key, Double.toString(d)), d);
    }
    *************************************/

	// ========================================
	//
	// methods: XmlDocument
	//
	// ========================================

	private static void __________XmlDocument__________()
	{
	}

	// ========================================

	public static String convertToString(Document document, String expression, String d)
	{
		String r = d;

		try
		{
			XPath xpath = XPathFactory.newInstance().newXPath();
			String s = (String) xpath.evaluate(expression, document, XPathConstants.STRING);
			r = ConvertTools.convertToString(s, d);
		}
		catch (Exception ex)
		{
		}

		return r;
	}

	// ========================================

	public static long convertToLong(Document document, String expression, long d)
	{
		return ConvertTools.convertToLong(ConvertTools.convertToString(document, expression, Long.toString(d)), d);
	}

	// ========================================

	public static int convertToInt(Document document, String expression, int d)
	{
		return ConvertTools.convertToInt(ConvertTools.convertToString(document, expression, Integer.toString(d)), d);
	}

	// ========================================

	public static boolean convertToBoolean(Document document, String expression, boolean d)
	{
		return ConvertTools
				.convertToBoolean(ConvertTools.convertToString(document, expression, Boolean.toString(d)), d);
	}

	// ========================================

	public static float convertToFloat(Document document, String expression, float d)
	{
		return ConvertTools.convertToFloat(ConvertTools.convertToString(document, expression, Float.toString(d)), d);
	}

	// ========================================

	public static double convertToDouble(Document document, String expression, double d)
	{
		return ConvertTools.convertToDouble(ConvertTools.convertToString(document, expression, Double.toString(d)), d);
	}

	// ========================================
	//
	// methods: XmlNode
	//
	// ========================================

	private static void __________XmlNode__________()
	{
	}

	// ========================================

	public static String convertToString(Node node, String expression, String d)
	{
		String r = d;

		try
		{
			XPath xpath = XPathFactory.newInstance().newXPath();
			String s = (String) xpath.evaluate(expression, node, XPathConstants.STRING);
			r = ConvertTools.convertToString(s, d);
		}
		catch (Exception ex)
		{
		}

		return r;
	}

	// ========================================

	public static long convertToLong(Node node, String expression, long d)
	{
		return ConvertTools.convertToLong(ConvertTools.convertToString(node, expression, Long.toString(d)), d);
	}

	// ========================================

	public static int convertToInt(Node node, String expression, int d)
	{
		return ConvertTools.convertToInt(ConvertTools.convertToString(node, expression, Integer.toString(d)), d);
	}

	// ========================================

	public static boolean convertToBoolean(Node node, String expression, boolean d)
	{
		return ConvertTools.convertToBoolean(ConvertTools.convertToString(node, expression, Boolean.toString(d)), d);
	}

	// ========================================

	public static float convertToFloat(Node node, String expression, float d)
	{
		return ConvertTools.convertToFloat(ConvertTools.convertToString(node, expression, Float.toString(d)), d);
	}

	// ========================================

	public static double convertToDouble(Node node, String expression, double d)
	{
		return ConvertTools.convertToDouble(ConvertTools.convertToString(node, expression, Double.toString(d)), d);
	}

	// ========================================
	//
	// methods: XmlElement
	//
	// ========================================

	private static void __________XmlElement__________()
	{
	}

	// ========================================

	public static String convertToString(Element element, String expression, String d)
	{
		String r = d;

		try
		{
			XPath xpath = XPathFactory.newInstance().newXPath();
			String s = (String) xpath.evaluate(expression, element, XPathConstants.STRING);
			r = ConvertTools.convertToString(s, d);
		}
		catch (Exception ex)
		{
		}

		return r;
	}

	// ========================================

	public static long convertToLong(Element element, String expression, long d)
	{
		return ConvertTools.convertToLong(ConvertTools.convertToString(element, expression, Long.toString(d)), d);
	}

	// ========================================

	public static int convertToInt(Element element, String expression, int d)
	{
		return ConvertTools.convertToInt(ConvertTools.convertToString(element, expression, Integer.toString(d)), d);
	}

	// ========================================

	public static boolean convertToBoolean(Element element, String expression, boolean d)
	{
		return ConvertTools.convertToBoolean(ConvertTools.convertToString(element, expression, Boolean.toString(d)), d);
	}

	// ========================================

	public static float convertToFloat(Element element, String expression, float d)
	{
		return ConvertTools.convertToFloat(ConvertTools.convertToString(element, expression, Float.toString(d)), d);
	}

	// ========================================

	public static double convertToDouble(Element element, String expression, double d)
	{
		return ConvertTools.convertToDouble(ConvertTools.convertToString(element, expression, Double.toString(d)), d);
	}

	// ========================================
	//
	// methods: Other
	//
	// ========================================

	private static void __________Other__________()
	{
	}

	// ========================================

	public static String convertToCanonicalPath(String path)
	{
		try
		{
			File f = new File(path);
			path = f.getCanonicalPath();
		}
		catch (Exception ex)
		{
		}
		return path;
	}

	// ========================================

	public static String convertToXmlStringFromProperties(Properties p)
	{
		String xml = "";
		xml += "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n";
		xml += "<!DOCTYPE properties SYSTEM \"http://java.sun.com/dtd/properties.dtd\">\r\n";
		xml += "<properties>\r\n";
		xml += "<comment></comment>\r\n";
		xml += "</properties>\r\n";

		try
		{
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			p.storeToXML(baos, "");
			xml = baos.toString();
		}
		catch (Exception ex)
		{
		}

		return xml;
	}

	// ========================================

	public static Properties convertToPropertiesFromXmlString(String xml)
	{
		Properties p = new Properties();

		try
		{
			p.loadFromXML(new ByteArrayInputStream(xml.getBytes()));
		}
		catch (Exception ex)
		{
		}

		return p;
	}

	// ========================================

	public static Properties convertToPropertiesFromNameValueString(String s, Properties d)
	{
		Properties r = d;

		try
		{
			Properties properties = new Properties();
			String[] nameValuePairs = s.split(";");
			for (int i = 0; i < nameValuePairs.length; i++)
			{
				String[] nameValueParts = nameValuePairs[i].split("=", 2);
				if (nameValueParts.length == 2)
				{
					String name = nameValueParts[0];
					String value = nameValueParts[1];
					properties.put(name, value);
				}
			}

			r = properties;
		}
		catch (Exception ex)
		{
		}

		return r;
	}

	// ========================================

	public static String convertToNameValueStringFromProperties(Properties properties, String d)
	{
		String r = d;

		try
		{
			StringBuilder sb = new StringBuilder();
			for (Iterator iter = properties.keySet().iterator(); iter.hasNext();)
			{
				String key = (String) iter.next();
				String value = properties.getProperty(key);
				sb.append(key + "=" + value + ";");
			}
			String s = sb.toString();
			s = StringUtils.chomp(s, ";");
			r = s;
		}
		catch (Exception ex)
		{
		}

		return r;
	}

	// ========================================

	public static String convertToHexStringFromByteList(byte[] byteList, int start, int length)
	{
		StringBuffer sb = new StringBuffer();

		for (int i = start; i < start + length; i++)
		{
			int b = ((int) byteList[i] & 0x000000ff);
			if (b < 16)
			{
				sb.append("0");
			}
			sb.append(Integer.toHexString(b).toUpperCase());
		}

		return sb.toString();
	}

	// ========================================

	public static String convertToHexStringFromByteList(byte[] byteList, int length)
	{
		return convertToHexStringFromByteList(byteList, 0, length);
	}

	// ========================================

	public static String convertToHexStringFromByteList(byte[] byteList)
	{
		return convertToHexStringFromByteList(byteList, 0, byteList.length);
	}

	// ========================================

	public static byte[] convertToByteListFromHexString(String s)
	{
		byte[] byteList = new byte[s.length() / 2];

		for (int i = 0; i < byteList.length; i++)
		{
			byteList[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
		}

		return byteList;
	}

	// ========================================

	public static String convertToHexStringFromString(String s, String d)
	{
		String r = d;

		try
		{
			r = convertToHexStringFromByteList(s.getBytes());
		}
		catch (Exception ex)
		{
		}

		return r;
	}

	// ========================================

	public static String convertToStringFromHexString(String s, String d)
	{
		String r = d;

		try
		{
			r = new String(convertToByteListFromHexString(s));
		}
		catch (Exception ex)
		{
		}

		return r;
	}

	// ========================================

	public static String convertToStringFromBase64String(String s, String d)
	{
		String r = d;

        try
        {
    		byte[] byteList = Base64.decode(s);
            r = new String(byteList);
        }
        catch (Exception ex)
        {
        }

        return r;
	}

	// ========================================

	public static String convertToBase64StringFromString(String s, String d)
	{
		String r = d;

		if (null != s) {
		    r = Base64.encodeBytes(s.getBytes());
		}

		return r;
	}

	// ========================================

	public static String convertToStringFromInputStream(InputStream is, String d)
	{
		String r = d;
		try
		{
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			byte[] buffer = new byte[1024];
			int len;
			while ((len = is.read(buffer)) > 0)
			{
				baos.write(buffer, 0, len);
			}
			byte[] data = baos.toByteArray();
			r = new String(data);
		}
		catch (Exception ex)
		{
		}

		return r;
	}

	// ========================================

	public static String convertToStringFromResource(String resource, String d)
	{
		String r = d;
		try
		{
			InputStream is = String.class.getResourceAsStream(resource);

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			byte[] buffer = new byte[1024];
			int len;
			while ((len = is.read(buffer)) > 0)
			{
				baos.write(buffer, 0, len);
			}
			byte[] data = baos.toByteArray();
			r = new String(data);
		}
		catch (Exception ex)
		{
		}

		return r;
	}

	// ========================================

	public static String convertToStringFromFile(File file, String d)
	{
		String r = d;
		try
		{
			InputStream is = new FileInputStream(file);

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			byte[] buffer = new byte[1024];
			int len;
			while ((len = is.read(buffer)) > 0)
			{
				baos.write(buffer, 0, len);
			}
			byte[] data = baos.toByteArray();
			r = new String(data);
		}
		catch (Exception ex)
		{
		}

		return r;
	}

}
