/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import sailpoint.object.Filter;
import sailpoint.object.Filter.LeafFilter;

public class SelectFilter
{
    private String _name;
    private LeafFilter.LogicalOperation _comparison;
    private String _value;


    public SelectFilter()
    {
        reset();
    }

    public void reset()
    {
        _name        = "";
        _comparison  = LeafFilter.LogicalOperation.EQ;
        _value       = "";
    }

    public String getComparison()
    {
        return _comparison.name();
    }

    public void setComparison(String comparison)
    {
        _comparison = Enum.valueOf(LeafFilter.LogicalOperation.class,
                                   comparison);
    }


    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        if (name == null)
        {
            name = "";
        }
        name  = name.trim();
        _name = name;
    }

    // ========================================

    public String getValue()
    {
        return _value;
    }

    public void setValue(String value)
    {
        if (value == null)
        {
            value = "";
        }
        value  = value.trim();
        _value = value;
    }

    // ========================================

    public Filter getFilter()
    {
        if (_name.length() == 0 ||
            _value.length() == 0)
        {
            return null;
        }
        return new LeafFilter(_comparison, _name, _value);
    }
}
