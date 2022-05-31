/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.fam;

import java.util.ArrayList;
import java.util.List;

import sailpoint.fam.model.SCIMObject;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Convert Filters to SCIM Filters
 *
 * As of v6.1:
 * Filter - Currently we only support the "and" logical operator between filter expressions ("or" is not supported)
 * Filter - If filter expression values include the reserved URL characters " '$-_.+!*'(),' ", they need to be changed to their encoded value.
 */
public class FAMFilterVisitor extends Filter.BaseFilterVisitor {

    List<String> queryParams;

    public String getQueryString() {
        StringBuilder sb = new StringBuilder();

        if (!Util.isEmpty(queryParams)) {
            boolean first = true;
            for (String param : Util.safeIterable(queryParams)) {
                if (!first) {
                    sb.append(" and ");
                }
                sb.append(param);
                first = false;
            }
        }

        return sb.toString();
    }


    protected FAMFilterVisitor visitFAMFilter(QueryOptions ops) throws GeneralException {

        List<Filter> filters = null;
        Filter combinedFilter = null;

        if (ops != null) {
            List<Filter> restrictions = ops.getRestrictions();
            if ( Util.size(restrictions) > 0 ) {
                filters = new ArrayList(restrictions);

                if (1 == filters.size()) {
                    combinedFilter  = filters.get(0);
                }
                else {
                    combinedFilter = Filter.and(filters);
                }

                combinedFilter.accept(this);
            }



        }

        return this;
    }

    /**
     * Composite filters must be anded together and only contain leaf filters
     * @param filter
     * @throws GeneralException
     */
    @Override
    public void visitAnd(Filter.CompositeFilter filter) throws GeneralException {
        if (filter != null) {
            for (Filter child : Util.safeIterable(filter.getChildren())) {
                if (child instanceof Filter.LeafFilter) {
                    //Assume leaf filter
                    Filter.LeafFilter f = (Filter.LeafFilter) child;
                    f.accept(this);
                } else {
                    throw new GeneralException("Filter " + filter.getExpression() + " not supported");
                }
            }
        }

    }


    /**
     * Convert filter to present query param
     * FAM refers to this as present
     * @param filter
     * @throws GeneralException
     */
    @Override
    public void visitNotNull(Filter.LeafFilter filter) throws GeneralException {

        if (Util.isNullOrEmpty(filter.getProperty())) {
            throw new GeneralException("Filter property must be supplied");
        }

        if (this.queryParams == null) {
            this.queryParams = new ArrayList<>();
        }

        StringBuilder builder = new StringBuilder();
        builder.append(filter.getProperty());
        builder.append(" ");
        builder.append(SCIMObject.OPERATOR_PRESENT);
        this.queryParams.add(builder.toString());

    }

    /**
     * Convert Filter to eq QueryParam
     * @param filter
     * @throws GeneralException
     */
    @Override
    public void visitEQ(Filter.LeafFilter filter) throws GeneralException {
        if (Util.isNullOrEmpty(filter.getProperty())) {
            throw new GeneralException("Filter property must be supplied");
        }

        if (this.queryParams == null) {
            this.queryParams = new ArrayList<>();
        }

        StringBuilder builder = new StringBuilder();
        builder.append(filter.getProperty());
        builder.append(" ");
        builder.append(SCIMObject.OPERATOR_EQ);
        builder.append(" \"");
        builder.append(filter.getValue());
        builder.append("\"");
        this.queryParams.add(builder.toString());
    }


    /**
     * Convert Filter to StartsWith or Contains QueryParam
     * @param filter
     * @throws GeneralException
     */
    @Override
    public void visitLike(Filter.LeafFilter filter) throws GeneralException {

        String operator;
        if (filter.getMatchMode() != null) {
            if (filter.getMatchMode() == Filter.MatchMode.START) {
                operator = SCIMObject.OPERATOR_STARTS_WITH;
            } else if (filter.getMatchMode() == Filter.MatchMode.ANYWHERE) {
                operator = SCIMObject.OPERATOR_CONTAINS;
            } else {
                throw new GeneralException("MatchMode not supported");
            }
        } else {
            throw new GeneralException("No MatchMode provided");

        }

        if (Util.isNullOrEmpty(filter.getProperty())) {
            throw new GeneralException("Filter property must be supplied");
        }

        if (this.queryParams == null) {
            this.queryParams = new ArrayList<>();
        }


        StringBuilder builder = new StringBuilder();
        builder.append(filter.getProperty());
        builder.append(" ");
        builder.append(operator);
        builder.append(" \"");
        builder.append(filter.getValue());
        builder.append("\"");
        this.queryParams.add(builder.toString());
    }



}
