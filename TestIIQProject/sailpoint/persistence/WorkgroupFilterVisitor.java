/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.persistence;

import java.util.ArrayList;
import java.util.List;

import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Filter.BaseFilterVisitor;
import sailpoint.object.Filter.CompositeFilter;
import sailpoint.object.Filter.LeafFilter;
import sailpoint.object.Filter.LogicalOperation;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;


/**
 * A FilterVisitor will process all leaf filters and collect the 
 * attributes names being queried and look for Workgroup filters
 * to help detect if we can optimize out any of the filters.
 *
 * Specifically we've run into issues where if a query 
 * includes ( workgroup=0 OR workgroup=1 ) or 
 * (workgroup in (0,1))
 * 
 * @author Dan Smith
 */
class WorkgroupFilterVisitor
    extends BaseFilterVisitor { 

    /**
     * List of property names that are being queried.
     */
    List<String> _properties;

    /**
     * Flag that is set if the workgroup query is an IN operator
     * that includes both values.
     */
    boolean _canBeOptimized;

    /**
     * Flag that is set if the workgroup query as an EQ
     * operator and true has a value. 
     * When this flag flag is set true AND _workgroupFalse is also 
     * set to true this indicates the query and be optimized and the
     * workgroup filters removed.
     */
    boolean _workgroupTrue;

    /**
     * Flag that is set if the workgroup query as an EQ
     * operator and true has a value.
     * 
     * When this flag flag is set true AND _workgroupTrue is also 
     * set to true this indicates the query and be optimized and the
     * workgroup filters can be removed.
     */
    boolean _workgroupFalse;

    /** 
     * Flag that is set when we are examining and OR filter and 
     * unset when we are done.
     */
    boolean _inOr;

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    public WorkgroupFilterVisitor() {
        _properties = new ArrayList<String>();
        _workgroupFalse = false;
        _workgroupTrue = false;
        _canBeOptimized = false;
        _inOr = false;
    }

    public boolean inPropertyList(String name) {
        if ( Util.size(_properties) > 0 ) {
            if ( _properties.contains(name) )
                return true;
        }
        return false;
    }
    
    public boolean canBeOptimized() {
        if ( _workgroupTrue && _workgroupFalse ) {
            return true;
        } 
        return _canBeOptimized;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // FILTER VISITOR IMPLEMENTATION
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Check the LeafFilter keep a list of the properties being queried.
     * If its the workgroup property check the query and see if
     * there is either an IN operation that has both true and false,
     * or if there are TWO EQ queries that specify both
     * true and false.  Both cases are redundant and should filtered from
     * the query.
     * 
     * MySQL and possibly others have issues when you include both 
     * values during bit operations EVEN if there is an index.
     * BUG#7020 has more details surrounding this issue.
     */
    private void inspectFilter(LeafFilter filter) {
       String name = null;
       if ( filter != null )  
           name = filter.getProperty();

       if ( Util.getString(name) != null ) {
           _properties.add(name);
           if ( Identity.ATT_WORKGROUP_FLAG.compareTo(name) == 0 ) {
               if ( filter.getOperation().equals(LogicalOperation.IN) ) {
                   List vals = Util.asList(filter.getValue());
                   if ( Util.size(vals) == 2 ) {
                       _canBeOptimized = true;
                   } 
               } else
               if ( ( _inOr) && ( filter.getOperation().equals(LogicalOperation.EQ) ) ) {
                   // when both of these are set we can optimize them out 
                   boolean val = Util.otob(filter.getValue());
                   if ( val ) 
                       _workgroupTrue = true;
                   else 
                       _workgroupFalse = true;
               }
           }
       }
    }

    @Override
    public void visitEQ(LeafFilter filter) throws GeneralException {
        inspectFilter(filter);
    }

    @Override
    public void visitGE(LeafFilter filter) throws GeneralException {
        inspectFilter(filter);
    }

    @Override
    public void visitGT(LeafFilter filter) throws GeneralException {
        inspectFilter(filter);
    }

    @Override
    public void visitLE(LeafFilter filter) throws GeneralException {
        inspectFilter(filter);
    }

    @Override
    public void visitLT(LeafFilter filter) throws GeneralException {
        inspectFilter(filter);
    }

    @Override
    public void visitNE(LeafFilter filter) throws GeneralException {
        inspectFilter(filter);
    }

    @Override
    public void visitLike(LeafFilter filter) throws GeneralException {
        inspectFilter(filter);
    }

    @Override
    public void visitNotNull(LeafFilter filter) throws GeneralException {
        inspectFilter(filter);
    }

    @Override
    public void visitIsNull(LeafFilter filter) throws GeneralException {
        inspectFilter(filter);
    }

    @Override
    public void visitIsEmpty(LeafFilter filter) throws GeneralException {
        inspectFilter(filter);
    }

    @Override
    public void visitIn(LeafFilter filter) throws GeneralException {
        inspectFilter(filter);
    }

    @Override
    public void visitContainsAll(LeafFilter filter) throws GeneralException {
        inspectFilter(filter);
    }

    @Override
    public void visitJoin(LeafFilter filter) throws GeneralException {
        //TODO:
    }

    @Override
    public void visitLeftJoin(LeafFilter filter) throws GeneralException {
        //TODO:
    }

    @Override
    public void visitCollectionCondition(LeafFilter filter) throws GeneralException {
        inspectFilter(filter);
    }

    @Override
    public void visitSubquery(LeafFilter filter) throws GeneralException {
        inspectFilter(filter);
    }
    
    private void visitComposite(CompositeFilter filter) 
        throws GeneralException {
    
        List<Filter> children = filter.getChildren();
        if ((null != children) && !children.isEmpty()) {
            for (Filter child : children) {
                child.accept(this);
            }
        }
    }

    @Override
    public void visitAnd(CompositeFilter filter) throws GeneralException {
        this.visitComposite(filter);
    }

    @Override
    public void visitOr(CompositeFilter filter) throws GeneralException {
        _inOr = true;
        this.visitComposite(filter);
        _inOr = false;
    }

    @Override
    public void visitNot(CompositeFilter filter) throws GeneralException {
        this.visitComposite(filter);
    }
}
