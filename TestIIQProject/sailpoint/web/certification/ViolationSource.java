package sailpoint.web.certification;

public interface ViolationSource {
    public boolean inViolation( String app, String name, String value, String type );
}
