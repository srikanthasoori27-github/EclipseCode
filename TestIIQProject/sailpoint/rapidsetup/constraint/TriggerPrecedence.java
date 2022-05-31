/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */
package sailpoint.rapidsetup.constraint;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sailpoint.object.Configuration;
import sailpoint.tools.Util;

/**
 * Utility class to determine the precedence of RapidSetup business processes
 */
public class TriggerPrecedence {

    /**
     * a map of business process name mapped to its list of higher-precedence business processes.
     */
    private static Map<String,List<String>> orderedCheckFirstList = new HashMap<>();
    static {
        orderedCheckFirstList.put(Configuration.RAPIDSETUP_CONFIG_LEAVER,
                Collections.emptyList());

        orderedCheckFirstList.put(Configuration.RAPIDSETUP_CONFIG_JOINER,
                Arrays.asList(
                        Configuration.RAPIDSETUP_CONFIG_LEAVER
                )
        );

        orderedCheckFirstList.put(Configuration.RAPIDSETUP_CONFIG_MOVER,
                Arrays.asList(
                        Configuration.RAPIDSETUP_CONFIG_LEAVER,
                        Configuration.RAPIDSETUP_CONFIG_JOINER
                )
        );
    }

    /**
     * Return the list of higher-precedence business processes for the given
     * business process
     * @param businessProcess the business process to find the higher-precedence business processes for
     * @return the list of higher-precedence business processes for the given business process. Return
     * an empty list if none.
     */
    public static List<String> getOrderedCheckFirstList(String businessProcess) {
        List<String> prereqs = null;
        if (Util.isNotNullOrEmpty(businessProcess)) {
            prereqs = orderedCheckFirstList.get(businessProcess);
        }
        if (prereqs == null) {
            prereqs = Collections.emptyList();
        }
        return prereqs;
    }
}
