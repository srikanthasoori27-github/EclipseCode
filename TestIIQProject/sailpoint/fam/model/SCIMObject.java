/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.fam.model;

/**
 * Any object directly retrievable from the FAM SCIM API
 */
public interface SCIMObject {

    //QueryParameters
    /**
     * To filter results use the following syntax: attributeName operator “value”
     */
     String QUERY_PARAM_FILTER = "filter";

    /**
     * The number of objects returned in a list response per page. Max page size = 200.
     */
    String QUERY_PARAM_COUNT = "count";

    /**
     * The 1-based index of the first result in the current set of list results (starts from 1)
     */
    String QUERY_PARAM_START = "startIndex";

    /**
     * To retrieve specific attributes values, add the attributeName to the attributes query part
     */
    String QUERY_PARAM_ATTRIBUTES = "attributes";



    //Operators
    String OPERATOR_EQ = "eq";
    String OPERATOR_PRESENT = "pr";
    String OPERATOR_CONTAINS = "co";
    String OPERATOR_STARTS_WITH = "sw";


    /**
     * HTTP Path to the given resource. Implemented by all Objects directly accessible via SCIM API
     * @return
     */
    String getSCIMPath();


}
