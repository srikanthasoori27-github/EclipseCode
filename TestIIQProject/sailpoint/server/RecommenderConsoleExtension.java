/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.server;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;

import sailpoint.Version;
import sailpoint.api.CachedManagedAttributer;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.ApprovalItem;
import sailpoint.object.ApprovalSet;
import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.Reason;
import sailpoint.object.Recommendation;
import sailpoint.object.RecommenderDefinition;
import sailpoint.object.WorkItem;
import sailpoint.plugin.PluginsCache;
import sailpoint.recommender.IdentityEntitlementAddRequestBuilder;
import sailpoint.recommender.ReasonsLocalizer;
import sailpoint.recommender.RecommendationRequest;
import sailpoint.recommender.RecommendationResult;
import sailpoint.recommender.RecommendationService;
import sailpoint.recommender.RecommenderFactory;
import sailpoint.recommender.RecommenderUtil;
import sailpoint.tools.Console;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Util;

/**
 * This console extension implements the "recommender" command for the SailPoint
 * console
 */
public class RecommenderConsoleExtension implements Console.ConsoleCommandExtension {

    private SailPointConsole spConsole = null;

    public RecommenderConsoleExtension(SailPointConsole spConsole) {
        this.spConsole = spConsole;
    }

    @Override
    public String getCommandName() {
        return "recommender";
    }

    @Override
    public String getHelp() {
        return "manage and test recommendations";
    }

    /**
     * Entry point for the 'recommender' console command.
     *
     * @param args The arguments.
     * @param out The print writer.
     * @throws Exception
     */
    @Override
    public void execute(List<String> args, Console.FormattingOptions fOpts, PrintWriter out) throws GeneralException {
        if (args.size() == 0) {
            printRecommenderUsage(out);
        } else {
            String subCommand = args.remove(0);
            switch (subCommand) {

                case "list":
                    cmdRecommenderList(args, fOpts, out);
                    break;

                case "run":
                    cmdRecommenderRun(args, out);
                    break;

                case "use":
                    cmdRecommenderUse(args, out);
                    break;

                default:
                    printRecommenderUsage(out);
                    break;

            }
        }
    }

    /**
     * Prints the usage of the 'recommender' command.
     *
     * @param out The print writer.
     */
    private void printRecommenderUsage(PrintWriter out) {
        final String usage =
                "usage: recommender list                       List of recommenders \n" +
                "       recommender use <name>                 Use the given recommender\n" +
                "       recommender use --                     Use no recommender\n" +
                "       recommender run                        Run a recommender using a given request\n";

        out.println(usage);
    }

    /**
     * Change the 'recommender' to use.
     *
     * @param out The print writer.
     */
    private void cmdRecommenderUse(List<String> args, PrintWriter out) throws GeneralException {
        if (args.size() == 0) {
            out.println("no recommender name specified");
        } else {
            SailPointContext context = null;
            try {
                String recommenderName = args.get(0);
                context = spConsole.createContext();

                if ("--".equals(recommenderName)) {
                    // clear the recommender
                    recommenderName = null;
                    out.println("Clearing recommender selection");
                }
                else {
                    // validate it is a valid recommender name
                    RecommenderDefinition recommenderDefinition =
                            RecommenderFactory.getRecommenderDefinitionByIdOrName(context, recommenderName);
                    if (recommenderDefinition == null) {
                        out.format("Unable to find recommender with id or name: %s\n", recommenderName);
                        return;
                    }

                    // Use the name that was retrieved instead of the name provided.  This corrects case
                    // of the name.  Not needed, but it nice.  For example MYRecoMENDer might get changed
                    // to MyRecommender.
                    recommenderName = recommenderDefinition.getName();

                    // now validate that we can actually instantiate the recommender
                    try {
                        if (RecommenderFactory.recommenderByIdOrName(context, recommenderName) == null) {
                            out.format("Unable to create recommender with id or name: %s\n", recommenderName);
                            if (recommenderDefinition.getBoolean(RecommenderDefinition.ATT_IS_IAI_RECOMMENDER)) {
                                out.format("Possibly because: AIServices is not enabled\n");
                            }
                            return;
                        }
                    } catch (GeneralException ex) {
                        out.format("Unable to create recommender with id or name: %s\n", recommenderName);
                        out.format("%s\n", ex.getMessage());
                        return;
                    }
                    out.println("Setting recommender selection to '" + recommenderName + "'");
                }

                Configuration config =
                        context.getObjectByName(Configuration.class, Configuration.OBJ_NAME);
                config.getAttributes().put(Configuration.RECOMMENDER_SELECTED, recommenderName);
                context.saveObject(config);
                context.commitTransaction();

            } finally {
                SailPointFactory.releaseContext(context);
            }

        }
    }

