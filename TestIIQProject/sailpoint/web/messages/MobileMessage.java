package sailpoint.web.messages;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used in MessageKeys class to indicate the given message key should be included
 * for mobile page (see sailpoint.tools.Internationalizer.getMobileMessages(Locale) for usage)
 * 
 * Retention meta-annotation is required for this to be visible with reflection.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface MobileMessage {
}