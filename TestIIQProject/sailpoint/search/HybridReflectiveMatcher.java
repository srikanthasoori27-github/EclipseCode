package sailpoint.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import sailpoint.api.SailPointContext;
import sailpoint.object.Filter;
import sailpoint.object.SailPointObject;
import sailpoint.object.Filter.LeafFilter;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

/**
 * Extends ReflectiveMatcher to support the use of a SailPointContext
 * for filters that can't be implemented with reflection. Currently, this
 * is used to implement the subquery filter type.
 * @author jeff.upton
 */
public class HybridReflectiveMatcher extends ReflectiveMatcher
{
    /**
     * The context to use to perform queries on SailPointObject types.
     */
    SailPointContext _context;
    
    /**
     * Constructs a new HybridReflectiveMatcher with a valid context.
     * @param context The context to use for object queries.
     * @param filter The filter to use.
     */
    public HybridReflectiveMatcher(SailPointContext context, Filter filter)
    {        
        super(filter);
        
        assert(context != null);        
        _context = context;
    }
    
    /**
     * Implements the subquery using the SailPointContext if the subquery class is
     * is a descendant of SailPointObject.
     * @param filter The subquery filter.
     * @throws UnsupportedOperationException If the subquery class is not a descendant of SailPointObject.
     */
    @Override
    public void visitSubquery(LeafFilter filter) 
        throws GeneralException
    {
        if (filter.getSubqueryClass() == null || !SailPointObject.class.isAssignableFrom(filter.getSubqueryClass())) {
            throw new UnsupportedOperationException("Subquery class must be a child of SailPointObject");
        }
        
        QueryOptions options = new QueryOptions();
        options.add(filter.getSubqueryFilter());
        
        @SuppressWarnings("unchecked")
        Class<? extends SailPointObject> subqueryClass = (Class<? extends SailPointObject>)filter.getSubqueryClass();
        
        List<Object> subqueryResults = new ArrayList<Object>();
                
        Iterator<Object[]> subqueryResultIterator = _context.search(subqueryClass, options, Arrays.asList(filter.getSubqueryProperty()));        
        while (subqueryResultIterator.hasNext()) {
            Object[] row = subqueryResultIterator.next();
            assert(row.length == 1);
            
            subqueryResults.add(row[0]);
        }
        
        if (subqueryResults.isEmpty()) {
            evaluationStack.push(false);
        } else {
            Filter inFilter = Filter.in(filter.getProperty(), subqueryResults);
            // Filter.in will return a LeafFilter if subQueryResults size is <= 100
            // or a CompositeFilter if subQueryResults size is > 100.
            if (inFilter instanceof LeafFilter) {
                visitIn((LeafFilter)inFilter);
            } else if (inFilter instanceof Filter.CompositeFilter) {
                visitOr((Filter.CompositeFilter)inFilter);
            } else {
                throw new GeneralException("LeafFilter or CompositeFilter expected from Filter.in");
            }
        }
    }
}
