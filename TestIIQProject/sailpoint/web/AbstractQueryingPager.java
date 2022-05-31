/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web;

import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;


/**
 * A pager implementation that uses database queries to determine the total
 * number of objects and retrieve a paged sub-list of the relevant object.
 * Clients of this pager can use the QueryOptions returned by
 * <code>getPagedQueryOptions()</code> to perform their desired query and
 * construct a list of paged results.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public abstract class AbstractQueryingPager extends AbstractPager {

    private BaseBean baseBean;
    private QueryOptions queryOptions;
    private Class<? extends SailPointObject> clazz;

    private Integer total;


    /**
     * Default constructor.
     */
    public AbstractQueryingPager() {
        super();
    }

    /**
     * Constructor.
     * 
     * @param  pageSize  The number of items in a page.
     * @param  qo        The QueryOptions that will be used to query for the
     *                   objects to display.
     * @param  clazz     The SailPointObject class on which the query will be
     *                   run.
     */
    public AbstractQueryingPager(int pageSize, QueryOptions qo,
                                 Class<? extends SailPointObject> clazz) {
        super(pageSize);

        this.queryOptions = qo;
        this.clazz = clazz;
    }

    /**
     * Attach this pager to the given BaseBean.
     */
    public void attach(BaseBean bean) {
        this.baseBean = bean;
    }
    
    /* (non-Javadoc)
     * @see sailpoint.web.Pager#getTotal()
     */
    public int getTotal() {
        // Only calculate this once.
        if (null == this.total) {
            try {
                this.total = this.baseBean.getContext().countObjects(this.clazz, this.queryOptions);
            }
            catch (GeneralException e) {
                // Wrap with a runtime exception since this method doesn't throw.
                throw new RuntimeException(e);
            }
        }

        return this.total;
    }

    /**
     * Return a copy of the QueryOptions with the firstRow and resultLimit set
     * based on the current state of the pager.
     */
    public QueryOptions getPagedQueryOptions() {
        QueryOptions paged =
            (null != this.queryOptions) ? new QueryOptions(this.queryOptions)
                                        : new QueryOptions();
        paged.setFirstRow(this.getOffset());
        paged.setResultLimit(this.getPageSize());
        return paged;
    }
}
