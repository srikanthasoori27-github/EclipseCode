/* (c) Copyright 2012 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.connectorDependencies.unix;

import java.util.Map;

public class Command {
    boolean noOptAdded;
    private String commandString;
    private Map<String,String> expectedResult;

    public void setCommandString(String command) {
        this.commandString = command;
    }

    public String getCommandString() {
        return this.commandString;
    }

    public void setExpResult(Map<String,String> result) {
        this.expectedResult = result;
    }

    public Map<String,String> getExpResult() {
        return expectedResult;
    }

}
