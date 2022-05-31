/* (c) Copyright 2012 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.tools;

/**
 * @exclude
 * Only used by unit tests to compare performance between classes that use the
 * TracingAspect vs. those that don't.  This would ideally be in the test/
 * directory, but those classes are not woven.
 */
public class TracedObject {

    private String name;
    private String firstname;
    private String lastname;
    private String pw;
    
    public TracedObject() {
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

    @Untraced
    public String getUntraced() { return  this.pw; }


    public void setPw(String s) { this.pw = s; }

    @SensitiveTraceReturn
    public String getPw() { return this.pw; }
}
