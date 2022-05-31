/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Utilities for parsing a string containing variable references
 * of the form "$(name)" and building a string containing the expanded
 * values of those variables.
 *
 * This also handles nested references such as:
 *
 *     $(foo$(bar)baz)
 *
 * And it will ignore parenthesized sections that don't begin
 * with $ which is important for workflow scriptlets:
 *
 *  'Review violation of policy $(script:approvalObject.getPolicyName();)'
 *   
 */
public class VariableExpander 
{

    //////////////////////////////////////////////////////////////////////
    //
    // Concrete Map Resolvers
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * A class that implements the VariableResolver interface, and
     * looks up variables in a Map.  
     */
    private static class MapResolver implements VariableResolver 
    {
        private Map _map;

        public MapResolver(Map map) 
        {
            _map = map;
        }

        public Object resolveVariable(String name) 
        {
            Object value = null;

            if (_map != null)
            {
                value = _map.get(name);
            }

            return value;
        }
    }

    /**
     * Just like MapResolver class above but this
     * will support a.b.c type paths
     *
     */
    public static class PathMapResolver implements VariableResolver {

        private Map<String, Object> pathMap;
        
        public PathMapResolver(Map<String, Object> pathMap) {
            
            this.pathMap = pathMap;
        }
        
        public Object resolveVariable(String name) {

            try {
                return MapUtil.get(pathMap, name);
            } catch (GeneralException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Token inner class used for parsing
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * A parsed token from a string containing variables.
     */
    public static class Token {
        
        /**
         * The type of token.
         */
        public static enum Type {
            Literal,
            Reference
        }
        
        private Type type;
        private String value;
        
        /**
         * Constructor.
         */
        public Token(Type type, String value) {
            this.type = type;
            this.value = value;
        }
        
        public Type getType() {
            return this.type;
        }
        
        public String getValue() {
            return this.value;
        }
    }
    

    //////////////////////////////////////////////////////////////////////
    //
    // Expansion
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Process a string which may contain references to variables
     * and substitute the values of those variables.  Variable
     * values are supplied in a Map.
     */
    public static String expand(String src, Map variables) 
    {
        return expand(src, new MapResolver(variables));
    }

    /**
     * Process a string which may contain references to variables
     * and substitute the values of those variables, which can be
     * resolved using the given resolver.
     */
    public static String expand(String src, VariableResolver resolver) {

        if (src != null && src.indexOf("$(") >= 0) {
            
            StringBuilder b = new StringBuilder();

            List<Token> tokens = parse(src);
            for (Token token : tokens) {
                switch (token.getType()) {
                case Literal:
                    b.append(token.getValue());
                    break;
                case Reference:
                    // evaluate nested references
                    String ref = expand(token.getValue(), resolver);

                    // resolve it
                    Object resolved = resolver.resolveVariable(ref);
                    if (resolved != null) {
                        b.append(resolved.toString());
                    }
                    break;
                default:
                    throw new RuntimeException("Unknown type: " + token.getType());
                }
            }

            src = b.toString();
        }

        return src;
    }
    
    
    //////////////////////////////////////////////////////////////////////
    //
    // Parsing
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Parse the given string into tokens.
     */
    public static List<Token> parse(String src) {

        List<Token> tokens = new ArrayList<Token>();
        
        if (src != null) {

            StringBuilder b = new StringBuilder();
            int len = src.length();

            for (int i = 0 ; i < len ; i++) {

                char c = src.charAt(i);

                // If we have a non-special character and we're not at the
                // end, just collect the character.
                if (c != '$' || ((i+1) >= len) || src.charAt(i+1) != '(') {
                    b.append(c);
                }
                else {
                    // we're at $( find the ending paren
                    int end = findEnd(src, i, len);
                    if (end > i) {
                        // Flush the current literal.
                        flushLiteral(b, tokens);

                        // extract the reference and add a token with it.
                        String ref = src.substring(i+2, end);
                        tokens.add(new Token(Token.Type.Reference, ref));
                        i = end;
                    }
                    else {
                        // unbalanced parenthesis
                        // We can either just dump what remains or try
                        // to assume there is an implicit ) at the end.
                        // Since this is an error I like not doing any
                        // more mutations on it so the developer can
                        // see what went wrong.
                        b.append(src.substring(i));
                        i = len;
                        flushLiteral(b, tokens);
                    }
                }
            }

            // Make sure we have flushed everything.
            flushLiteral(b, tokens);
        }

        return tokens;
    }

    /**
     * Flush the literal in the given builder (if non-empty) into the tokens
     * list.  This also clears the builder.
     * @param b
     * @param tokens
     */
    private static void flushLiteral(StringBuilder b, List<Token> tokens) {

        // Only add a token if we collected stuff.
        if (b.length() > 0) {
            // Add the literal token.
            tokens.add(new Token(Token.Type.Literal, b.toString()));
            
            // Clear the buffer.
            b.setLength(0);
        }
    }
    
    /**
     * Intenral helper to look for a closing paren.
     * start is the index of the $ in the initial $( sequence.
     * Note that there is no escaping in here, may want that but
     * you really shouldn't be getting too clever inside a reference.
     * If we escape at all it should be the initial $ so we don't
     * think it's a balanced reference.
     */
    private static int findEnd(String src, int start, int len) {
        int end = -1;
        int level = 0;
        for (int i = start ; i < len ; i++) {
            char c = src.charAt(i);
            if (c == '(')
                level++;
            else if (c == ')') {
                level--;
                if (level == 0) {
                    end = i;
                    break;
                }
            }
        }
        return end;
    }
}
