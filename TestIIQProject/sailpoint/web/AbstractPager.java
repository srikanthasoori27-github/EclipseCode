/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import sailpoint.tools.GeneralException;

import javax.faces.event.ActionEvent;

import java.io.Serializable;


/**
 * An abstract implementation of the Pager interface.  
 * 
 * This abstract class can be used to page lists of things from the database.
 *
 * There are currently two implementations :
 *
 * 1) AbstractCountingPager: Should be used to page various lists of things that
 *    are not easily queried from the database in a way that can be queried 
 *    easily in pages. 
 *
 * 2) AbstractQueryingPager: Should be used when items are queried from the 
 *    database using the pager to drive the firstRow and resultLimit
 *    QueryOptions which prevents more then a page of results from being in
 *    memory at a given time.
 *
 * See these class for more details.
 *
 * An alternate implementation would be to wrap a list that is
 * already constructed and only return the relevant sublist.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public abstract class AbstractPager implements Pager, Serializable {

    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////

    private int pageSize;
    private int offset;


    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Default constructor required for reflective instantiation.
     */
    public AbstractPager() {}

    /**
     * Constructor.
     * 
     * @param  pageSize  The size of the page.
     */
    public AbstractPager(int pageSize) {
        this.pageSize = pageSize;
        this.offset = 0;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // ABSTRACT METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * This method is called when the previous/next actions occur.
     */
    protected abstract void prevNextFired() throws GeneralException;


    ////////////////////////////////////////////////////////////////////////////
    //
    // PAGER IMPLEMENTATION
    //
    ////////////////////////////////////////////////////////////////////////////

    public int getStartIndex() {
        return this.offset + 1;
    }

    public int getEndIndex() {
        return Math.min(this.offset + this.pageSize, this.getTotal());
    }

    public boolean getHasNext() {
        return this.getEndIndex() < this.getTotal();
    }

    public boolean getHasPrev() {
        return this.offset > 0;
    }

    public boolean isPagingRequired() {
        return this.getTotal() > this.pageSize;
    }
    
    public int getCurrentPage() {
        return (this.offset / this.pageSize) + 1;
    }

    public void setCurrentPage(int page) {

        // If the requested page falls outside of the appropriate range, just
        // use the first or last page.  Note that lastPage is 1-based.
        int lastPage = (int) Math.ceil((double) this.getTotal() / this.pageSize);
        page = Math.max(1, Math.min(page, lastPage));
        
        this.offset = (page-1) * this.pageSize;
    }
    

    ////////////////////////////////////////////////////////////////////////////
    //
    // METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Reset the offset for this pager.
     */
    public void resetOffset() {
        this.offset = 0;
    }

    /**
     * Return the zero-based offset of the starting item to display.
     */
    protected int getOffset() {
        return this.offset;
    }

    /**
     * Return the number of items to show in a page.
     */
    protected int getPageSize() {
        return this.pageSize;
    }

    /**
     * Produce a state-saving memento that can be consumed by restoreState() to
     * get this pager back to the same place.
     */
    public Object saveState() {
        Object[] state = new Object[2];
        state[0] = this.offset;
        state[1] = this.pageSize;
        return state;
    }

    /**
     * Read the state from the given memento.
     */
    public void restoreState(Object o) {
        Object[] state = (Object[]) o;
        this.offset = (Integer) state[0];
        this.pageSize = (Integer) state[1];
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // ACTION HANDLING
    //
    ////////////////////////////////////////////////////////////////////////////

    public void nextPage(ActionEvent evt) throws GeneralException {
        assert (this.getHasNext()) : "No more pages";
        this.offset += this.pageSize;
        this.prevNextFired();
    }

    public void prevPage(ActionEvent evt) throws GeneralException {
        assert (this.getHasPrev()) : "Already on first page";
        this.offset = Math.max(0, this.offset - this.pageSize);
        this.prevNextFired();
    }

    public void refresh(ActionEvent evt) throws GeneralException {
        this.prevNextFired();
    }
}
