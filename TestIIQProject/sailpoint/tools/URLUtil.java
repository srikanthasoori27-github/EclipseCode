/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

public class URLUtil
{
    public static Map<String,String> parseQueryParameters(String url)
    {
        Map<String,String> rv = new HashMap<String,String>();
        int pos = url.indexOf('?');
        String params;
        if (pos != -1)
        {
            params = url.substring(pos+1);
        }
        else
        {
            params = url;
        }
        StringTokenizer tokenizer = new StringTokenizer(params,"&",false);
        while (tokenizer.hasMoreTokens())
        {
            String token = tokenizer.nextToken();
            
            pos = token.indexOf('=');
            if (pos != -1)
            {
                String key   = token.substring(0,pos);
                String value = token.substring(pos+1);
                
                key   = URLUtil.decodeUTF8(key);
                value = URLUtil.decodeUTF8(value);
                rv.put(key, value);
            }
        }

        return rv;
    }

    public static String encodeQueryParameters(Map<String,String> params)
    {
        StringBuilder rv = new StringBuilder();
        String sep = "";
        for (Map.Entry<String,String> entry : params.entrySet())
        {
            String key = entry.getKey();
            String val = entry.getValue();
            rv.append(sep);
            rv.append(encodeUTF8(key)).append("=").append(encodeUTF8(val));
            sep = "&";
        }
        return rv.toString();
    }

    public static String decodeUTF8(String val)
    {
        try
        {
            return URLDecoder.decode(val, "UTF-8");
        }
        catch (UnsupportedEncodingException ex)
        {
            throw new RuntimeException(ex);
        }
    }

    public static String encodeUTF8(String val)
    {
        try
        {
            return URLEncoder.encode(val, "UTF-8");
        }
        catch (UnsupportedEncodingException ex)
        {
            throw new RuntimeException(ex);
        }
    }
        
}
