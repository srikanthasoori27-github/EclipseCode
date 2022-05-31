/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * Utility to evaluate a Filter with values provided by a pluggable interface.
 *
 * Author: Jeff
 *
 * Created for in-memory filter evaluation of entitlement filters in the 
 * new focused cert generator, but it is general.  It does not support
 * all of the Filter language, especially not subqueries and joining.  These properties
 * of the LeafFilter are not supported:
 *
 *   private boolean _ignoreCase;
 *   private String _joinProperty;
 *   private CompositeFilter _collectionCondition;
 *   private String _cast;
 *   private Filter _subqueryFilter;
 *   private String _subqueryProperty;
 *   private Class<?> _subqueryClass;
 *
 * Ignore case is subtle.  We now do not require it in most Hibernate bound filters
 * because we can figure it out from the mapping files based on the type of
 * index declared.  Here we're not dealing with Hibernate and I don't want to add the
 * expensive of having the ValueSource figure that out.  We will assume all comparisons
 * are case insensitive.
 * 
 * This is not true for IN and CONTAINS_ALL whicih just use Collection methods that do not
 * ignore case.  I don't really feel like implementing that since we're not going to 
 * be using those operators anyway.
 *
 */

package sailpoint.certification;

import java.util.Collection;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Filter;
import sailpoint.object.Filter.BooleanOperation;
import sailpoint.object.Filter.CompositeFilter;
import sailpoint.object.Filter.LogicalOperation;
import sailpoint.object.Filter.MatchMode;

public class FilterEvaluator {

    private static Log log = LogFactory.getLog(FilterEvaluator.class);

    Filter _filter;
    ValueSource _source;

    public interface ValueSource {

        public Object getValue(String propertyName);
        
    }

    /**
     * Constructor for when the filter is variable.
     */
    public FilterEvaluator(ValueSource source) {
        _source = source;
    }

    /**
     * Constructor for when the filter is static.
     */
    public FilterEvaluator(Filter filter, ValueSource source) {
        _filter = filter;
        _source = source;
    }

    /**
     * Evaluate the static filter.
     */
    public boolean eval() {
        return eval(_filter);
    }

    /**
     * Evaluate a random filter (may be the static one).
     */
    public boolean eval(Filter f) {

        boolean result = false;

        if (f instanceof Filter.LeafFilter) {
            result = evalLeaf((Filter.LeafFilter)f);
        }
        else {
            result = evalComposite((Filter.CompositeFilter)f);
        }

        return result;
    }

    /**
     * Not supporting LT, GT, LE, GE
     * The filter builder won't use them and we should support
     * both Integer and Float.  Don't want to mess with it.
     *
     * Not supporting JOIN, COLLECTION_CONDITION
     */
    private boolean evalLeaf(Filter.LeafFilter f) {

        boolean result = false;

        LogicalOperation op = f.getOperation();
        Object filterValue = f.getValue();
        Object sourceValue = _source.getValue(f.getProperty());
        
        if (op != null) {
            // ugh, after all these years I still haven't trained
            // emacs to format switches properly
            switch (op) {
            case EQ: {
                result = isEqual(filterValue, sourceValue);
            }
                break;
            case NE: {
                result = !isEqual(filterValue, sourceValue);
            }
                break;
            case IN: {
                // not used by the cert filter builder but easy
                // this isn't doing case insensntive comparison
                if (filterValue instanceof Collection) {
                    Collection col = (Collection)filterValue;
                    result = col.contains(sourceValue);
                }
            }
                break;
            case CONTAINS_ALL: {
                // not used by the cert filter builder but easy
                // this isn't doing case insensntive comparison
                if (filterValue instanceof Collection) {
                    Collection col = (Collection)filterValue;
                    if (sourceValue instanceof Collection) {
                        result = col.containsAll((Collection)sourceValue);
                    }
                    else {
                        result = col.contains(sourceValue);
                    }
                }
            }
                break;
            case LIKE: {
                // cert filter builder will only use START so
                // it can use a db index, the others are easy but we'll
                // never see them
                MatchMode mode = f.getMatchMode();
                if (mode == MatchMode.START) {
                    if (filterValue != null && sourceValue != null) {
                        result = sourceValue.toString().startsWith(filterValue.toString());
                    }
                }
                else {
                    log.error("Unsupported MatchMode: " + mode);
                }
            }
                break;
            case NOTNULL: {
                result = (sourceValue != null);
            }
                break;
            case ISNULL: {
                result = (sourceValue == null);
            }
                break;
            case ISEMPTY: {
                // not sure about null, what SQL would be generated from this?
                if (sourceValue == null) {
                    result = true;
                }
                else if (sourceValue instanceof Collection) {
                    result = ((Collection)sourceValue).isEmpty();
                }
            }
                break;
            default: {
                log.error("Unsupported logical operation: " + op);
            }
            }
        }

        return result;
    }

    /**
     * Return true if two objects are equal.
     * For strings use case insensitive comparison.
     * Unlike SQL we're treating null as comparable, but filters should
     * be using isNotNull and isNull for that.
     */
    private boolean isEqual(Object o1, Object o2) {

        boolean equal = false;

        if (o1 != null) {
            if (o1 instanceof String && o2 instanceof String) {
                equal = ((String)o1).equalsIgnoreCase((String)o2);
            }
            else {
                equal = o1.equals(o2);
            }
        }
        else if (o2 == null) {
            equal = true;
        }

        return equal;
    }

    /**
     * Evaluate a composite filter.
     */
    private boolean evalComposite(CompositeFilter f) {

        boolean result = false;

        BooleanOperation op = f.getOperation();
        List<Filter> children = f.getChildren();

        if (op != null) {
            switch (op) {
            case NOT: {
                // what would SQL do?
                if (children == null || children.size() == 0) {
                    result = true;
                }
                else {
                    // can only have one child
                    Filter cf = children.get(0);
                    result = !eval(cf);
                }
            }
                break;
            case AND: {
                if (children != null && children.size() > 0) {
                    result = true;
                    for (Filter cf : children) {
                        boolean cresult = eval(cf);
                        if (!cresult) {
                            result = false;
                            break;
                        }
                    }
                }
            }
                break;
            case OR: {
                if (children != null && children.size() > 0) {
                    for (Filter cf : children) {
                        boolean cresult = eval(cf);
                        if (cresult) {
                            result = true;
                            break;
                        }
                    }
                }
            }
            }
        }

        return result;
    }

}
        