    /**
     * Get a recommendation
     *
     * @param out The print writer.
     */
    private void cmdRecommenderRun(List<String> args, PrintWriter out) throws GeneralException {
        String identId = null;
        String entId = null;
        String workItemId = null;
        String requestFilePath = null;
        String jsonrequest = null;
        String translationKey = null;
        String languageTag = null;
        boolean update = false;
        boolean asBulk = false;
        boolean catalog = false;

        if (args.size() < 2) {
            printRecommenderRunUsage(out);
            return;
        }
        
        for(int i =0; i < args.size(); i++) {
            String arg = args.get(i);

            if (arg.equals("-bulk")) {
                asBulk = true;
            }
            if (arg.equals("-update")) {
                update = true;
            }
            else if (arg.equals("-catalog")) {
                i++;
                catalog = true;
                translationKey = args.get(i);
                i++;
                if (i < args.size()) { // handle optional param
                    languageTag = args.get(i);
                }
            }
            else if (arg.equals("-workitem")) {
                i++;
                workItemId = args.get(i);
            }
            /*
            example request file contents
            [
                {
                    "requestType":"UNKNOWN",
                    "attributes": {
                        "id":"123",
                        "ent":"456"
                    }
                },
                {
                    "requestType":"IDENTITY_ENTITLEMENT",
                    "attributes": {
                        "id":"346",
                        "ent":"780"
                    }
                },
            ]
            */
            else if (arg.equals("-requestfile")) {
                i++;
                requestFilePath = args.get(i);
            }
            /*
            example json request
            -jsonrequest "[{'requestType':'IDENTITY_ENTITLEMENT', 'attributes': { 'id':'346', 'ent':'780'}}]"
            -jsonrequest '[{"requestType":"IDENTITY_ENTITLEMENT", "attributes": { "id":"346", "ent":"780"}}]'
            */
            else if (arg.equals("-jsonrequest")) {
                i++;
                jsonrequest = args.get(i);
            }
            else if (arg.equals("-id")) {
                i++;
                identId = args.get(i);
            }
            else if (arg.equals("-ent")) {
                i++;
                entId = args.get(i);
            }
        }

        SailPointContext context = null;
        try {
            context = spConsole.createContext();
            RecommendationService recoService = getRecommender(context, out);
            if (recoService != null) {
                if (catalog) {
                    cmdRecommenderRunCatalog(context, recoService, translationKey, languageTag, out);
                }
                else if (requestFilePath != null) {
                    if (update || jsonrequest != null || workItemId != null || entId != null || identId != null) {
                        printRecommenderRunUsage(out);
                        return;
                    }
                    List<RecommendationRequest> recommendationRequests = getRecommendationRequests(requestFilePath, out);
                    if(recommendationRequests != null) {
                        cmdRecommenderRunIdentityEntitlement(context, recoService, recommendationRequests, asBulk, out);
                    }
                }
                else if (jsonrequest != null) {
                    if (update || workItemId != null || entId != null || identId != null) {
                        printRecommenderRunUsage(out);
                        return;
                    }
                    List<RecommendationRequest> recommendationRequests =
                            getRecommendationRequestsFromString(jsonrequest, out);
                    if(recommendationRequests != null) {
                        cmdRecommenderRunIdentityEntitlement(context, recoService, recommendationRequests, asBulk, out);
                    }
                }
                else if (workItemId != null) {
                    if (asBulk || entId != null || identId != null) {
                        printRecommenderRunUsage(out);
                        return;
                    }
                    cmdRecommenderRunWorkItem(context, recoService, out, workItemId, update);
                } else if (identId != null && entId != null) {
                    if (update) {
                        printRecommenderRunUsage(out);
                        return;
                    }
                    List<RecommendationRequest> recommendationRequests = new ArrayList<RecommendationRequest>();
                    recommendationRequests.add(new IdentityEntitlementAddRequestBuilder().
                            identityId(identId).entitlementId(entId).build());
                    cmdRecommenderRunIdentityEntitlement(context, recoService, recommendationRequests, asBulk, out);
                } else {
                    printRecommenderRunUsage(out);
                }
            }
        } finally {
            SailPointFactory.releaseContext(context);
        }
    }

