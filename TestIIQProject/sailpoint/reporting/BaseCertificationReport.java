/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting;

import sailpoint.object.Attributes;
import sailpoint.reporting.datasource.BaseCertificationDataSource;
import sailpoint.reporting.datasource.TopLevelDataSource;
import sailpoint.tools.GeneralException;

/**
 * Base class for certification reports that handles populating the datasource
 * with some common filtering options.  Subclasses should implement the
 * internalCreateDataSource(), which will be adorned with the common options.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public abstract class BaseCertificationReport extends JasperExecutor {

    // List of attributes coming from the report form. Each of the date attributes
    // is appended with START, END or USE_ATTRIBUTE so that we can cut down on the
    // number of definitions we have to do here. It also allows us to use some
    // common functions to retrieve the values.
    public static final String ATTRIBUTE_EXPIRATION = "expirationDate";
    public static final String ATTRIBUTE_CREATED = "creationDate";
    public static final String ATTRIBUTE_SIGNED = "signedDate";

    // These two values are appended to an attribute value to indicate
    // which end of the date range the value is
    public static final String START = "Start";
    public static final String END = "End";

    // Attribute holding the IDs of the Tags to filter on.
    public static final String ATTRIBUTE_TAGS_IDS = "tags";
    

    /**
     * Default constructor.
     */
    public BaseCertificationReport() {
        super();
    }

    /* (non-Javadoc)
     * @see sailpoint.reporting.JasperExecutor#getDataSource()
     */
    @Override
    protected TopLevelDataSource getDataSource() throws GeneralException {

        BaseCertificationDataSource datasource = internalCreateDataSource();
        
        Attributes<String,Object> attributes = getInputs();

        if (attributes.getDate(ATTRIBUTE_CREATED  + START )!=null)
            datasource.setCreatedStartDate(attributes.getDate(ATTRIBUTE_CREATED  + START));

        if (attributes.getDate(ATTRIBUTE_CREATED  + END)!=null)
            datasource.setCreatedEndDate(attributes.getDate(ATTRIBUTE_CREATED  + END));

        if (attributes.getDate(ATTRIBUTE_SIGNED  + START)!=null)
            datasource.setSignedStartDate(attributes.getDate(ATTRIBUTE_SIGNED  + START));

        if (attributes.getDate(ATTRIBUTE_SIGNED  + END)!=null)
            datasource.setSignedEndDate(attributes.getDate(ATTRIBUTE_SIGNED  + END));

        if (attributes.getDate(ATTRIBUTE_EXPIRATION  + START)!=null)
            datasource.setExpirationStartDate(attributes.getDate(ATTRIBUTE_EXPIRATION  + START));

        if (attributes.getDate(ATTRIBUTE_EXPIRATION  + END)!=null)
            datasource.setExpirationEndDate(attributes.getDate(ATTRIBUTE_EXPIRATION  + END));

        String tagIds = attributes.getString(ATTRIBUTE_TAGS_IDS);
        if (null != tagIds) {
            datasource.setTagIds(ReportParameterUtil.splitAttributeValue(tagIds));
        }
        
        return datasource;
    }

    /**
     * Subclasses should override this to create the datasource with any options
     * specific to the report set.  This is a template method called by
     * getDataSource(), which will later be adorned with the common filtering
     * options.
     */
    protected abstract BaseCertificationDataSource internalCreateDataSource()
        throws GeneralException;
}
