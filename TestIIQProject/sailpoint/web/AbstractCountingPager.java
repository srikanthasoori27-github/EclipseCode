/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web;


/**
 * A pager implementation that counts items as they are encountered while
 * iterating over a list to determine whether the item fits in a page.
 * Instead of wrapping a list, this implementation expects the code that 
 * builds the list to call <code>encounteredItem()</code> every time an item in
 * the list is about to be added.  This method will return whether the item fits
 * within the current page or not.  This is useful when constructing the items
 * to put in the list is expensive.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public abstract class AbstractCountingPager extends AbstractPager {

    private int currentIdx;


    /**
     * Default constructor.
     */
    public AbstractCountingPager() {
        super();
    }

    /**
     * Constructor.
     */
    public AbstractCountingPager(int pageSize) {
        super(pageSize);
        this.reset();
    }

    /**
     * Return the total number of items.  Note that this value is not valid
     * until the pager is fully-initialized by iterating through the list
     * completely with encounteredItem().
     */
    public int getTotal() {
        return this.currentIdx + 1;
    }

    /**
     * This resets the pager's index - clients should call this before the list
     * is constructed and encounterItem() is used.
     */
    public void reset() {
        this.currentIdx = -1;
    }

    /**
     * Clients should call this method before adding an item to the list.
     * 
     * @return True if the current item fits within the page, false otherwise.
     */
    public boolean encounteredItem() {
        this.currentIdx++;

        // Return true if the index is in the offset/pageSize window.
        return (this.currentIdx >= getOffset()) &&
               (this.currentIdx < getOffset()+getPageSize());
    }
}