    private List<RecommendationRequest> getRecommendationRequestsFromString(String jsonString, PrintWriter out) {
        List<RecommendationRequest> requests = new ArrayList<RecommendationRequest>();
        try {
            requests = JsonHelper.listFromJson(RecommendationRequest.class, jsonString);
        } catch (GeneralException e) {
            out.println("Unable to read requests from provided JSON string");
        }

        return requests;
    }

    private List<RecommendationRequest> getRecommendationRequests(String requestFilePath, PrintWriter out) {
        String requestFileFullPath = Util.findFile("user.dir", requestFilePath, true);
        File requestFile = new File(requestFileFullPath);
        List<RecommendationRequest> requests = new ArrayList<RecommendationRequest>();
        // Use objectMapper directly so we can deserialize from reader,
        // we could add more overrides in JsonHelper but this is an uncommon usage.
        ObjectMapper objectMapper = JsonHelper.getObjectMapper();
        CollectionType javaType = objectMapper.getTypeFactory()
                .constructCollectionType(List.class, RecommendationRequest.class);
        try (Reader reader = new FileReader(requestFile)) {
            requests = objectMapper.readValue(reader, javaType);
        } catch (FileNotFoundException e) {
            out.println("Unable to open file " + requestFilePath);
        } catch (IOException e) {
            out.println("Unable to read requests from file " + requestFilePath);
        }

        return requests;
    }

    private RecommendationService getRecommender(SailPointContext context, PrintWriter out) throws GeneralException {
        RecommendationService recoService = null;
        String recommenderName = RecommenderFactory.recommenderName(context);
        if (recommenderName == null) {
            out.println("No recommender is selected. Use 'recommender use <name>' to make selection");
        }
        else {
            try {
                recoService = RecommenderFactory.recommendationService(context);
            } catch (Exception e) {
                out.println("Could not construct recommender " + recommenderName + ": " + e.toString());
            }
        }

        if (recoService == null) {
            out.println("Could not construct recommender " + recommenderName);
        }

        return recoService;
    }

    private void printRecommenderRunUsage(PrintWriter out) {
        final String usage =
                "usage: recommender run -id <identId> -ent <entId>\n" +
                "                                              Get the recommendation for the given identity\n" +
                "       recommender run [-update] -workitem <workItemId>\n" +
                "                                              Get the recommendations for the workitem\n" +
                "       recommender run [-bulk] -requestfile <requestfile path>\n" +
                "                                              Get the recommendations for the requests(s)\n" +
                "       recommender run [-bulk] -jsonrequest <list of requests as JSON string>\n" +
                "                                              Get the recommendations for the request(s)\n" +
                "       recommender run -catalog <translationKey> [languageTag]\n" + 
                "                                              Get the translation using the given locale language tag (default: server locale)\n" +
                " \n" +
                " \n" +
                "   -bulk            Perform as a bulk request - If this option is specified, all recommender requests" +
                " will be combined into a single bulk recommender request.  If this option is omitted, each request will" +
                " be submitted individually\n" +
                "   -update          Update the object with the recommendation\n" +
                "   -requestfile     A JSON file containing a list of RecommendationRequests.\n";
                out.println(usage);
    }

