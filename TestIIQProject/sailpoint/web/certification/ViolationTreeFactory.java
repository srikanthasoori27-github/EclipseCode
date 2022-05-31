package sailpoint.web.certification;


import sailpoint.object.IdentitySelector;

public class ViolationTreeFactory {
    
    public static final PolicyTreeNode createViolationTree( PolicyTreeNode policyTree, ViolationSource violationSource ) {
        PolicyTreeNode root = null;
        if( policyTree != null ) {
            root = createViolationTreeInternal( policyTree, violationSource );
        }
        return simplifyTree( root );
    }

    private static PolicyTreeNode createViolationTreeInternal( PolicyTreeNode policyNode, ViolationSource violationSource ) {
        if( policyNode.isLeaf() ) {
            if( violationSource.inViolation( policyNode.getApplication(), policyNode.getName(), policyNode.getValue(), policyNode.getType() ) ) {
                PolicyTreeNode response = new PolicyTreeNode( policyNode.getApplication(), policyNode.getName(),
                        policyNode.getValue(), policyNode.getApplicationId(), policyNode.isPermission(),
                        policyNode.getType(), policyNode.getContributingEntitlements() );

                //Contributing Entitlements live on the ViolationSource
                if (violationSource instanceof AbstractPolicyTreeViolationSource) {
                    Object o = ((AbstractPolicyTreeViolationSource) violationSource).getItemInViolation(policyNode.getApplication(),
                            policyNode.getName(), policyNode.getValue(), policyNode.getType());

                    if (o != null) {
                        Object item = o;
                        while (item instanceof AbstractPolicyTreeViolationSource) {
                            //ViolationSource contains MetaViolationSource, which contains MatchTermsViolationSource && PolicyTreeViolationSource
                            item = ((AbstractPolicyTreeViolationSource)item).getItemInViolation(policyNode.getApplication(),
                                    policyNode.getName(), policyNode.getValue(), policyNode.getType());
                        }


                        if (item instanceof IdentitySelector.MatchTerm) {
                            response.setContributingEntitlements(((IdentitySelector.MatchTerm) item).getContributingEntitlements());
                        } else if (item instanceof PolicyTreeNode) {
                            response.setContributingEntitlements(((PolicyTreeNode) item).getContributingEntitlements());
                        }
                    }

                }

                return response;
            }
            return null;
        }

        PolicyTreeNode violationNode = new PolicyTreeNode( getViolationOperator( policyNode.getOperator() ) );
        for( PolicyTreeNode child : policyNode.getChildren() ) {
            PolicyTreeNode violationChild = createViolationTree( child, violationSource );
            if( violationChild != null ) {
                violationNode.add( violationChild );
            } else {
                if ( policyNode.getOperator() == Operator.AND ) {
                    return null;
                }
            }
        }
        PolicyTreeNode response = null;
        if( violationNode.getChildCount() > 0 ) {
            response = violationNode;
        }
        return response;
    }
    
    private static PolicyTreeNode simplifyTree( PolicyTreeNode tree ) {
        PolicyTreeNode response = null;
        
        if( tree != null ) {
            if( tree.isLeaf() ) {
                response = tree;
            } else {
                int childCount = tree.getChildCount();
                if( childCount == 1 ) {
                    response = simplifyTree( tree.getChildren().iterator().next() );
                } else {
                    response = new PolicyTreeNode( tree.getOperator() );
                    for( PolicyTreeNode child : tree.getChildren() ) {
                        if( tree.getOperator().equals( child.getOperator() ) ) {
                            for( PolicyTreeNode grandChild : child.getChildren() ) {
                                response.add( simplifyTree( grandChild ) );
                            }
                        } else {
                            response.add( simplifyTree( child ) );
                        }
                    }
                }
            }
        }
        return response;
    }

    private static Operator getViolationOperator( Operator operator ) {
        if( operator == Operator.AND ) {
            return Operator.OR;
        }
        return Operator.AND;
    }
    
}
