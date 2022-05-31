package sailpoint.web.certification;

import java.util.ArrayList;
import java.util.List;

import sailpoint.object.GenericConstraint;
import sailpoint.object.IdentitySelector;
import sailpoint.object.IdentitySelector.MatchExpression;
import sailpoint.object.IdentitySelector.MatchTerm;

public class PolicyTreeNodeFactory {

    public static PolicyTreeNode create( GenericConstraint constraint ) {
        PolicyTreeNode response = new PolicyTreeNode( Operator.AND );
        for( IdentitySelector selector : constraint.getSelectors() ) {
            PolicyTreeNode node = createNode( selector.getMatchExpression() );
            response.add( node );
        }
        return response;
    }

    private static PolicyTreeNode createNode( MatchExpression expression ) {
        PolicyTreeNode response;
        if( expression.isAnd() ) {
            response = new PolicyTreeNode( Operator.AND );
        } else {
            response = new PolicyTreeNode( Operator.OR );
        }
        
        for( MatchTerm term : expression.getTerms() ) {
            response.add( createNode( term ) );
        }
        return response;
    }
    
    private static PolicyTreeNode createNode( MatchTerm term ) {
        PolicyTreeNode response;
        if( term.isContainer() ) {
            Operator operator = Operator.OR;
            if( term.isAnd() ) {
                operator = Operator.AND;
            }
            response = new MatchTermPolicyTreeNode( operator, term );
        } else {
            response = new MatchTermPolicyTreeNode( term );
        }
        return response;
    }
    
    private static class MatchTermPolicyTreeNode extends PolicyTreeNode {
        private MatchTermPolicyTreeNode( Operator operator, MatchTerm term ) {
            super( operator );
            initializeChildren( term );
        }
    
        private MatchTermPolicyTreeNode( MatchTerm constraint ) {

            super( constraint.getName(), constraint.getValue(), constraint.isPermission(), constraint.getType() != null ? constraint.getType().name() : null, constraint.getContributingEntitlements() );
            String source, sourceType, sourceId;

            source = sourceType = sourceId = null;

            if (constraint.getApplication() != null) {
                source = constraint.getApplication().getName();
                sourceId = constraint.getApplication().getId();
                sourceType = PolicyTreeNode.TYPE_APPLICATION;
            } else if (constraint.getTargetSource() != null) {
                source = constraint.getTargetSource().getName();
                sourceId = constraint.getTargetSource().getId();
                sourceType = PolicyTreeNode.TYPE_TARGET_SOURCE;
            } else {
                sourceId = "IIQ";
            }

            setApplication(source);
            setApplicationId(sourceId);
            setSourceType(sourceType);


            initializeChildren( constraint );
        }
        
        private void initializeChildren( MatchTerm matchTerm ) {
            for( MatchTerm term : matchTerm.getChildren() ) {
                children.add( createNode( term ) );
            }
        }
        
        @Override
        public List<PolicyTreeNode> getChildren() {
            return children;
        }
        
        @Override
        public int getChildCount() {
            return children.size();
        }
    
        @Override
        public void add( PolicyTreeNode child ) {
            children.add( child );
        }
        
        private final List<PolicyTreeNode> children = new ArrayList<PolicyTreeNode>();
    }
}
