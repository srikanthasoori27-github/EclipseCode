/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.util.ArrayList;
import java.util.List;

/**
 * The JSFOutcomeParser is a utility that can read JSF outcomes that may have query or hash segments
 * on them, and split these into their components.
 */
public class JSFOutcomeParser {

    /**
     * Split the given outcome into segments by the special characters '#' and '?'.  The resulting
     * list will contain the parsed outcome in the first element.  The remaining elements will
     * contain any special character segments, including the special character, in the order that
     * they are found in the original outcome.
     *
     * @param  outcome  The JSF outcome.
     *
     * @return The parsed outcome, or an empty list if the outcome is null.
     */
    public static List<String> parseOutcome(String outcome) {
        ArrayList<String> chunks = new ArrayList<String>();

        if (null != outcome) {
            int hashIdx = outcome.indexOf('#');
            int questionIdx = outcome.indexOf('?');
    
            int firstSpecialIdx = questionIdx;
            if ((hashIdx > -1) && ((questionIdx == -1) || (hashIdx < questionIdx))) {
                firstSpecialIdx = hashIdx;
            }
    
            String parsedOutcome =
                (firstSpecialIdx > -1) ? outcome.substring(0, firstSpecialIdx) : outcome;
            String hash = grabChunk(hashIdx, questionIdx, outcome);
            String query = grabChunk(questionIdx, hashIdx, outcome);
    
            // Add the fragments in the appropriate order.
            addChunk(chunks, hash, hashIdx, questionIdx);
            addChunk(chunks, query, questionIdx, hashIdx);
    
            // Stick the outcome at the beginning.
            chunks.add(0, parsedOutcome);
        }

        return chunks;
    }

    /**
     * Grab the special character segment starting at the given index and ending either at the
     * otherSpecialIdx (if it is after the startIdx) or the end of the string.
     */
    private static String grabChunk(int startIdx, int otherSpecialIdx, String outcome) {
        String chunk = null;
        if (startIdx > -1) {
            if (otherSpecialIdx > startIdx) {
                chunk = outcome.substring(startIdx, otherSpecialIdx);
            }
            else {
                chunk = outcome.substring(startIdx);
            }
        }
        return chunk;
    }

    /**
     * Add the given chunk to the chunks list - either at the beginning if it comes before the
     * otherIdx or at the end of the list.
     */
    private static void addChunk(List<String> chunks, String chunk, int thisIdx, int otherIdx) {
        if (null != chunk) {
            if ((-1 != otherIdx) && (thisIdx < otherIdx)) {
                chunks.add(0, chunk);
            }
            else {
                chunks.add(chunk);
            }
        }
    }
}
