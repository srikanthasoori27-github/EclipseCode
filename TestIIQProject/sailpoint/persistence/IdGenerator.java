package sailpoint.persistence;

import java.io.Serializable;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.UUIDGenerator;

import sailpoint.object.SailPointObject;

public class IdGenerator extends UUIDGenerator
{

    public IdGenerator() {
    }

    public Serializable generate(SharedSessionContractImplementor session,
                                 Object object)
        throws HibernateException {

        Serializable id = null;

	    if (object instanceof SailPointObject)
            id = ((SailPointObject)object).getId();

        if (id == null) {
            //Only strip here, allow custom ids for unit tests -rap
            //Need to strip "-". Might be more efficient to use compiled regex? -rap
            id = String.valueOf(super.generate(session, object)).replaceAll("-", "");

        }

        return id;
     }
}
