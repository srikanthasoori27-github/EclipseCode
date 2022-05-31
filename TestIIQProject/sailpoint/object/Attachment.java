package sailpoint.object;

import java.util.ArrayList;
import java.util.List;

import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.XMLClass;

/**
 * (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved.
 *
 * Used to represent a file that can be attached to other objects in IdentityIQ.
 *
 * Initially this will be used for LCM Requests.
 */
@XMLClass
public class Attachment extends SailPointObject {
    private byte[] content;

    public Attachment() {}

    public Attachment(String fileName, Identity owner) {
        setName(fileName);
        setOwner(owner);
    }

    /**
     * Get the file content as a byte array.
     * @return
     */
    public byte[] getContent() {
        return content;
    }

    /**
     * Set the content of the file
     * @param content byte array representation of the file contents
     */
    public void setContent(byte[] content) {
        this.content = content;
    }

    /**
     * @exclude
     * Let the PersistenceManager know the name is not unique.
     */
    @Override
    public boolean isNameUnique() {
        return false;
    }

    /**
     * Returns true if the attachment is referenced by an IdentityRequestItem
     * @param context
     * @return boolean
     * @throws GeneralException
     */
    public boolean isInUse(SailPointContext context) throws GeneralException {
        if (context != null) {
            QueryOptions qo = new QueryOptions();
            List<Filter> filters = new ArrayList<Filter>();
            filters.add( Filter.eq("id", getId()));
            qo.add(Filter.collectionCondition("attachments", Filter.or(filters)));
            return context.countObjects(IdentityRequestItem.class, qo) > 0;
        }
        return true;
    }

}
