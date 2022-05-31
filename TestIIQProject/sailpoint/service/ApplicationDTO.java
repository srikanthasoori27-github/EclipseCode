package sailpoint.service;

import sailpoint.object.Application;
import sailpoint.object.ColumnConfig;
import sailpoint.object.CorrelationConfig;
import sailpoint.object.DirectAssignment;
import sailpoint.object.Filter;
import sailpoint.object.Schema;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ApplicationDTO extends BaseDTO {

	private static final String ATTR_ACCEL_PACK_ENABLED = "acceleratorPackEnabled";

	/**
	 * Application ID
	 */
	private String id;

	/**
	 * Application Name
	 */
	private String name;

	/**
	 * is authoritative app?
	 */
	private boolean authoritative;

	/**
	 * ListFilterValue for managerCorrelation
	 */
	private ListFilterValue managerCorrelationFilter;

	/**
	 * application features
	 */
	List<String> features;

	/**
	 * ListFilterValue for identityCorrelation
	 */
	private ListFilterValue identityCorrelationFilter;

	/**
	 * boolean to indicate if identityCorrelationFilter is invalid
	 */
	private boolean identityCorrelationFilterInvalidState;

	/**
	 * List of ListFilterValue for serviceAccountFilter
	 * Expectation is that this is a list of one value
	 */
	private List<ListFilterValue> serviceAccountFilter;

	/**
	 * boolean to indicate if serviceAccountFilter is invalid
	 */
	private boolean serviceAccountFilterInvalidState;

	/**
	 * List of ListFilterValue for rpaAccountFilter
	 * Expectation is that this is a list of one value
	 */
	private List<ListFilterValue> rpaAccountFilter;

	/**
	 * boolean to indicate if rpaAccountFilter is invalid
	 */
	private boolean rpaAccountFilterInvalidState;


	/**
	 * List of ListFilterValue for disableAccountFilter
	 * Expectation is that this is a list of one value
	 */
	private List<ListFilterValue> disableAccountFilter;

	/**
	 * boolean to indicate if disableAccountFilter is invalid
	 */
	private boolean disableAccountFilterInvalidState;

	/**
	 * boolean to indicate if lockAccountFilter is invalid
	 */
	private boolean lockAccountFilterInvalidState;

	private static final List<ListFilterValue.Operation> allowedAccountFilters =
			Arrays.asList(new ListFilterValue.Operation[]{
					ListFilterValue.Operation.Equals,
					ListFilterValue.Operation.StartsWith,
					ListFilterValue.Operation.EndsWith,
					ListFilterValue.Operation.Contains
			});

	/**
     * List of ListFilterValue for lockAccountFilter
	 * Expectation is that this is a list of one value
     */
	private List<ListFilterValue> lockAccountFilter;

	/**
	 * the application type
	 */
	private String type;

	/**
	 * true if this application has been onboarded by Accelerator Pack
	 */
	private boolean acceleratorPackEnabled;

    /**
     * list of schemas that could be marked as not requestables
     */
    private List<SuggestObjectDTO> allowedSchemaValues;

    /**
     * selection of not requestable schemas
     */
    private List<SuggestObjectDTO> notRequestableSchemas;

    /**
     * indicates if application should create entitlements as requestable
     */
    private boolean createNotRequestableEntitlements;

	//////////////////////////////
	// Constructors
	//////////////////////////////

	public ApplicationDTO() {}

	public ApplicationDTO(String appName) {
		setName(appName);
	}

	public ApplicationDTO(Application application) {
		populate(application);
	}

	public ApplicationDTO(Map<String,Object> app, List<ColumnConfig> cols, List<String> additionalColumns) {
		super(app, cols, additionalColumns);
	}

	///////////////////////////////////////////////
	// Getters/Setters
	///////////////////////////////////////////////

	/**
	 * @return Application Id
	 */
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	/**
	 * @return Name of the application
	 */
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getType() { return type; }

	public void setType(String type) { this.type = type;}

	public void populate(Application application) {
		this.name = application.getName();
		this.id = application.getId();
		this.managerCorrelationFilter = convertLeafFilterToListFilterValue(application.getManagerCorrelationFilter());
		this.identityCorrelationFilterInvalidState = false;
		CorrelationConfig correlationConfig = application.getAccountCorrelationConfig();
		this.identityCorrelationFilter = calcIdentityCorrelationFilter(correlationConfig);
		setType(application.getType());

		String featureString = application.getFeaturesString();
		if (Util.isNotNullOrEmpty(featureString)) {
			this.features = sailpoint.tools.Util.csvToList(featureString);
		}

		this.serviceAccountFilter = application.getServiceAccountFilter();
		this.disableAccountFilterInvalidState = notDisplayableFilter(this.serviceAccountFilter);
		this.rpaAccountFilter = application.getRpaAccountFilter();
		this.rpaAccountFilterInvalidState = notDisplayableFilter(this.rpaAccountFilter);
		this.disableAccountFilter = application.getDisableAccountFilter();
		this.disableAccountFilterInvalidState = notDisplayableFilter(this.disableAccountFilter);
		this.lockAccountFilter = application.getLockAccountFilter();
		this.lockAccountFilterInvalidState = notDisplayableFilter(this.lockAccountFilter);
		this.authoritative = application.isAuthoritative();
		this.acceleratorPackEnabled = application.getBooleanAttributeValue(ATTR_ACCEL_PACK_ENABLED );
        populateNotRequestableEntitlementConfig(application);
    }

    /**
     * Populates the setup for the requestable entitlements configuration at
     * application and schemas levels, also, the list of schemas to potentially
     * marked as not requestable.
     *
     * @param schemas
     *            The schemas of the corresponding application.
     */
    private void populateNotRequestableEntitlementConfig(Application app) {
        Schema accountSchema = app.getAccountSchema();
        List<String> objectTypes = app.getGroupSchemaObjectTypes();

        List<SuggestObjectDTO> allowedSchemaValues = new ArrayList<>();
        List<SuggestObjectDTO> notRequestableSchemas = new ArrayList<>();
        SuggestObjectDTO suggestObject = null;

        for (String objectType : Util.safeIterable(objectTypes)) {
            if (accountSchema != null) {
                // If group attribute in the account schema, then the schema requestable.
                if (accountSchema.getGroupAttribute(objectType) != null) {
                    Schema schema = app.getSchema(objectType);
                    suggestObject = new SuggestObjectDTO(schema.getId(), schema.getNativeObjectType(),
                            schema.getNativeObjectType());
                    allowedSchemaValues.add(suggestObject);
                    if (Util.getBoolean(schema.getConfig(), Schema.ATTR_NOT_REQUESTABLE)) {
                        notRequestableSchemas.add(suggestObject);
                    }
                }
            }
        }
        // UI directives expect a null instance of empty collections
        this.allowedSchemaValues = !allowedSchemaValues.isEmpty() ? allowedSchemaValues : null;
        this.notRequestableSchemas = !notRequestableSchemas.isEmpty() ? notRequestableSchemas : null;
        this.createNotRequestableEntitlements = app.getNotCreateRequestableEntitlements() != null;
    }

	private boolean validateCorrelationConfig(CorrelationConfig correlationConfig) {
		List<Filter> correlationConfigAttributeAssignments = correlationConfig.getAttributeAssignments();
		List<DirectAssignment> correlationConfigDirectAssignments = correlationConfig.getDirectAssignments();

		if (Util.isEmpty(correlationConfigDirectAssignments) && Util.isEmpty(correlationConfigAttributeAssignments)) {
			return true;
		}
		if (!Util.isEmpty(correlationConfigDirectAssignments) || correlationConfigAttributeAssignments.size() > 1) {
			return false;
		}
		// check the type of the only AttributeAssignment filter present
		Filter filter = correlationConfigAttributeAssignments.get(0);
		if (filter == null) return true;
		if (filter instanceof Filter.CompositeFilter) {
			return false;
		}

		return true;
	}

    public List<SuggestObjectDTO> getAllowedSchemaValues() {
        return allowedSchemaValues;
    }

    public void setAllowedSchemaValues(List<SuggestObjectDTO> allowedSchemaValues) {
        this.allowedSchemaValues = allowedSchemaValues;
    }

    public List<SuggestObjectDTO> getNotRequestableSchemas() {
        return notRequestableSchemas;
    }

    public void setNotRequestableSchemas(List<SuggestObjectDTO> notRequestableSchemas) {
        this.notRequestableSchemas = notRequestableSchemas;
    }

    public boolean isCreateNotRequestableEntitlements() {
        return createNotRequestableEntitlements;
    }

    public void setCreateNotRequestableEntitlements(boolean createNotRequestableEntitlements) {
        this.createNotRequestableEntitlements = createNotRequestableEntitlements;
    }

	private boolean notDisplayableFilter(List<ListFilterValue> filterList) {
		// Any disable or lock account filter that has more than one item is too complex to display
		if (Util.nullSafeSize(filterList) > 1) {
			return true;
		}

		if(Util.nullSafeSize(filterList) > 0) {
			ListFilterValue listFilterValue = filterList.get(0);
			if((listFilterValue != null) &&
					(!allowedAccountFilters.contains(listFilterValue.getOperation()))) {
				return true;
			}
		}
		return false;
	}

	public ListFilterValue calcIdentityCorrelationFilter(CorrelationConfig correlationConfig) {
		if (correlationConfig == null) {
			return null;
		}

		if (!validateCorrelationConfig(correlationConfig)) {
			this.identityCorrelationFilterInvalidState = true;
			return null;
		}

		if (correlationConfig.getAttributeAssignments() == null ||
				correlationConfig.getAttributeAssignments().get(0) == null) {
			// a sad, empty CorrelationConfig
			return null;
		} else {
			// At this point, only 1 leafFilter is present in getAttributeAssignment
			Filter.LeafFilter correlationConfigLeafFilter = new Filter.LeafFilter((Filter.LeafFilter)correlationConfig.getAttributeAssignments().get(0));
			return convertLeafFilterToListFilterValue(correlationConfigLeafFilter);
		}
	}

	/**
	 * @return managerLeafFilter
	 */
	public ListFilterValue getManagerCorrelationFilter() {
		return managerCorrelationFilter;
	}

	public void setManagerCorrelationFilter(ListFilterValue listFilterValue) {
		this.managerCorrelationFilter = listFilterValue;
	}

	public ListFilterValue convertLeafFilterToListFilterValue(Filter.LeafFilter leafFilter) {
		if (leafFilter == null) return null;

		ListFilterValue listFilterValue = new ListFilterValue();
		listFilterValue.setProperty(leafFilter.getProperty());
		listFilterValue.setValue(leafFilter.getValue());
		listFilterValue.setOperation(ListFilterValue.Operation.Equals);
		return listFilterValue;
	}

	public List<String> getFeatures() {
		return features;
	}

	public void setFeatures(List<String> features) {
		this.features = features;
	}
	
	/**
	 * @return identityLeafFilter
	 */
	public ListFilterValue getIdentityCorrelationFilter() {
		return identityCorrelationFilter;
	}

	public void setIdentityCorrelationFilter(ListFilterValue leafFilter) {
		this.identityCorrelationFilter = leafFilter;
	}

	/**
	 * @return identityCorrelationFilterInvalidState
	 */
	public boolean isIdentityCorrelationFilterInvalidState() {
		return identityCorrelationFilterInvalidState;
	}

	public void setIdentityCorrelationFilterInvalidState(boolean b) {
		this.identityCorrelationFilterInvalidState = b;
	}

	/**
	 * @return `serviceAccount`Filter
	 */
	public List<ListFilterValue> getServiceAccountFilter() {
		return serviceAccountFilter;
	}

	public void setServiceAccountFilter(List<ListFilterValue> serviceAccountFilter) {
		this.serviceAccountFilter = serviceAccountFilter;
	}

	/**
	 * @return serviceAccountFilterInvalidState
	 */
	public boolean isServiceAccountFilterInvalidState() {
		return serviceAccountFilterInvalidState;
	}

	public void setServiceAccountFilterInvalidState(boolean b) {
		this.serviceAccountFilterInvalidState = b;
	}


	/**
	 * @return `rpaAccount`Filter
	 */
	public List<ListFilterValue> getRpaAccountFilter() {
		return rpaAccountFilter;
	}

	public void setRpaAccountFilter(List<ListFilterValue> rpaAccountFilter) {
		this.rpaAccountFilter = rpaAccountFilter;
	}

	/**
	 * @return rpaAccountFilterInvalidState
	 */
	public boolean isRpaAccountFilterInvalidState() {
		return rpaAccountFilterInvalidState;
	}

	public void setRpaAccountFilterInvalidState(boolean b) {
		this.rpaAccountFilterInvalidState = b;
	}


	/**
	 * @return `disableAccount`Filter
	 */
	public List<ListFilterValue> getDisableAccountFilter() {
		return disableAccountFilter;
	}

	public void setDisableAccountFilter(List<ListFilterValue> disableAccountFilter) {
		this.disableAccountFilter = disableAccountFilter;
	}

	/**
	 * @return disableAccountFilterInvalidState
	 */
	public boolean isDisableAccountFilterInvalidState() {
		return disableAccountFilterInvalidState;
	}

	public void setDisableAccountFilterInvalidState(boolean b) {
		this.disableAccountFilterInvalidState = b;
	}

	/**
	 * @return lockAccountFilter
	 */
	public List<ListFilterValue> getLockAccountFilter() {
		return lockAccountFilter;
	}

	public void setLockAccountFilter(List<ListFilterValue> lockAccountFilter) {
		this.lockAccountFilter = lockAccountFilter;
	}

	/**
	 * @return lockAccountFilterInvalidState
	 */
	public boolean isLockAccountFilterInvalidState() {
		return lockAccountFilterInvalidState;
	}

	public void setLockAccountFilterInvalidState(boolean b) {
		this.lockAccountFilterInvalidState = b;
	}

	public boolean isAuthoritative() {
		return authoritative;
	}

	public void setAuthoritative(boolean authoritative) {
		this.authoritative = authoritative;
	}

	public boolean isAcceleratorPackEnabled() {
		return acceleratorPackEnabled;
	}

	public void setAcceleratorPackEnabled(boolean acceleratorPackEnabled) {
		this.acceleratorPackEnabled = acceleratorPackEnabled;
	}

}