    private void cmdRecommenderRunCatalog(SailPointContext context, RecommendationService recoService, String translationKey, String languageTag, PrintWriter out) {
        out.println("Making reason catalog request");
        out.println("============================");
        Locale locale = (languageTag == null) ? Locale.getDefault() : Locale.forLanguageTag(languageTag);
        String reason = recoService.getLocalizedReason(new Reason(translationKey), locale, TimeZone.getDefault());
        out.println(reason);
    }

    private void cmdRecommenderRunIdentityEntitlement(SailPointContext context, RecommendationService recoService, List<RecommendationRequest> requests,
                                                      boolean asBulk, PrintWriter out) throws GeneralException {
        List<RecommendationResult> results = new ArrayList<RecommendationResult>();
        if (asBulk) {
            out.println("Making a bulk request for " + requests.size() + " recommendations");
            results = recoService.getRecommendations(requests);
        } else {
            out.println("Making individual requests");
            for(RecommendationRequest request : requests) {
                results.add(recoService.getRecommendation(request));
            }
        }

        for(RecommendationResult result : results) {
            out.println("============================");
            if (result != null) {
                printRecommendationRequest(out, result.getRequest());
                Recommendation recommendation = result.getRecommendation();
                printRecommendation(context, out, recommendation);
            }
            else {
                out.println("** No result returned from recommender **");
            }
        }

        if(results.isEmpty()) {
            out.println("No recommender results were returned");
        } else {
            out.println("============================");
        }
    }

    private void cmdRecommenderRunWorkItem(SailPointContext context, RecommendationService recoService,
                                           PrintWriter out, String workItemId, boolean saveWorkItem) {
        WorkItem workItem = null;
        CachedManagedAttributer cachedManagedAttributer = new CachedManagedAttributer(context);

        try {
            workItem = context.getObjectById(WorkItem.class, workItemId);
            if (workItem == null) {
                out.println("Cannot find workItem " + workItemId);
                return;
            }
            if (!workItem.isType(WorkItem.Type.Approval)) {
                out.println("Workitem is not an approval workitem");
                return;
            }

            String identityId = null;
            String identityName = (String)workItem.get("identityName");
            Identity identity = (Identity)context.getObjectByName(Identity.class, identityName);
            if (identity != null) {
                identityId = identity.getId();
            }
            if (identityId == null) {
                out.println("WorkItem contains no valid identityName");
                return;
            }

            ApprovalSet approvalSet = workItem.getApprovalSet();
            if (approvalSet != null) {
                List<ApprovalItem> approvalItems = approvalSet.getItems();
                if (!Util.isEmpty(approvalItems)) {

                    if (!saveWorkItem) {
                        out.println("Making a bulk request for " + approvalItems.size() + " recommendations");
                        List<RecommendationRequest> requests =
                                RecommenderUtil.createRecommendationRequestsForApprovalSet(identityId, approvalSet,
                                        context, cachedManagedAttributer);

                        out.println("============================");
                        List<RecommendationResult> results = recoService.getRecommendations(requests);
                        for (RecommendationResult result : Util.safeIterable(results)) {
                            Recommendation reco = result.getRecommendation();
                            printRecommendationRequest(out, result.getRequest());
                            printRecommendation(context, out, reco);
                            out.println("============================");
                        }
                    }
                    else {
                        for(ApprovalItem approvalItem : approvalItems) {
                            if (recoService != null) {
                                RecommendationRequest recoReq = RecommenderUtil.createRecommendationRequestForApprovalItem(
                                        identityId, approvalItem, context, cachedManagedAttributer);

                                if (recoReq != null) {
                                    RecommendationResult recoResult = recoService.getRecommendation(recoReq);
                                    printRecommendationRequest(out, recoResult.getRequest());
                                    Recommendation reco = recoResult.getRecommendation();
                                    approvalItem.setRecommendation(reco);
                                    printRecommendation(context, out, reco);
                                    out.println("============================");
                                }
                            }
                        }
                        context.saveObject(workItem);
                        context.commitTransaction();
                        out.println();
                        out.println("The " + approvalItems.size() + " approvalItem(s) in the workitem " + workItemId + " now have saved recommendations");
                    }
                }
                else {
                    out.println("WorkItem contains no approval items");
                }
            }
            else {
                out.println("WorkItem contains no approval set");
            }
        }
        catch (GeneralException e) {
            out.println("Failed to run workitem " + workItemId + " : " + e.toString());
        }
    }

