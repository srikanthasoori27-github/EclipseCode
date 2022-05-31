package sailpoint.rest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.CorrelationConfig;
import sailpoint.object.Filter;
import sailpoint.object.Schema;
import sailpoint.service.ApplicationDTO;
import sailpoint.service.BaseListService;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.ListServiceColumnSelector;
import sailpoint.service.SuggestObjectDTO;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;

import static java.util.stream.Collectors.toSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ApplicationService extends BaseListService<BaseListServiceContext>  {

	private static Log log = LogFactory.getLog(ApplicationService.class);

	private Application application;
	private SailPointContext context;

	public ApplicationService(SailPointContext context, BaseListServiceContext listServiceContext, ListServiceColumnSelector columnSelector) {
		super(context, listServiceContext, columnSelector);
	}

	public ApplicationService(SailPointContext context) {
		super(context, null, null);
	}

	public ApplicationService(String applicationId, UserContext userContext) throws GeneralException {
		super(userContext.getContext(), null, null);

		if (Util.isNullOrEmpty(applicationId)) {
			throw new InvalidParameterException("applicationId");
		}

		initContext(userContext);

		this.application = context.getObjectById(Application.class, applicationId);
		if (this.application == null) {
			throw new ObjectNotFoundException(Application.class, applicationId);
		}
	}

	/**
	 * Initialize the context
	 * @param userContext UserContext
	 * @throws GeneralException
	 */
	private void initContext(UserContext userContext) throws GeneralException {
		if (userContext == null) {
			throw new InvalidParameterException("userContext");
		}

		this.context = userContext.getContext();
	}

	public void setApplication(Application application) {
		this.application = application;
	}

	/**
	 * Get applicationDTO object
	 * @param appId Application id to get the DTO for
	 * @return
	 * @throws GeneralException
	 */
	public ApplicationDTO getApplicationDTO(SailPointContext context, String appId) throws GeneralException {
		this.context = context;
		if (application == null) {
			this.application = context.getObjectById(Application.class, appId);
		}
		ApplicationDTO dto = new ApplicationDTO(this.application);

		return dto;
	}

	public ApplicationDTO getApplicationDTO() throws GeneralException {
		ApplicationDTO applicationDTO = new ApplicationDTO(this.application);

		if (null == applicationDTO) {
			throw new GeneralException("ApplicationDTO not found for id " + this.application.getId());
		}
		return applicationDTO;
	}

	public ApplicationDTO updateApplication(ApplicationDTO dto) throws GeneralException {
		return updateApplication(dto, true);
	}

	private void setLeafFilterFromListFilterValue(Filter.LeafFilter leafFilter, ListFilterValue listFilterValue) {
		leafFilter.setProperty(listFilterValue.getProperty());
		leafFilter.setValue(listFilterValue.getValue());
		leafFilter.setOperation(Filter.LogicalOperation.EQ);
	}

	public CorrelationConfig updateNewCorrelationConfig(ListFilterValue listFilterValue) throws GeneralException {
		// create a new CorrelationConfig
		CorrelationConfig newCorrelationConfig = new CorrelationConfig();

		// Find a unique name for it
		String squashedName = application.getName().replaceAll("\\s+","");
		String candidateName = squashedName + " Correlation Config";
		String uniqueName = ObjectUtil.generateUniqueName(context, null, candidateName, CorrelationConfig.class, 0);
		newCorrelationConfig.setName(uniqueName);

		// Build and set the attribute assignments on the CorrelationConfig
		List<Filter> attrAssignments = new ArrayList<Filter>();
		Filter.LeafFilter leafFilter = new Filter.LeafFilter();
		setLeafFilterFromListFilterValue(leafFilter, listFilterValue);

		attrAssignments.add(leafFilter);
		newCorrelationConfig.setAttributeAssignments(attrAssignments);
		log.debug("Creating new CorrelationConfig " + newCorrelationConfig.getName());

		return newCorrelationConfig;
	}

	public CorrelationConfig updateExistingCorrelationConfig(ListFilterValue listFilterValue, CorrelationConfig correlationConfig) {
		Filter existingFilter = null;

		// is existing too complicated?
		List<Filter> attrAssignments = correlationConfig.getAttributeAssignments();
		if (!Util.isEmpty(attrAssignments)) {
			if (!ObjectUtil.isSimple(correlationConfig)) {
				// too complicated, leave it alone
				log.debug("Existing CorrelationConfig " + correlationConfig.getName() + " is too complex.  Not updating.");
				return correlationConfig;
			}
			else {
				existingFilter = attrAssignments.get(0);
			}
		}
		else {
			attrAssignments = new ArrayList<Filter>();
			correlationConfig.setAttributeAssignments(attrAssignments);
		}

		// has it changed?
		if (existingFilter instanceof Filter.LeafFilter) {
			Filter.LeafFilter leafFilter = (Filter.LeafFilter)existingFilter;
			if (leafFilter.getOperation() == Filter.LeafFilter.LogicalOperation.EQ) {
				if (listFilterValue.getProperty().equals(leafFilter.getProperty())) {
					if (listFilterValue.getValue().equals(leafFilter.getValue())) {
						// we are going to leave alone
						log.debug("No change made to CorrelationConfig " + correlationConfig.getName());
						return correlationConfig;
					}
				}
			}
		}

		// update existing
		attrAssignments.clear();
		Filter.LeafFilter leafFilter = new Filter.LeafFilter();
		setLeafFilterFromListFilterValue(leafFilter, listFilterValue);
		attrAssignments.add(leafFilter);
		correlationConfig.setAttributeAssignments(attrAssignments);
		log.debug("Updating existing CorrelationConfig " + correlationConfig.getName());
		return correlationConfig;
	}

	public CorrelationConfig updateCorrelationConfig(ListFilterValue filterToUpdate) throws GeneralException {
		CorrelationConfig correlationConfig = application.getAccountCorrelationConfig();

		if (correlationConfig != null) {
			return updateExistingCorrelationConfig(filterToUpdate, correlationConfig);
		} else {
			return updateNewCorrelationConfig(filterToUpdate);
		}
	}

	private Filter.LeafFilter convertListFilterValueToLeafFilter(ListFilterValue listFilterValue) {
		if (listFilterValue == null) return null;

		Filter.LeafFilter leafFilter = new Filter.LeafFilter();
		leafFilter.setProperty(listFilterValue.getProperty());
		leafFilter.setValue(listFilterValue.getValue());
		leafFilter.setOperation(Filter.LeafFilter.LogicalOperation.EQ);
		return leafFilter;
	}

	/**
	 * An AccountFilter is valid only if:
	 * 	it has a single filter value
	 * 	the single filter value has a property, operation, and value
	 *
	 * We currently only support a single filter value and certain operations
	 * If in the future we support more complex filters (composite) and additional operation types
	 * (ex: some do not require a value), this will need to be refactored
	 */
	private boolean isAccountFilterValid (List<ListFilterValue> filterValueList) {
		if (Util.nullSafeSize(filterValueList) != 1) return false;
		ListFilterValue filterValue = filterValueList.get(0);
		if (filterValue == null) return false;
		if (filterValue.getProperty() == null) return false;
		if (Util.isEmpty(filterValue.getProperty())) return false;
		if (filterValue.getOperation() == null) return false;
		if (filterValue.getValue() == null) return false;
		return true;
	}

	public ApplicationDTO updateApplication(ApplicationDTO dto, boolean commit) throws GeneralException {
		ListFilterValue managerFilterValue = dto.getManagerCorrelationFilter();
		application.setManagerCorrelationFilter(convertListFilterValueToLeafFilter(managerFilterValue));
		if (dto.isIdentityCorrelationFilterInvalidState()) {
			log.debug("invalid state, so cannot update identity correlation config");
		} else {
			if (dto.getIdentityCorrelationFilter() != null) {
				if (dto.getIdentityCorrelationFilter().getOperation() == null || ListFilterValue.Operation.Equals.equals(dto.getIdentityCorrelationFilter().getOperation())) {
					CorrelationConfig correlationConfig = updateCorrelationConfig(dto.getIdentityCorrelationFilter());
					context.saveObject(correlationConfig);
					application.setAccountCorrelationConfig(correlationConfig);
				} else {
					throw new GeneralException("Invalid IdentityCorrelationFilter operation requested.");
				}
			}
			else {
				// clear the correlation config
				application.setAccountCorrelationConfig(null);
			}
		}
		// serviceAccountFilter
		if (dto.isServiceAccountFilterInvalidState()) {
			log.debug("Service Account filter cannot be displayed in the UI, previous value will be preserved");
		} else {
			List<ListFilterValue> serviceAccountFilterList = dto.getServiceAccountFilter();
			if (Util.nullSafeSize(serviceAccountFilterList) == 0) {
				// if rpaAccountFilterList is null or empty an existing Service Account Filter may need to be removed
				application.removeAttribute(application.ATTR_SERVICE_ACCOUNT_FILTER);
			} else if (isAccountFilterValid(serviceAccountFilterList)) {
				application.setServiceAccountFilter(serviceAccountFilterList);
			} else {
				throw new GeneralException("Invalid Service Account Filter configuration");
			}
		}

		// rpaAccountFilter
		if (dto.isRpaAccountFilterInvalidState()) {
			log.debug("RPA Account filter cannot be displayed in the UI, previous value will be preserved");
		} else {
			List<ListFilterValue> rpaAccountFilterList = dto.getRpaAccountFilter();
			if (Util.nullSafeSize(rpaAccountFilterList) == 0) {
				// if rpaAccountFilterList is null or empty an existing RPA Account Filter may need to be removed
				application.removeAttribute(application.ATTR_RPA_ACCOUNT_FILTER);
			}
			else if (isAccountFilterValid(rpaAccountFilterList)) {
				application.setRpaAccountFilter(rpaAccountFilterList);
			} else {
				throw new GeneralException("Invalid RPA Account Filter configuration");
			}
		}
		// disableAccountFilter
		if (dto.isDisableAccountFilterInvalidState()) {
			log.debug("Disable Account filter cannot be displayed in the UI, previous value will be preserved");
		} else {
			List<ListFilterValue> disableAccountFilterList = dto.getDisableAccountFilter();
			if (Util.nullSafeSize(disableAccountFilterList) == 0) {
				// if disableAccountFilterList is null or empty an existing Disable Account Filter may need to be removed
				application.removeAttribute(application.ATTR_ACCOUNT_DISABLE_FILTER);
			}
			else {
				if (isAccountFilterValid(disableAccountFilterList)) {
					application.setDisableAccountFilter(disableAccountFilterList);
				}
				else {
					throw new GeneralException("Invalid Disable Account Filter configuration");
				}
			}
		}
		// lockAccountFilter
		if (dto.isLockAccountFilterInvalidState()) {
			log.debug("Lock Account filter cannot be displayed in the UI, previous value will be preserved");
		} else {
			List<ListFilterValue> lockAccountFilterList = dto.getLockAccountFilter();
			if (Util.nullSafeSize(lockAccountFilterList) == 0) {
				// if lockAccountFilterList is null or empty an existing Lock Account Filter may need to be removed
				application.removeAttribute(application.ATTR_ACCOUNT_LOCK_FILTER);
			} else {
				if (isAccountFilterValid(lockAccountFilterList)) {
					application.setLockAccountFilter(lockAccountFilterList);
				} else {
					throw new GeneralException("Invalid Lock Account Filter configuration");
				}
			}
		}

        // Populates the not requestable entitlements configuration at different levels:
        // application and/or schemas
        if (dto.isCreateNotRequestableEntitlements()) {
            Set<String> notRequestableSchemaIds = Util.safeStream(dto.getNotRequestableSchemas())
                    .map(SuggestObjectDTO::getId).collect(toSet());
            Application.NotRequestableEntsLvl level = null;

            if (Util.isEmpty(notRequestableSchemaIds)) {
                level = Application.NotRequestableEntsLvl.APPLICATION;
            } else {
                level = Application.NotRequestableEntsLvl.SCHEMA;
            }
            for (Schema schema : Util.iterate(application.getSchemas())) {
                // If no schema was selected, then all the schemas are
                // not requestable.
                if (notRequestableSchemaIds.contains(schema.getId())) {
                    schema.addConfig(Schema.ATTR_NOT_REQUESTABLE, true);
                } else if (schema.containsConfig(Schema.ATTR_NOT_REQUESTABLE)) {
                    schema.removeAttribute(Schema.ATTR_NOT_REQUESTABLE);
                }
            }
            application.setAttribute(Application.ATTR_NOT_REQUESTABLE_ENTITLEMENTS, level.getText());
        } else {
            if (application.getAttributes().containsKey(Application.ATTR_NOT_REQUESTABLE_ENTITLEMENTS)) {
                application.removeAttribute(Application.ATTR_NOT_REQUESTABLE_ENTITLEMENTS);
            }
            for (Schema schema : Util.iterate(application.getSchemas())) {
                if (schema.containsConfig(Schema.ATTR_NOT_REQUESTABLE)) {
                    schema.removeAttribute(Schema.ATTR_NOT_REQUESTABLE);
                }
            }
        }

		context.saveObject(application);
		if (commit) {
			context.commitTransaction();
		}

		return getApplicationDTO();
	}
}
