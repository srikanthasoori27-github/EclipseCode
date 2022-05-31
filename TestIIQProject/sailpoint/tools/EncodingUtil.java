/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

public class EncodingUtil
{
    public static String bytesToHex(byte [] bytes)
    {
        StringBuffer sb = new StringBuffer();

        for (int i = 0; i < bytes.length; i++)
        {
            int b = ((int) bytes[i] & 0x000000ff);
            if (b < 16)
            {
                sb.append("0");
            }
            sb.append(Integer.toHexString(b).toUpperCase());
        }

        return sb.toString();
    }

    public static byte [] hexToBytes(String s)
    {
        byte[] byteList = new byte[s.length() / 2];

        for (int i = 0; i < byteList.length; i++)
        {
            byteList[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        }

        return byteList;
    }

    public static byte [] stringToUTF8Bytes(String s)
    {
        try
        {
            return s.getBytes("UTF-8");
        }
        catch (java.io.UnsupportedEncodingException e)
        {
            throw new RuntimeException(e);
        }
    }

    public static String bytesToUTF8String(byte [] bytes)
    {
        try
        {
            return new String(bytes,"UTF-8");
        }
        catch (java.io.UnsupportedEncodingException e)
        {
            throw new RuntimeException(e);
        }
    }

}
