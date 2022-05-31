package sailpoint.web.certification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointFactory;
import sailpoint.object.Filter;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Util;
import sailpoint.web.util.WebUtil;

public final class PolicyViolationJsonUtil {
    /**
     * Converts PolicyTreeNode JSON List into List of PolicyTreeNode objects   
     *   
     * @param json The JSON value to Decode
     * @return Decoded list
     */
    public static List<PolicyTreeNode> decodeSelectedEntitlementsJson( String json ) throws GeneralException{
        List<PolicyTreeNode> response = new ArrayList<PolicyTreeNode>();
        if (!Util.isNullOrEmpty(json)) {
            response = JsonHelper.listFromJson(PolicyTreeNode.class, json);
        }

        return response;
    }

    public static String encodeEntitlementViolationTree( PolicyTreeNode violations, List<PolicyTreeNode> selectedViolations, Locale locale ) {
        //Prepare
        prepareEntitlementViolationTree(violations, selectedViolations, locale);
        return JsonHelper.toJson(violations);
    }

    /**
     * Convert list of policy tree nodes to JSON payload
     *
     * @param entitlements List<PolicyTreeNode> list of entitlements to convert
     * @return JSON string representation of list of entitlements
     */
    public static String encodeEntitlementsPolicyTreeNodeList(List<PolicyTreeNode> entitlements) {
        return JsonHelper.toJson(entitlements);
    }

    /**
     * Given a policy tree and the selected violations tree, walk the tree,
     * marking any nodes that have been selected.
     */
    public static PolicyTreeNode prepareEntitlementViolationTree(PolicyTreeNode node, List<PolicyTreeNode> selectedViolations, Locale locale ) {
        if (node == null) {
            return null;
        }

        if (node != null && selectedViolations != null && !selectedViolations.isEmpty()){
            node.setSelected(selectedViolations.contains(node));
        }

        if (node != null && node.getChildCount() > 0){
            for(PolicyTreeNode child : node.getChildren()){
                prepareEntitlementViolationTree(child, selectedViolations, locale);
            }
        }

        if (node != null && node.isLeaf()) {
            Filter filters = Filter.eq( "application.id", node.getApplicationId() );
            filters = Filter.and( filters, Filter.eq( "attribute", node.getName() ) );
            if( node.isPermission() ) {
                filters = Filter.and( filters, Filter.eq( "type", ManagedAttribute.Type.Permission.name() ) );
            } else {
                filters = Filter.and( filters, Filter.ne( "type", ManagedAttribute.Type.Permission.name() ) );
                filters = Filter.and( filters, Filter.eq( "value", node.getValue() ) );
            }
            QueryOptions queryOptions = new QueryOptions( filters );
            List<ManagedAttribute> attributes;
            try {
                attributes = SailPointFactory.getCurrentContext().getObjects( ManagedAttribute.class, queryOptions );
                if( attributes != null && attributes.size() == 1 ) {
                    ManagedAttribute attribute = attributes.get( 0 );
                    node.setDisplayValue(attribute.getDisplayName());
                    node.setDescription(WebUtil.stripHTML( attribute.getDescription( locale ) ));
                }
            } catch ( GeneralException ignore ) {
                // ignore me
            }
        }

        return node;
    }
    
    private static Log log = LogFactory.getLog( PolicyViolationJsonUtil.class );
}
