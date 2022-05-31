package sailpoint.web.certification;

import java.util.List;

public abstract class AbstractPolicyTreeViolationSource<T> implements ViolationSource {

    public AbstractPolicyTreeViolationSource( List<T> list ) {
        this.list = list;
        
    }
    protected boolean areStringsEqual( String s1, String s2 ) {
        boolean response = false;
        if( s1 == null ) {
            response = s2 == null;
        } else {
            response = s1.equals( s2 );
        }
        return response;
    }
    
    public boolean inViolation( String app, String name, String value, String type ) {
        for ( T item : list ) {
            if ( itemIsEqual( app, name, value, type, item ) ) {
                return true;
            }
        }
        return false;
    }

    protected List<T> getList() { return list; }

    private final List<T> list;
    abstract protected boolean itemIsEqual( String app, String name, String value, String type, T item );

    abstract protected T getItemInViolation( String app, String name, String value, String type);

}