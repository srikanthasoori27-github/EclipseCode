/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.search;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Stack;

import sailpoint.object.Attributes;
import sailpoint.object.EntitlementCollection;
import sailpoint.object.Filter;
import sailpoint.object.Filter.BaseFilterVisitor;
import sailpoint.object.Filter.LeafFilter;
import sailpoint.tools.GeneralException;


/**
 * An abstract matcher that implements matching by comparing values in a given
 * Object with values specified in Filters.  Subclasses should implement
 * <code>getPropertyValue(LeafFilter, Object)</code> to retrieve the value of
 * a property from an object.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public abstract class JavaMatcher extends BaseFilterVisitor implements Matcher
{
    protected Stack<Boolean> evaluationStack = new Stack<Boolean>();
    protected Object objectToMatch;
    protected Filter filter;
    protected Map<String,Object> matchedValues = new HashMap<String,Object>();
    protected boolean matchCompleted = false;


    public JavaMatcher(Filter filter)
    {
        this.filter = filter;
    }

    /**
     * Get the value requested by the given leaf filter's property from the
     * given Object.
     * 
     * @param  leaf  The LeafFilter that specifies the property to retrieve.
     * @param  o     The Object from which to retrieve the property value.
     * 
     * @return The value requested by the given leaf filter's property from the
     *         given Object.
     */
    abstract public Object getPropertyValue(Filter.LeafFilter leaf, Object o)
        throws GeneralException;


    /* (non-Javadoc)
     * @see sailpoint.object.Matcher#matches(java.lang.Object)
     */
    public boolean matches(Object o) throws GeneralException
    {
        this.matchCompleted = false;
        this.objectToMatch = o;
        boolean matches = false;
        try {
            this.filter.accept(this);
            if (1 != this.evaluationStack.size())
                throw new GeneralException("Evaluation stack should have one element " + this.evaluationStack);
            matches = this.evaluationStack.pop();
            this.matchCompleted = true;
        }
        finally {
            this.objectToMatch = null;
            this.evaluationStack.clear();
        }
        return matches;
    }

    private void assertMatchCompleted() throws IllegalStateException
    {
        if (!this.matchCompleted)
            throw new IllegalStateException("matches(Object) must be called first.");
    }

    /**
     * Return a map mapping attribute name to the value (or Set of values on a
     * multi-values attribute) that were matched.
     * 
     * @return A map mapping attribute name to the value (or Set of values on a
     *         multi-values attribute) that were matched.
     */
    public Map<String,Object> getMatchedValues()
    {
        assertMatchCompleted();
        return this.matchedValues;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // FilterVisitor implementation
    //
    ////////////////////////////////////////////////////////////////////////////

    public void visitAnd(Filter.CompositeFilter filter) throws GeneralException {
        visitAndNot(filter);
    }

    public void visitNot(Filter.CompositeFilter filter) throws GeneralException {
        visitAndNot(filter);
    }

    private void visitAndNot(Filter.CompositeFilter filter) throws GeneralException {
        int startSize = this.evaluationStack.size();

        // Get a copy of the matched attributes.  We'll want to reset these if
        // the match fails.  Using Attributes so we can use the mediumClone()
        // method.
        Map<String,Object> previousMatchedValues =
            new Attributes<String,Object>(this.matchedValues).mediumClone();

        boolean andVal = true;
        for (Filter f : filter.getChildren())
        {
            f.accept(this);
            if (!this.evaluationStack.peek())
            {
                andVal = false;
                break;
            }
        }

        andVal = (Filter.BooleanOperation.NOT.equals(filter.getOperation())) ? !andVal : andVal;

        // If the match failed, reset the matchedValues to their previous state.
        if (!andVal) {
            this.matchedValues = previousMatchedValues;
        }

        // Pop back to the original size.
        this.evaluationStack.setSize(startSize);
        this.evaluationStack.push(andVal);
    }

    public void visitOr(Filter.CompositeFilter filter) throws GeneralException {
        int startSize = this.evaluationStack.size();
        boolean isTrue = false;
        for (Filter f : filter.getChildren()) {
            f.accept(this);
            if (this.evaluationStack.peek()) {
                // Don't short-circuit the OR ... keep processing to find all
                // matched values.
                isTrue = true;
            }
        }
        this.evaluationStack.setSize(startSize);
        this.evaluationStack.push(isTrue);
    }

    private void visitLeaf(Filter.LeafFilter leaf) throws GeneralException {
        Object actual = getPropertyValue(leaf, this.objectToMatch);

        JavaPropertyMatcher jpm = new JavaPropertyMatcher(leaf);
        boolean matches = jpm.matches(actual);

        if (matches) {
            this.matchedValues =
                EntitlementCollection.mergeValues(leaf.getProperty(),
                                                  jpm.getMatchedValue(),
                                                  this.matchedValues);
        }

        this.evaluationStack.push(matches);
    }

    public void visitEQ(LeafFilter filter) throws GeneralException { visitLeaf(filter); }
    public void visitNE(LeafFilter filter) throws GeneralException { visitLeaf(filter); }
    public void visitLT(LeafFilter filter) throws GeneralException { visitLeaf(filter); }
    public void visitGT(LeafFilter filter) throws GeneralException { visitLeaf(filter); }
    public void visitLE(LeafFilter filter) throws GeneralException { visitLeaf(filter); }
    public void visitGE(LeafFilter filter) throws GeneralException { visitLeaf(filter); }
    public void visitIn(LeafFilter filter) throws GeneralException { visitLeaf(filter); }
    public void visitContainsAll(LeafFilter filter) throws GeneralException { visitLeaf(filter); }
    public void visitLike(LeafFilter filter) throws GeneralException { visitLeaf(filter); }
    public void visitNotNull(LeafFilter filter) throws GeneralException { visitLeaf(filter); }
    public void visitIsNull(LeafFilter filter) throws GeneralException { visitLeaf(filter); }
    public void visitIsEmpty(LeafFilter filter) throws GeneralException { visitLeaf(filter); }

    /**
     * Apply a case-insensitive conversion to the given object if ignoreCase is
     * true.  If the object is a string, this returns a lowercase version of the
     * string.  If this is a collection or object array, this returns a
     * Collection/object array with lowercase strings. If the object is a Boolean,
     * this returns a lowercase string of the value of the Boolean.
     * 
     * @param  o           The object to which to apply ignoreCase.
     * @param  ignoreCase  Whether case should be ignored.
     * 
     * @return If the given Object is a String, Boolean, Collection of String, or array
     *         that contains Strings AND ignore case is true this returns a
     *         lower case instance of the given Object, otherwise return the
     *         given Object.
     */
    @SuppressWarnings("unchecked")
    static Object applyIgnoreCase(Object o, boolean ignoreCase)
    {
        if (ignoreCase) {
            if (o instanceof String)
                return ((String) o).toLowerCase();
            else if (o instanceof Boolean) {
                String s = String.valueOf((Boolean) o);
                return s.toLowerCase();
            }
            else if (o instanceof Collection) {
                Collection col = new LinkedHashSet();
                for (Iterator it=((Collection) o).iterator(); it.hasNext(); ) {
                    Object current = it.next();
                    if (current instanceof String)
                        col.add(((String) current).toLowerCase());
                    else
                        col.add(current);
                }
                return col;
            }
            else if (o instanceof Object[]) {
                Object[] input = (Object[]) o;
                Object[] rv = new Object[input.length];
                for (int i=0; i<input.length; i++) {
                    if (input[i] instanceof String)
                        rv[i] = ((String) input[i]).toLowerCase();
                    else
                        rv[i] = input[i];
                }
                return rv;
            }
        }

        return o;
    }
}
