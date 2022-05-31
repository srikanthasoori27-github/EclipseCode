/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import java.util.ArrayList;
import java.util.List;
import java.io.Serializable;

abstract public class Projection implements Serializable
{
    public static class LeafProjection extends Projection
    {
        //To be added to on an as-needed basis. currently just have
        //property, but might want to expose more things like max, etc
        public static enum Operation
        {
            PROPERTY //selects the given property
        };
        
        private Operation _operation;
        private String _propertyName;

        public LeafProjection()
        {
        }

        public LeafProjection(Operation operation, String propertyName)
        {
            _operation    = operation;
            _propertyName = propertyName;
        }

        public Operation getOperation()
        {
            return _operation;
        }

        public String getPropertyName()
        {
            return _propertyName;
        }
    }

    public static class ListProjection extends Projection
    {
        private List<Projection> _projections = new ArrayList();

        public ListProjection()
        {
        }

        public ListProjection add(Projection projection)
        {
            _projections.add(projection);
            return this;
        }

        public List<Projection> getProjections()
        {
            return _projections;
        }
    }

    public static Projection property(String propName)
    {
        return new LeafProjection(LeafProjection.Operation.PROPERTY, propName);
    }

    public static ListProjection list(Projection... projections)
    {
        ListProjection rv = new ListProjection();
        for (int i = 0; i < projections.length; i++)
        {
            rv.add(projections[i]);
        }
        return rv;
    }
}
