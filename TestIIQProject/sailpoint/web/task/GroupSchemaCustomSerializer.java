package sailpoint.web.task;

import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;
import sailpoint.web.BaseTaskBean;
import sailpoint.web.extjs.ApplicationSchemaSerializer;

/**
 * Created by tapash.majumder on 8/26/14.
 *
 * Performs custom serialization of Application object type (groups, roles etc).
 * {@link sailpoint.web.BaseTaskBean.ICustomSerializer}
 * {@see sailpoint.web.extjs.ApplicationSchemaSerializer}
 */
public class GroupSchemaCustomSerializer implements BaseTaskBean.ICustomSerializer {

    /* (non-Javadoc)
     * @see sailpoint.web.extjs.ApplicationSchemaSerializer#serialize(java.lang.Object, sailpoint.api.SailPointContext)
     */
    @Override
    public String serialize(Object val, SailPointContext context) throws GeneralException {

        return ApplicationSchemaSerializer.serialize(val, context);
    }


    /* (non-Javadoc)
     * @see sailpoint.web.extjs.ApplicationSchemaSerializer#deserialize(java.lang.String, sailpoint.api.SailPointContext)
     */
    @Override
    public Object deserialize(String val, SailPointContext context) throws GeneralException {

        return ApplicationSchemaSerializer.deserialize(val, context);
    }
}
