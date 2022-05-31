/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Filter;
import sailpoint.object.Filter.BaseFilterVisitor;
import sailpoint.tools.ConvertTools;
import sailpoint.tools.GeneralException;


/**
 * A matcher that applies LeafFilter matching to a Java property.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class JavaPropertyMatcher extends BaseFilterVisitor implements Matcher
{
    private static Log log = LogFactory.getLog(JavaPropertyMatcher.class);
    private Filter.LeafFilter filter;
    private Object objectToMatch;
    private Object matchedValue;
    
    private Object filterVal;
    private Object matchVal;
    private boolean matched = false;


    public JavaPropertyMatcher(Filter.LeafFilter filter)
    {
        this.filter = filter;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // Matcher interface
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Match the given Object using Java semantics.
     */
    public boolean matches(Object o) throws GeneralException
    {
        this.filterVal =
            JavaMatcher.applyIgnoreCase(filter.getValue(), filter.isIgnoreCase());
        this.matchVal = JavaMatcher.applyIgnoreCase(o, filter.isIgnoreCase());
        if (null != filterVal)
            this.matchVal = ConvertTools.convert(matchVal, filterVal.getClass());
        this.objectToMatch = o;

        this.filter.accept(this);
        
        return this.matched;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // FilterHandler methods
    //
    ////////////////////////////////////////////////////////////////////////////

    public void visitIsNull(Filter.LeafFilter filter) {
        this.matched = (null == matchVal);
        if (this.matched)
            this.matchedValue = this.objectToMatch;
    }

    public void visitNotNull(Filter.LeafFilter filter) {
        this.matched = (null != matchVal);
        if (this.matched)
            this.matchedValue = this.objectToMatch;
    }

    public void visitIsEmpty(Filter.LeafFilter filter) {
        this.matched = ((null == matchVal) ||
                        (matchVal instanceof Collection &&
                         ((Collection)matchVal).size() == 0));

        if (this.matched)
            this.matchedValue = this.objectToMatch;
    }

    public void visitEQ(Filter.LeafFilter filter) {
        this.matched =
            (((null == filterVal) && (null == matchVal)) ||
             ((null != filterVal) && filterVal.equals(matchVal)));
        if (this.matched)
            this.matchedValue = this.objectToMatch;
    }

    public void visitNE(Filter.LeafFilter filter) {
        this.matched =
            (((null == filterVal) && (null != matchVal)) ||
             ((null != filterVal) && (null == matchVal)) ||
             ((null != filterVal) && !filterVal.equals(matchVal)));
        if (this.matched)
            this.matchedValue = this.objectToMatch;
    }

    @SuppressWarnings("unchecked")
    public void visitLT(Filter.LeafFilter filter) throws GeneralException {
        assertFilterValueNotNull();
        if (null != this.matchVal) {
            assertInstanceOf(Comparable.class, filterVal, matchVal);
            this.matched = (((Comparable) matchVal).compareTo(filterVal) < 0);
            if (this.matched)
                this.matchedValue = this.objectToMatch;
        }
    }

    @SuppressWarnings("unchecked")
    public void visitGT(Filter.LeafFilter filter) throws GeneralException {
        assertFilterValueNotNull();
        if (null != this.matchVal) {
            assertInstanceOf(Comparable.class, filterVal, matchVal);
            this.matched = (((Comparable) matchVal).compareTo(filterVal) > 0);
            if (this.matched)
                this.matchedValue = this.objectToMatch;
        }
    }

    @SuppressWarnings("unchecked")
    public void visitLE(Filter.LeafFilter filter) throws GeneralException {
        assertFilterValueNotNull();
        if (null != this.matchVal) {
            assertInstanceOf(Comparable.class, filterVal, matchVal);
            this.matched = (((Comparable) matchVal).compareTo(filterVal) <= 0);
            if (this.matched)
                this.matchedValue = this.objectToMatch;
        }
    }

    @SuppressWarnings("unchecked")
    public void visitGE(Filter.LeafFilter filter) throws GeneralException {
        assertFilterValueNotNull();
        if (null != this.matchVal) {
            assertInstanceOf(Comparable.class, filterVal, matchVal);
            this.matched = (((Comparable) matchVal).compareTo(filterVal) >= 0);
            if (this.matched)
                this.matchedValue = this.objectToMatch;
        }
    }

    public void visitIn(Filter.LeafFilter filter) throws GeneralException {
        assertFilterValueNotNull();
        if (null != this.matchVal) {
            if (filterVal instanceof Collection)
                this.matched = ((Collection) filterVal).contains(matchVal);
            else if (filterVal instanceof Object[])
                this.matched = Arrays.asList((Object[]) filterVal).contains(matchVal);
            else
                throw new GeneralException("Filter value must be a collection for IN operator.");
            if (this.matched)
                this.matchedValue = this.objectToMatch;
        }
    }

    @SuppressWarnings("unchecked")
    public void visitContainsAll(Filter.LeafFilter filter) throws GeneralException {

        assertFilterValueNotNull();

        // Bail out without matching immediately if the value is null.
        if (null == this.objectToMatch) {
            return;
        }

        // Use the objectToMatch rather than the matchVal so that our call to
        // containsAll() will return the matched values without case-insensitivity
        // applied (ie - toLowerCase()).
        Collection actualCollection = null;
        if (this.objectToMatch instanceof Collection)
            actualCollection = (Collection) this.objectToMatch;
        else if (this.objectToMatch instanceof Object[])
            actualCollection = Arrays.asList((Object[]) this.objectToMatch);
        else if (null != this.objectToMatch)
        {
            // Single value ... upgrade to a collection.
            actualCollection = new ArrayList();
            actualCollection.add(this.objectToMatch);
        }

        if (this.filterVal instanceof Collection)
        {
            Collection coll = (Collection) this.filterVal;
            Collection matchedValues =
                containsAll(actualCollection, coll, filter.isIgnoreCase());
            if (null != matchedValues) {
                this.matched = true;
                this.matchedValue = matchedValues;
            }
        }
        else if (this.filterVal instanceof Object[])
        {
            Collection coll = Arrays.asList((Object[]) this.filterVal);
            Collection matchedValues =
                containsAll(actualCollection, coll, filter.isIgnoreCase());
            if (null != matchedValues) {
                this.matched = true;
                this.matchedValue = matchedValues;
            }
        }
        else
            throw new GeneralException("containsAll() filter value must be a collection.");
    }

    /**
     * Check if the given collection contains all of the elements of the given
     * subset (ignore case if ignoreCase is true).  Return null if the
     * collection didn't contain all elements of the subset, or the values in
     * the collection that matched the values in the subset if all elements
     * were found in the subset.
     * 
     * @param  collection  The collection of elements in which to look for the
     *                     subset.
     * @param  subSet      The subset of elements required to be found in the
     *                     collection for this to return a match.
     * @param  ignoreCase  Whether to use case sensitive matching or not.
     * 
     * @return Return null if the collection didn't contain all elements of the
     *         subset, or the values in the collection that matched the values
     *         in the subset if all elements were found in the subset.
     */
    @SuppressWarnings("unchecked")
    private static Collection containsAll(Collection collection,
                                          Collection subSet,
                                          boolean ignoreCase) {

        Collection matched = new HashSet();
        for (Iterator it=subSet.iterator(); it.hasNext(); ) {
            Object subSetCurrent = it.next();
            Object ignoreCaseSubSetCurrent =
                JavaMatcher.applyIgnoreCase(subSetCurrent, ignoreCase);
            boolean found = false;

            for (Iterator it2=collection.iterator(); it2.hasNext() && !found; ) {
                Object superSetCurrent = it2.next();
                if ( superSetCurrent != null ) {
                    Object ignoreCaseSuperSetCurrent =
                        JavaMatcher.applyIgnoreCase(superSetCurrent, ignoreCase);
                    if (ignoreCaseSuperSetCurrent.equals(ignoreCaseSubSetCurrent)) {
                        matched.add(superSetCurrent);
                        found = true;
                    }
                } else {
                    log.debug("Null list item found while evaluating containsAll.");                     
                }
            }

            if (!found) {
                return null;
            }
        }

        return matched;
    }

    public void visitLike(Filter.LeafFilter filter) throws GeneralException {
        assertFilterValueNotNull();
        if (null != this.matchVal) {
            // jsl - formerly required these to both be Strings, but we
            // had an issue where the UI produced a profile filter that
            // used LIKE on the multi-valued attribute memberOf.  It seems
            // reasonalbe to coerce whatever we get to a string for LIKE.
            //assertInstanceOf(String.class, filterVal, matchVal);

            this.matched = compareStrings(filterVal.toString(), matchVal.toString(),
                                          filter.getMatchMode());
            if (this.matched)
                this.matchedValue = this.objectToMatch;
        }
    }

    public Object getMatchedValue()
    {
        return this.matchedValue;
    }

    private static boolean compareStrings(String expected, String actual,
                                          Filter.MatchMode matchMode)
        throws GeneralException
    {
        switch(matchMode)
        {
        case START:
            return actual.startsWith(expected);
        case END:
            return actual.endsWith(expected);
        case EXACT:
            return actual.equals(expected);
        default:
            // assume anywhere if null
            return actual.indexOf(expected) > -1;
        }
    }

    private void assertFilterValueNotNull() throws GeneralException {
        if (null == this.filterVal) {
            throw new GeneralException("Filter value cannot be null: " +
                                       this.filter.getOperation() + " on " +
                                       this.filter.getProperty());
        }
    }

    private static void assertInstanceOf(Class clazz, Object... o)
        throws GeneralException
    {
        for (Object current : o)
            if (!clazz.isInstance(current))
                throw new GeneralException("Filter value '" + current + "' not a " +
                                           clazz + " - it is a " + current.getClass() + ".");
    }
}
