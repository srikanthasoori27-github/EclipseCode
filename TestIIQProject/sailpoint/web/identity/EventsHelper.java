package sailpoint.web.identity;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import sailpoint.api.IdentityEventLogger;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.object.Request;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.object.ProvisioningRequest;
import sailpoint.tools.GeneralException;
import sailpoint.web.EventBean;

/**
 * This bean is initialized by IdentityDTO for
 * View Identity -> Events tab
 *
 */
public class EventsHelper {

    private IdentityDTO parent;
    private List<IdentityEventLogger.IdentityEvent> pastIdentityEvents;

    public EventsHelper(IdentityDTO parent) {
        this.parent = parent;
    }

    public Map<String,Boolean> getEventSelections() {
        return this.parent.getState().getEventSelections();
    }

    /**
     * Get a list of inner beans representing the identity snapshots.
     */
    public List<EventBean> getEvents()
        throws GeneralException {

        if (this.parent.getState().getEvents() == null) {
            this.parent.getState().setEvents(fetchEvents(this.parent.getObject(), this.parent.getContext()));
        }
        return this.parent.getState().getEvents();
    }

    private List<EventBean> fetchEvents(Identity identity, SailPointContext context)
            throws GeneralException {

        List<EventBean> events = new ArrayList<EventBean>();

        if (null != identity) {
            // first get Requests
            addRequestEvents(identity, context, events);

            // then ProvisioningRequests
            addProvisioningRequestEvents(identity, context, events);
        }

        return events;
    }

    @SuppressWarnings("unchecked")
    private void addRequestEvents(Identity identity, SailPointContext context,
            List<EventBean> events) throws GeneralException {

        List<String> columns = new ArrayList<String>();
        columns.add("id");
        columns.add("created");
        columns.add("launcher");
        columns.add("nextLaunch");
        columns.add("name");
        columns.add("definition.name");
        columns.add("attributes");

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("owner", identity));
        ops.setOrderBy("created");
        ops.setOrderAscending(false);

        Iterator<Object[]> result = context.search(Request.class, ops,
                columns);

