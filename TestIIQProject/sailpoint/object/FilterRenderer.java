/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/*
 * A FilterVisitor that renders the filter as a SQL-like string.
 * This is an alternative to the Filter.toString representation which
 * is more Java-like and geeky looking.  The intention is that this
 * be used whenever you have to display a read-only summary of the
 * filter, such as in approval work items.   The syntax should
 * more closely resemble what we show in the modeler.
 *
 * Author: Jeff
 *
 * I split this out of Filter to make it easier to deal with.
 * We may want several rendering styles for different purposes.
 * Now that we have this we will eventually want a parser too, at
 * which time this could be a full replacement for the original
 * Java-like syntax if we want to simplify things.  Currently
 * though I don't think the grammar is non-ambiguous.
 *
 */

package sailpoint.object;

import java.util.Date;
import java.util.List;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.object.Filter.CompositeFilter;
import sailpoint.object.Filter.LeafFilter;


/**
 * A <code>FilterVisitor</code> that renders the filter as a SQL-like string.
 * This is an alternative to the Filter.toString representation which
 * is more Java-like. The intention is that this
 * be used whenever you have to display a read-only summary of the
 * filter, such as in approval work items. The syntax should
 * more closely resemble what is shown in the modeler.
 */
public class FilterRenderer implements Filter.FilterVisitor {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    static final String DATE_PREFIX = "DATE$";

    /**
     * The filter being rendered.
     */
    Filter _filter;

    /**
     * The buffer rendered to.
     */
    StringBuilder _builder;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public FilterRenderer() {
    }

    public FilterRenderer(Filter f) {
        _filter = f;
    }

    public String render(Filter f) {
        _filter = f;
        return render();
    }

