/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

/**
 * Hibernate uses dynamically generated proxy classes to wrap persistent
 * objects.  While these usually behave fairly sanely, there is a known problem
 * with using instanceof with elements from a collection of polymorphic objects.
 * See http://www.hibernate.org/280.html for more details.  To get around this,
 * one can add a set of methods to the superclass and implement them on the
 * subclasses to return themselves if they are of the desired subtype.  For
 * example, consider two classes Cat and Dog that extend Pet.  Trying to do
 * an instanceof check does not work, so we would add a couple of methods to
 * Pet - Dog getMeIfIAmADog() and Cat getMeIfIAmACat().  Both methods are
 * implemented in Pet to return null, and overridden in the appropriate subclass
 * to return "this" if they are of the desired type.  Unfortunately, the
 * hibernate proxies are a little too smart for their own good and will return
 * the proxy class rather than the actual object if you try to return "this"
 * from a method.  Returning the proxy does not work because it cannot be cast
 * to the subclass, hence ... the mule.
 * <p>
 * According to wikipedia (http://en.wikipedia.org/wiki/Mule_%28smuggling%29):
 * A mule or courier is someone who smuggles something with him or her (as
 * opposed to sending by mail, etc.) across a national border, including
 * smuggling into and out of an international plane, especially a small amount,
 * transported for a smuggling organization.
 * <p>
 * Similar to a real-life "mule", this class is intended to smuggle the "this"
 * out of hibernate land, through the dynamic proxy, and into freedom.  I
 * considered naming this class "Harriet Tubman" because this seemed a bit more
 * benevolent and kind of fits with the goal of emancipating our object from the
 * clutches of hibernate.  However, I settled on mule because this is an extreme
 * hack and could be considered a bit disparaging to Harriet.  
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class Mule<T>
{
    private T payload;

    /**
     * Swallow the payload.
     * 
     * @param  payload  Don't worry - we double-bagged it.  Just don't do any
     *                  jumping jacks and you should be fine.
     */
    public Mule(T payload)
    {
        this.payload = payload;
    }

    /**
     * Retrieve the payload from the mule.  We'll leave this one alone.
     * 
     * @return  The original payload ... YOU'RE FREE!!!
     */
    public T extractPayload()
    {
        return this.payload;
    }
}
