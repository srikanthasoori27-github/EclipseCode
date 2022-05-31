/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rapidsetup.constraint.implicits;

import sailpoint.object.Configuration;

public class ImplicitCheckFactory {

    /**
     * Get the ImplicitCheck implementation for the given process.
     * @param process the process for which to get the ImplciitCheck implementation
     * @return the ImplicitCheck implementation for the given process
     */
    public static ImplicitCheck getImplicitCheck(String process) {

        // default to the noop implementation
        ImplicitCheck implicitCheck = new NoOpImplicitCheck();

        if (Configuration.RAPIDSETUP_CONFIG_JOINER.equals(process)) {
            implicitCheck = new JoinerImplicitCheck();
        }
        else if (Configuration.RAPIDSETUP_CONFIG_MOVER.equals(process)) {
            implicitCheck = new MoverImplicitCheck();
        }
        else if (Configuration.RAPIDSETUP_CONFIG_LEAVER.equals(process)) {
            implicitCheck = new LeaverImplicitCheck();
        }
        return implicitCheck;
    }

}
