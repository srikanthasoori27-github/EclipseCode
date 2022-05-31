package sailpoint.service.listfilter;

import sailpoint.api.SailPointContext;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.UIConfig;
import sailpoint.service.LCMConfigService;
import sailpoint.service.useraccess.UserAccessSearchOptions;
import sailpoint.service.useraccess.UserAccessService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Abstract implementation of ListFilterContext for various UserAccessService lists
 */
public abstract class BaseUserAccessFilterContext extends BaseListFilterContext {
    
    protected static final String IDENTITY_ATTR_PREFIX = "Identity.";
    protected static final String PROPERTY_IDENTITY_IDS = "identityIds";
    protected static final String IDENTITY_ID_POSTFIX = ".id";

    protected Identity requester;
    protected Identity target;
    private UserAccessSearchOptions.ObjectTypes objectType;
    private String enabledAttributesKey;
    protected String quickLink;

    /**
     * Constructor
     * @param objectConfig ObjectConfig
     * @param enabledAttributesKey Key for UIConfig value containing CSV list of attributes to include. Can be null.
     * @param objectType ObjectTypes. Can be null.
     * @param requester Identity requesting access. Can be null.
     * @param target Identity being targeted by request. Can be null.
     */
    public BaseUserAccessFilterContext(ObjectConfig objectConfig, String enabledAttributesKey, 
                                       UserAccessSearchOptions.ObjectTypes objectType, Identity requester, Identity target) {
        super(objectConfig);
        
        this.requester = requester;
        this.target = target;
        this.objectType = objectType;
        this.enabledAttributesKey = enabledAttributesKey;
    }

    public void setQuickLink(String qlName) {
        this.quickLink = qlName;
    }

    public String getQuickLink() { return this.quickLink; }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEnabled(SailPointContext context) throws GeneralException {
        boolean enabled = true;

        // If we have an object type, call through to UserAccessService to see if enabled or not.
        if (objectType != null) {
            enabled = new UserAccessService(context).isEnabled(this.objectType, this.requester, this.target);
        }
        return enabled;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ObjectAttribute> getFilterObjectAttributes(SailPointContext context) throws GeneralException {
        List<ObjectAttribute> attributes = super.getFilterObjectAttributes(context);
        
        // If the enabledAttributeKey is defined, look for the list of allowed attributes in UIConfig
        List<String> enabledAttributes = new ArrayList<String>();
        if (this.enabledAttributesKey != null) {
            UIConfig config = UIConfig.getUIConfig();
            if (config!=null) {
                enabledAttributes = config.getList(this.enabledAttributesKey);
            }
        }
        
        // If the value is empty, we let everything through
        boolean filterAttributes = !Util.isEmpty(enabledAttributes);
        if (filterAttributes) {
            Iterator<ObjectAttribute> attributesIterator = attributes.iterator();
            while (attributesIterator.hasNext()) {
                ObjectAttribute attribute = attributesIterator.next();
                if (!Util.nullSafeContains(enabledAttributes, attribute.getName())) {
                    attributesIterator.remove();
                }
            }
                
        }
        return attributes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Filter.MatchMode getMatchMode(SailPointContext context) throws GeneralException {
        return new LCMConfigService(context).getSearchMode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Filter convertFilter(String propertyName, ListFilterValue filterValue, SailPointContext context) throws GeneralException {        
        // Just bail, cant do anything without a property
        if (propertyName == null) {
            return null;
        }

        Filter filter = null;
        
        /* Ugh. Classic UI sends .id extension on the end of its property name if the type is Identity
         * So we have to chop that off, find a ListFilterDTO, and if we do then just use an straight equality. */
        if (propertyName.endsWith(IDENTITY_ID_POSTFIX)) {
            String newPropertyName = propertyName.substring(0, propertyName.length() - IDENTITY_ID_POSTFIX.length());
            if (newPropertyName.startsWith(IDENTITY_ATTR_PREFIX)) {
                newPropertyName = newPropertyName.substring(IDENTITY_ATTR_PREFIX.length());
            }
            ListFilterDTO filterDTO = getFilterDTO(newPropertyName);
            if (filterDTO != null) {
                // It exists, just return an equality filter for original property name
                filter = getSimpleFilter(propertyName, getValueString(filterValue), ListFilterValue.Operation.Equals);
            }
        }

        if (filter == null) {
            filter = super.convertFilter(propertyName, filterValue, context);
        }
        
        return filter;
    }
    
    protected ListFilterDTO getFilterDTO(String identityPropertyName) {
        ListFilterDTO filterDTO = null;
        ObjectConfig objectConfig = ObjectConfig.getObjectConfig(Identity.class);
        ObjectAttribute objectAttribute = (objectConfig == null) ? null : objectConfig.getObjectAttribute(identityPropertyName);
        
        if (objectAttribute != null) {
            // Locale doesn't matter
            filterDTO = new ListFilterDTO(objectAttribute, null, getSuggestUrl());
        }
        return filterDTO;
    }
}