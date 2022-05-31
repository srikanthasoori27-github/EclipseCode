/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Base class for parsing newline protocols
 */
abstract public class NewlineProtocolHandler
{
    private StringBuilder _buffer = new StringBuilder();

    /**
     * To be overridden by subclasses to capture each line.
     * Default implementation parses the line as a space-separated list
     * and calls doCommand.
     */
    protected void doLine(String line)
    {
        StringTokenizer tok = new StringTokenizer(line, " ", false);
        if (tok.hasMoreTokens())
        {
            String command    = tok.nextToken();
            List<String> args = new ArrayList<String>();
            while (tok.hasMoreTokens())
            {
                args.add(tok.nextToken());
            }
            doCommand(command, args);
        }
    }

    /**
     * To be overridden by subclasses which want to process the commands
     * as a space-separated list.
     */
    protected void doCommand(String command, List<String> args)
    {
    }

    /**
     * To be called when receiving a chunk of bytes. Will result in
     * one or more calls to doLine
     */
    public void addBytes(byte [] bytes)
    {
        String str = EncodingUtil.bytesToUTF8String(bytes);
        for (int i = 0; i < str.length(); i++)
        {
            char c = str.charAt(i);
            switch (c)
            {
                case '\r':
                    break;
                case '\n':
                    String line = _buffer.toString();
                    _buffer.setLength(0);
                    doLine(line);
                    break;
                default:
                    _buffer.append(c);
            }
        }
    }
}
