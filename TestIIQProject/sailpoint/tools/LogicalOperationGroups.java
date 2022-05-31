/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import java.util.ArrayList;
import java.util.List;

import sailpoint.object.Filter;

public class LogicalOperationGroups {
    // static operation lists only need to be generated once
    public static List<Filter.LogicalOperation> MultiValuedOps;
    public static List<Filter.LogicalOperation> NonLikeOps;
    public static List<Filter.LogicalOperation> BooleanOps;
    public static List<Filter.LogicalOperation> PermissionOps;
    public static List<Filter.LogicalOperation> NumericalOps;
    public static List<Filter.LogicalOperation> StringOps;
    public static List<Filter.LogicalOperation> StandardOps;
    public static List<Filter.LogicalOperation> EqualOp;
    
    static {
        MultiValuedOps = new ArrayList<Filter.LogicalOperation>();
        MultiValuedOps.add(Filter.LogicalOperation.CONTAINS_ALL);
        MultiValuedOps.add(Filter.LogicalOperation.ISNULL);
        MultiValuedOps.add(Filter.LogicalOperation.NOTNULL);
        MultiValuedOps.add(Filter.LogicalOperation.IN);

        NonLikeOps = new ArrayList<Filter.LogicalOperation>();
        NonLikeOps.add(Filter.LogicalOperation.EQ);
        NonLikeOps.add(Filter.LogicalOperation.GE);
        NonLikeOps.add(Filter.LogicalOperation.GT);
        NonLikeOps.add(Filter.LogicalOperation.IN);
        NonLikeOps.add(Filter.LogicalOperation.ISNULL);
        NonLikeOps.add(Filter.LogicalOperation.LE);
        NonLikeOps.add(Filter.LogicalOperation.LT);
        NonLikeOps.add(Filter.LogicalOperation.NE);
        NonLikeOps.add(Filter.LogicalOperation.NOTNULL);

        BooleanOps = new ArrayList<Filter.LogicalOperation>();
        BooleanOps.add(Filter.LogicalOperation.EQ);
        BooleanOps.add(Filter.LogicalOperation.NE);
        BooleanOps.add(Filter.LogicalOperation.ISNULL);
        BooleanOps.add(Filter.LogicalOperation.LIKE);
        BooleanOps.add(Filter.LogicalOperation.NOTNULL);
        
        PermissionOps = new ArrayList<Filter.LogicalOperation>();
        PermissionOps.add(Filter.LogicalOperation.EQ);
        PermissionOps.add(Filter.LogicalOperation.IN);
        PermissionOps.add(Filter.LogicalOperation.ISNULL);
        PermissionOps.add(Filter.LogicalOperation.NE);
        PermissionOps.add(Filter.LogicalOperation.NOTNULL);

        NumericalOps = new ArrayList<Filter.LogicalOperation>();
        NumericalOps.add(Filter.LogicalOperation.EQ);
        NumericalOps.add(Filter.LogicalOperation.GE);
        NumericalOps.add(Filter.LogicalOperation.GT);
        NumericalOps.add(Filter.LogicalOperation.LT);
        NumericalOps.add(Filter.LogicalOperation.LE);
        NumericalOps.add(Filter.LogicalOperation.NE);
        NumericalOps.add(Filter.LogicalOperation.ISNULL);
        NumericalOps.add(Filter.LogicalOperation.NOTNULL);

        StringOps = new ArrayList<Filter.LogicalOperation>();
        StringOps.add(Filter.LogicalOperation.EQ);
        StringOps.add(Filter.LogicalOperation.IN);
        StringOps.add(Filter.LogicalOperation.ISNULL);
        StringOps.add(Filter.LogicalOperation.LIKE);
        StringOps.add(Filter.LogicalOperation.NE);
        StringOps.add(Filter.LogicalOperation.NOTNULL);
        
        StandardOps = new ArrayList<Filter.LogicalOperation>();
        StandardOps.add(Filter.LogicalOperation.EQ);
        StandardOps.add(Filter.LogicalOperation.GE);
        StandardOps.add(Filter.LogicalOperation.GT);
        StandardOps.add(Filter.LogicalOperation.IN);
        StandardOps.add(Filter.LogicalOperation.ISNULL);
        StandardOps.add(Filter.LogicalOperation.LIKE);
        StandardOps.add(Filter.LogicalOperation.LT);
        StandardOps.add(Filter.LogicalOperation.NE);
        StandardOps.add(Filter.LogicalOperation.NOTNULL);
        
        EqualOp = new ArrayList<Filter.LogicalOperation>();
        EqualOp.add(Filter.LogicalOperation.EQ);
    }
    
    private LogicalOperationGroups(){}
}
