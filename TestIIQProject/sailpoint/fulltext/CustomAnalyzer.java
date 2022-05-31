/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.fulltext;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.jfree.util.Log;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * This Analyzer is case insensitive and divides tokens at all non-letters.
 * @author Bernie Margolis
 */
public final class CustomAnalyzer extends ConfigurableAnalyzer {
    /**
     *  Configuration attribute that indicates which characters to use as delimiters.
     *  This attribute's value must be a String or a List of Strings:
     *  <ul>
     *    <li> A single String contains all delimiter characters</li>
     *    <li> Each String in a List of Strings contains a single delimiter character</li>
     *  </ul>
     */
    public static final String ATTR_DELIMITERS = "delimiters";

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer;
        try {
            Set<Integer> parsedDelimiters = parseDelimiters();
            tokenizer = new CustomTokenizer(parsedDelimiters);
        } catch (GeneralException e) {
            Log.warn("A plain whitespace tokenizer is being used because the CustomAnalyzer was misconfigured", e);
            tokenizer = new WhitespaceTokenizer();
        }
        TokenStream result = new LowerCaseFilter(tokenizer);
        return new TokenStreamComponents(tokenizer, result);
    }

    private Set<Integer> parseDelimiters() throws GeneralException {
        Set<Integer> parsedDelimiters  = new HashSet<Integer>();
        List<String> delimitersList = config.getStringList(ATTR_DELIMITERS);

        if (!Util.isEmpty(delimitersList) && delimitersList.size() == 1) {
            // If there is a single String, assume that all the delimiters have been crammed into
            // it, and iterate over the String accordingly
            String delimitersString = delimitersList.get(0);
            for (int i = 0; i < delimitersString.length(); ++i) {
                parsedDelimiters.add(Character.codePointAt(delimitersString, i));
            }
        } else if (!Util.isEmpty(delimitersList)) {
            // If there is a List of Strings, assume that each element contains a delimiter
            for (String delimiter : delimitersList) {
                if (delimiter.length() == 1) {
                    parsedDelimiters.add(Character.codePointAt(delimiter, 0));
                } else {
                    Log.warn("The ''delimiters'' configuration attribute is misconfigured.  When a List is used, each element must be a single-character String.  The value, ''" + delimiter + "'', is being ignored.");
                }
            }
        }
        // else if no delimiters are specified, just return an empty list and 
        // fall back to the same behavior as the WhitespaceAnalyzer

        return parsedDelimiters;
    }
}
