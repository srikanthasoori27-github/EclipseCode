/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.fulltext;

import java.util.Set;

import org.apache.lucene.analysis.util.CharTokenizer;

/**
 * This Tokenizer uses the specified set of delimiters
 * in addition to breaking at whitespace.  
 *
 * @author Bernie Margolis
 *
 */
public class CustomTokenizer extends CharTokenizer {
    /**
     * Set of codePoints that should be excluded from the tokens
     */
    Set<Integer> delimiters;

    /**
     * Constructor for the CustomTokenizer
     * @param delimiters Set of codePoints to exclude from the tokens.
     *        See the {@link java.lang.Character} class for details about codePoints 
     */
    public CustomTokenizer(Set<Integer> delimiters) {
        super();
        this.delimiters = delimiters;
    }

    @Override
    protected boolean isTokenChar(int c) {
        return !Character.isWhitespace(c) && !delimiters.contains(c);
    }
}