    private void printRecommendation(SailPointContext context, PrintWriter out, Recommendation recommendation) {
        out.println("Recommendation");
        out.println("    Decision: " + recommendation.getRecommendedDecision().toString());
        for(String reason : Util.safeIterable((new ReasonsLocalizer(context, recommendation)).getReasons())) {
            out.println("    Reason:   " + reason);
        }
    }

    private void printRecommendationRequest(PrintWriter out, RecommendationRequest recoRequest) {
        String type = recoRequest.getRequestType() == null ? null : recoRequest.getRequestType().toString();
        String identityId = recoRequest.getAttribute(RecommendationRequest.IDENTITY_ID, String.class);
        String entitlementId = recoRequest.getAttribute(RecommendationRequest.ENTITLEMENT_ID, String.class);
        String roleId = recoRequest.getAttribute(RecommendationRequest.ROLE_ID, String.class);

        out.println("RecommendationRequest");
        if (Util.isNotNullOrEmpty(type)) {
            out.println("    Type:         " + type);
        }
        if (Util.isNotNullOrEmpty(identityId)) {
            out.println("    Identity:     " + identityId);
        }
        if (Util.isNotNullOrEmpty(entitlementId)) {
            out.println("    Entitlement:  " + entitlementId);
        }
        if (Util.isNotNullOrEmpty(roleId)) {
            out.println("    Role:         " + roleId);
        }
    }

    private void cmdRecommenderList(List<String> args, Console.FormattingOptions fOpts, PrintWriter out) throws GeneralException {
        SailPointContext context = null;

        try {
            context = spConsole.createContext();

            boolean showColumnHeader = fOpts != null ? !fOpts.isNoHeaderColumn() : true;

            // find the RecommenderDefinitions
            QueryOptions qo = new QueryOptions();
            List<RecommenderDefinition> recDefs = context.getObjects(RecommenderDefinition.class, qo);

            if (Util.isEmpty(recDefs)) {
                out.println("no RecommenderDefinitions installed");
            } else {
                if (showColumnHeader) {
                    out.println("Name                      Status    ");
                    out.println("========================  ==========");
                }

                String currentRecommenderName = RecommenderFactory.recommenderName(context);

                for (RecommenderDefinition recDef : Util.safeIterable(recDefs)) {
                    String status = "Available";
                    String pluginName = recDef.getString(RecommenderDefinition.ATT_PLUGINNAME);
                    if (recDef.getName().equals(currentRecommenderName)) {
                        status = "In Use";
                    }

                    // if the plugin is disabled, mark the recommender Unavailable
                    if(Util.isNotNullOrEmpty(pluginName)) {
                        PluginsCache plugins = Environment.getEnvironment().getPluginsCache();
                        if(!plugins.getCachedPlugins().contains(pluginName)) {
                            status = "Unavailable - plugin disabled or not installed";
                        }
                    }

                    boolean isIAI = recDef.getBoolean(RecommenderDefinition.ATT_IS_IAI_RECOMMENDER);
                    if (isIAI) {
                        boolean isIAIEnabled = Version.isIdentityAIEnabled();
                        if (!isIAIEnabled) {
                            status = "Unavailable - AIServices is not enabled";
                        }
                    }

                    String recName = recDef.getName();
                    String line = String.format("%-24s  %s", recName, status);
                    out.println(line);
                }
            }

        } finally {
            SailPointFactory.releaseContext(context);
        }
    }



}
