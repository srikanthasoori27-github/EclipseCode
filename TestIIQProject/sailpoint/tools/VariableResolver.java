/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

public interface VariableResolver 
{

    /**
     * Resolve a variable referenced by name.
     * Return null if this variable doesn't have a meaningful binding.
     */
    public Object resolveVariable(String name);

}
