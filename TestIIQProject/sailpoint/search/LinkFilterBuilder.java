/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.search;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Filter;
import sailpoint.object.Filter.CompositeFilter;
import sailpoint.object.Filter.FilterVisitor;
import sailpoint.object.Filter.LeafFilter;
import sailpoint.object.SearchInputDefinition.PropertyType;
import sailpoint.tools.GeneralException;

public class LinkFilterBuilder extends BaseFilterBuilder {
    
    private static class LinkFilterConverter implements FilterVisitor {

        public void visitAnd(CompositeFilter filter) throws GeneralException {
            // Breakdown composites and visit children
            for (Filter child : filter.getChildren()) {
                child.accept(this);
            }
        }

        public void visitOr(CompositeFilter filter) throws GeneralException {
            // Breakdown composites and visit children
            for (Filter child : filter.getChildren()) {
                child.accept(this);
            }            
        }

        public void visitNot(CompositeFilter filter) throws GeneralException {
            // Breakdown composites and visit children
            for (Filter child : filter.getChildren()) {
                child.accept(this);
            }        
        }

        public void visitEQ(LeafFilter filter) throws GeneralException {
            // if the object is boolean, recast as String
            if (filter.getValue() instanceof Boolean) {
                castValueToString(filter);
            }
            
        }

        public void visitNE(LeafFilter filter) throws GeneralException {
            // if the object is boolean, recast as String
            if (filter.getValue() instanceof Boolean) {
                castValueToString(filter);
            }            
        }

        public void visitLT(LeafFilter filter) throws GeneralException {
            // if the object is boolean, recast as String
            if (filter.getValue() instanceof Boolean) {
                castValueToString(filter);
            }            
        }

        public void visitGT(LeafFilter filter) throws GeneralException {
            // if the object is boolean, recast as String
            if (filter.getValue() instanceof Boolean) {
                castValueToString(filter);
            }            
        }

        public void visitLE(LeafFilter filter) throws GeneralException {
            // if the object is boolean, recast as String
            if (filter.getValue() instanceof Boolean) {
                castValueToString(filter);
            }            
        }

        public void visitGE(LeafFilter filter) throws GeneralException {
            // if the object is boolean, recast as String
            if (filter.getValue() instanceof Boolean) {
                castValueToString(filter);
            }            
        }

        public void visitIn(LeafFilter filter) throws GeneralException {
            // if the object is boolean, recast as String
            if (filter.getValue() instanceof Boolean) {
                castValueToString(filter);
            }
            
        }

        public void visitContainsAll(LeafFilter filter) throws GeneralException {
            // if the object is boolean, recast as String
            if (filter.getValue() instanceof Boolean) {
                castValueToString(filter);
            }
            
        }

        public void visitLike(LeafFilter filter) throws GeneralException {
            // if the object is boolean, recast as String
            if (filter.getValue() instanceof Boolean) {
                castValueToString(filter);
            }
            
        }

        public void visitNotNull(LeafFilter filter) throws GeneralException {
            // if the object is boolean, recast as String
            if (filter.getValue() instanceof Boolean) {
                castValueToString(filter);
            }
            
        }

        public void visitIsNull(LeafFilter filter) throws GeneralException {
            // if the object is boolean, recast as String
            if (filter.getValue() instanceof Boolean) {
                castValueToString(filter);
            }
            
        }

        public void visitIsEmpty(LeafFilter filter) throws GeneralException {
            // if the object is boolean, recast as String
            if (filter.getValue() instanceof Boolean) {
                castValueToString(filter);
            }
            
        }

        public void visitJoin(LeafFilter filter) throws GeneralException {
            // if the object is boolean, recast as String
            if (filter.getValue() instanceof Boolean) {
                castValueToString(filter);
            }
            
        }

        public void visitLeftJoin(LeafFilter filter) throws GeneralException {
            // if the object is boolean, recast as String
            if (filter.getValue() instanceof Boolean) {
                castValueToString(filter);
            }
        }

        public void visitCollectionCondition(LeafFilter filter)
                throws GeneralException {
            // if the object is boolean, recast as String
            if (filter.getValue() instanceof Boolean) {
                castValueToString(filter);
            }
            
        }

        public void visitSubquery(LeafFilter filter) throws GeneralException {
            // if the object is boolean, recast as String
            if (filter.getValue() instanceof Boolean) {
                castValueToString(filter);
            }
            
        }
        
        private void castValueToString(LeafFilter filter) {
            Object value = filter.getValue();
            if (value instanceof Boolean) {
                filter.setValue(String.valueOf(value));
            }
        }
        
    }
    
	private static final Log log = LogFactory.getLog(LinkFilterBuilder.class);
	
	@Override 
	public Filter getJoin() {
		Filter join = Filter.join("id", "Link.identity.id");
		return join;
	}
	
	@Override
	public Filter getFilter() throws GeneralException {
	    // Bug 10729 -- let's make the Link filter play nicer with booleans
	    Filter candidate = super.getFilter();
	    if (propertyType == PropertyType.Boolean) {
	        LinkFilterConverter converter = new LinkFilterConverter();
	        candidate.accept(converter);
	    }

	    return candidate;
	}
}
