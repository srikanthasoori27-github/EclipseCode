/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.tools;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Rule;

/**
 * Project: identityiq
 * Author: michael.hide
 * Created: 3/6/13 1:24 PM
 */
public class ScriptPreParser {

    private static Log _log = LogFactory.getLog(ScriptPreParser.class);

    private final String frontToken = "sailpoint.tools.MapUtil.get(";
    private final String commaToken = ", \"";
    private final String backToken = "\")";
    private final String dotToken = ".";
    private final char escapeToken = '/';
    private Mode mode;
    private StringBuffer payloadBuffer, out;
    private CharacterIterator ci;
    private boolean trailingSemi = false;

    /**
     * @param input The path of attributes on the base object to get.
     *              The base object will be parsed out as the word before the first
     *              dot '.' in the input.
     * @return The parsed source with tokens converted to MapUtil.get calls.
     */
    public String preParse(String input) {
        return preParse(null, input);
    }

    /**
     * The logic for this parser is as follows:
     * Step through each char in the string and look for a dollar sign followed by a paren '$(' to indicate the 
     * start of the payload (i.e. the 'interesting' stuff).  Keep stepping through until we find either a non-letter 
     * or non-digit, or an end of file indicator (CharacterIterator.DONE) and note the start and end indexes.  Pass those
     * indices to the formatter to wrap with the call to MapUtil.get.
     *
     * If we encounter a quote (") then ignore everything until we reach the next quote.  This is the same behavior
     * whether the quote is before or after the start sequence, so we effectively ignore any payload contained within
     * a string, and ignore any non-letter or non-digit contained in a string in the payload.
     * If we encounter a bracket ([) inside the payload, ignore everything until the closing bracket. (Nested
     * brackets ARE supported, though I'm not sure what the use case would be.)
     *
     * The formatter will take the payload and wrap it in a call to MapUtil.get() with either a reference to the
     * basePath, or will parse out the first 'word' from the payload and use that as the first parameter for
     * the function. (e.g. "identityModel.foo.bar" will become "MapUtil.get(identityModel, \"foo.bar\")" )
     *
     * If we encounter a new line char, a semi-colon, or a closing parenthesis within the payload, make note and
     * append them after the call to MapUtil.get in order to preserve formatting. (e.g. "identityModel.foo.bar)"
     * will become "MapUtil.get(identityModel, \"foo.bar\"))".  The use case for that is if we have
     * MyObject.someFunction($(identityModel.someAttr)) it should evaluate to
     * MyObject.someFunction(MapUtil.get(identityModel, "someAttr")) instead of dropping the trailing paren.)
     *
     *
     * @param basePath The base object to use for MapUtil.get(). If empty, will parse out the value from input.
     * @param input The path of attributes on the base object to get.
     * @return The parsed source with tokens converted to MapUtil.get calls.
     */
    public String preParse(String basePath, String input) {
        _log.debug("basePath: " + basePath);
        _log.debug("Initial source: " + input);

        input = StringUtils.trimToEmpty(input);

        // no need to attempt any work if the input is empty
        if (input.isEmpty()) {
            return "";
        }

        // THIS IS A HACK - the algorithm below truncates the end of the script. Add a comment
        // the the end of the script so truncation will not matter.  TODO - the algorithm is
        // confusing and fragile. open a ticket to rework in release 8.2
        input += ("\n//__END OF SCRIPT__    ");

        // Initialize global variables
        out = new StringBuffer();
        ci = new StringCharacterIterator(input);
        mode = Mode.TEXT;

        // Initialize local variables
        int startIdx = 0;
        int endIdx = input.length();
        int len = endIdx; //endIdx gets modified, so use len for the loop.
        int bracketCount = 0; // keep track of brackets inside the payload.
        trailingSemi = false; // keep track of any pre-existing semi-colons near the payload.
        char c = ci.first();

        // Keep track of start and end payload markers.
        // Increment for each $ and decrement when we find the end of the payload.
        int payloadCnt = 0;

        // Loop through each char in the string and switch states based on what we find.
        for (int i = 0; i <= len; i++) {
            // This happens if the parsed token is at the end of the source.
            // We need to hit the end to KNOW we're done, so jump back and
            // catch the END switch.
            if(c == CharacterIterator.DONE && payloadCnt > 0) {
                ci.previous();
                endIdx = ci.getIndex() - 1;
                payloadCnt--;
                mode = Mode.END;
            }
            else if(c == CharacterIterator.DONE && payloadCnt <= 0) {
                ci.previous();
            }

            switch (mode) {
                case TEXT: {
                    //System.out.println("TEXT : " + c);
                    if (c == '"') {
                        out.append(c);
                        mode = Mode.TEXT_QUOTE;
                    }
                    else if (c == '$') {
                        c = ci.next(); // look ahead
                        ci.previous(); // go back to where we just were
                        if(c == '(') {
                            startIdx = ci.getIndex();
                            payloadCnt++;
                            mode = Mode.START;
                        }
                        else {
                            out.append(ci.current());
                        }
                    }
                    else {
                        if(c != CharacterIterator.DONE) {
                            out.append(c);
                        }
                    }
                    break;
                }
                case START: {
                    //System.out.println("START : " + c);
                    if (c == '(') {
                        mode = Mode.START_PAREN;
                    }
                    else if (c == '[') {
                        bracketCount++;
                        mode = Mode.START_BRACKET;
                    }
                    else if (c == '"') {
                        mode = Mode.START_QUOTE;
                    }
                    else if (c != '.' && !Character.isLetterOrDigit(c)) {
                        if(c == ';') {
                            trailingSemi = true;
                        }
                        // If we're at the end of the line inside the payload,
                        // jump back a couple steps to catch the END switch
                        c = ci.next(); // look ahead
                        //System.out.println("Look ahead : " + c + " = " + Character.getNumericValue(c));
                        ci.previous(); // go back to where we just were
                        if(c == CharacterIterator.DONE) {
                            ci.previous(); // go back one more
                            i--;
                        }
                        endIdx = ci.getIndex() - 1;
                        payloadCnt--;
                        mode = Mode.END;
                    }
                    break;
                }
                case START_PAREN: {
                    //System.out.println("START_PAREN : " + c);
                    if (c == ')' || c == CharacterIterator.DONE) {
                        endIdx = ci.getIndex() - 1;
                        payloadCnt--;
                        mode = Mode.END;
                    }
                    break;
                }
                case START_QUOTE: {
                    //System.out.println("START_QUOTE : " + c);
                    if (c == '"') {
                        mode = Mode.START;
                    }
                    else if (c == CharacterIterator.DONE) {
                        endIdx = ci.getIndex() - 1;
                        payloadCnt--;
                        mode = Mode.END;
                    }
                    break;
                }
                case TEXT_QUOTE: {
                    //System.out.println("TEXT_QUOTE : " + c);
                    if(c != CharacterIterator.DONE) {
                        out.append(c);
                    }
                    if (c == '"') {
                        mode = Mode.TEXT;
                    }
                    break;
                }
                case START_BRACKET: {
                    //System.out.println("START_BRACKET : " + c);
                    // Catch nested brackets
                    if (c == '[') {
                        bracketCount++;
                    }
                    else if (c == ']') {
                        bracketCount--;
                    }
                    if (bracketCount == 0) {
                        payloadCnt++;
                        mode = Mode.START; // Go back to START mode
                    }
                    break;
                }
                case END: {
                    //System.out.println("END : " + c);
                    if(c == ';') {
                        trailingSemi = true;
                    }
                    ci.setIndex(startIdx);

                    // this will handle null or empty basePath
                    formatPayload(ci, out, basePath, startIdx, endIdx);
                    mode = Mode.TEXT;
                    break;
                }
            }
            c = ci.next();
        }

        // remove HACK added above
        String parsedScript = out.substring(0, out.lastIndexOf("//__END OF")).trim();

        _log.debug("Processed source: " + parsedScript);

        return parsedScript;
    }