        if (result != null) {
            while (result.hasNext()) {
                Object[] row = result.next();
                EventBean ev = new EventBean();
                ev.setType(EventBean.Type.Request);
                ev.setId((String) row[0]);
                ev.setCreated((Date) row[1]);
                ev.setCreator((String) row[2]);
                ev.setDue((Date) row[3]);
                ev.setName((String) row[4]);
                ev.setDefinitionName((String) row[5]);
                ev.setAttributes((Attributes<String, Object>) row[6]);
                events.add(ev);
            }
        }
    }


    private void addProvisioningRequestEvents(Identity identity,
            SailPointContext context, List<EventBean> events)
            throws GeneralException {

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("identity.id", identity.getId()));
        ops.setOrderBy("created");
        List<ProvisioningRequest> preqs = context.getObjects(ProvisioningRequest.class, ops);
        if (preqs != null) {
            for (ProvisioningRequest preq : preqs) {
                EventBean ev = new EventBean();
                ev.setType(EventBean.Type.ProvisioningRequest);
                ev.setId(preq.getId());
                ev.setCreated(preq.getCreated());
                ev.setCreator(preq.getRequester());
                ev.setDue(preq.getExpiration());

                // should we invent something?
                ev.setName("Provision to " + preq.getTarget());
                ev.setDefinitionName("Provisioning");

                ev.setAttributes(getProvisioningRequestDetails(context, preq));
                events.add(ev);
            }
        }
    }

    private Attributes<String, Object> getProvisioningRequestDetails(SailPointContext ctx, ProvisioningRequest req)
            throws GeneralException {

        Attributes<String, Object> attrs = new Attributes<String, Object>();

        ProvisioningPlan plan =  req.getPlan();
        if (plan.getAccountRequests() == null) {
            return attrs;
        }

        List<Map<String, Object>> accountRequests = new ArrayList<Map<String,Object>>();
        attrs.put("accountRequests", accountRequests);
        Map<AccountRequest.Operation, List<AccountRequest>> operationsToAccounts = new HashMap<>();
        for (AccountRequest accountReq : plan.getAccountRequests()) {
            Map<String, Object> accountInfo = new HashMap<String, Object>();
            accountRequests.add(accountInfo);
            accountInfo.put("application", accountReq.getApplication());
            accountInfo.put("operation", accountReq.getOperation());
            operationsToAccounts.computeIfAbsent(accountReq.getOperation(), k -> new ArrayList<>());
            operationsToAccounts.get(accountReq.getOperation()).add(accountReq);
            includeDisplayName(ctx, accountReq, accountInfo);
            if (accountReq.getAttributeRequests() != null) {
                List<Map<String, Object>> attributeRequests = new ArrayList<Map<String,Object>>();
                accountInfo.put("attributeRequests", attributeRequests);
                for (AttributeRequest attrReq : accountReq.getAttributeRequests()) {
                    Map<String, Object> attributeInfo = new HashMap<String, Object>();
                    attributeInfo.put("name", attrReq.getName());
                    attributeInfo.put("operation", attrReq.getOperation());
                    attributeInfo.put("value", attrReq.getValue());
                    attributeRequests.add(attributeInfo);
                }
            }
        }
        attrs.put("noAccountAttributesLayout", Stream.of(AccountRequest.Operation.Disable,
                AccountRequest.Operation.Enable, AccountRequest.Operation.Create, AccountRequest.Operation.Delete)
                .anyMatch(operationsToAccounts::containsKey));
        return attrs;
    }

    private void includeDisplayName(SailPointContext ctx, AccountRequest accountReq, Map<String, Object> accountInfo)
            throws GeneralException {
        QueryOptions opts = new QueryOptions(Filter.eq("nativeIdentity", accountReq.getNativeIdentity()));
        Iterator<Link> search = ctx.search(Link.class, opts);
        if (search.hasNext()) {
            accountInfo.put("displayName", search.next().getDisplayableName());
        }
    }

    public List<IdentityEventLogger.IdentityEvent> getPastIdentityEvents()
        throws GeneralException {

        if (null == this.pastIdentityEvents) {
            this.pastIdentityEvents =
                new IdentityEventLogger(this.parent.getContext()).getIdentityEvents(this.parent.getObject());
        }
        return this.pastIdentityEvents;
    }

    void addDeletedEventsToRequest(AccountRequest account) {

        // jsl - it's more complicated now since the one event bean
        // list stores events for different things...
        AttributeRequest eventRequest = null;
        AttributeRequest provisionRequest = null;

        List<EventBean> events = this.parent.getState().getEvents();
        if (events != null) {
            for (EventBean event : events) {
                if (event.isPendingDelete()) {

                    // TODO: need a better way to type these...
                    AttributeRequest req = null;
                    if ("Provisioning".equals(event.getDefinitionName())) {
                        if (provisionRequest == null) {
                            provisionRequest = new AttributeRequest();
                            provisionRequest.setName(ProvisioningPlan.ATT_IIQ_PROVISIONING_REQUESTS);
                            provisionRequest.setOperation(Operation.Remove);
                        }
                        req = provisionRequest;
                    }
                    else {
                        if (eventRequest == null) {
                            eventRequest = new AttributeRequest();
                            eventRequest.setName(ProvisioningPlan.ATT_IIQ_EVENTS);
                            eventRequest.setOperation(Operation.Remove);
                        }
                        req = eventRequest;
                    }

                    @SuppressWarnings("unchecked")
                    List<String> toRemove = (List<String>)req.getValue();
                    if (toRemove == null) {
                        toRemove = new ArrayList<String>();
                        req.setValue(toRemove);
                    }
                    toRemove.add(event.getId());
                }
            }
        }

        if (eventRequest != null)
            account.add(eventRequest);

        if (provisionRequest != null)
            account.add(provisionRequest);
    }

    //Actions
    public String deleteEvents() throws GeneralException {

        Identity ident = this.parent.getObject();
        if (ident != null) {
            Map<String,Boolean> selections = this.parent.getState().getEventSelections();
            if (selections != null) {
                for (String key : selections.keySet()) {
                    if (selections.get(key)) {
                        this.parent.getState().removeEvent(key);
                    }
                }
                this.parent.getState().clearEventSelections();
            }
        }

        this.parent.saveSession();
        return null;
    }


}
