package sailpoint.build;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Helper class to parse the message properties file
 */
public class MessagePropertiesFileParser {

    private static final String MOBILE_MESSAGE_PREFIX = "mobileMessage";
    private static int MOBILE_MESSAGE_PREFIX_LENGTH = MOBILE_MESSAGE_PREFIX.length();
    private static final String MOBILE_MESSAGE_START = ":START";
    private static final String MOBILE_MESSAGE_END = ":END";

    /**
     * Get all the property keys from the properties file. Keys will have periods replaced with underscores.
     * @param file File path
     * @return List of sorted property keys
     * @throws IOException
     */
    public static List<String> getPropertyKeys(String file) throws IOException {

        Properties messages = new Properties();
        FileInputStream in = new FileInputStream(file);
        messages.load(in);
        in.close();

        List<String> keys = new ArrayList<String>();
        for (Object key : messages.keySet()) {
            if (key != null){
                keys.add(makeMessageKey(key));
            }
        }

        Collections.sort(keys);

        return keys;

    }

    /**
     * Get the property keys for properties specified as mobile messages in file comments. 
     *
     * #mobileMessage  
     * Single comment indicates the next message should be a mobile message
     *
     * Example:
     *
     * #mobileMessage
     * ui_something=Something
     *
     *
     * #mobileMessage:START, #mobileMessage:END
     * All messages between the :START and :END block should be considered mobile messages
     *
     * Example:
     *
     * #mobileMessage:START
     * ui_this=This
     * ui_that=That
     * #mobileMessage:END
     *
     * @param file Properties file path
     * @return Set of property keys for mobile messages. Not all entries are guaranteed to be "real" message keys, 
     * so do not use as authoritative list. This is because we do not do all the locale and string comparison that the 
     * Properties class does when loading. Instead, use this as a comparative set, any key that IS real and is in this
     * list should be a mobile message. 
     * @throws IOException
     */
    public static Set<String> getMobilePropertyKeys(String file) throws IOException {
        FileReader fileReader = new FileReader(file);
        BufferedReader bufferedReader = new BufferedReader(fileReader);

        boolean mobileMessages = false;
        boolean singleLine = false;
        Set<String> mobileMessageKeys = new HashSet<String>();

        try {
            String fileLine = bufferedReader.readLine();
            while (fileLine != null) {
                if (fileLine.startsWith("#")) {
                    int mobileIndex = fileLine.indexOf(MOBILE_MESSAGE_PREFIX, 1);
                    if (mobileIndex > 0) {
                        int restOfIndex = mobileIndex + MOBILE_MESSAGE_PREFIX_LENGTH;

                        // If we are in a mobile message block, check if this indicates the end
                        if (mobileMessages && fileLine.indexOf(MOBILE_MESSAGE_END, restOfIndex) != -1) {
                            mobileMessages = false;
                        } else if (!mobileMessages) {
                            mobileMessages = true;

                            // If we do not have the :START notation, this is a single line mobile message
                            singleLine = (fileLine.indexOf(MOBILE_MESSAGE_START, restOfIndex) == -1);
                        }
                    }
                } else {
                    if (mobileMessages) {
                        // Only presume a message key if there is an = sign . This is not always strictly
                        // correct but good enough. 
                        int indexOfEquals = fileLine.indexOf("=");
                        if (indexOfEquals > 0) {
                            mobileMessageKeys.add(makeMessageKey(fileLine.substring(0, indexOfEquals)));
                            // If we were in single line mode, reset flag
                            if (singleLine) {
                                mobileMessages = false;
                            }
                        }
                    }
                }

                fileLine = bufferedReader.readLine();
            }

        } finally {
            fileReader.close();
        }

        return mobileMessageKeys;
    }
    
    private static String makeMessageKey(Object key) {
        return (key != null) ? (key.toString().replace(".", "_")) : null;
    }
}