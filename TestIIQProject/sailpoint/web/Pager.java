/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import sailpoint.tools.GeneralException;

import javax.faces.event.ActionEvent;


/**
 * A Pager is a JSF bean that allows paging through a list of data.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public interface Pager {

    /**
     * Get the starting index for the current page (1-based).
     */
    public int getStartIndex();

    /**
     * Get the ending index for the current page (1-based).
     */
    public int getEndIndex();

    /**
     * Get the current page number (1-based).
     */
    public int getCurrentPage();

    /**
     * Set the current page number (1-based) to jump to a new page.  If you try
     * to go to a page past the end, this will just go to the last page.
     */
    public void setCurrentPage(int page);
    
    /**
     * Get the total number of items in ALL pages.
     */
    public int getTotal();


    /**
     * Return whether there is a next page.
     */
    public boolean getHasNext();

    /**
     * Return whether there is a previous page.
     */
    public boolean getHasPrev();

    /**
     * Return whether paging is required (ie - there are more items than can fit
     * in a single page).
     */
    public boolean isPagingRequired();

    
    /**
     * A JSF ActionListener that gets called to request the next page.
     */
    public void nextPage(ActionEvent evt) throws GeneralException;

    /**
     * A JSF ActionListener that gets called to request the previous page.
     */
    public void prevPage(ActionEvent evt) throws GeneralException;

    /**
     * A JSF ActionListener that gets called to refresh the paging.  This can
     * be used after the current page has been set to go to the new page.
     */
    public void refresh(ActionEvent evt) throws GeneralException;
}
