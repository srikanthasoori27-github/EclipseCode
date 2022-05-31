/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

public class CookieUtil
{
    private final static String SEP = "|";
    /**
     * Parses a cookie value which is stored in the format
     * key1|value1|...|keyn|valuen. This isn't a standard or anything,
     * just *a* way to encode a map of key-value pairs in a cookie.
     */
    public static Map<String,String> decodeCookieValue(String cookievalue)
    {

        Map<String,String> rv = new 
            HashMap<String,String>();
        StringTokenizer tok = new StringTokenizer(cookievalue,SEP,true);
        while (tok.hasMoreTokens())
        {
            String key   = tok.nextToken();
            if (key.equals(SEP))
            {
                throw new RuntimeException("Keys may not be empty: "+cookievalue);
            }
            String value = null;
            
            if ( tok.hasMoreTokens() )
            {
                String sep = tok.nextToken(); //must be a |
                
                if (tok.hasMoreTokens())
                {
                    //could be a | in which case value is empty
                    //or a value
                    String sepOrValue = tok.nextToken();
                    if (!sepOrValue.equals(SEP))
                    {
                        value = sepOrValue;
                        //eat the next | 
                        if (tok.hasMoreTokens())
                        {
                            sep = tok.nextToken();
                        }
                    }
                }
                
            }
            String keyDecoded   = URLUtil.decodeUTF8(key);
            String valueDecoded = value == null ? null : URLUtil.decodeUTF8(value);
            rv.put(keyDecoded, valueDecoded);
        }
        return rv;
    }

    public static String encodeCookieValue(Map<String,String> pairs)
    {
        StringBuilder buf = new StringBuilder();
        String sep = "";
        for (Map.Entry<String,String> entry : pairs.entrySet())
        {
            String key   = entry.getKey();
            if (key == null || key.equals(""))
            {
                continue;
            }
            String value = entry.getValue() == null ? "" : entry.getValue();
            
            buf.append(sep);

            buf.append(URLUtil.encodeUTF8(key));
            buf.append(SEP);
            buf.append(URLUtil.encodeUTF8(value));
            
            sep = SEP;
        }
        return buf.toString();
    }
}