    /**
     * Takes the payload from preParser and wraps it with a call to MapUtil.get().  This will parse out
     * the first param for MapUtil.get if basePath is null and there is a dot (.) in the string, otherwise
     * it will just return the payload.
     *
     * @param ci The source input
     * @param out Buffer for the parsed source output
     * @param basePath Name of the object map to get attributes from
     * @param startIdx Starting index of payload in ci
     * @param endIdx Ending index of the payload in ci
     */
    private void formatPayload(CharacterIterator ci, StringBuffer out, String basePath, int startIdx, int endIdx) {
        payloadBuffer = new StringBuffer();
        char n;
        boolean closingParam = false;
        boolean openingParam = false;
        boolean trailingSpace = false;
        boolean escapeToRoot = false;
        int len = (endIdx - startIdx);
        int newLineCnt = 0; // don't want any newlines inside of the params, so count 'em up and tack 'em on at the end.

        // protect against null and trim it up
        basePath = (basePath == null ? "" : basePath.trim());

        // loop through the payload and filter out unwanted chars while appending to a buffer
        for (int j = 0; j <= len; j++) {
            n = ci.next();
            if (j == 0 && n == '(') {
                openingParam = true;
            }

            if(j <= 1 && n == escapeToken) {
                escapeToRoot = true;
            }

            if (n == '\n') {
                newLineCnt++;
            }
            else if (n != '(' && n != ')' && n != ';' && n != escapeToken) { // don't include the formatting parenthesis or trailing semi-colon.
                if(n == '"'){
                    payloadBuffer.append("\\" + n);
                }
                else {
                    payloadBuffer.append(n);
                }
            }
            else if (!openingParam && len == j && n == ')') {
                closingParam = true;
            }
        }

        // strip off the trailing space if there is one (should only ever be one)
        if (payloadBuffer.charAt(payloadBuffer.length() - 1) == ' ') {
            trailingSpace = true;
            payloadBuffer.deleteCharAt(payloadBuffer.length() - 1);
        }

        String pb = payloadBuffer.toString();
        // If there is a basePath, use that for the base object (i.e. the first param of MapUtil.get)
        if(StringUtils.isNotEmpty(basePath)) {
            // If the payload starts with a slash (/) we want to jump up to the root of the basePath.
            // For example: if basePath is identityModel.links[AD] and the payload is $(/firstname), we
            // want to end up with identityModel.firstname and not identityModel.links[AD].firstname.
            if(escapeToRoot && basePath.contains(dotToken)) {
                basePath = basePath.substring(0, basePath.indexOf(dotToken));
            }
            // If the basePath contains a dot, we need to pull out the root and move the rest to the right side.
            else if(basePath.contains(dotToken)) {
                String sub = basePath.substring(basePath.indexOf(dotToken) + 1, basePath.length());
                basePath = basePath.substring(0, basePath.indexOf(dotToken));
                // Check if the right side already starts with the basePath
                if(!pb.startsWith(basePath + dotToken + sub)) {
                    pb = sub + dotToken + pb;
                }
            }
            pb = frontToken + basePath + commaToken + stripBasePath(pb.replace("\"", "\\\""), basePath) + backToken;
        }
        // otherwise parse out the first 'word' as the base object and use the rest as the second param of MapUtil.get
        else if (pb.contains(dotToken)) {
            pb = frontToken + pb.substring(0, pb.indexOf(dotToken)) + commaToken + pb.substring(pb.indexOf(dotToken) + 1, pb.length()) + backToken;
        }
        else {
            // If we get this far with no context and no parsable base object, assume
            // this is NOT an interesting string and put it back the way it was.
            if(openingParam) {
                // reinstate params
                pb = "$(" + pb + ")";
            }
            else {
                pb = "$" + pb;
            }
            // Write a warning as this situation shouldn't happen under normal circumstances,
            // and will likely result in an error down the line.
            _log.warn(Rule.MODEL_BASE_PATH + " is null, but we were expecting a value. Leaving payload as is: " + pb);
        }

        // If we encounter something like MyObject.someFunction($identityModel.someAttr), we need
        // to put the closing paren back in place instead of assuming it's part of the payload.
        if (closingParam) {
            pb += ')';
        }

        // If there was an existing semi-colon, it should be appended instead of included in the payload.
        if(trailingSemi) {
            pb += ';';
            if(openingParam) {
                // Skip the next char because we already included it here.
                // But only if we found parens in the payload.
                ci.next();
            }
            trailingSemi = false;
        }

        // If there was a trailing space, replace it to keep the same formatting.
        if(trailingSpace) {
            pb += ' ';
        }

        // If there were any new lines in the payload, move them to the end.
        for (int nl = 0; nl < newLineCnt; nl++) {
            pb += "\n";
        }

        out.append(pb);
    }

    /**
     * If the basePath is the same as the first word of the payload, we can assume
     * that they are referencing the same thing.  So make sure we don't double up
     * basePaths and just return the interesting part.
     *
     * @param args
     * @param basePath
     * @return the interesting part of the payload
     */
    private String stripBasePath(String args, String basePath) {
        if(args.contains(dotToken)) {
            if(args.substring(0, args.indexOf(dotToken)).equals(basePath)) {
                return args.substring(args.indexOf(dotToken) + 1, args.length());
            }
        }
        return args;
    }

    private enum Mode {
        TEXT,           // The usual mode stepping through the source
        TEXT_QUOTE,     // For quoted strings in the source (outside of the payload)
        START,          // For the start of the payload
        START_PAREN,    // For an opening parenthesis inside the payload
        START_BRACKET,  // For an opening bracket inside the payload
        START_QUOTE,    // For an opening quote inside the payload
        END             // For the end of the payload
    }
}
