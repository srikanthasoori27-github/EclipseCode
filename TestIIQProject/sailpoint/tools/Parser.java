/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Date;


/**
 * A text Parser to be used by compilers.
 * <p>
 * Special thanks to Mike Martin and the TJDO team for influence into this
 * parser code.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class Parser
{
    /**
     * Interface implemented to allow look-ahead to determine and resolve
     * ambiguities in the grammar.  Each LookAhead should implement a given
     * look-ahead rule to resolve a single grammar ambiguity.
     */
    public static interface LookAhead {
        
        /**
         * Return whether the given character can be ambiguous for the grammar.
         */
        public boolean isAmbiguous(char c);

        /**
         * Called with the Parser whenever isAmbiguous() returns true.  This
         * should tell whether the next characters in the Parser's stream
         * should be considered part of the token being parsed or if the next
         * characters are a part of the next token in the grammer.
         * 
         * @param  p  The Parser that encountered the ambiguity.
         * 
         * @return True if the next characters in the Parser's stream are a part
         *         of the current token being parsed.
         */
        public boolean continueParsing(Parser p);
    }

    /**
     * A base implementation of LookAhead that delegates the logic to the
     * continueParsingInternal() template method and pushes back any characters
     * that were read from the stream while looking ahead.
     */
    public static abstract class BaseLookAhead implements LookAhead {
        
        /**
         * A template method that should be overridden to check if we should
         * continue parsing.
         */
        protected abstract boolean continueParsingInternal(Parser p);

        /**
         * Implementation of continueParsing() that delegates the logic to the
         * continueParsingInteral() template method, but pushes the consumed
         * input back onto the parsing after we peek.
         */
        public final boolean continueParsing(Parser p) {
            int savedStart = p.getIndex();
            
            boolean continueParsing = false;
            try {
                continueParsing = continueParsingInternal(p);
            }
            finally {
                p.setIndex(savedStart);
            }

            return continueParsing;
        }
    }


    private final String input;
    protected final CharacterIterator ci;


    public Parser(String input)
    {
        this.input = input;
        this.ci = new StringCharacterIterator(input);
    }


    public String getInput()
    {
        return input;
    }


    public int getIndex()
    {
        return ci.getIndex();
    }

    
    void setIndex(int idx)
    {
        ci.setIndex(idx);
    }


    public int skipWS()
    {
        int startIdx = ci.getIndex();
        char c = ci.current();

        while (Character.isWhitespace(c))
            c = ci.next();

        return startIdx;
    }


    public boolean parseEOS()
    {
        skipWS();

        return ci.current() == CharacterIterator.DONE;
    }


    public boolean parseChar(char c)
    {
        skipWS();

        if (ci.current() == c)
        {
            ci.next();
            return true;
        }
        else
            return false;
    }


    public boolean parseString(String s)
    {
        int savedIdx = skipWS();

        int len = s.length();
        char c = ci.current(); 

        for (int i = 0; i < len; ++i)
        {
            if (c != s.charAt(i))
            {
                ci.setIndex(savedIdx);
                return false;
            }

            c = ci.next();
        }

        return true;
    }


    public String parseIdentifier()
    {
        return parseIdentifier(false, null);
    }


    /**
     * Parse a java identifier optionally allowing spaces in the identifier.
     * 
     * @param  allowSpaces  Whether to allow spaces.
     * @param  lookAhead    The LookAhead to use to determine and resolve
     *                      ambiguities in the grammar.
     *
     * @return The java identifier, or null if it cannot be parsed.
     */
    public String parseIdentifier(boolean allowSpaces, LookAhead lookAhead)
    {
        skipWS();

        char c = ci.current();

        if (!Character.isJavaIdentifierStart(c))
            return null;

        StringBuffer id = new StringBuffer().append(c);

        while (Character.isJavaIdentifierPart(c = ci.next()) ||
               (allowSpaces && (c == ' '))) {

            id.append(c);

            if ((null != lookAhead) && lookAhead.isAmbiguous(c)) {

                if (!lookAhead.continueParsing(this)) {
                    // If we're not supposed to continue, jump ship.
                    break;
                }
            }
        }

        return Util.trimWhitespace(id.toString());
    }
    
    public String parseDottedClassIdentifier()
    {
        return this.parseDottedClassIdentifier(true);
    }
    
    private String parseDottedClassIdentifier(boolean requireFirstCaps)
    {
        StringBuilder buffer = new StringBuilder();
        int startIdx = ci.getIndex();

        skipWS();

        // First must be a class name, assumed to start with capital letter.
        // TODO: Maybe we shouldn't require a class name.  Maybe this should
        // just parse dotted identifiers.
        char c = ci.current();
        if (!requireFirstCaps || Character.isUpperCase(c)) {
            String s;
            while (null != (s = parseIdentifier())) {
                buffer.append(s);
                if (parseChar('.')) {
                    buffer.append('.');
                }
                else {
                    break;
                }
            }
        }

        // If we didn't collect anything, rewind to where we started.
        if (0 == buffer.length()) {
            ci.setIndex(startIdx);
        }

        return (0 == buffer.length()) ? null : buffer.toString();
    }

    public Class<?> parseFullyQualifiedClass() throws ParseException
    {
        String className = this.parseDottedClassIdentifier(false);
        if (null == className) {
            throw new ParseException("Expected a fully qualified class name " + className, this);
        }

        Class<?> clazz = null;
        try {
            clazz = Class.forName(className);
        }
        catch (ClassNotFoundException e) {
            throw new ParseException("Could not load class: " + className, this);
        }

        return clazz;
    }
    
    public Object parseLiteral() throws ParseException
    {
        Object lit;

        if ((lit = parseStringLiteral()) != null)
            ;
        else if ((lit = parseDateLiteral()) != null)
            ;
        else if ((lit = parseEnumLiteral()) != null)
            ;        
        else if ((lit = parseFloatingPointLiteral()) != null)
            ;
        else if ((lit = parseIntegerLiteral()) != null)
            ;
        else if ((lit = parseCharacterLiteral()) != null)
            ;
        else if ((lit = parseBooleanLiteral()) != null)
            ;

        return lit;
    }


    private final static boolean isDecDigit(char c)
    {
        return c >= '0' && c <= '9';
    }


    private final static boolean isOctDigit(char c)
    {
        return c >= '0' && c <= '7';
    }


    private final static boolean isHexDigit(char c)
    {
        return c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F';
    }
    
    public final static boolean isAlphaChar(char c)
    {
        return (c >= 'A' && c <= 'Z') || (c >='a' && c <='z');
    }
    
    public Date parseDateLiteral()
    {
        StringBuffer str = new StringBuffer();
        int savedIdx = skipWS();
        char c = ci.current();
        while (isAlphaChar(c) || (c == '$')) 
        {
            str.append(c);
            c = ci.next();
        }
        
        if(!str.toString().equals("DATE$")) {
            ci.setIndex(savedIdx);
            return null;
        }
        
        str = new StringBuffer();
        
        while (isDecDigit(c)){
            str.append(c);
            c = ci.next();
        }
        return new Date(Long.parseLong(str.toString()));
        
    }


    public Number parseIntegerLiteral()
    {
        int savedIdx = skipWS();
        StringBuffer digits = new StringBuffer();
        char suffix = '\u0000';
        int radix;
        char c = ci.current();

        if (c == '0')
        {
            c = ci.next();

            if (c == 'x' || c == 'X')
            {
                radix = 16;
                c = ci.next();

                while (isHexDigit(c))
                {
                    digits.append(c);
                    c = ci.next();
                }
            }
            else if (isOctDigit(c))
            {
                radix = 8;

                do
                {
                    digits.append(c);
                    c = ci.next();
                } while (isOctDigit(c));
            }
            else
            {
                radix = 10;
                digits.append('0');
            }
        }
        else
        {
            radix = 10;

            while (isDecDigit(c))
            {
                digits.append(c);
                c = ci.next();
            }
        }

        if (digits.length() == 0)
        {
            ci.setIndex(savedIdx);
            return null;
        }

        if (c == 'l' || c == 'L')
        {
            suffix = Character.toLowerCase(c);
            ci.next();
        }

        BigInteger val = new BigInteger(digits.toString(), radix);
        long l = val.longValue();
        int i = (int)l;

        if (suffix != 'l' && val.equals(BigInteger.valueOf(i)))
            return new Integer(i);
        else if (val.equals(BigInteger.valueOf(l)))
            return new Long(l);
        else
            return val;
    }


    public Number parseFloatingPointLiteral()
    {
        int savedIdx = skipWS();
        StringBuffer str = new StringBuffer();
        boolean dotSeen = false;
        boolean expSeen = false;
        char suffix = '\u0000';

        char c = ci.current();

        while (isDecDigit(c))
        {
            str.append(c);
            c = ci.next();
        }

        if (c == '.')
        {
            dotSeen = true;
            str.append(c);
            c = ci.next();

            while (isDecDigit(c))
            {
                str.append(c);
                c = ci.next();
            }
        }

        if (str.length() < (dotSeen ? 2 : 1))
        {
            ci.setIndex(savedIdx);
            return null;
        }

        if (c == 'e' || c == 'E')
        {
            expSeen = true;
            str.append(c);
            c = ci.next();

            if (c != '+' && c != '-' && !isDecDigit(c))
            {
                ci.setIndex(savedIdx);
                return null;
            }

            do
            {
                str.append(c);
                c = ci.next();
            } while (isDecDigit(c));
        }

        if (c == 'f' || c == 'F' || c == 'd' || c == 'D')
        {
            suffix = Character.toLowerCase(c);
            ci.next();
        }

        if (!dotSeen && !expSeen && suffix == '\u0000')
        {
            ci.setIndex(savedIdx);
            return null;
        }

        BigDecimal val = new BigDecimal(str.toString());

        if (expSeen || suffix != '\u0000')
        {
            float f;
            double d;

            if (suffix == 'f' && !Float.isInfinite(f = val.floatValue()))
                return new Float(f);
            else if (!Double.isInfinite(d = val.doubleValue()))
                return new Double(d);
        }

        return val;
    }


    public Boolean parseBooleanLiteral()
    {
        int savedIdx = skipWS();
        String id;

        if ((id = parseIdentifier()) == null)
            return null;

        if (id.equals("true"))
            return Boolean.TRUE;
        else if (id.equals("false"))
            return Boolean.FALSE;
        else
        {
            ci.setIndex(savedIdx);
            return null;
        }
    }


    public Character parseCharacterLiteral() throws ParseException
    {
        skipWS();

        if (ci.current() != '\'')
            return null;

        char c = ci.next();

        if (c == CharacterIterator.DONE)
            throw new ParseException("Invalid character literal", this);

        if (c == '\\')
            c = parseEscapedCharacter();

        if (ci.next() != '\'')
            throw new ParseException("Invalid character literal", this);

        ci.next();

        return new Character(c);
    }


    public String parseStringLiteral() throws ParseException
    {
        skipWS();

        if (ci.current() != '"')
            return null;

        StringBuffer lit = new StringBuffer();
        char c;

        while ((c = ci.next()) != '"')
        {
            if (c == CharacterIterator.DONE)
                throw new ParseException("Invalid string literal", this);

            if (c == '\\')
                c = parseEscapedCharacter();

            lit.append(c);
        }

        ci.next();

        return lit.toString();
    }


    private char parseEscapedCharacter() throws ParseException
    {
        char c;

        if (isOctDigit(c = ci.next()))
        {
            int i = c - '0';

            if (isOctDigit(c = ci.next()))
            {
                i = i * 8 + (c - '0');

                if (isOctDigit(c = ci.next()))
                    i = i * 8 + (c - '0');
                else
                    ci.previous();
            }
            else
                ci.previous();

            if (i > 0xff)
                throw new ParseException("Invalid character escape: '\\" + Integer.toOctalString(i) + "'", this);

            return (char)i;
        }
        else
        {
            switch (c)
            {
                case 'b':   return '\b';
                case 't':   return '\t';
                case 'n':   return '\n';
                case 'f':   return '\f';
                case 'r':   return '\r';
                case '"':   return '"';
                case '\'':  return '\'';
                case '\\':  return '\\';
                default:
                    throw new ParseException("Invalid character escape: '\\" + c + "'", this);
            }
        }
    }
    
    /**
     * Enum inputs are in the form: <className>.<enumValue>
     * The final '.' is assumed to be the end of the class name.  If there is only one '.' the class
     * is assumed to be a sailpoint object and that package will be appended accordingly 
     * @return
     * @throws ParseException
     */
    @SuppressWarnings("unchecked")
    private Enum parseEnumLiteral() throws ParseException 
    {
        final Enum lit;
        
        final int savedIdx = skipWS();
        
        String nextPart = parseIdentifier();
        
        if (nextPart == null) 
        {
            lit = null;
        } 
        else 
        {
            StringBuffer buf = new StringBuffer();
            int numParts = 0;
            
            while (ci.current() == '.') 
            {
                numParts++;
                buf.append(nextPart);                
                ci.next();
                
                nextPart = parseIdentifier();
                
                if (ci.current() == '.') 
                {
                    buf.append(".");
                }
            }
            
            if (numParts == 0) 
            {
                lit = null;
            } 
            else 
            {
                if (numParts == 2) 
                {
                    // Prepend the default sailpoint object package if we only had two dots in the input 
                    buf.insert(0, "sailpoint.object.");
                } 
                
                final int replaceChar = buf.lastIndexOf(".");
                
                if (replaceChar > 0) 
                {
                    buf.replace(replaceChar, replaceChar + 1, "$");
                }
                
                final String className = buf.toString();
                
                try {
                    Class clazz = Class.forName(className);
                    lit = Enum.valueOf(clazz, nextPart);
                } catch (ClassNotFoundException e) {
                    // If the class doesn't exist we will return null
                    ci.setIndex(savedIdx);
                    return null;
                }
            }
        }
        
        if (lit == null) 
        {
            ci.setIndex(savedIdx);
        }
                
        return lit;
    }


    public boolean parseNullLiteral()
    {
        return "null".equals(parseIdentifier());
    }


    /**
     * Runtime exception thrown when a parse error occurs.  The message contains
     * the text of the filter string with a '^' under the spot where the error
     * occurred.
     */
    public static class ParseException extends RuntimeException
    {
        private static final long serialVersionUID = -2530446961080675326L;

        public ParseException(String msg, Parser p)
        {
            super(msg + " - " + getParserStateString(p));
        }
    
        private static String getParserStateString(Parser p)
        {
            StringBuilder sb = new StringBuilder();
            sb.append("Index: ").append(p.getIndex()).append("\n");
            sb.append(p.getInput().replaceAll("\n", " "));
            sb.append("\n");
            for (int i=0; i<p.getIndex(); i++)
            {
                sb.append(' ');
            }
            sb.append('^');
            return sb.toString();
        }
    }
}
