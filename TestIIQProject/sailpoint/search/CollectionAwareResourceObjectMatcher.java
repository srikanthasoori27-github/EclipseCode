/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.search;

import java.util.Collection;
import java.util.Iterator;

import sailpoint.object.EntitlementCollection;
import sailpoint.object.Filter;
import sailpoint.object.Filter.LeafFilter;
import sailpoint.tools.GeneralException;

public class CollectionAwareResourceObjectMatcher extends ResourceObjectMatcher {
    public CollectionAwareResourceObjectMatcher(Filter filter) {
        super(filter);
    }

    @Override
    public void visitLike(LeafFilter filter) throws GeneralException {
        visitEachValue(filter);
    }

    @Override
    public void visitEQ(LeafFilter filter) throws GeneralException {
        visitEachValue(filter);
    }

    @Override
    public void visitNE(LeafFilter filter) throws GeneralException {
        visitEachValue(filter);
    }

    @Override
    public void visitLT(LeafFilter filter) throws GeneralException {
        visitEachValue(filter);
    }

    @Override
    public void visitGT(LeafFilter filter) throws GeneralException {
        visitEachValue(filter);
    };

    @Override
    public void visitLE(LeafFilter filter) throws GeneralException {
        visitEachValue(filter);
    };

    @Override
    public void visitGE(LeafFilter filter) throws GeneralException {
        visitEachValue(filter);
    };

    @Override
    public void visitIn(LeafFilter filter) throws GeneralException {
        visitEachValue(filter);
    };

    @Override
    public void visitContainsAll(LeafFilter filter) throws GeneralException {
        visitEachValue(filter);
    };

    @Override
    public void visitNotNull(LeafFilter filter) throws GeneralException {
        visitEachValue(filter);
    };

    @Override
    public void visitIsNull(LeafFilter filter) throws GeneralException {
        visitEachValue(filter);
    };

    @Override
    public void visitIsEmpty(LeafFilter filter) throws GeneralException {
        visitEachValue(filter);
    };

    private void visitEachValue(LeafFilter filter) throws GeneralException {
        Object actual = getPropertyValue(filter, this.objectToMatch);
        JavaPropertyMatcher jpm = new JavaPropertyMatcher(filter);
        boolean matches = false;
        if(actual instanceof Collection) {
            Iterator iterator = ((Collection)actual).iterator();
            while(iterator.hasNext()) {
                matches = visitSingleValue(filter, iterator.next());
                if(matches) {
                    // if any value in the collection matches, it's a match
                    break;
                }
            }
        } else {
            matches = visitSingleValue(filter, actual);
        }

        this.evaluationStack.push(Boolean.valueOf(matches));
    }

    private Boolean visitSingleValue(LeafFilter filter, Object value) throws GeneralException {
        JavaPropertyMatcher jpm = new JavaPropertyMatcher(filter);
        boolean matches = jpm.matches(value);

        if (matches) {
            this.matchedValues =
                    EntitlementCollection.mergeValues(filter.getProperty(),
                            jpm.getMatchedValue(),
                            this.matchedValues);
        }

        return matches;
    }
}
