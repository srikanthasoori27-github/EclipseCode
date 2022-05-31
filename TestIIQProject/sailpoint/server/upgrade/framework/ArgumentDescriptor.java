package sailpoint.server.upgrade.framework;

import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;

/**
 * Describes an argument required by an upgrader.
 * @author Jeff Upton
 */
public abstract class ArgumentDescriptor
{    
    private String _name;
    private String _description;

    public ArgumentDescriptor(String name, String description)
    {
        _name = name;
        _description = description;
    }

    public String getName()
    {
        return _name;
    }

    public String getDescription()
    {
        return _description;
    }
    
    /**
     * Validates the specified value using this descriptor's validator if one exists.
     * If validation fails, the reason is returned as a String.
     * 
     * @param value The value to validate.
     * 
     * @return null if validation is successfull, the reason for validation failure otherwise.
     */
    public String validate(String value) 
    {
        return null;
    }

    /**
     * Subclasses should override this method in order to specify a default value
     * that the argument should have when one is not specified on the command line.
     * Returning a non-null value means that the uprade framework will not prompt the user for
     * a value.
     */
    public String getDefaultValue(SailPointContext context)
        throws GeneralException
    {
        return null;
    }
}
