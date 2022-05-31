package sailpoint.persistence;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.Assigned;
import java.io.Serializable;


import sailpoint.object.SailPointObject;

import java.util.UUID;

public class AssignedKeyGenerator extends Assigned
{

    public Serializable generate(SharedSessionContractImplementor session,
                                 Object object)
        throws HibernateException

     {
	    if (object instanceof SailPointObject)
	    {
	        SailPointObject spo = (SailPointObject) object;
	        if (spo.getId() != null)
	        {
	            return spo.getId();
	        }
	    }

            String id = UUID.randomUUID().toString();
            // jsl - I like not having the hyphens it makes
            // them a little shorter and less distracting in the XML
            // and emacs can skip over them without stopping
            id = id.replaceAll("-", "");

            return id;
     }
}
