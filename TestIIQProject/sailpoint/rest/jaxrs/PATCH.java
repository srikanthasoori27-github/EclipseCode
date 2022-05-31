package sailpoint.rest.jaxrs;

import javax.ws.rs.HttpMethod;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom @PATCH annotation for REST resources.
 * This HTTP method is useful for partial updates to objects.
 * 
 * When using Jersey testing methods with this annotation, you may instead
 * have to use POST method with X-HTTP-Method-Override=PATCH in the header
 * (see MethodOverrideFilter.java)
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@HttpMethod("PATCH")
public @interface PATCH {
}