package sailpoint.web.certification;

import java.util.List;

import sailpoint.object.IdentitySelector.MatchTerm;
import sailpoint.tools.Util;

public class ViolationSourceFactory {
    
    public static ViolationSource create( Class<?> c, List<?> terms ) {
        if( c.isAssignableFrom( MatchTerm.class ) ) {
            return new MatchTermsViolationSource( (List<MatchTerm>)terms );
        } else if( c.isAssignableFrom( PolicyTreeNode.class ) ) {
            return new PolicyTreeViolationSource( (List<PolicyTreeNode>)terms );
        } else if( c.isAssignableFrom( ViolationSource.class ) ) {
            return new MetaViolationSource( (List<ViolationSource>)terms );
        }
        throw new RuntimeException( "Don't know what to do with a list of " + terms.getClass().getComponentType() );
    }
    
    private static final class MetaViolationSource extends AbstractPolicyTreeViolationSource<ViolationSource>{
        public MetaViolationSource( List<ViolationSource> list ) {
            super( list );
        }

        @Override
        protected boolean itemIsEqual( String app, String name, String value, String type, ViolationSource item) {
            return item.inViolation( app, name, value, type );
        }

        @Override
        protected ViolationSource getItemInViolation(String app, String name, String value, String type) {
            for (ViolationSource t : Util.safeIterable(getList())) {
                if (itemIsEqual(app, name, value, type, t)) {
                    return t;
                }
            }

            return null;
        }
    }
    
    private static final class MatchTermsViolationSource extends AbstractPolicyTreeViolationSource<MatchTerm> {
        public MatchTermsViolationSource( List<MatchTerm> terms ) {
            super( terms );
        }

        protected boolean itemIsEqual( String app, String name, String value, String type, MatchTerm item ) {
            boolean response = true;
            response &= areStringsEqual( app, ( item.getApplication() == null ? item.getTargetSource() != null ? item.getTargetSource().getName() : null : item.getApplication().getName() ) );
            response &= areStringsEqual( name, item.getName() );
            response &= areStringsEqual( value, item.getValue() );
            response &= areStringsEqual( type, item.getType() != null ? item.getType().name() : null);
            return response;
        }

        @Override
        protected MatchTerm getItemInViolation(String app, String name, String value, String type) {
            for (MatchTerm t : Util.safeIterable(getList())) {
                if (itemIsEqual(app, name, value, type, t)) {
                    return t;
                }
            }

            return null;
        }
    }

    private static final class PolicyTreeViolationSource extends AbstractPolicyTreeViolationSource<PolicyTreeNode> {

        public PolicyTreeViolationSource( List<PolicyTreeNode> list ) {
            super( list );
        }

        protected boolean itemIsEqual( String app, String name, String value, String type, PolicyTreeNode item ) {
            boolean response = true;
            if( app == null ) {
                response &= ( null == item.getApplication() || "null".equals( item.getApplication() ) ); 
            } else {
                response &= areStringsEqual( app, item.getApplication() );
            }
            response &= areStringsEqual( name, item.getName() );
            response &= areStringsEqual( value, item.getValue() );
            response &= areStringsEqual( type, item.getType());
            return response;
        }

        @Override
        protected PolicyTreeNode getItemInViolation(String app, String name, String value, String type) {
            for (PolicyTreeNode n : Util.safeIterable(getList())) {
                if (itemIsEqual(app, name, value, type, n)) {
                    return n;
                }
            }

            return null;
        }
    }

}
    
