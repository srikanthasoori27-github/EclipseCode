/* (c) Copyright 2012 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.tools;

/**
 * @exclude
 * Only used by unit tests to compare performance between classes that use the
 * TracingAspect vs. those that don't.  This would ideally be in the test/
 * directory, but those classes are not woven.  This class is explicitly
 * excluded in the TracingAspect.
 */
@Untraced
public class UntracedObject {
    
    private String name;
    private String firstname;
    private String lastname;
    
    public UntracedObject() {
    }

    public String getName() {
        return this.name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public String getFirstname() {
        return this.firstname;
    }
    public void setFirstname(String firstname) {
        this.firstname = firstname;
    }

    public String getLastname() {
        return this.lastname;
    }
    public void setLastname(String lastname) {
        this.lastname = lastname;
    }
}