    public String render() {
        String str = null;
        if (_filter != null) {
            _builder = new StringBuilder();
            try {
                _filter.accept(this);
            }
            catch (GeneralException e) {
                // we don't throw   
            }
            str = _builder.toString();
        }
        return str;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // FilterVisitor
    //
    //////////////////////////////////////////////////////////////////////

    public void visitAnd(CompositeFilter filter) {
        visitComposite(filter, "and");
    }

    public void visitOr(CompositeFilter filter) {
        visitComposite(filter, "or");
    }

    private void visitComposite(CompositeFilter filter, String op) {

        List<Filter> children = filter.getChildren();
        if (children != null && !children.isEmpty()) {
            _builder.append("(");
            int count = 0;
            for (Filter f : children) {
                if (count > 0) {
                    _builder.append(" ");
                    _builder.append(op);
                    _builder.append(" ");
                }
                try {
                    f.accept(this);
                }
                catch (GeneralException e) {
                    // we don't throw
                }
                count++;
            }
            _builder.append(")");
        }
    }

    public void visitNot(CompositeFilter filter) {

        List<Filter> children = filter.getChildren();

        // Since we're generally doing this for  diagnostic purposes
        // don't throw if we find a malformed node, but leave something
        // obvious behind

        if (children == null) {
            // collapse "not null" to nothing
        }
        else if (children.size() != 1) {
            _builder.append("*** error in 'not' expression ***");
        }
        else {
            _builder.append("not ");
            try {
                children.get(0).accept(this);
            }
            catch (GeneralException e) {
                // we don't throw
            }
        }
    }

    // Leaf operations.
    public void visitEQ(LeafFilter filter) { 
        visitComparisonOp(filter, "=");
    }
    public void visitNE(LeafFilter filter) {
        visitComparisonOp(filter, "!=");
    }
    public void visitLT(LeafFilter filter) {
        visitComparisonOp(filter, "<"); 
    }
    public void visitGT(LeafFilter filter) {
        visitComparisonOp(filter, ">"); 
    }
    public void visitLE(LeafFilter filter) {
        visitComparisonOp(filter, "<="); 
    }
    public void visitGE(LeafFilter filter) {
        visitComparisonOp(filter, ">="); 
    }
    public void visitNotNull(LeafFilter filter) { 
        visitUnaryOp(filter, "notnull"); 
    }
    public void visitIsNull(LeafFilter filter) { 
        visitUnaryOp(filter, "isnull"); 
    }
    public void visitIsEmpty(LeafFilter filter) { 
        visitUnaryOp(filter, "isempty"); 
    }

    private void visitComparisonOp(LeafFilter filter, String op) {

        // Encode the property name to make it a valid java identifier
        // this is what toString does, it looks reasonable here too?
        _builder.append(Util.encodeJavaIdentifier(filter.getProperty()));
        _builder.append(" ");

        // jsl - not sure I like this, should convert to a functionish
        // equalNocase(xxx, yyy) ?  or perhaps xxx noCaseEquals yyy

        if (filter.isIgnoreCase())
            _builder.append('i');

        _builder.append(op);
        _builder.append(" ");

        renderValue(filter.getValue());
    }
        
    private void visitUnaryOp(LeafFilter filter, String op) {

        _builder.append(Util.encodeJavaIdentifier(filter.getProperty()));
        _builder.append(" ");
        _builder.append(op);
    }

    private void renderValue(Object o) {

        if (o instanceof String) {
            String s = (String)o;
            _builder.append("\"");
            for (int i = 0 ; i < s.length() ; i++) {
                char c = s.charAt(i);
                if (c == '"' || c == '\\')
                    _builder.append("\\");
                _builder.append(c);
            }
            _builder.append("\"");
        }
        else if (o instanceof Enum) {
            String name = o.getClass().getName();
            for (int i = 0 ; i < name.length() ; i++) {
                char c = name.charAt(i);
                if (c == '$')
                    _builder.append(".");
                else
                    _builder.append(c);
            }
            _builder.append(".");
            _builder.append(o.toString());
        }
        else if (o instanceof Date) {
            _builder.append(DATE_PREFIX);
            _builder.append(((Date)o).getTime());
        }
    }

    public void visitLike(LeafFilter filter) {

        _builder.append(Util.encodeJavaIdentifier(filter.getProperty()));
        _builder.append(" ");
        switch(filter.getMatchMode()) {
            case START:
                _builder.append("startsWith");
                break;
            case END:
                _builder.append("endsWith");
                break;
            default:
                _builder.append("contains");
                break;
        }
        if (filter.isIgnoreCase())
            _builder.append("IgnoreCase");

        _builder.append(" ");
        renderValue(filter.getValue());
    }

    public void visitIn(LeafFilter filter) { 
        visitListFilter(filter); 
    }
    public void visitContainsAll(LeafFilter filter) { 
        visitListFilter(filter); 
    }

    private void visitListFilter(LeafFilter filter) {

        _builder.append(Util.encodeJavaIdentifier(filter.getProperty()));
        Object value = filter.getValue();
        if ((value instanceof Iterable) || (value instanceof Object[])) {

            if (Filter.LogicalOperation.IN.equals(filter.getOperation()))
                _builder.append(" in");
            else
                _builder.append(" containsAll");

            if (filter.isIgnoreCase())
                _builder.append("IgnoreCase");

            _builder.append(" {");

            String sep = "";
            if (value instanceof Iterable) {
                for (Object current : (Iterable) value) {
                    _builder.append(sep);
                    renderValue(current);
                    sep = ", ";
                }
            }
            else if (value instanceof Object[]) {
                for (Object current : (Object[]) value) {
                    _builder.append(sep);
                    renderValue(current);
                    sep = ", ";
                }
            }
            _builder.append("}");
        }
        else {
            _builder.append("*** list term without array or collection ***");
        }
    }

    public void visitJoin(LeafFilter filter) {

        // these are obscure and won't be used in profile filters, so we'll
        // retain the toString syntax
        _builder.append(Util.encodeJavaIdentifier(filter.getProperty()));
        _builder.append(".join(");
        _builder.append(Util.encodeJavaIdentifier(filter.getJoinProperty()));
        _builder.append(")");
    }

    public void visitLeftJoin(LeafFilter filter) {

        // these are obscure and won't be used in profile filters, so we'll
        // retain the toString syntax
        _builder.append(Util.encodeJavaIdentifier(filter.getProperty()));
        _builder.append(".leftJoin(");
        _builder.append(Util.encodeJavaIdentifier(filter.getJoinProperty()));
        _builder.append(")");
    }

    public void visitCollectionCondition(LeafFilter filter) {

        // another obscurity that won't appear in profile filters
        // retain the toString syntax

        // Escape any double quotes in the condition string.
        String conditionString = filter.getCollectionCondition().getExpression();
        conditionString = conditionString.replace("\"", "\\\"");

        _builder.append(Util.encodeJavaIdentifier(filter.getProperty()));
        _builder.append(".collectionCondition(\"");
        _builder.append(conditionString);
        _builder.append("\")");
    }

    public void visitSubquery(LeafFilter filter) throws GeneralException {
        // another obscurity that won't appear in profile filters
        // retain the toString syntax
        _builder.append(filter.getExpression());
    }
}
